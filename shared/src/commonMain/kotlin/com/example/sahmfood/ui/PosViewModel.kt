package com.example.sahmfood.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sahmfood.domain.Order
import com.example.sahmfood.domain.PosRepository
import com.example.sahmfood.domain.ReceiptPrinter
import com.example.sahmfood.sync.SyncManager
import kotlinx.collections.immutable.toImmutableList
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

    private val _uiState = MutableStateFlow(PosState(order = newDraftOrder()))
    val uiState: StateFlow<PosState> = _uiState.asStateFlow()

    init {
        repo.observeProducts()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
            .also { flow ->
                viewModelScope.launch {
                    flow.collect { ps ->
                        _uiState.update { it.copy(products = ps.toImmutableList()) }
                    }
                }
            }
    }

    fun selectCategory(c: String?) = _uiState.update { it.copy(category = c) }

    fun add(productId: String) {
        val product = _uiState.value.products.firstOrNull { it.id == productId } ?: return
        _uiState.update { it.copy(order = it.order.addItem(product)) }
    }

    fun changeQuantity(productId: String, qty: Int) =
        _uiState.update { it.copy(order = it.order.changeQuantity(productId, qty)) }

    fun remove(productId: String) =
        _uiState.update { it.copy(order = it.order.removeItem(productId)) }

    fun applyDiscount(percent: Int) =
        _uiState.update { it.copy(order = it.order.applyDiscount(percent)) }

    fun payCash() {
        val order = _uiState.value.order
        if (order.isEmpty) {
            _uiState.update { it.copy(message = "Cart is empty") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isPaying = true) }
            val paid = order.markPaid()
            repo.saveOrder(paid)
            val receipt = printer.print(paid)
            _uiState.update {
                it.copy(
                    isPaying = false,
                    receipt = receipt,
                    order = newDraftOrder(),
                )
            }
            launch { syncManager.syncNow() }
        }
    }

    companion object {
        private fun newDraftOrder(): Order =
            Order.newDraft(branchId = "BR-001", cashierId = "C-001")
    }
}
