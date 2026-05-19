# SahmFood POS — Architecture Notes

A Kotlin Multiplatform (Android + iOS) Point-of-Sale module showcasing
clean architecture, offline-first persistence, and background sync.

---

## 1. Project Structure

Four Gradle modules form a strict dependency chain (inner → outer):

```
composeApp ──> shared ──> data ──> domain
                  ↘─────────────────↗
```

| Module | Responsibility | Depends on |
|---|---|---|
| `:domain` | Pure business entities, repository contracts | nothing |
| `:data` | SQLDelight persistence, mock backend, sync glue | `:domain` |
| `:shared` | Compose UI, view models, DI, hardware mocks | `:data`, `:domain` |
| `:composeApp` | Android & iOS entry points (MainActivity, MainViewController) | `:shared` |

### Inside each module

```
domain/commonMain
├── Money.kt              value class — type-safe currency
├── Order.kt              Product, OrderItem, OrderStatus, SyncState, Order
├── PosRepository.kt      contract + SyncReport
└── ReceiptPrinter.kt     contract for hardware printer

data/commonMain
├── DatabaseFactory.kt    expect class — platform-specific driver
├── DataModule.kt         Koin module
├── MockBackend.kt        simulates remote API (idempotency + transient failures)
├── PosRepositoryImpl.kt  SQLDelight-backed implementation
├── SeedMenu.kt           initial menu data
└── sqldelight/.../*.sq   Products.sq + Orders.sq schemas

data/androidMain  → DatabaseFactory.android.kt (AndroidSqliteDriver)
data/iosMain      → DatabaseFactory.ios.kt     (NativeSqliteDriver)

shared/commonMain
├── di/SharedModule.kt              Koin singletons + viewModels
├── hardware/MockReceiptPrinter.kt  implements ReceiptPrinter
├── sync/SyncManager.kt             background sync orchestrator
└── ui/
    ├── App.kt                      root @Composable entry
    ├── theme/                      Material3 theme + colors
    └── pos/
        ├── PosScreen.kt            top-level screen + state hoisting
        ├── PosState.kt             @Immutable UI state
        ├── PosViewModel.kt         orchestration (no business logic)
        └── components/             CartPanel, CategoryStrip, ProductCard, ReceiptDialog

composeApp/androidMain
├── PosApplication.kt   bootstraps Koin, seeds DB, starts sync
└── MainActivity.kt     sets content = App()

composeApp/iosMain
└── MainViewController.kt   same bootstrap, gated by a `started` flag
```

---

## 2. Why This Architecture

### Clean Architecture across 3 layers

- **Domain (innermost)** — depends on nothing. Pure Kotlin. Fast unit tests.
- **Data** — implements domain interfaces. Knows about DB, network.
- **UI/Shared** — orchestrates domain + data into screens.

**The Dependency Inversion Principle is enforced by module boundaries.**
If a UI module tried to import a data class, Gradle would refuse — there's no
edge in the dependency graph. This makes wrong layering impossible.

### Kotlin Multiplatform

- ~95% of the code lives in `commonMain` and is shared verbatim between
  Android and iOS.
- Platform-specific code uses `expect/actual` only where unavoidable
  (`DatabaseFactory` needs a Context on Android, nothing on iOS).
- The same UI (Compose Multiplatform) renders on both — one design, one bug
  surface, one test pass.

### What it would cost to swap pieces

| Swap | Files touched |
|---|---|
| SQLDelight → Room | `:data` only |
| MockBackend → Ktor + real API | `:data` only |
| Compose Multiplatform → SwiftUI on iOS | `:shared/ui` and `iosApp/` only — domain & data untouched |
| Add a tablet self-serve kiosk app | Reuse `:domain` + `:data`, write new UI module |

---

## 3. State Management

**Pattern: MVVM + UDF (Unidirectional Data Flow).**

```
       USER ACTION
            │
            ▼
   ViewModel function  (addProduct, payCash, …)
            │
            ▼
   _uiState.update { it.copy(…) }   ← reducer pattern, immutable
            │
            ▼
       StateFlow<PosState>
            │
            ▼
   collectAsState() in PosScreen
            │
            ▼
       Compose re-renders
```

### Key choices

- **Single `PosState`** — one `@Immutable data class` holds everything the
  screen needs. No scattered LiveData/StateFlow per field.
- **`@Immutable` + `ImmutableList`** — Compose smart-skipping works at the
  granularity of the data class, so unrelated UI doesn't re-render.
