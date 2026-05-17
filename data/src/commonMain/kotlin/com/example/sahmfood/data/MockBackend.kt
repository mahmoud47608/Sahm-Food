package com.example.sahmfood.data

import com.example.sahmfood.domain.Order
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random


class MockBackend {

    private val mutex = Mutex()
    private val processedOrderIds = mutableSetOf<String>()

    private val transientFailureRate = 0.15

    suspend fun submitOrder(order: Order): Result<Unit> {
        delay(400)

        return mutex.withLock {

            if (order.id in processedOrderIds) {
                Napier.i { "[Backend] Idempotent replay for ${order.orderNumber} — already processed" }
                return@withLock Result.success(Unit)
            }

            if (Random.nextDouble() < transientFailureRate) {
                Napier.w { "[Backend] Transient failure for ${order.orderNumber}" }
                return@withLock Result.failure(TransientNetworkException("Simulated timeout"))
            }

            processedOrderIds += order.id
            Napier.i { "[Backend] Accepted ${order.orderNumber} (total processed: ${processedOrderIds.size})" }
            Result.success(Unit)
        }
    }
}

class TransientNetworkException(message: String) : RuntimeException(message)
