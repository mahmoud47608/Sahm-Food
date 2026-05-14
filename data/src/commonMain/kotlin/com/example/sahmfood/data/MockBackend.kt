package com.example.sahmfood.data

import com.example.sahmfood.domain.Order
import kotlinx.coroutines.delay
import kotlin.random.Random


suspend fun mockUpload(@Suppress("unused") order: Order): Result<Unit> {
    delay(400)
    return if (Random.Default.nextDouble() < 0.10)
        Result.failure(RuntimeException("Network timeout"))
    else
        Result.success(Unit)
}
