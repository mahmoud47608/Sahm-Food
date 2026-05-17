package com.example.sahmfood.ui.pos

import androidx.compose.runtime.Immutable
import com.example.sahmfood.domain.Order
import com.example.sahmfood.domain.Product
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Immutable
data class PosState(
    val order: Order,
    val products: ImmutableList<Product> = persistentListOf(),
    val category: String? = null,
    val isPaying: Boolean = false,
    val receipt: String? = null,
    val message: String? = null,
) {
    val categories: ImmutableList<String>
        get() = products.map { it.category }.distinct().sorted().toImmutableList()

    val visibleProducts: ImmutableList<Product>
        get() = if (category == null) products
                else products.filter { it.category == category }.toImmutableList()
}
