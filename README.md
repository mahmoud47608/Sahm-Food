# Sahm POS — Mini POS System (Kotlin Multiplatform)

A simplified **offline-first Point-of-Sale** module built with Kotlin Multiplatform and Compose Multiplatform, targeting Android and iOS from a single shared codebase.

> **Status:** Functional demo. Not production-hardened — see [Trade-offs](#trade-offs--what-id-do-with-more-time).

---

## What it does

A cashier on a tablet can:

1. Browse a product menu by category.
2. Add items to a cart, change quantities, remove items.
3. Apply a flat-percentage discount.
4. Pay in cash → order is **persisted locally first**, then a receipt prints, then a background sync attempts to upload it to the server.
5. Keep operating with no internet — orders queue locally and sync when connectivity returns.

The same Compose UI runs on Android and iOS. The same business logic, persistence layer, and sync engine are written **once** in `commonMain`.

---

## Quick start

```bash
# Android
./gradlew :composeApp:assembleDebug
# or open in Android Studio and Run.

# iOS
# Open iosApp/iosApp.xcodeproj in Xcode → Run.
```

The app is seeded with a 10-item demo menu on first launch. Cart and receipt are simulated; no real hardware or backend is required.

---

## Project structure

```
SahmFood/
├── domain/      ← Pure Kotlin. Zero framework deps. Entities + Repository contract.
├── data/        ← SQLDelight (SQLite), repository impl, mock backend.
├── shared/      ← Compose UI, ViewModel, SyncManager, Mock hardware, DI module.
└── composeApp/  ← Thin Android (MainActivity) + iOS (MainViewController) entry points.
```

Why four modules instead of one big one?

| Module | Depends on | Purpose |
|---|---|---|
| `:domain` | nothing (only kotlinx-coroutines + datetime) | Pure business logic. Easy to unit-test in isolation. |
| `:data` | `:domain` | Persistence + remote (currently mocked). Knows about SQL, doesn't know about UI. |
| `:shared` | `:domain`, `:data` | UI + presentation + DI wiring. Compose Multiplatform lives here. |
| `:composeApp` | `:shared` | Just the platform-specific entry points (Application class, ViewController). |

The arrows only go down. The domain layer doesn't know SQL exists.

---

## Core architecture

### Layers (Clean Architecture, slimmed down)

```
┌────────────────────────────────────────────────────────────────────────────┐
│  Compose UI (PosScreen)        ← :shared (commonMain)                      │
│         ▼                                                                  │
│  PosViewModel  (UiState as StateFlow)                                      │
│         ▼                              ▼                                   │
│  PosRepository (interface)        SyncManager                              │
│         ▼                              ▼                                   │
│  PosRepositoryImpl (SQLDelight)   MockBackend (network simulation)         │
└────────────────────────────────────────────────────────────────────────────┘
       :data + :domain                  :data + :shared
```

### State management

- **Single source of truth per screen:** a single `UiState` `data class` exposed as `StateFlow<UiState>`.
- **Unidirectional flow:** UI emits intents (`vm.add(productId)`) → ViewModel updates state → Compose observes via `collectAsStateWithLifecycle()`.
- **The `Order` itself is immutable.** Every mutation (`addItem`, `changeQuantity`) returns a new `Order`. This makes state transitions trivial to reason about and eliminates a whole class of concurrency bugs.

### Hardware simulation

- **Receipt printing** is implemented via `ReceiptPrinter` interface in `:domain`, with `MockReceiptPrinter` producing an ASCII receipt 32 chars wide (Arabic + English) and logging it via Napier. Swapping in a real ESC/POS printer means writing one new implementation and changing one DI binding — no other code changes.

---

## Offline-first strategy

This is the heart of the system. The design rule is:

> **Every state-changing user action commits to the local DB before any network call. The user never waits for the network.**

### The flow when the cashier hits "Pay Cash"

```
                 ┌─────────────────────────────────────────────┐
                 │  1. order.markPaid()  (domain rule check)   │
                 └────────────────────┬────────────────────────┘
                                      ▼
                 ┌─────────────────────────────────────────────┐
                 │  2. repo.saveOrder(order) with PENDING flag │
                 │     → row in SQLite, transactional.         │
                 └────────────────────┬────────────────────────┘
                                      ▼
                 ┌─────────────────────────────────────────────┐
                 │  3. printer.print(order)                    │
                 │     → cashier hands receipt to customer.    │
                 └────────────────────┬────────────────────────┘
                                      ▼
                 ┌─────────────────────────────────────────────┐
                 │  4. syncManager.syncNow()  (fire-and-forget)│
                 │     If network works: SYNCED.               │
                 │     If not: stays PENDING/FAILED;           │
                 │     background loop will retry.             │
                 └─────────────────────────────────────────────┘
```

If steps 1–3 succeed but step 4 fails, **the customer still gets their receipt and the order is safe in SQLite.** The cashier never sees a spinner waiting on a server.

### Storage

- **SQLDelight** — type-safe SQL with KMP-native drivers (`AndroidSqliteDriver` on Android, `NativeSqliteDriver` on iOS).
- Two tables: `Orders` (with `syncState`), `OrderItems` (FK with `ON DELETE CASCADE`).
- An index on `syncState` so the sync loop's `WHERE syncState IN ('PENDING','FAILED')` query stays O(log n).
- All schema lives in `data/src/commonMain/sqldelight/` — one source of truth, generates Kotlin code for both platforms.

### Sync design

The sync engine is two pieces:

**A. `SyncManager.syncNow()`** — one-shot attempt that:
1. Loads every order with `syncState IN ('PENDING','FAILED')`.
2. Uploads each via `MockBackend.submitOrder`.
3. Updates each row to `SYNCED` or `FAILED` based on the result.
4. Returns a `SyncReport(attempted, succeeded, failed)`.

A mutex around it makes concurrent calls (e.g., one from `payCash`, one from the background loop) serialize naturally rather than double-uploading.

**B. `SyncManager.startBackgroundLoop()`** — a coroutine that calls `syncNow()` on an interval. The interval uses **exponential backoff** when uploads keep failing:

| Last cycle | Next delay |
|---|---|
| Nothing to sync (idle) | 5 s — keep responsive, the user can sell any second |
| Success (full or partial) | 5 s — back to baseline |
| All uploads failed | × 2, capped at 60 s |
| Loop itself crashed | × 2, capped at 60 s |

This prevents hammering a dead server while still recovering within a minute of connectivity returning.

### Conflict handling & idempotency

This is the most underrated part of any offline-first system. **`MockBackend` demonstrates the contract** that any real backend would have to honor:

#### 1. Idempotency

Every `Order` carries a client-generated 128-bit ID (`Order.newDraft`). The backend treats this as the **idempotency key**.

`MockBackend` keeps an in-memory `processedOrderIds` set. The scenario it solves:

```
Client → Server: POST /orders { id: "abc123", ... }
Server processes the order successfully, stores it.
Server → Client: 200 OK  ✓
            (network drops here ☠)
Client (didn't see the response): retries.
Client → Server: POST /orders { id: "abc123", ... }  ← same id
Server: "I've seen abc123 already" → 200 OK, no double-charge.
```

This is why the `Order.id` is generated **on the client at draft time**, not assigned by the server. The client can retry indefinitely with no fear of duplicate charges.

#### 2. Failure classification

`MockBackend` distinguishes:

- **Transient failures** (`TransientNetworkException`) — keep retrying.
- *(Architecturally allowed)* **Permanent failures** (e.g., 4xx) — would mark the order with a different state and surface to the user. Not simulated in the mock, but the `Result<Unit>` contract is wide enough to carry this distinction in a real impl.

#### 3. The "what about real conflicts?" question

True write-write conflicts on the same `Order` shouldn't happen in this design:

- An `Order` is owned by one cashier session.
- It's append-only after `markPaid()` — no edits, no deletes.
- Refunds/voids in real systems are modeled as **new** orders linked to the original, not as edits — so no merge logic is needed.

Where conflicts could appear: the **product catalog** (price changed at HQ while cashier is offline). The current design ignores this — the cashier transacts with whatever price was last seen. A real implementation would version the menu and refuse syncs whose price differs from the canonical one, prompting the cashier to re-confirm. This is called out in [Trade-offs](#trade-offs--what-id-do-with-more-time).

---

## Multi-branch scaling

The schema already includes `branchId` and `cashierId` on every order — that wasn't accidental. Here's how this system scales to dozens or hundreds of restaurant branches:

### Data model

- **Orders are partitioned by `branchId`.** A central server stores all orders but indexes them by branch.
- **Each device only sees its own branch's data** — sync is bidirectional but the read side filters by `branchId`.
- **Idempotency keys are globally unique** (128-bit IDs are collision-safe even across 100k devices).

### Sync topology

```
        [HQ Dashboard / Analytics]
                  ▲
                  │  reads (aggregated)
                  │
        ┌─────────┴─────────┐
        │   Sync Backend    │  ← REST or gRPC. Idempotent POST /orders.
        │   (regional)      │     Per-branch partitioning. Source of truth.
        └─────┬──────┬──────┘
              ▲      ▲
              │      │
         ┌────┴──┐ ┌─┴────┐  ...
         │BR-001 │ │BR-002│  (each branch = one or more tablets)
         │ tablet│ │tablet│
         └───────┘ └──────┘
```

### What changes vs. the demo

| Concern | Demo | Multi-branch real |
|---|---|---|
| Backend | In-memory `MockBackend` set | Per-region REST API with Postgres/DynamoDB |
| Auth | Hardcoded `BR-001` / `C-001` | Login → JWT carrying `branchId` + `cashierId` |
| Menu | Bundled `seedMenu()` | Versioned menu pull on startup + push from HQ |
| Reporting | None | Server-side aggregation; clients never compute cross-branch totals |
| Pricing changes | Static | HQ pushes new menu version; clients reject orders priced against stale versions, prompting re-entry |
| Audit | Napier logs only | Append-only event log shipped centrally; orders never deleted |

### Scale-relevant properties that already exist

- **Local-first reads (`Flow<List<Product>>`)** — UI never blocks on network, even with thousands of products.
- **Bounded sync work** — `selectPendingSync` query is cheap because of the index; even if a branch is offline for a week and accumulates 1000 orders, sync drains them in one pass.
- **Backoff with cap** — if a regional server is melting, the fleet doesn't make it worse.
- **Idempotent submission** — re-shipping a queue against a recovered server can't double-charge.

### What you'd add for true production scale

- **Per-day / per-branch order numbering** — currently random `A####`. Real POS uses sequential per-branch sequences for human-readable receipts and reconciliation.
- **Reconciliation job** — nightly, server reports back to each branch tablet what it has so the device can detect "I have orders the server doesn't" and re-push them.
- **Schema migrations** — SQLDelight supports `verifyMigrations`; needed before the first prod deploy.
- **Conflict alerts in UI** — a small banner showing "N orders pending sync, last attempt failed".

---

## State management approach

There's one `UiState` per screen, held by the ViewModel as `MutableStateFlow`. It contains:

- The current `Order` (immutable; cart edits replace it).
- The product list (mirrored from the DB Flow).
- Transient UI flags: `isPaying`, `receipt`, `message`.

Why this and not, say, MVI with reducers, or a state machine library?

- **Cost vs benefit.** A POS cart screen has maybe a dozen distinct user actions. The boilerplate of a reducer-per-action would outweigh the clarity. `_state.update { ... }` is already pure and testable.
- **Compose collaborates naturally with `StateFlow`.** No adapters needed.
- **Order itself is immutable.** All the "real" state transitions are pure functions on the `Order` data class — testable without any ViewModel or Compose at all.

The trade-off: harder to time-travel debug than a reducer setup. Acceptable for an app this size; would revisit at ~5+ screens.

---

## Why these choices

| Decision | Why | Alternative considered |
|---|---|---|
| **Kotlin Multiplatform + Compose Multiplatform** | Share business logic AND UI between Android & iOS, with native performance. | Flutter (good UI, second language, no native interop). React Native (JS perf concerns). KMP + native UIs (more code, more divergence). |
| **SQLDelight** | Type-safe SQL, KMP-native, no annotation processing, easy to introspect. | Room (Android only). Realm (heavy, weird query language). Plain SQLite (no codegen, error-prone). |
| **Koin** | Lightweight, works in commonMain, no kapt. | Dagger/Hilt (Android-only, kapt slowness). Kodein (similar, smaller ecosystem). |
| **Four modules, not one** | Pure domain layer can be tested without Android/iOS setup. Sync code can be rewritten without touching UI. | Single module — faster build, but bleeds responsibilities. |
| **Client-generated order IDs** | Idempotent retries, no server round-trip to create a draft. | Server-generated IDs — would require online creation. |
| **Mock backend in-process** | No infra needed to demo the contract. | Local docker/json-server — more realistic but adds setup friction. |

---

## Trade-offs — what I'd do with more time

Listed roughly in order of how much they'd matter in a real deployment.

1. **Real Ktor client backend.** Today `MockBackend` is a `Map` — the architecture's clean, the contract is right, but no actual HTTP. Adding it is one new class implementing the same `(Order) -> Result<Unit>` signature.
2. **Conflict handling for menu/price drift.** Already covered in the conflict section above. The data flow is in place; the policy isn't.
3. **Schema migrations.** `verifyMigrations.set(true)` is on but there's only one schema version. First prod ship needs a real migration story.
4. **Proper auth.** `branchId` and `cashierId` are hardcoded `BR-001` / `C-001`. A login screen wiring these from a JWT is straightforward, just out of scope here.
5. **Receipt format → real printer.** ASCII works; ESC/POS over USB or Bluetooth is the next implementation of `ReceiptPrinter`.
6. **More tests.** Currently only a placeholder `1+2=3` exists. The pure `:domain` module is the easiest win — `Money`, `Order.addItem`, tax/discount math are all pure functions.
7. **Battery-aware sync.** Mobile devices should suspend the background loop when on low battery and the screen is off. Trivial with `WorkManager` on Android, slightly more involved with `BGTaskScheduler` on iOS.
8. **Order numbering.** Random `A####` is fine for a demo, terrible for reconciliation. Replace with per-day, per-branch sequential.
9. **Sync UI affordances.** No badge today says "3 orders pending sync." A persistent banner with manual "Sync now" + last-success time would help cashiers trust the system.
10. **Multi-tab / multi-cashier on one device.** Out of scope; would require session scoping the `PosViewModel`.

---

## Performance & scaling notes

The hottest paths are (a) the cart rendering on every key-tap, and (b) the sync loop.

- The cart is `LazyColumn` and `LazyVerticalGrid`, keyed by stable product IDs — Compose skips unchanged items. Adding a thousand cart lines stays smooth.
- The sync query uses `WHERE syncState IN ('PENDING','FAILED')` against an index, so an offline week's worth of orders drains in one pass without table-scan cost.
- All DB writes are `Dispatchers.Default` and wrapped in `db.transaction { ... }`, so multi-row order saves are atomic and don't block the main thread.

For a single-branch tablet doing a few hundred orders a day, the system has tens of thousands of orders of headroom. For a chain rolling out to hundreds of branches, the bottleneck shifts to the server side — see [Multi-branch scaling](#multi-branch-scaling).

---

## Tech stack

- Kotlin 2.3.21
- Compose Multiplatform 1.10.3 (Android + iOS)
- SQLDelight 2.0.2
- Koin 4.0.4
- kotlinx-coroutines 1.10.2, kotlinx-datetime 0.6.2
- Napier 2.7.1 (multiplatform logging)
- AGP 8.11.2, Android compileSdk 36, minSdk 24

---

## Build & run

```bash
# Android debug APK
./gradlew :composeApp:assembleDebug

# Run unit tests (currently minimal)
./gradlew :domain:allTests
./gradlew :data:allTests

# iOS — open in Xcode
open iosApp/iosApp.xcodeproj
```

See [ARCHITECTURE.md](./ARCHITECTURE.md) for a deeper dive into how each module is wired.
