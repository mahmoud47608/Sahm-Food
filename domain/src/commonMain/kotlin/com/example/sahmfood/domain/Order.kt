package com.example.sahmfood.domain

import kotlin.time.Clock
import kotlin.time.Clock.System
import kotlin.time.Instant
import kotlin.random.Random

data class Product(
    val id: String,
    val sku: String,
    val name: String,
    val price: Money,
    val taxRateBps: Int = 1400,
    val category: String = "General",
)

data class OrderItem(
    val productId: String,
    val productName: String,
    val unitPrice: Money,
    val taxRateBps: Int,
    val quantity: Int,
) {
    val lineSubtotal: Money get() = unitPrice * quantity
    val lineTax:      Money get() = lineSubtotal.percent(taxRateBps)
    val lineTotal:    Money get() = lineSubtotal + lineTax
}

enum class OrderStatus { DRAFT, PAID }

enum class SyncState { PENDING, SYNCED, FAILED }

data class Order(
    val id: String,
    val orderNumber: String,
    val branchId: String,
    val cashierId: String,
    val items: List<OrderItem> = emptyList(),
    val status: OrderStatus = OrderStatus.DRAFT,
    val discountBps: Int = 0,
    val createdAt: Instant = Clock.System.now(),
    val paidAt: Instant? = null,
) {
    val subtotal: Money get() = items.fold(Money.ZERO) { acc, i -> acc + i.lineSubtotal }
    val tax:      Money get() = items.fold(Money.ZERO) { acc, i -> acc + i.lineTax }
    val discount: Money get() = subtotal.percent(discountBps)
    val total:    Money get() = subtotal + tax - discount

    val itemCount: Int get() = items.sumOf { it.quantity }
    val isEmpty: Boolean get() = items.isEmpty()

    fun addItem(product: Product, qty: Int = 1): Order {
        require(qty > 0)
        check(status == OrderStatus.DRAFT)
        val existing = items.indexOfFirst { it.productId == product.id }
        val newItems = if (existing >= 0) {
            items.toMutableList().also {
                it[existing] = it[existing].copy(quantity = it[existing].quantity + qty)
            }
        } else {
            items + OrderItem(product.id, product.name, product.price, product.taxRateBps, qty)
        }
        return copy(items = newItems)
    }

    fun changeQuantity(productId: String, newQty: Int): Order {
        check(status == OrderStatus.DRAFT)
        return if (newQty <= 0) removeItem(productId)
        else copy(items = items.map {
            if (it.productId == productId) it.copy(quantity = newQty) else it
        })
    }

    fun removeItem(productId: String): Order {
        check(status == OrderStatus.DRAFT)
        return copy(items = items.filterNot { it.productId == productId })
    }

    fun applyDiscount(percent: Int): Order {
        require(percent in 0..100)
        return copy(discountBps = percent * 100)
    }

    fun markPaid(now: Instant = Clock.System.now()): Order {
        check(status == OrderStatus.DRAFT)
        require(!isEmpty) { "Cannot pay an empty order" }
        return copy(status = OrderStatus.PAID, paidAt = now)
    }

    companion object {
        fun newDraft(branchId: String, cashierId: String, now: Instant = Clock.System.now()): Order =
            Order(
                id = newOrderId(),
                orderNumber = newOrderNumber(),
                branchId = branchId,
                cashierId = cashierId,
                createdAt = now,
            )

        private fun newOrderId(): String =
            (1..32).joinToString("") { Random.nextInt(16).toString(16) }

        private fun newOrderNumber(): String =
            "A" + Random.nextInt(1000, 9999).toString()
    }
}
