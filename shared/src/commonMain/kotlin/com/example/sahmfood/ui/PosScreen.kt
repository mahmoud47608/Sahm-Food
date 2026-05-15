package com.example.sahmfood.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sahmfood.domain.OrderItem
import com.example.sahmfood.domain.Product
import org.koin.compose.viewmodel.koinViewModel

// ============== Theme ==============

private val LightColors = lightColorScheme(
    primary = Color(0xFFE53935), onPrimary = Color.White,
    secondary = Color(0xFFFB8C00), surface = Color(0xFFFAFAFA),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFEF5350), onPrimary = Color.White,
)

@Composable
fun SahmTheme(content: @Composable () -> Unit) {
    MaterialTheme(if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}

// ============== App entry ==============

@Composable
fun App() = SahmTheme { PosScreen() }

// ============== Screen ==============

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScreen(vm: PosViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Sahm POS  •  ${state.order.orderNumber}") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.weight(1.3f).padding(12.dp)) {
                CategoryStrip(state.categories, state.category, vm::selectCategory)
                Spacer(Modifier.height(12.dp))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(140.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.visibleProducts, key = { it.id }) {
                        ProductCard(it) { vm.add(it.id) }
                    }
                }
            }
            CartPanel(state, vm, Modifier.weight(1f).widthIn(min = 320.dp).fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp))
        }
    }

    state.receipt?.let { ReceiptDialog(it, vm::consumeReceipt) }
}

// ============== Sub-composables ==============

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryStrip(categories: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected == null, { onSelect(null) }, { Text("All") })
        categories.forEach { c ->
            FilterChip(selected == c, { onSelect(c) }, { Text(c) })
        }
    }
}

@Composable
private fun ProductCard(product: Product, onAdd: () -> Unit) {
    Card(onClick = onAdd, modifier = Modifier.fillMaxWidth().height(96.dp)) {
        Column(Modifier.fillMaxWidth().padding(10.dp), Arrangement.SpaceBetween) {
            Text(product.name, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold, maxLines = 2)
            Text(product.price.formatted(),
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CartPanel(state: PosViewModel.UiState, vm: PosViewModel, modifier: Modifier) {
    val order = state.order
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Cart", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (!order.isEmpty) {
                Text("${order.itemCount} item${if (order.itemCount > 1) "s" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))

        if (order.isEmpty) {
            Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                Text("No items yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(order.items, key = { it.productId }) { item ->
                    CartItemRow(
                        item,
                        onInc = { vm.changeQuantity(item.productId, item.quantity + 1) },
                        onDec = { vm.changeQuantity(item.productId, item.quantity - 1) },
                        onRemove = { vm.remove(item.productId) },
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        TotalRow("Subtotal", order.subtotal.formatted())
        TotalRow("Tax",      order.tax.formatted())
        if (order.discountBps > 0)
            TotalRow("Discount (${order.discountBps / 100}%)", "−${order.discount.formatted()}")
        TotalRow("TOTAL", order.total.formatted(), bold = true)

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton({ vm.applyDiscount(10) }, Modifier.weight(1f)) { Text("10% off") }
            OutlinedButton({ vm.applyDiscount(0) },  Modifier.weight(1f)) { Text("Reset") }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = vm::payCash,
            enabled = !order.isEmpty && !state.isPaying,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            if (state.isPaying) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            else Text("PAY CASH  •  ${order.total.formatted()}", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CartItemRow(item: OrderItem, onInc: () -> Unit, onDec: () -> Unit, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(item.productName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2)
                    Spacer(Modifier.height(2.dp))
                    Text(item.unitPrice.formatted(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onRemove, Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, "remove",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onDec, Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Remove, "decrease", modifier = Modifier.size(18.dp))
                    }
                    Text(item.quantity.toString(),
                        modifier = Modifier.widthIn(min = 40.dp).padding(horizontal = 8.dp),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium)
                    FilledTonalIconButton(onInc, Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Add, "increase", modifier = Modifier.size(18.dp))
                    }
                }
                Text(item.lineTotal.formatted(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun TotalRow(label: String, value: String, bold: Boolean = false) {
    val w = if (bold) FontWeight.Bold else FontWeight.Normal
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
        Text(label, fontWeight = w); Text(value, fontWeight = w)
    }
}

@Composable
private fun ReceiptDialog(text: String, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Receipt") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(text, fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClose) { Text("Close") } },
    )
}
