package com.example.sahmfood.ui.pos.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.sahmfood.domain.Order
import com.example.sahmfood.domain.OrderItem

/**
 * Right-hand cart panel: items list, totals, discount buttons, pay button.
 *
 * Stateless — receives [order] + callbacks, emits intents up.
 */
@Composable
internal fun CartPanel(
    order: Order,
    isPaying: Boolean,
    onChangeQuantity: (productId: String, quantity: Int) -> Unit,
    onRemoveProduct: (productId: String) -> Unit,
    onApplyDiscount: (percent: Int) -> Unit,
    onPayCash: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        CartHeader(order = order)
        Spacer(Modifier.height(12.dp))

        if (order.isEmpty) {
            Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                Text("No items yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(order.items, key = { it.productId }) { item ->
                    CartItemRow(
                        item = item,
                        onIncrease = { onChangeQuantity(item.productId, item.quantity + 1) },
                        onDecrease = { onChangeQuantity(item.productId, item.quantity - 1) },
                        onRemove = { onRemoveProduct(item.productId) },
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        CartTotals(order = order)
        Spacer(Modifier.height(12.dp))
        DiscountButtons(onApplyDiscount = onApplyDiscount)
        Spacer(Modifier.height(8.dp))
        PayCashButton(order = order, isPaying = isPaying, onPayCash = onPayCash)
    }
}

@Composable
private fun CartHeader(order: Order) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "Cart",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        if (!order.isEmpty) {
            Text(
                text = "${order.itemCount} item${if (order.itemCount > 1) "s" else ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CartTotals(order: Order) {
    TotalRow("Subtotal", order.subtotal.formatted())
    TotalRow("Tax", order.tax.formatted())
    if (order.discountBps > 0) {
        TotalRow(
            label = "Discount (${order.discountBps / 100}%)",
            value = "−${order.discount.formatted()}",
        )
    }
    TotalRow("TOTAL", order.total.formatted(), bold = true)
}

@Composable
private fun DiscountButtons(onApplyDiscount: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(
            onClick = { onApplyDiscount(10) },
            modifier = Modifier.weight(1f),
        ) { Text("10% off") }
        OutlinedButton(
            onClick = { onApplyDiscount(0) },
            modifier = Modifier.weight(1f),
        ) { Text("Reset") }
    }
}

@Composable
private fun PayCashButton(order: Order, isPaying: Boolean, onPayCash: () -> Unit) {
    Button(
        onClick = onPayCash,
        enabled = !order.isEmpty && !isPaying,
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        if (isPaying) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp),
            )
        } else {
            Text(
                text = "PAY CASH  •  ${order.total.formatted()}",
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun CartItemRow(
    item: OrderItem,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.productName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = item.unitPrice.formatted(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onRemove, Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "remove",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onDecrease, Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Remove,
                            contentDescription = "decrease",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text(
                        text = item.quantity.toString(),
                        modifier = Modifier
                            .widthIn(min = 40.dp)
                            .padding(horizontal = 8.dp),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    FilledTonalIconButton(onIncrease, Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "increase",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Text(
                    text = item.lineTotal.formatted(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun TotalRow(label: String, value: String, bold: Boolean = false) {
    val weight = if (bold) FontWeight.Bold else FontWeight.Normal
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
        Text(label, fontWeight = weight)
        Text(value, fontWeight = weight)
    }
}

