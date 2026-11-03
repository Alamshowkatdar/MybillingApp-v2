package com.factorybill.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.factorybill.app.core.Gst
import com.factorybill.app.core.InvoiceCalculator
import com.factorybill.app.core.LineInput
import com.factorybill.app.core.Money
import com.factorybill.app.core.Validation
import com.factorybill.app.data.Product

@Composable
internal fun LineEditorCard(
    index: Int,
    line: LineState,
    products: List<Product>,
    includeGst: Boolean,
    isPurchase: Boolean,
    liveStock: (LineState) -> Double,
    onRemove: () -> Unit,
) {
    var productMenu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        "${index + 1}",
                        Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(if (isPurchase) "Purchase item" else "Invoice item", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (products.isNotEmpty()) {
                    Box {
                        TextButton(onClick = { productMenu = true }) { Text("Catalog") }
                        DropdownMenu(productMenu, { productMenu = false }) {
                            products.forEach { product ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(product.name)
                                            Text("${Money.format(product.defaultPricePaise)} · ${product.defaultGstRate.toString().removeSuffix(".0")}% GST",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    onClick = { line.use(product); productMenu = false },
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = onRemove) { Icon(Icons.Default.Close, contentDescription = "Remove item") }
            }
            FormField(
                label = if (isPurchase) "Product / item name *" else "Product / service name *",
                value = line.product.name,
                onChange = { line.product = line.product.copy(name = it) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField("HSN / SAC", line.product.hsn, { line.product = line.product.copy(hsn = it) }, Modifier.weight(1f))
                FormField("Unit", line.product.unit, { line.product = line.product.copy(unit = it) }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField("Quantity *", line.qty, { line.qty = it }, Modifier.weight(1f), number = true)
                FormField(if (isPurchase) "Unit cost ₹ *" else "Unit price ₹ *", line.price, { line.price = it }, Modifier.weight(1f), number = true)
            }
            Text("GST rate", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Gst.STANDARD_RATES.forEach { rate ->
                    FilterChip(
                        selected = line.gst == rate,
                        onClick = { line.gst = rate },
                        label = { Text("${rate.toString().removeSuffix(".0")}%") },
                    )
                }
            }
            val input = LineInput(line.qty.toDoubleOrNull() ?: 0.0, Money.parseRupees(line.price) ?: -1L, line.gst, includeGst)
            if (Validation.validateLineInputs(listOf(input)).isEmpty()) {
                val result = runCatching { InvoiceCalculator.line(input) }.getOrNull()
                if (result != null) {
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Taxable ${Money.format(result.taxablePaise)} · GST ${Money.format(result.gstPaise)}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Money.format(result.totalPaise), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            if (!isPurchase && line.product.id > 0) {
                val stock = liveStock(line)
                val qty = line.qty.toDoubleOrNull() ?: 0.0
                val short = InvoiceCalculator.shortage(stock, qty)
                if (short > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(Icons.Default.WarningAmber, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.error)
                        Text("Only $stock ${line.product.unit} in stock (short by ${"%.2f".format(short).trimEnd('0').trimEnd('.')}). You can still issue.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
internal fun CatalogHint(products: Int) {
    if (products == 0) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.Inventory2, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Add products in Inventory to reuse them here.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
