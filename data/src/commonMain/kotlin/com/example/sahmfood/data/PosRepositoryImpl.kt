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


class PosRepositoryImpl(
    private val db: SahmFoodDatabase,
) : PosRepository {

    private val productQueries = db.productsQueries
    private val orderQueries = db.ordersQueries

    override suspend fun seedIfEmpty(
        menu: List<Product>,
    ) = withContext(Dispatchers.Default) {
        val hasData = productQueries.countAll().executeAsOne() > 0
        if (hasData) return@withContext

        db.transaction {
            menu.forEach(::upsertProduct)
        }
    }

    override fun getProducts(): Flow<List<Product>> {
        return productQueries
            .selectAll()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map(List<DbProduct>::toProducts)
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun saveOrder(
        order: Order,
        syncState: SyncState,
    ) = withContext(Dispatchers.Default) {
        db.transaction {
            upsertOrder(order, syncState)
            orderQueries.deleteItemsForOrder(order.id)
            order.items.forEach { item ->
                insertOrderItem(order.id, item)
            }
        }
    }

    override suspend fun trySyncPending(
        upload: suspend (Order) -> Result<Unit>,
    ): SyncReport = withContext(Dispatchers.Default) {
        val pendingOrders = getPendingOrders()
        if (pendingOrders.isEmpty()) {
            return@withContext SyncReport.IDLE
        }

        var successCount = 0
        var failedCount = 0
        pendingOrders.forEach { order ->
            val isUploaded = upload(order).isSuccess
            updateSyncState(order.id, isUploaded)
            if (isUploaded) successCount++ else failedCount++
        }

        SyncReport(
            attempted = pendingOrders.size,
            succeeded = successCount,
            failed = failedCount,
        )
    }

    private fun upsertProduct(product: Product) {
        productQueries.upsert(
            id = product.id,
            sku = product.sku,
            name = product.name,
            priceMinor = product.price.minorUnits,
            taxBps = product.taxRateBps.toLong(),
            category = product.category,
            available = true,
        )
    }

    @OptIn(ExperimentalTime::class)
    private fun upsertOrder(
        order: Order,
        syncState: SyncState,
    ) {
        orderQueries.upsertOrder(
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
    }

    private fun insertOrderItem(
        orderId: String,
        item: OrderItem,
    ) {
        orderQueries.insertItem(
            orderId = orderId,
            productId = item.productId,
            productName = item.productName,
            unitMinor = item.unitPrice.minorUnits,
            taxBps = item.taxRateBps.toLong(),
            quantity = item.quantity.toLong(),
        )
    }

    private fun updateSyncState(
        orderId: String,
        success: Boolean,
    ) {
        orderQueries.updateSyncState(
            state = if (success) SyncState.SYNCED.name else SyncState.FAILED.name,
            id = orderId,
        )
    }

    private fun getPendingOrders(): List<Order> {
        return orderQueries
            .selectPendingSync()
            .executeAsList()
            .map { order ->
                val items = orderQueries
                    .selectItemsForOrder(order.id)
                    .executeAsList()
                    .map(DbItem::toDomain)
                order.toDomain(items)
            }
    }
}

private fun List<DbProduct>.toProducts(): List<Product> {
    return map(DbProduct::toDomain)
}

private fun DbProduct.toDomain(): Product {
    return Product(
        id = id,
        sku = sku,
        name = name,
        price = Money.fromMinor(priceMinor),
        taxRateBps = taxBps.toInt(),
        category = category,
    )
}

private fun DbItem.toDomain(): OrderItem {
    return OrderItem(
        productId = productId,
        productName = productName,
        unitPrice = Money.fromMinor(unitMinor),
        taxRateBps = taxBps.toInt(),
        quantity = quantity.toInt(),
    )
}

@OptIn(ExperimentalTime::class)
private fun DbOrder.toDomain(
    items: List<OrderItem>,
): Order {
    return Order(
        id = id,
        orderNumber = orderNumber,
        branchId = branchId,
        cashierId = cashierId,
        items = items,
        status = OrderStatus.valueOf(status),
        discountBps = discountBps.toInt(),
        createdAt = Instant.fromEpochMilliseconds(createdAtMs),
        paidAt = paidAtMs?.let(Instant::fromEpochMilliseconds),
    )
}
