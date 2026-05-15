package com.example.sahmfood.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sahmfood.domain.Order
import com.example.sahmfood.domain.PosRepository
import com.example.sahmfood.domain.Product
import com.example.sahmfood.domain.ReceiptPrinter
import com.example.sahmfood.sync.SyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PosViewModel(
    private val repo: PosRepository,
    private val printer: ReceiptPrinter,
    private val syncManager: SyncManager,
) : ViewModel() {

    data class UiState(
        val order: Order = newDraftOrder(),
        val products: List<Product> = emptyList(),
        val category: String? = null,
        val isPaying: Boolean = false,
        val receipt: String? = null,
        val message: String? = null,
    ) {
        val categories: List<String> get() = products.map { it.category }.distinct().sorted()
        val visibleProducts: List<Product> get() =
            if (category == null) products else products.filter { it.category == category }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        repo.observeProducts()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
            .also { flow ->
                viewModelScope.launch { flow.collect { ps -> _state.update { it.copy(products = ps) } } }
            }
    }

    fun selectCategory(c: String?) = _state.update { it.copy(category = c) }

    fun add(productId: String) {
        val product = _state.value.products.firstOrNull { it.id == productId } ?: return
        _state.update { it.copy(order = it.order.addItem(product)) }
    }

    fun changeQuantity(productId: String, qty: Int) =
        _state.update { it.copy(order = it.order.changeQuantity(productId, qty)) }

    fun remove(productId: String) =
        _state.update { it.copy(order = it.order.removeItem(productId)) }

    fun applyDiscount(percent: Int) =
        _state.update { it.copy(order = it.order.applyDiscount(percent)) }
    fun payCash() {
        val order = _state.value.order
        if (order.isEmpty) {
            _state.update { it.copy(message = "Cart is empty") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isPaying = true) }
            val paid = order.markPaid()
            repo.saveOrder(paid)
            val receipt = printer.print(paid)
            _state.update {
                it.copy(
                    isPaying = false,
                    receipt = receipt,
                    order = newDraftOrder(),
                )
            }
            launch { syncManager.syncNow() }
        }
    }

    fun consumeReceipt() = _state.update { it.copy(receipt = null) }
    fun consumeMessage() = _state.update { it.copy(message = null) }

    companion object {
        private fun newDraftOrder() = Order.newDraft(branchId = "BR-001", cashierId = "C-001")
    }
}