- **`remember(key)` for derived UI values** — `categories` and
  `visibleProducts` live in the screen with a cache key, so the
  `map().distinct().sorted()` chain runs only when products or category
  actually change, not on every recomposition.
- **State hoisting** — `PosContent` and `MenuPane` are stateless
  composables. `PosScreen` is the only place that knows about the
  ViewModel. This makes `@Preview` and unit tests trivial.

### Intents (UI → VM)

Direct method calls on the ViewModel (`viewModel.addProduct(id)`), not a
sealed `Intent` class. The screen has 6 actions — MVI's boilerplate would
add cost without benefit at this scale.

### Events (VM → UI)

One-time events are surfaced through the state itself as nullable fields
(`receipt: String?`, `message: String?`) with `consume*()` methods to
clear them after the UI handles them. Snackbar messages from background
sync flow through the same `message` channel via a `SharedFlow` collected
in `init {}`.

---

## 4. Offline-First Strategy

> **The local DB is the source of truth. The network is a best-effort
> mirror.**

### Order lifecycle

```
 ┌──────────┐         ┌────────────────────┐
 │   USER   │ pay     │   PosRepository    │
 │ presses  │────────▶│ saveOrder(order,   │
 │ PAY CASH │         │  syncState=PENDING)│
 └──────────┘         └──────────┬─────────┘
                                 │
                                 ▼
                       ┌────────────────────┐
                       │  SQLDelight (DB)   │ ← committed transactionally
                       │  status=PAID       │   the moment pay is pressed.
                       │  syncState=PENDING │   The user never waits.
                       └──────────┬─────────┘
                                  │
                                  ▼
                       ┌────────────────────┐
                       │   SyncManager      │ ← background loop, decoupled
                       │   tries upload     │   from the UI thread.
                       └──────────┬─────────┘
                                  │
                          success │ failure
                                  ▼
                       syncState = SYNCED / FAILED (retry later)
```

The UI **always** observes the DB via `Flow<List<Product>>`. Network
state never leaks to the screen.

### Properties enforced

- **No data loss** — every paid order is in SQLite before `payCash`
  returns. Killing the app, losing power, or going offline preserves
  every transaction.
- **No UI blocking** — receipts print immediately. Sync is fire-and-forget.
- **Eventually consistent** — `SyncManager` keeps retrying until every
  pending order is SYNCED.

---

## 5. Sync Design

`SyncManager` is a singleton with two entry points:

```kotlin
suspend fun syncNow(): SyncReport                      // manual trigger
fun startBackgroundLoop(scope: CoroutineScope): Job    // long-running
```

### What runs each iteration

```
delay(currentDelay ± 20% jitter)
   ↓
mutex.withLock {
   pending = SELECT * FROM Orders WHERE syncState IN (PENDING, FAILED)
   for each pending order:
      result = upload(order)          ← injected lambda (MockBackend.submitOrder)
      orders.updateSyncState(order.id, if (result.isSuccess) SYNCED else FAILED)
   return SyncReport(attempted, succeeded, failed)
}
   ↓
nextDelay = if (anyFailures) min(currentDelay * 2, 60s) else 5s
   ↓
_events.tryEmit(report)   ← snackbar feedback
```

### Failure handling

| Concern | Mitigation |
|---|---|
| Server temporarily down | Exponential backoff capped at 60s |
| Server permanently down | Backoff stabilises; orders accumulate locally; no data loss |
| Network jitter causes duplicate retry | `order.id` is a 128-bit hex (32 chars) used as **idempotency key**. `MockBackend.submitOrder` rejects duplicates with `Result.success`, so the client retries without double-charging |
| Thousands of tablets reboot simultaneously after an outage | ±20% jitter spreads requests over a window, avoiding a thundering-herd spike on the server |
| Two coroutines trigger sync at once | `Mutex.withLock` serialises them; no double-submission |
| Sync coroutine crashes | `runCatching` in the loop catches it, logs to Napier, and increases backoff |

### What's intentionally simple (and what would change in prod)

- The "remote" is `MockBackend` — a class in the same process that
  simulates 15% transient failure. In prod this is a Ktor client.
- Conflicts (server says "this order was already cancelled") are not yet
  modelled. The contract `upload: suspend (Order) -> Result<Unit>` is
  the seam where conflict resolution would live.
- No WorkManager / iOS BackgroundTasks. If the OS kills the process,
  sync resumes on next launch from the DB.

---

## 6. Hardware Simulation: Receipt Printing

