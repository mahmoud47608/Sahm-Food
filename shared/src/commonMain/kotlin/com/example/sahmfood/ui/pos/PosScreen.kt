package com.example.sahmfood.ui.pos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.sahmfood.domain.Product
import com.example.sahmfood.ui.pos.components.CartPanel
import com.example.sahmfood.ui.pos.components.CategoryStrip
import com.example.sahmfood.ui.pos.components.ProductCard
import com.example.sahmfood.ui.pos.components.ReceiptDialog
import kotlinx.collections.immutable.ImmutableList
import org.koin.compose.viewmodel.koinViewModel

/**
 * Top-level POS screen — handles VM wiring, snackbar, and receipt dialog.
 *
 * The actual layout lives in [PosContent], which is stateless and
 * receives intent callbacks. This split makes the UI previewable
 * and testable without instantiating the ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScreen(viewModel: PosViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { msg ->
            snackbarHost.showSnackbar(msg)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Sahm POS  •  ${uiState.order.orderNumber}") })
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        PosContent(
            uiState = uiState,
            onSelectCategory = viewModel::selectCategory,
            onAddProduct = viewModel::addProduct,
            onChangeQuantity = viewModel::changeQuantity,
            onRemoveProduct = viewModel::removeProduct,
            onApplyDiscount = viewModel::applyDiscount,
            onPayCash = viewModel::payCash,
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }

    uiState.receipt?.let { text ->
        ReceiptDialog(text = text, onClose = viewModel::consumeReceipt)
    }
}

/**
 * Stateless layout: menu grid on the left, cart panel on the right.
 *
 * Takes everything it needs as parameters — safe for `@Preview` and unit tests.
 */
@Composable
private fun PosContent(
    uiState: PosState,
    onSelectCategory: (String?) -> Unit,
    onAddProduct: (String) -> Unit,
    onChangeQuantity: (productId: String, quantity: Int) -> Unit,
    onRemoveProduct: (String) -> Unit,
    onApplyDiscount: (Int) -> Unit,
    onPayCash: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier) {
        MenuPane(
            categories = uiState.categories,
            selectedCategory = uiState.category,
            products = uiState.visibleProducts,
            onSelectCategory = onSelectCategory,
            onAddProduct = onAddProduct,
            modifier = Modifier.weight(1.3f).padding(12.dp),
        )
        CartPanel(
            order = uiState.order,
            isPaying = uiState.isPaying,
            onChangeQuantity = onChangeQuantity,
            onRemoveProduct = onRemoveProduct,
            onApplyDiscount = onApplyDiscount,
            onPayCash = onPayCash,
            modifier = Modifier
                .weight(1f)
                .widthIn(min = 320.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(16.dp),
        )
    }
}

@Composable
private fun MenuPane(
    categories: ImmutableList<String>,
    selectedCategory: String?,
    products: ImmutableList<Product>,
    onSelectCategory: (String?) -> Unit,
    onAddProduct: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        CategoryStrip(categories, selectedCategory, onSelectCategory)
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(products, key = { it.id }) { product ->
                ProductCard(product) { onAddProduct(product.id) }
            }
        }
    }
}
