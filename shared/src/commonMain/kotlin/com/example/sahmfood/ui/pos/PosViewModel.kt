package com.example.sahmfood.ui.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sahmfood.domain.Order
import com.example.sahmfood.domain.PosRepository
import com.example.sahmfood.domain.ReceiptPrinter
import com.example.sahmfood.sync.SyncManager
import io.github.aakira.napier.Napier
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PosViewModel(
    private val repository: PosRepository,
    private val printer: ReceiptPrinter,
    private val syncManager: SyncManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PosState(order = newDraftOrder()))
    val uiState: StateFlow<PosState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getProducts().collect { products ->
                _uiState.update { it.copy(products = products.toImmutableList()) }
            }
        }

        // Surface background-sync results to the snackbar.
        viewModelScope.launch {
            syncManager.events.collect { report ->
                val message = when {
                    report.failed > 0 && report.succeeded > 0 ->
                        "Synced ${report.succeeded}, failed ${report.failed}"
                    report.failed > 0 ->
                        "Sync failed (${report.failed} pending)"
                    report.succeeded > 0 ->
                        "Synced ${report.succeeded} order${if (report.succeeded > 1) "s" else ""} ✓"
                    else -> return@collect
                }
                _uiState.update { it.copy(message = message) }
            }
        }
    }

    // ─── User intents ──────────────────────────────────────

    fun selectCategory(category: String?) =
        _uiState.update { it.copy(category = category) }

    fun addProduct(productId: String) {
        val product = _uiState.value.products.firstOrNull { it.id == productId } ?: return
        _uiState.update { it.copy(order = it.order.addItem(product)) }
    }

    fun changeQuantity(productId: String, quantity: Int) =
        _uiState.update { it.copy(order = it.order.changeQuantity(productId, quantity)) }

    fun removeProduct(productId: String) =
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
            try {
                val paid = order.markPaid()
                repository.saveOrder(paid)
                val receipt = printer.print(paid)
                _uiState.update {
                    it.copy(
                        isPaying = false,
                        receipt = receipt,
                        order = newDraftOrder(),
                    )
                }
                launch { syncManager.syncNow() }
            } catch (e: Exception) {
                Napier.e(e) { "[Pos] payCash failed" }
                _uiState.update {
                    it.copy(
                        isPaying = false,
                        message = "Payment failed: ${e.message ?: "unknown error"}",
                    )
                }
            }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    fun consumeReceipt() = _uiState.update { it.copy(receipt = null) }

    private fun newDraftOrder(): Order = Order.newDraft(branchId = "BR-001", cashierId = "C-001")
}