`ReceiptPrinter` is an interface in `:domain`:

```kotlin
interface ReceiptPrinter {
    suspend fun print(order: Order): String
}
```

The current implementation, `MockReceiptPrinter`, renders an ASCII
receipt and returns it as a String. The String shows up in the
`ReceiptDialog` so the UI confirms the cashier saw it.

Swapping to a real Bluetooth/network printer is one class change — the
ViewModel and the rest of the app are untouched.

---

## 7. Multi-Branch Scalability

The model assumes many branches, each with one or more tablets, talking
to one central server. The schema and IDs are designed for this from day
one.

| Concern | How the current design handles it |
|---|---|
| Branch identity | Every `Order` carries `branchId`. The server can shard / partition by it. |
| Per-tablet DB | Each tablet keeps its own SQLite — no cross-tablet locking. Network sync is the only fan-in. |
| Menu pushed from HQ | `seedIfEmpty` is the local seed today. In prod, replace with a `MenuSync` that fetches `/menu/{branchId}` and runs the same upsert. |
| Order ID uniqueness across branches | 128-bit random hex — Birthday-collision probability is negligible even at billions of orders. |
| Server-side tracking | The `Order` payload already contains `branchId`, `cashierId`, `orderNumber` (human), `id` (idempotency). Server can build per-branch dashboards. |
| Cashier audit | `cashierId` is on every order. |
| Per-branch product catalogue | Today every tablet seeds the same menu. To support per-branch menus, add `branchId` to `Products` (or a `BranchProducts` join table) and gate `selectAll` by it. |
| Brownouts and partial outages | Local DB keeps the till alive. When connectivity returns, `SyncManager` drains the backlog. |

### What would scale-out look like in production

1. **Auth layer** in `:data` — `AuthManager` injected into `PosViewModel`
   to drive `branchId` / `cashierId` instead of hard-coded `"BR-001"`.
2. **Real remote** — `RemoteBackend(ktorClient)` implementing the same
   shape as `MockBackend`. Same `SyncManager`, no changes to the loop.
3. **WorkManager (Android)** + **BGTaskScheduler (iOS)** — to survive
   the app being killed.
4. **Observability** — Napier already in place; pipe it to a remote sink
   (Crashlytics / Sentry) by adding a custom `Antilog`.
5. **Conflict resolution** — extend `upload` to return a richer result
   (`Conflict(serverVersion)` instead of `Result.failure`) and let the
   Repository decide whether to overwrite, merge, or surface to the UI.

---

## 8. Trade-offs & Decisions

| Decision | Why | What it costs |
|---|---|---|
| `Money` as `value class Money(minorUnits: Long)` | Exact integer math; no float rounding errors; zero allocation on JVM | Slightly more boxing in some iOS scenarios (interfaces, generics) — negligible here |
| Tax & discount in **basis points** (Int) | Integer math, no FP drift | Slight cognitive overhead — `1400 = 14%` |
| SQLDelight, not Room | KMP support; Android-only on Room | Less mature tooling on iOS side |
| Koin, not Hilt | KMP support | Runtime DI instead of compile-time graph |
| Compose Multiplatform | One UI codebase | iOS preview tooling is younger |
| Direct method calls on VM, not MVI `Intent` | Less boilerplate at this scale | Would refactor if the screen grew to 15+ actions |
| `private` Money constructor + `Money.of` / `Money.fromMinor` factories | Call sites are self-documenting (`Money.fromMinor(12550)` vs ambiguous `Money(12550)`) | Two factories instead of one constructor |
| Snackbar messages live in `PosState.message` | Survives configuration changes & process death without a separate event channel | Slight coupling between state and effect |

---

## 9. What I Would Improve With More Time

1. **Tests** — `Order.kt`, `Money.kt`, `PosRepositoryImpl` are very testable
   but no unit tests are checked in yet.
2. **Real backend integration** — replace `MockBackend` with a Ktor client.
3. **WorkManager on Android / BGTaskScheduler on iOS** — guarantee sync
   even when the process is killed.
4. **Auth flow** — make `branchId` / `cashierId` come from a login screen,
   not constructor defaults.
5. **Error UX** — `payCash` shows `"Payment failed: …"` in a snackbar today;
   could be a richer error sheet with retry.
6. **Currency / locale** — `Money.formatted` hard-codes `"EGP"`. Should
   come from a user preference.
7. **Conflict resolution** for sync (last-write-wins or CRDT).
8. **Compose previews** — `@Preview` for every component.

