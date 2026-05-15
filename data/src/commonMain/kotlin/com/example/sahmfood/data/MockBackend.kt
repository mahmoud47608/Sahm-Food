package com.example.sahmfood.data

import com.example.sahmfood.domain.Order
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * Backend وهمي بيمثل سيرفر POS مركزي.
 *
 * بيوضح حاجتين مهمتين في أي offline-first system:
 *
 * 1) **Idempotency** — السيرفر بيستخدم `order.id` كـ idempotency key.
 *    لو الـ client أرسل نفس الـ order مرتين (مثلاً تايم-اوت بعد ما السيرفر
 *    خزّن الـ order)، السيرفر بيرجّع Success ساكت بدون ما يعمل double-charge.
 *
 * 2) **Transient vs permanent failures** — السيرفر بيفرّق بين:
 *    - فشل عابر (network jitter) → الـ client يفضل يحاول.
 *    - فشل دائم (مثلاً منتج اتشال من القائمة) → الـ client يبطّل retry.
 *
 *    في الـ mock بنسيمول الـ failure rate بـ 15% ، كلهم transient
 *    عشان الـ retry loop يتختبر فعلياً.
 *
 * In production: ده هيتبدّل بـ Ktor client بيكلم REST/gRPC endpoint.
 * الـ contract اللي محتاجه الـ repository (suspend (Order) -> Result<Unit>)
 * مش هيتغير، ف الـ migration cost صفر.
 */
class MockBackend {

    private val mutex = Mutex()
    /** سجل الـ orders اللي السيرفر شافها قبل كده (state على السيرفر). */
    private val processedOrderIds = mutableSetOf<String>()

    /** نسبة الفشل العابر — رفعناها شوية عشان الـ retry يبان. */
    private val transientFailureRate = 0.15

    suspend fun submitOrder(order: Order): Result<Unit> {
        // محاكاة round-trip شبكة
        delay(400)

        return mutex.withLock {
            // (1) Idempotency check — حتى لو الفشل العشوائي ضرب، الـ retry
            //     بنفس الـ order.id بيرجع success بدون ما يعيد الـ processing.
            if (order.id in processedOrderIds) {
                Napier.i { "[Backend] Idempotent replay for ${order.orderNumber} — already processed" }
                return@withLock Result.success(Unit)
            }

            // (2) محاكاة فشل عابر (network timeout / 503)
            if (Random.nextDouble() < transientFailureRate) {
                Napier.w { "[Backend] Transient failure for ${order.orderNumber}" }
                return@withLock Result.failure(TransientNetworkException("Simulated timeout"))
            }

            // (3) نجاح → خزّن الـ id كـ "seen" قبل ما ترجع
            processedOrderIds += order.id
            Napier.i { "[Backend] Accepted ${order.orderNumber} (total processed: ${processedOrderIds.size})" }
            Result.success(Unit)
        }
    }

    /** للـ debugging والـ tests فقط. */
    suspend fun processedCount(): Int = mutex.withLock { processedOrderIds.size }
}

/** فشل عابر — الـ client يفضل يحاول. */
class TransientNetworkException(message: String) : RuntimeException(message)
