package com.example.sahmfood.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.example.sahmfood.db.OrderItems as DbItem
import com.example.sahmfood.db.Orders as DbOrder
import com.example.sahmfood.db.Products as DbProduct
import com.example.sahmfood.db.SahmFoodDatabase
import com.example.sahmfood.domain.Money
import com.example.sahmfood.domain.Order
import com.example.sahmfood.domain.OrderItem
import com.example.sahmfood.domain.OrderStatus
import com.example.sahmfood.domain.PosRepository
import com.example.sahmfood.domain.Product
import com.example.sahmfood.domain.SyncReport
import com.example.sahmfood.domain.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.ExperimentalTime
import kotlin.time.Instant


class PosRepositoryImpl(private val db: SahmFoodDatabase) : PosRepository {

    private val products get() = db.productsQueries
    private val orders   get() = db.ordersQueries

    override fun getProducts(): Flow<List<Product>> =
        products.selectAll().asFlow().mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun seedIfEmpty(menu: List<Product>) = withContext(Dispatchers.Default) {
        if (products.countAll().executeAsOne() > 0) return@withContext
        db.transaction {
            menu.forEach {
                products.upsert(it.id, it.sku, it.name, it.price.minorUnits,
                    it.taxRateBps.toLong(), it.category, true)
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun saveOrder(order: Order, syncState: SyncState) =
        withContext(Dispatchers.Default) {
            db.transaction {
                orders.upsertOrder(
                    id = order.id,
                    orderNumber = order.orderNumber,
                    branchId = order.branchId,
                    cashierId = order.cashierId,
                    status = order.status.name,
                    discountBps = order.discountBps.toLong(),
                    createdAtMs = order.createdAt.toEpochMilliseconds(),
                    paidAtMs = order.paidAt?.toEpochMilliseconds(),
                    syncState = syncState.name,
                )
                orders.deleteItemsForOrder(order.id)
                order.items.forEach { i ->
                    orders.insertItem(order.id, i.productId, i.productName,
                        i.unitPrice.minorUnits, i.taxRateBps.toLong(), i.quantity.toLong())
                }
            }
        }

    override suspend fun trySyncPending(upload: suspend (Order) -> Result<Unit>): SyncReport =
        withContext(Dispatchers.Default) {
            val pending = orders.selectPendingSync().executeAsList().map { row ->
                val items = orders.selectItemsForOrder(row.id).executeAsList().map { it.toDomain() }
                row.toDomain(items)
            }
            if (pending.isEmpty()) return@withContext SyncReport.IDLE

            var ok = 0
            var fail = 0
            for (order in pending) {
                val success = upload(order).isSuccess
                orders.updateSyncState(
                    if (success) SyncState.SYNCED.name else SyncState.FAILED.name,
                    order.id,
                )
                if (success) ok++ else fail++
            }
            SyncReport(attempted = pending.size, succeeded = ok, failed = fail)
        }
}

private fun DbProduct.toDomain() = Product(id, sku, name, Money.fromMinor(priceMinor),
    taxBps.toInt(), category)

private fun DbItem.toDomain() = OrderItem(productId, productName,
    Money.fromMinor(unitMinor), taxBps.toInt(), quantity.toInt())

@OptIn(ExperimentalTime::class)
private fun DbOrder.toDomain(items: List<OrderItem>) = Order(
    id = id,
    orderNumber = orderNumber,
    branchId = branchId,
    cashierId = cashierId,
    items = items,
    status = OrderStatus.valueOf(status),
    discountBps = discountBps.toInt(),
    createdAt = Instant.fromEpochMilliseconds(createdAtMs),
    paidAt = paidAtMs?.let { Instant.fromEpochMilliseconds(it) },
)
