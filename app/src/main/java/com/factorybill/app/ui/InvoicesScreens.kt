package com.factorybill.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.factorybill.app.core.*
import com.factorybill.app.data.*
import kotlinx.coroutines.launch

@Composable
fun InvoiceListScreen(onOpen: (Long) -> Unit, onCreate: () -> Unit) {
    // List data is loaded by the caller through a lightweight live repository observer.
    val repo = LocalRepository.current
    val invoices by repo.invoices.collectAsStateWithLifecycle(emptyList())
    val customers by repo.customers.collectAsStateWithLifecycle(emptyList())
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    val names = remember(customers) { customers.associate { it.id to it.name } }

    val issued = invoices.filter { it.status == "ISSUED" }
    val total = issued.sumOf { it.totalPaise }
    val collected = issued.sumOf { it.paymentPaise }
    val outstanding = (total - collected).coerceAtLeast(0)
    val filtered = invoices.filter { invoice ->
        val matches = query.isBlank() || invoice.number.contains(query, true) ||
            (names[invoice.customerId]?.contains(query, true) == true)
        val state = paymentState(invoice.totalPaise, invoice.paymentPaise, invoice.status == "VOID")
        val stateMatches = when (filter) {
            "Unpaid" -> state == PaymentState.UNPAID
            "Partial" -> state == PaymentState.PARTIAL
            "Paid" -> state == PaymentState.PAID
            "Void" -> state == PaymentState.VOID
            else -> true
        }
        matches && stateMatches
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeading(
                title = "Invoices",
                subtitle = "${issued.size} issued · ${Money.format(outstanding)} outstanding",
                trailing = {
                    FilledTonalButton(onClick = onCreate) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("New")
                    }
                },
            )
        }
        item {
            SectionCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SummaryValue("Billed", Money.format(total), Modifier.weight(1f))
                    SummaryValue("Collected", Money.format(collected), Modifier.weight(1f))
                    SummaryValue("Outstanding", Money.format(outstanding), Modifier.weight(1f),
                        valueColor = if (outstanding > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
            }
        }
        item { SearchField(query, { query = it }, "Search invoice number or customer") }
        item { FilterRow(filter, listOf("All", "Unpaid", "Partial", "Paid", "Void"), { filter = it }) }
        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    title = if (invoices.isEmpty()) "No invoices yet" else "No matching invoices",
                    message = if (invoices.isEmpty()) "Create a GST invoice and it will appear here with its payment status." else "Try a different search or filter.",
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    actionLabel = if (invoices.isEmpty()) "Create invoice" else null,
                    onAction = if (invoices.isEmpty()) onCreate else null,
                )
            }
        }
        items(filtered, key = { it.id }) { invoice ->
            InvoiceListRow(
                invoice = invoice,
                customerName = names[invoice.customerId] ?: "Customer",
                onClick = { onOpen(invoice.id) },
            )
        }
    }
}

