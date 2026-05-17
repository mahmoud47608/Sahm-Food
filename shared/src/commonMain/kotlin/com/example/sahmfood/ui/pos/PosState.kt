package com.example.sahmfood.ui.pos

import androidx.compose.runtime.Immutable
import com.example.sahmfood.domain.Order
import com.example.sahmfood.domain.Product
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class PosState(
    val order: Order,
    val products: ImmutableList<Product> = persistentListOf(),
    val category: String? = null,
    val isPaying: Boolean = false,
    val receipt: String? = null,
    val message: String? = null,
)
