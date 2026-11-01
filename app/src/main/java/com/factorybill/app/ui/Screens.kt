package com.factorybill.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.factorybill.app.FactoryApp
import com.factorybill.app.core.*
import com.factorybill.app.data.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, number: Boolean = false) =
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, modifier = modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text))

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier, bad: Boolean = false) =
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge,
                color = if (bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        }
    }

@Composable
private fun QuickAction(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onInvoiceSelect: (Long) -> Unit = {},
    onNavigate: (String) -> Unit = {},
) {
    val sales by vm.sales.collectAsStateWithLifecycle(); val dues by vm.dues.collectAsStateWithLifecycle()
    val supplierDues by vm.supplierDues.collectAsStateWithLifecycle()
    val exp by vm.expenses.collectAsStateWithLifecycle(); val neg by vm.negativeStock.collectAsStateWithLifecycle()
    val invoices by vm.invoices.collectAsStateWithLifecycle()
    val company by vm.company.collectAsStateWithLifecycle(null)
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    val df = remember { SimpleDateFormat("dd MMM", Locale("en", "IN")) }
    val today = remember { SimpleDateFormat("EEEE, d MMMM", Locale("en", "IN")).format(Date()) }
    LazyColumn(
        contentPadding = PaddingValues(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(today, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        company?.name?.trim()?.takeIf(String::isNotEmpty) ?: "Your business",
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Text("F", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("TOTAL SALES", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .8f))
                    Text(Money.format(sales), style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onPrimary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${invoices.count { it.status == "ISSUED" }} invoices issued",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .82f))
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { onNavigate("invoice") }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)) {
                            Text("New invoice"); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Customer dues", Money.format(dues), Modifier.weight(1f), dues > 0)
                MetricCard("Supplier payable", Money.format(supplierDues), Modifier.weight(1f), supplierDues > 0)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Expenses", Money.format(exp), Modifier.weight(1f))
                MetricCard("Negative stock", "$neg items", Modifier.weight(1f), neg > 0)
            }
        }
        item { Text("SHORTCUTS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction("Create invoice", "Bill a customer", Icons.AutoMirrored.Filled.ReceiptLong) { onNavigate("invoice") }
                QuickAction("Scan a bill", "Capture and review a bill", Icons.Default.CameraAlt) { onNavigate("scan") }
                QuickAction("Business tools", "Stock, purchases and expenses", Icons.Default.Inventory2) { onNavigate("ops") }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Recent invoices", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { onNavigate("invoice") }) { Text("Create new"); Icon(Icons.Default.Add, null) }
            }
        }
        if (invoices.isEmpty()) item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("No invoices yet", style = MaterialTheme.typography.titleMedium)
                    Text("Create your first GST invoice to see it here.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilledTonalButton(onClick = { onNavigate("invoice") }) { Text("Create invoice") }
                }
            }
        }
        items(invoices, key = { it.id }) { inv ->
            Card(Modifier.fillMaxWidth().clickable { onInvoiceSelect(inv.id) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, end = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(inv.number + if (inv.status == "VOID") "  ·  VOID" else "", style = MaterialTheme.typography.titleSmall)
                        Text(df.format(Date(inv.dateMillis)), style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(Money.format(inv.totalPaise), style = MaterialTheme.typography.titleSmall)
                        val due = inv.totalPaise - inv.paymentPaise
                        Text(if (due > 0) "Due ${Money.format(due)}" else "Paid", style = MaterialTheme.typography.labelMedium,
                            color = if (due > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = {
                        scope.launch {
                            val f = vm.pdfFor(ctx, inv.id, accent) ?: return@launch
                            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
                            val i = Intent(Intent.ACTION_SEND).apply { type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                            ctx.startActivity(Intent.createChooser(i, "Share or print invoice"))
                        }
                    }) { Icon(Icons.Default.Share, contentDescription = "Share invoice PDF") }
                }
            }
        }
    }
}

@Composable
internal fun TotalRow(a: String, b: Long) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(a); Text(Money.format(b)) }

