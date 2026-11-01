package com.factorybill.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
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
private fun Summary(label: String, value: String, modifier: Modifier, bad: Boolean = false) =
    Card(modifier) { Column(Modifier.padding(14.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleLarge, color = if (bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    } }

@Composable
fun HomeScreen(vm: HomeViewModel, onInvoiceSelect: (Long) -> Unit = {}) {
    val sales by vm.sales.collectAsStateWithLifecycle(); val dues by vm.dues.collectAsStateWithLifecycle()
    val supplierDues by vm.supplierDues.collectAsStateWithLifecycle()
    val exp by vm.expenses.collectAsStateWithLifecycle(); val neg by vm.negativeStock.collectAsStateWithLifecycle()
    val invoices by vm.invoices.collectAsStateWithLifecycle()
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    val df = remember { SimpleDateFormat("dd MMM", Locale("en", "IN")) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Good business starts with a clear picture.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Text("Overview", style = MaterialTheme.typography.headlineMedium) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Summary("Sales to date", Money.format(sales), Modifier.weight(1f)); Summary("Customer dues", Money.format(dues), Modifier.weight(1f), dues > 0) } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Summary("Expenses to date", Money.format(exp), Modifier.weight(1f)); Summary("Negative stock items", "$neg", Modifier.weight(1f), neg > 0) } }
        item { Summary("Supplier payable", Money.format(supplierDues), Modifier.fillMaxWidth(), supplierDues > 0) }
        item { Text("Recent invoices", style = MaterialTheme.typography.titleMedium) }
        if (invoices.isEmpty()) item { Text("Your issued invoices will appear here.", style = MaterialTheme.typography.bodyMedium) }
        items(invoices, key = { it.id }) { inv ->
            Card(Modifier.fillMaxWidth().clickable { onInvoiceSelect(inv.id) }) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(inv.number + if (inv.status == "VOID") "  ·  VOID" else "", style = MaterialTheme.typography.titleSmall)
                        Text(df.format(Date(inv.dateMillis)), style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(Money.format(inv.totalPaise))
                        val due = inv.totalPaise - inv.paymentPaise
                        Text(if (due > 0) "Due ${Money.format(due)}" else "Paid", style = MaterialTheme.typography.bodySmall,
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
    var showCust by remember { mutableStateOf(false) }; var showProd by remember { mutableStateOf(false) }
    var custMenu by remember { mutableStateOf(false) }; var prodMenu by remember { mutableStateOf(false) }
    val totals = vm.previewTotals()

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Create invoice", style = MaterialTheme.typography.headlineMedium) }
            item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Customer", style = MaterialTheme.typography.titleMedium)
                Box { OutlinedButton({ custMenu = true }, Modifier.fillMaxWidth()) { Text(vm.customer?.name ?: "Select customer") }
                    DropdownMenu(custMenu, { custMenu = false }) {
                        customers.forEach { c -> DropdownMenuItem({ Text(c.name) }, { vm.selectCustomer(c); custMenu = false }) }
                        DropdownMenuItem({ Text("+ New customer…") }, { showCust = true; custMenu = false })
                    } }
                vm.customer?.let { Text(listOf(it.contactPerson, it.phone, it.gstin, it.billingAddress).filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.bodySmall) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(vm.intraState, { vm.intraState = it }); Spacer(Modifier.width(8.dp))
                    Text(if (vm.intraState) "Same state: CGST + SGST" else "Other state: IGST") }
                Field("Place of supply", vm.placeOfSupply, { vm.placeOfSupply = it })
            } } }
            itemsIndexed(vm.lines) { i, l ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(l.product.name, style = MaterialTheme.typography.titleSmall)
                        TextButton({ vm.lines.removeAt(i) }) { Text("Remove") } }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field("Qty", l.qty, { l.qty = it }, Modifier.weight(1f), true)
                        Field("Price ₹", l.price, { l.price = it }, Modifier.weight(1f), true) }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Gst.STANDARD_RATES.forEach { r -> FilterChip(l.gst == r, { l.gst = r }, { Text("${r.toString().removeSuffix(".0")}%") }) } }
                    val short = InvoiceCalculator.shortage(vm.liveStock(l), l.qty.toDoubleOrNull() ?: 0.0)
                    if (short > 0) Text("⚠ Stock ${vm.liveStock(l)}, short by $short. You can still issue; stock will go negative.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    val input = vm.inputs().getOrNull(i)
                    val result = input?.takeIf { Validation.validateLineInputs(listOf(it)).isEmpty() }
                        ?.let(InvoiceCalculator::line)
                    result?.let { Text("Taxable ${Money.format(it.taxablePaise)} · GST ${Money.format(it.gstPaise)} · ${Money.format(it.totalPaise)}", style = MaterialTheme.typography.bodySmall) }
                } }
            }
            item { Box { OutlinedButton({ prodMenu = true }, Modifier.fillMaxWidth()) { Text("+ Add item") }
                DropdownMenu(prodMenu, { prodMenu = false }) {
                    products.forEach { p -> DropdownMenuItem({ Text(p.name) }, { vm.lines.add(LineState(p)); prodMenu = false }) }
                    DropdownMenuItem({ Text("+ New product…") }, { showProd = true; prodMenu = false })
                } } }
            item { Field("Payment received ₹", vm.payment, { vm.payment = it }, number = true) }
            item { Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(vm.pricesIncludeGst, { vm.pricesIncludeGst = it })
                Spacer(Modifier.width(8.dp))
                Column { Text("Prices include GST"); Text("Turn off to add GST to entered rates.", style = MaterialTheme.typography.bodySmall) }
            } }
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
    if (showCust) CustomerDialog({ showCust = false }) { vm.addCustomer(it); showCust = false }
    if (showProd) ProductDialog({ showProd = false }) { vm.addProduct(it); showProd = false }
}

@Composable
private fun CustomerDialog(onDismiss: () -> Unit, onSave: (Customer) -> Unit) {
    var c by remember { mutableStateOf(Customer(name = "")) }
    var error by remember { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()
    AlertDialog(onDismiss, title = { Text("New customer") },
        text = { Column(Modifier.verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Field("Name", c.name, { c = c.copy(name = it) }); Field("Contact person", c.contactPerson, { c = c.copy(contactPerson = it) })
            Field("Phone", c.phone, { c = c.copy(phone = it) }); Field("GSTIN (format check only)", c.gstin, { c = c.copy(gstin = it.uppercase()) })
            Field("Billing address", c.billingAddress, { c = c.copy(billingAddress = it) }); Field("Shipping address", c.shippingAddress, { c = c.copy(shippingAddress = it) })
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
        confirmButton = { TextButton({
            if (c.name.isBlank()) error = "Customer name is required."
            else if (!Validation.phoneOk(c.phone)) error = "Enter a valid phone number."
            else if (!Validation.gstinFormatOk(c.gstin)) error = "GSTIN format looks invalid."
            else onSave(c)
        }) { Text("Save") } }, dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
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
    val theme by app.settings.theme.collectAsStateWithLifecycle(ThemeMode.SYSTEM)
    val accent by app.settings.accent.collectAsStateWithLifecycle(0)
    val saved by app.repo.company.collectAsStateWithLifecycle(null)
    var form by remember(saved) { mutableStateOf(saved ?: CompanyProfile()) }
    var msg by remember { mutableStateOf<String?>(null) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.headlineMedium) }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { ThemeMode.values().forEach { m ->
                FilterChip(theme == m, { scope.launch { app.settings.setTheme(m) } }, { Text(m.name.lowercase().replaceFirstChar(Char::uppercase)) }) } }
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
