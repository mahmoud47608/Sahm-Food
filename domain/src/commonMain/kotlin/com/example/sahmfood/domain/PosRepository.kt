package com.example.sahmfood.domain

import kotlinx.coroutines.flow.Flow

interface PosRepository {

    fun getProducts(): Flow<List<Product>>
    suspend fun seedIfEmpty(menu: List<Product>)
    suspend fun saveOrder(order: Order, syncState: SyncState = SyncState.PENDING)
    suspend fun trySyncPending(upload: suspend (Order) -> Result<Unit>): SyncReport
}

data class SyncReport(
    val attempted: Int,
    val succeeded: Int,
    val failed: Int,
) {
    val hasFailures: Boolean get() = failed > 0
    val isIdle: Boolean get() = attempted == 0

    companion object {
        val IDLE = SyncReport(0, 0, 0)
    }
}