@Composable
fun InvoiceScreen(vm: InvoiceViewModel) {
    val customers by vm.customers.collectAsStateWithLifecycle(); val products by vm.products.collectAsStateWithLifecycle()
    var customerMenu by remember { mutableStateOf(false) }
    var productMenu by remember { mutableIntStateOf(-1) }
    val totals = vm.previewTotals()

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Create invoice", style = MaterialTheme.typography.headlineMedium) }
            item {
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Customer details", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Box {
                            TextButton(onClick = { customerMenu = true }, enabled = customers.isNotEmpty()) { Text("Use saved") }
                            DropdownMenu(customerMenu, { customerMenu = false }) {
                                customers.forEach { c ->
                                    DropdownMenuItem(
                                        text = { Text(c.name) },
                                        onClick = { vm.selectCustomer(c); customerMenu = false },
                                    )
                                }
                            }
                        }
                    }
                    val customer = vm.customerDraft
                    Field("Customer / business name *", customer.name, { value ->
                        vm.updateCustomer { draft -> draft.copy(name = value) }
                    })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field("Contact person", customer.contactPerson, { value ->
                            vm.updateCustomer { draft -> draft.copy(contactPerson = value) }
                        }, Modifier.weight(1f))
                        Field("Phone", customer.phone, { value ->
                            vm.updateCustomer { draft -> draft.copy(phone = value) }
                        }, Modifier.weight(1f))
                    }
                    Field("Customer GSTIN", customer.gstin, { value ->
                        vm.updateCustomer { draft -> draft.copy(gstin = value.uppercase()) }
                    })
                    Field("Billing address", customer.billingAddress, { value ->
                        vm.updateCustomer { draft -> draft.copy(billingAddress = value) }
                    })
                    Field("Shipping address", customer.shippingAddress, { value ->
                        vm.updateCustomer { draft -> draft.copy(shippingAddress = value) }
                    })
                    Field("Place of supply", vm.placeOfSupply, { vm.placeOfSupply = it })
                    HorizontalDivider()
                    Text("Tax treatment", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(vm.intraState, { vm.intraState = it }); Spacer(Modifier.width(8.dp))
                    Text(if (vm.intraState) "Intra-state · CGST + SGST" else "Inter-state · IGST") }
                } }
            }
            item { Text("PRODUCTS & SERVICES", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            itemsIndexed(vm.lines) { i, l ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Item ${i + 1}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        if (products.isNotEmpty()) Box {
                            TextButton(onClick = { productMenu = i }) { Text("From catalog") }
                            DropdownMenu(productMenu == i, { productMenu = -1 }) {
                                products.forEach { product ->
                                    DropdownMenuItem(
                                        text = { Text("${product.name} · ${Money.format(product.defaultPricePaise)}") },
                                        onClick = { l.use(product); productMenu = -1 },
                                    )
                                }
                            }
                        }
                        TextButton(onClick = { if (vm.lines.size > 1) vm.lines.removeAt(i) else l.use(Product(name = "")) }) { Text("Remove") }
                    }
                    Field("Product / service name *", l.product.name, { l.product = l.product.copy(name = it) })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field("HSN / SAC", l.product.hsn, { l.product = l.product.copy(hsn = it) }, Modifier.weight(1f))
                        Field("Unit", l.product.unit, { l.product = l.product.copy(unit = it) }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field("Quantity *", l.qty, { l.qty = it }, Modifier.weight(1f), true)
                        Field("Unit price ₹ *", l.price, { l.price = it }, Modifier.weight(1f), true)
                    }
                    Text("GST rate", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Gst.STANDARD_RATES.forEach { r -> FilterChip(l.gst == r, { l.gst = r }, label = { Text("${r.toString().removeSuffix(".0")}%") }) } }
                    val short = InvoiceCalculator.shortage(vm.liveStock(l), l.qty.toDoubleOrNull() ?: 0.0)
                    if (l.product.id > 0 && short > 0) Text("Stock ${vm.liveStock(l)}, short by $short.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    val input = vm.inputs().getOrNull(i)
                    val result = input?.takeIf { Validation.validateLineInputs(listOf(it)).isEmpty() }
                        ?.let(InvoiceCalculator::line)
                    result?.let { Text("Taxable ${Money.format(it.taxablePaise)}  ·  GST ${Money.format(it.gstPaise)}  ·  Total ${Money.format(it.totalPaise)}", style = MaterialTheme.typography.bodySmall) }
                } }
            }
            item {
                OutlinedButton(onClick = { vm.addLine() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Add another item")
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(vm.pricesIncludeGst, { vm.pricesIncludeGst = it })
                    Spacer(Modifier.width(8.dp))
                    Column { Text("Prices include GST"); Text("Turn off to add GST to entered rates.", style = MaterialTheme.typography.bodySmall) }
                }
            }
            item { Field("Payment received ₹", vm.payment, { vm.payment = it }, number = true) }
            item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                TotalRow("Taxable value", totals.taxablePaise)
                if (vm.intraState) { TotalRow("CGST", totals.split.cgstPaise); TotalRow("SGST", totals.split.sgstPaise) } else TotalRow("IGST", totals.split.igstPaise)
                HorizontalDivider()
                TotalRow("Invoice total", totals.totalPaise)
            } } }
        }
        Surface(tonalElevation = 3.dp) { Column(Modifier.fillMaxWidth().padding(12.dp)) {
            vm.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            val lineWarnings = Validation.validateLineInputs(vm.inputs()).filterNot { it == "Add at least one item" }
            lineWarnings.distinct().forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Balance due", style = MaterialTheme.typography.labelMedium)
                    Text(Money.format(totals.balancePaise), style = MaterialTheme.typography.titleLarge) }
                Button({ vm.message = null; vm.issue() }) { Text("Issue invoice") }
            }
        } }
    }
}