@Composable
private fun SummaryValue(label: String, value: String, modifier: Modifier = Modifier, valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun InvoiceListRow(invoice: Invoice, customerName: String, onClick: () -> Unit) {
    val state = paymentState(invoice.totalPaise, invoice.paymentPaise, invoice.status == "VOID")
    val due = (invoice.totalPaise - invoice.paymentPaise).coerceAtLeast(0)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(42.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(customerName.trim().take(2).uppercase(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(invoice.number, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    StatusPill(state)
                }
                Text(customerName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(
                        formatDate(invoice.dateMillis),
                        if (invoice.dueDateMillis > 0) "Due ${formatShortDate(invoice.dueDateMillis)}" else null,
                    ).filterNotNull().joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(Money.format(invoice.totalPaise), style = MaterialTheme.typography.titleSmall)
                if (state != PaymentState.VOID && due > 0) {
                    Text("${Money.format(due)} due", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                } else if (state == PaymentState.PAID) {
                    Text("Settled", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun InvoiceCreateScreen(
    vm: InvoiceFormViewModel,
    invoiceId: Long? = null,
    onBack: () -> Unit,
    onIssued: (Long) -> Unit,
) {
    val repo = LocalRepository.current
    val customers by vm.customers.collectAsStateWithLifecycle(emptyList())
    val products by vm.products.collectAsStateWithLifecycle(emptyList())
    var customerMenu by remember { mutableStateOf(false) }
    var showCustomerDetails by remember { mutableStateOf(false) }
    var ready by remember(invoiceId) { mutableStateOf(invoiceId == null) }
    val totals = vm.previewTotals()

    LaunchedEffect(invoiceId) {
        if (invoiceId == null) {
            vm.startNew()
            ready = true
        } else {
            val invoice = repo.invoice(invoiceId)
            val customer = invoice?.let { repo.customer(it.customerId) }
            if (invoice != null && customer != null) {
                vm.loadForEdit(invoice, customer, repo.invoiceLines(invoice.id))
            } else {
                vm.message = "Invoice not found."
            }
            ready = true
        }
    }

    LaunchedEffect(vm.savedInvoiceId) {
        vm.savedInvoiceId?.let { id ->
            vm.resetAfterSave()
            onIssued(id)
        }
    }

    if (!ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp, end = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text(if (vm.isEditing) "Edit invoice" else "New invoice", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (vm.isEditing) "Update the bill and its line items" else "GST sales invoice",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (vm.saving) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = "Invoice details", subtitle = "Number, dates and supply information") {
                    FormField(
                        "Invoice number (optional)",
                        vm.invoiceNumber,
                        { vm.invoiceNumber = it },
                        supporting = "Leave blank to auto-assign with your configured prefix.",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { DateField("Invoice date", vm.invoiceDate) { vm.invoiceDate = it } }
                        Box(Modifier.weight(1f)) { DateField("Due date", vm.dueDate, clearable = true) { vm.dueDate = it } }
                    }
                    FormField("Place of supply", vm.placeOfSupply) { vm.placeOfSupply = it }
                    FormField("Notes (optional)", vm.notes, { vm.notes = it }, singleLine = false, minLines = 2)
                }
            }
            item {
                BillPhotoField(
                    path = vm.scanImagePath,
                    onChange = { vm.scanImagePath = it },
                    onMessage = { vm.message = it },
                )
            }
            item {
                SectionCard(
                    title = "Customer",
                    trailing = {
                        Box {
                            TextButton(onClick = { customerMenu = true }, enabled = customers.isNotEmpty()) { Text("Saved") }
                            DropdownMenu(customerMenu, { customerMenu = false }) {
                                customers.forEach { c ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(c.name)
                                                if (c.gstin.isNotBlank()) Text(c.gstin, style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        },
                                        onClick = { vm.selectCustomer(c); customerMenu = false },
                                    )
                                }
                            }
                        }
                    },
                ) {
                    val customer = vm.customerDraft
                    FormField("Customer / business name *", customer.name) { vm.updateCustomer { d -> d.copy(name = it) } }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FormField("Contact person", customer.contactPerson, { vm.updateCustomer { d -> d.copy(contactPerson = it) } }, Modifier.weight(1f))
                        FormField("Phone", customer.phone, { vm.updateCustomer { d -> d.copy(phone = it) } }, Modifier.weight(1f))
                    }
                    FormField("GSTIN", customer.gstin, { vm.updateCustomer { d -> d.copy(gstin = it.uppercase()) } })
                    TextButton(onClick = { showCustomerDetails = !showCustomerDetails }) {
                        Text(if (showCustomerDetails) "Hide address details" else "Add address details")
                    }
                    if (showCustomerDetails) {
                        FormField("Billing address", customer.billingAddress, { vm.updateCustomer { d -> d.copy(billingAddress = it) } }, singleLine = false, minLines = 2)
                        FormField("Shipping address", customer.shippingAddress, { vm.updateCustomer { d -> d.copy(shippingAddress = it) } }, singleLine = false, minLines = 2)
                        FormField("Email", customer.email, { vm.updateCustomer { d -> d.copy(email = it) } })
                        FormField("Customer notes", customer.notes, { vm.updateCustomer { d -> d.copy(notes = it) } }, singleLine = false, minLines = 2)
                    }
                }
            }
            item {
                ScreenHeading(title = "Items", subtitle = "${vm.lines.size} line${if (vm.lines.size == 1) "" else "s"}")
            }
            items(vm.lines.size) { index ->
                LineEditorCard(
                    index = index,
                    line = vm.lines[index],
                    products = products,
                    includeGst = vm.pricesIncludeGst,
                    isPurchase = false,
                    liveStock = vm::liveStock,
                    onRemove = {
                        if (vm.lines.size > 1) vm.lines.removeAt(index)
                        else vm.lines[index].use(Product(name = ""))
                    },
                )
            }
            item {
                OutlinedButton(onClick = vm::addLine, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Add item")
                }
                CatalogHint(products.size)
            }
            item {
                SectionCard(title = "Tax & pricing") {
                    SettingSwitch(
                        checked = vm.pricesIncludeGst,
                        onCheckedChange = { vm.pricesIncludeGst = it },
                        title = "Prices include GST",
                        subtitle = "Turn off to add GST on top of entered prices.",
                    )
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(vm.intraState, { vm.intraState = it })
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (vm.intraState) "Intra-state · CGST + SGST" else "Inter-state · IGST", style = MaterialTheme.typography.bodyMedium)
                            Text("Derived from GSTIN state codes. You can override it.", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (vm.isEditing) {
                item {
                    SectionCard(title = "Payments") {
                        Text(
                            "${Money.format(vm.paymentPaise() ?: 0)} already recorded. " +
                                "Payments and their history are kept when you edit this invoice.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                item {
                    SectionCard(title = "Payment received") {
                        FormField("Amount received ₹", vm.payment, { vm.payment = it }, number = true)
                        DateField("Payment date", vm.paymentDate) { vm.paymentDate = it }
                        PaymentModeChips(vm.paymentMode, { vm.paymentMode = it })
                    }
                }
            }
            item {
                SectionCard(title = "Summary") {
                    MoneyRow("Taxable value", totals.taxablePaise)
                    if (vm.intraState) {
                        MoneyRow("CGST", totals.split.cgstPaise)
                        MoneyRow("SGST", totals.split.sgstPaise)
                    } else MoneyRow("IGST", totals.split.igstPaise)
                    HorizontalDivider()
                    MoneyRow("Invoice total", totals.totalPaise, strong = true)
                    MoneyRow("Payment received", totals.paymentPaise)
                    MoneyRow("Balance due", totals.balancePaise, strong = true,
                        valueColor = if (totals.balancePaise > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
            }
        }
        Surface(tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                vm.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Invoice total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Money.format(totals.totalPaise), style = MaterialTheme.typography.titleLarge)
                    }
                    Button(onClick = vm::issue, enabled = !vm.saving) {
                        Text(
                            when {
                                vm.saving -> "Saving…"
                                vm.isEditing -> "Save changes"
                                else -> "Issue invoice"
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked, onCheckedChange)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun InvoiceDetailScreen(
    repo: Repository,
    invoiceId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDeleted: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    val invoice by repo.invoiceFlow(invoiceId).collectAsStateWithLifecycle(null)
    val payments by repo.invoicePayments(invoiceId).collectAsStateWithLifecycle(emptyList())
    var customer by remember { mutableStateOf<Customer?>(null) }
    var lines by remember { mutableStateOf<List<InvoiceLine>>(emptyList()) }
    var showPayment by remember { mutableStateOf(false) }
    var showVoid by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(invoice) {
        invoice?.let { saved ->
            customer = repo.customer(saved.customerId)
            lines = repo.invoiceLines(saved.id)
        }
    }

    val saved = invoice
    if (saved == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val state = paymentState(saved.totalPaise, saved.paymentPaise, saved.status == "VOID")
    val balance = (saved.totalPaise - saved.paymentPaise).coerceAtLeast(0)

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(saved.number, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        StatusPill(state)
                    }
                    Text("Tax invoice", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (saved.status == "ISSUED") {
                    IconButton(onClick = { onEdit(saved.id) }) {
                        Icon(Icons.Default.Edit, "Edit invoice")
                    }
                }
                IconButton(onClick = {
                    val c = customer ?: return@IconButton
                    scope.launch {
                        try {
                            shareInvoicePdf(context, repo, saved, lines, c, accent)
                        } catch (e: Exception) {
                            message = e.message ?: "Could not create the PDF."
                        }
                    }
                }) { Icon(Icons.Default.Share, "Share invoice PDF") }
            }
        }
        item {
            SectionCard(title = customer?.name ?: "Customer", subtitle = "Invoice ${saved.number}") {
                customer?.let { c ->
                    if (c.gstin.isNotBlank()) Text("GSTIN ${c.gstin}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (c.phone.isNotBlank()) Text("Phone ${c.phone}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (c.billingAddress.isNotBlank()) Text(c.billingAddress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SummaryValue("Invoice date", formatDate(saved.dateMillis), Modifier.weight(1f))
                    SummaryValue("Due date", if (saved.dueDateMillis > 0) formatDate(saved.dueDateMillis) else "Not set", Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SummaryValue("Tax", if (saved.intraState) "CGST + SGST" else "IGST", Modifier.weight(1f))
                    SummaryValue("Place of supply", saved.placeOfSupply.ifBlank { "—" }, Modifier.weight(1f))
                }
                if (saved.status == "VOID") {
                    Text("Void · ${saved.voidReason.orEmpty()}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (saved.notes.isNotBlank()) {
                    HorizontalDivider()
                    Text(saved.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                saved.scanImagePath?.let { path ->
                    TextButton(onClick = { openPrivateFile(context, path, "image/*") }) {
                        Icon(Icons.Default.Image, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("Open attached bill photo")
                    }
                }
            }
        }
        item { ScreenHeading(title = "Line items", subtitle = "${lines.size} item${if (lines.size == 1) "" else "s"}") }
        item {
            SectionCard {
                if (lines.isEmpty()) Text("Loading items…", style = MaterialTheme.typography.bodySmall)
                lines.forEachIndexed { index, line ->
                    if (index > 0) HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(line.productName, style = MaterialTheme.typography.bodyMedium)
                            Text("${line.qty.toString().removeSuffix(".0")} ${line.unit} × ${Money.format(line.unitPricePaise)} · GST ${line.gstRate.toString().removeSuffix(".0")}%",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (line.hsn.isNotBlank()) Text("HSN ${line.hsn}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(Money.format(line.totalPaise), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
        item {
            SectionCard(title = "Invoice totals") {
                MoneyRow("Taxable value", saved.taxablePaise)
                if (saved.intraState) {
                    MoneyRow("CGST", saved.gstPaise / 2)
                    MoneyRow("SGST", saved.gstPaise - saved.gstPaise / 2)
                } else MoneyRow("IGST", saved.gstPaise)
                HorizontalDivider()
                MoneyRow("Total", saved.totalPaise, strong = true)
                MoneyRow("Received", saved.paymentPaise)
                MoneyRow("Balance", balance, strong = true,
                    valueColor = if (balance > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        }
        item {
            PaymentHistoryCard(
                entries = payments.map { PaymentRow(it.amountPaise, it.dateMillis, it.mode, it.note) },
                totalPaise = saved.totalPaise,
                paidPaise = saved.paymentPaise,
                onRecordPayment = if (saved.status == "ISSUED" && balance > 0) {
                    { showPayment = true }
                } else null,
            )
        }
        if (saved.status == "ISSUED") {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (balance > 0) {
                        Button(onClick = { showPayment = true }, modifier = Modifier.weight(1f)) { Text("Record payment") }
                    }
                    OutlinedButton(onClick = { showVoid = true }, modifier = Modifier.weight(1f)) { Text("Void invoice") }
                }
            }
        }
        item {
            TextButton(
                onClick = { showDelete = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Delete invoice")
            }
        }
        message?.let { text ->
            item { Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }

    if (showPayment) {
        PaymentDialog(
            title = "Record customer payment",
            balancePaise = balance,
            onDismiss = { showPayment = false },
            onSave = { amount, date, mode, note ->
                showPayment = false
                scope.launch {
                    try {
                        repo.recordInvoicePayment(saved.id, amount, date, mode, note)
                        message = "Payment recorded."
                    } catch (e: IllegalArgumentException) {
                        message = e.message ?: "Could not record payment."
                    }
                }
            },
        )
    }
    if (showVoid) {
        ReasonDialog(
            title = "Void invoice?",
            message = "The invoice stays in your records, is marked void, and its stock movement is reversed.",
            confirmLabel = "Confirm void",
            onDismiss = { showVoid = false },
            onConfirm = { reason ->
                showVoid = false
                scope.launch {
                    try {
                        repo.voidInvoice(saved.id, reason)
                        message = "Invoice voided and stock reversed."
                    } catch (e: IllegalArgumentException) {
                        message = e.message ?: "Could not void invoice."
                    }
                }
            },
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete invoice?") },
            text = {
                Text(
                    if (saved.status == "ISSUED") {
                        "Invoice ${saved.number}, its line items and payment history will be permanently deleted. " +
                            "Stock will be returned to inventory. This cannot be undone."
                    } else {
                        "This void invoice and all of its records will be permanently deleted. This cannot be undone."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showDelete = false
                        scope.launch {
                            try {
                                repo.deleteInvoice(saved.id)
                                onDeleted()
                            } catch (e: IllegalArgumentException) {
                                message = e.message ?: "Could not delete invoice."
                            }
                        }
                    },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton({ showDelete = false }) { Text("Cancel") } },
        )
    }
}
