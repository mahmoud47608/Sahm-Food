package com.example.sahmfood.domain

import kotlinx.coroutines.flow.Flow

interface PosRepository {

    fun observeProducts(): Flow<List<Product>>

    suspend fun seedIfEmpty(menu: List<Product>)

    suspend fun saveOrder(order: Order, syncState: SyncState = SyncState.PENDING)

    suspend fun trySyncPending(upload: suspend (Order) -> Result<Unit>): Int
}