@Composable
internal fun ProductDialog(onDismiss: () -> Unit, onSave: (Product) -> Unit) {
    var name by remember { mutableStateOf("") }; var price by remember { mutableStateOf("") }
    var hsn by remember { mutableStateOf("") }; var stock by remember { mutableStateOf("0") }
    var gst by remember { mutableStateOf(18.0) }; var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismiss, title = { Text("New product") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Field("Name", name, { name = it }); Field("HSN / SAC", hsn, { hsn = it }); Field("Price ₹", price, { price = it }, number = true)
            Field("Opening stock", stock, { stock = it }, number = true)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Gst.STANDARD_RATES.forEach { r -> FilterChip(gst == r, { gst = r }, { Text("${r.toString().removeSuffix(".0")}%") }) } }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
        confirmButton = { TextButton({
            val parsedPrice = Money.parseRupees(price)
            val parsedStock = stock.toDoubleOrNull()
            if (name.isBlank()) error = "Product name is required."
            else if (parsedPrice == null || parsedStock == null || !parsedStock.isFinite()) error = "Enter a valid price and opening stock."
            else onSave(Product(name = name.trim(), hsn = hsn.trim(), defaultPricePaise = parsedPrice, defaultGstRate = gst, stockQty = parsedStock))
        }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}


@Composable
fun InvoiceUnavailable() = Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
    Text("Invoice unavailable", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Text("The invoice number is invalid.")
}

@Composable
fun MoreScreen(app: FactoryApp) {
    val scope = rememberCoroutineScope()
    val theme by app.settings.theme.collectAsStateWithLifecycle(ThemeMode.LIGHT)
    val accent by app.settings.accent.collectAsStateWithLifecycle(0)
    val saved by app.repo.company.collectAsStateWithLifecycle(null)
    var form by remember(saved) { mutableStateOf(saved ?: CompanyProfile()) }
    var msg by remember { mutableStateOf<String?>(null) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.headlineMedium) }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            Text("Choose how FactoryBill looks on this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.values().forEach { mode ->
                    val label = when (mode) { ThemeMode.SYSTEM -> "System"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark" }
                    FilterChip(
                        selected = theme == mode,
                        onClick = { scope.launch { app.settings.setTheme(mode) } },
                        label = { Text(label) },
                    )
                }
            }
            Text(if (theme == ThemeMode.DARK || (theme == ThemeMode.SYSTEM && androidx.compose.foundation.isSystemInDarkTheme())) "Dark appearance is on" else "Light appearance is on",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { AccentPalette.forEachIndexed { i, c ->
                Box(Modifier.size(36.dp).clip(CircleShape).background(c).clickable { scope.launch { app.settings.setAccent(i) } }) {
                    if (i == accent) Text("✓", Modifier.align(Alignment.Center), color = androidx.compose.ui.graphics.Color.White) } } }
        } } }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Company, GST and bank details", style = MaterialTheme.typography.titleMedium)
            Field("Company name", form.name, { form = form.copy(name = it) }); Field("GSTIN", form.gstin, { form = form.copy(gstin = it.uppercase()) })
            Field("Address", form.address, { form = form.copy(address = it) }); Field("Phone", form.phone, { form = form.copy(phone = it) })
            Field("Email", form.email, { form = form.copy(email = it) }); Field("Bank name", form.bankName, { form = form.copy(bankName = it) })
            Field("Account number", form.accountNumber, { form = form.copy(accountNumber = it) }); Field("IFSC", form.ifsc, { form = form.copy(ifsc = it.uppercase()) })
            Field("UPI ID (for payment QR)", form.upiId, { form = form.copy(upiId = it) })
            Text("Invoice template", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("classic", "modern", "compact").forEach { t ->
                FilterChip(form.template == t, { form = form.copy(template = t) }, { Text(t.replaceFirstChar(Char::uppercase)) }) } }
            msg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Button({ scope.launch { msg = try { app.repo.saveCompany(form); "Saved" } catch (e: IllegalArgumentException) { e.message } } }, Modifier.fillMaxWidth()) { Text("Save") }
        } } }
    }
}
