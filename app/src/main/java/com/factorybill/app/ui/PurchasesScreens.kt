package com.factorybill.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.factorybill.app.core.*
import com.factorybill.app.data.*
import kotlinx.coroutines.launch

@Composable
fun PurchaseListScreen(onOpen: (Long) -> Unit, onCreate: () -> Unit) {
    val repo = LocalRepository.current
    val purchases by repo.purchases.collectAsStateWithLifecycle(emptyList())
    val suppliers by repo.suppliers.collectAsStateWithLifecycle(emptyList())
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    val names = remember(suppliers) { suppliers.associate { it.id to it.name } }

    val recorded = purchases.filter { it.status == "RECORDED" }
    val total = recorded.sumOf { it.totalPaise }
    val paid = recorded.sumOf { it.paidPaise }
    val outstanding = (total - paid).coerceAtLeast(0)
    val filtered = purchases.filter { purchase ->
        val matches = query.isBlank() || purchase.number.contains(query, true) ||
            purchase.billNumber.contains(query, true) ||
            (names[purchase.supplierId]?.contains(query, true) == true)
        val state = paymentState(purchase.totalPaise, purchase.paidPaise, purchase.status == "VOID")
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
                title = "Purchases",
                subtitle = "${recorded.size} supplier bill${if (recorded.size == 1) "" else "s"} · ${Money.format(outstanding)} payable",
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
                    PurchaseSummaryValue("Billed", Money.format(total), Modifier.weight(1f))
                    PurchaseSummaryValue("Paid", Money.format(paid), Modifier.weight(1f))
                    PurchaseSummaryValue(
                        "Payable", Money.format(outstanding), Modifier.weight(1f),
                        valueColor = if (outstanding > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        item { SearchField(query, { query = it }, "Search purchase, bill number or supplier") }
        item { FilterRow(filter, listOf("All", "Unpaid", "Partial", "Paid", "Void"), { filter = it }) }
        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    title = if (purchases.isEmpty()) "No purchases yet" else "No matching purchases",
                    message = if (purchases.isEmpty()) {
                        "Record supplier bills to track stock, payables and payment history."
                    } else {
                        "Try a different search or filter."
                    },
                    icon = Icons.Default.ShoppingCart,
                    actionLabel = if (purchases.isEmpty()) "Record purchase" else null,
                    onAction = if (purchases.isEmpty()) onCreate else null,
                )
            }
        }
        items(filtered, key = { it.id }) { purchase ->
            PurchaseListRow(
                purchase = purchase,
                supplierName = names[purchase.supplierId] ?: "Supplier",
                onClick = { onOpen(purchase.id) },
            )
        }
    }
}

@Composable
private fun PurchaseSummaryValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PurchaseListRow(purchase: Purchase, supplierName: String, onClick: () -> Unit) {
    val state = paymentState(purchase.totalPaise, purchase.paidPaise, purchase.status == "VOID")
    val due = (purchase.totalPaise - purchase.paidPaise).coerceAtLeast(0)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.size(42.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(supplierName.trim().take(2).uppercase(), style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.tertiary)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        purchase.number.ifBlank { "Purchase #${purchase.id}" },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    StatusPill(state)
                }
                Text(supplierName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        purchase.billNumber.takeIf(String::isNotBlank)?.let { "Bill $it" },
                        formatDate(purchase.dateMillis),
                        purchase.dueDateMillis.takeIf { it > 0 }?.let { "Due ${formatShortDate(it)}" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(Money.format(purchase.totalPaise), style = MaterialTheme.typography.titleSmall)
                when {
                    state == PaymentState.VOID -> Text("Void", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    due > 0 -> Text("${Money.format(due)} due", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error)
                    else -> Text("Settled", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun PurchaseCreateScreen(
    vm: PurchaseFormViewModel,
    purchaseId: Long? = null,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    val repo = LocalRepository.current
    val suppliers by vm.suppliers.collectAsStateWithLifecycle(emptyList())
    val products by vm.products.collectAsStateWithLifecycle(emptyList())
    var supplierMenu by remember { mutableStateOf(false) }
    var showSupplierDetails by remember { mutableStateOf(false) }
    var ready by remember(purchaseId) { mutableStateOf(purchaseId == null) }
    val totals = vm.previewTotals()

    LaunchedEffect(purchaseId) {
        if (purchaseId == null) {
            vm.startNew()
            ready = true
        } else {
            val purchase = repo.purchase(purchaseId)
            val supplier = purchase?.let { repo.supplier(it.supplierId) }
            if (purchase != null && supplier != null) {
                vm.loadForEdit(purchase, supplier, repo.purchaseLines(purchase.id))
            } else {
                vm.message = "Purchase not found."
            }
            ready = true
        }
    }

    LaunchedEffect(vm.savedPurchaseId) {
        vm.savedPurchaseId?.let { id ->
            vm.resetAfterSave()
            onSaved(id)
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
                Text(
                    if (vm.isEditing) "Edit purchase" else "Record purchase",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    if (vm.isEditing) "Update the supplier bill and its items" else "Supplier bill and stock receipt",
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
                SectionCard(title = "Purchase details", subtitle = "Supplier bill, dates and reference") {
                    FormField("Supplier bill number (optional)", vm.billNumber, { vm.billNumber = it })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            DateField("Purchase date", vm.purchaseDate) { vm.purchaseDate = it }
                        }
                        Box(Modifier.weight(1f)) {
                            DateField("Payment due", vm.dueDate, clearable = true) { vm.dueDate = it }
                        }
                    }
                    FormField("Internal note (optional)", vm.note, { vm.note = it }, singleLine = false, minLines = 2)
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
                    title = "Supplier",
                    trailing = {
                        Box {
                            TextButton(onClick = { supplierMenu = true }, enabled = suppliers.isNotEmpty()) { Text("Saved") }
                            DropdownMenu(supplierMenu, { supplierMenu = false }) {
                                suppliers.forEach { supplier ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(supplier.name)
                                                if (supplier.gstin.isNotBlank()) {
                                                    Text(supplier.gstin, style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        },
                                        onClick = { vm.selectSupplier(supplier); supplierMenu = false },
                                    )
                                }
                            }
                        }
                    },
                ) {
                    val supplier = vm.supplierDraft
                    FormField("Supplier / business name *", supplier.name) { value ->
                        vm.updateSupplier { it.copy(name = value) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FormField("Contact person", supplier.contactPerson, { value ->
                            vm.updateSupplier { it.copy(contactPerson = value) }
                        }, Modifier.weight(1f))
                        FormField("Phone", supplier.phone, { value ->
                            vm.updateSupplier { it.copy(phone = value) }
                        }, Modifier.weight(1f))
                    }
                    FormField("GSTIN", supplier.gstin, { value ->
                        vm.updateSupplier { it.copy(gstin = value.uppercase()) }
                    })
                    TextButton(onClick = { showSupplierDetails = !showSupplierDetails }) {
                        Text(if (showSupplierDetails) "Hide address details" else "Add address details")
                    }
                    if (showSupplierDetails) {
                        FormField("Address", supplier.address, { value ->
                            vm.updateSupplier { it.copy(address = value) }
                        }, singleLine = false, minLines = 2)
                        FormField("Email", supplier.email, { value ->
                            vm.updateSupplier { it.copy(email = value) }
                        })
                        FormField("Supplier notes", supplier.notes, { value ->
                            vm.updateSupplier { it.copy(notes = value) }
                        }, singleLine = false, minLines = 2)
                    }
                }
            }
            item {
                ScreenHeading(title = "Items received", subtitle = "${vm.lines.size} line${if (vm.lines.size == 1) "" else "s"}")
            }
            items(vm.lines.size) { index ->
                LineEditorCard(
                    index = index,
                    line = vm.lines[index],
                    products = products,
                    includeGst = vm.pricesIncludeGst,
                    isPurchase = true,
                    liveStock = { 0.0 },
                    onRemove = {
                        if (vm.lines.size > 1) vm.lines.removeAt(index)
                        else vm.lines[index].use(Product(name = ""))
                    },
                )
            }
            item {
                OutlinedButton(onClick = vm::addLine, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add item")
                }
                CatalogHint(products.size)
            }
            item {
                SectionCard(title = "Pricing") {
                    SettingSwitch(
                        checked = vm.pricesIncludeGst,
                        onCheckedChange = { vm.pricesIncludeGst = it },
                        title = "Prices include GST",
                        subtitle = "Turn off to add GST on top of entered supplier costs.",
                    )
                }
            }
            if (vm.isEditing) {
                item {
                    SectionCard(title = "Payments") {
                        Text(
                            "${Money.format(vm.paidPaise() ?: 0)} already recorded. " +
                                "Payments and their history are kept when you edit this purchase.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                item {
                    SectionCard(title = "Payment made") {
                        FormField("Amount paid ₹", vm.paid, { vm.paid = it }, number = true)
                        DateField("Payment date", vm.paymentDate) { vm.paymentDate = it }
                        PaymentModeChips(vm.paymentMode, { vm.paymentMode = it })
                    }
                }
            }
            item {
                SectionCard(title = "Summary") {
                    MoneyRow("Taxable value", totals.taxablePaise)
                    MoneyRow("GST", totals.gstPaise)
                    HorizontalDivider()
                    MoneyRow("Purchase total", totals.totalPaise, strong = true)
                    MoneyRow("Amount paid", totals.paymentPaise)
                    MoneyRow(
                        "Payable", totals.balancePaise, strong = true,
                        valueColor = if (totals.balancePaise > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Surface(tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                vm.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Purchase total", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Money.format(totals.totalPaise), style = MaterialTheme.typography.titleLarge)
                    }
                    Button(onClick = vm::save, enabled = !vm.saving) {
                        Text(
                            when {
                                vm.saving -> "Saving…"
                                vm.isEditing -> "Save changes"
                                else -> "Save purchase"
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PurchaseDetailScreen(
    repo: Repository,
    purchaseId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDeleted: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val purchase by repo.purchaseFlow(purchaseId).collectAsStateWithLifecycle(null)
    val payments by repo.purchasePayments(purchaseId).collectAsStateWithLifecycle(emptyList())
    var supplier by remember { mutableStateOf<Supplier?>(null) }
    var lines by remember { mutableStateOf<List<PurchaseLine>>(emptyList()) }
    var showPayment by remember { mutableStateOf(false) }
    var showVoid by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(purchase) {
        purchase?.let { saved ->
            supplier = repo.supplier(saved.supplierId)
            lines = repo.purchaseLines(saved.id)
        }
    }

    val saved = purchase
    if (saved == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val state = paymentState(saved.totalPaise, saved.paidPaise, saved.status == "VOID")
    val balance = (saved.totalPaise - saved.paidPaise).coerceAtLeast(0)

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            saved.number.ifBlank { "Purchase #${saved.id}" },
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        StatusPill(state)
                    }
                    Text("Supplier purchase", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (saved.status == "RECORDED") {
                    IconButton(onClick = { onEdit(saved.id) }) {
                        Icon(Icons.Default.Edit, "Edit purchase")
                    }
                }
            }
        }
        item {
            SectionCard(title = supplier?.name ?: "Supplier", subtitle = "Purchase ${saved.number}") {
                supplier?.let { value ->
                    if (value.gstin.isNotBlank()) {
                        Text("GSTIN ${value.gstin}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (value.phone.isNotBlank()) {
                        Text("Phone ${value.phone}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (value.address.isNotBlank()) {
                        Text(value.address, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    PurchaseSummaryValue("Purchase date", formatDate(saved.dateMillis), Modifier.weight(1f))
                    PurchaseSummaryValue(
                        "Due date",
                        if (saved.dueDateMillis > 0) formatDate(saved.dueDateMillis) else "Not set",
                        Modifier.weight(1f),
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    PurchaseSummaryValue(
                        "Supplier bill",
                        saved.billNumber.ifBlank { "—" },
                        Modifier.weight(1f),
                    )
                    PurchaseSummaryValue("Payment", saved.status, Modifier.weight(1f))
                }
                if (saved.status == "VOID") {
                    Text("Void · ${saved.voidReason.orEmpty()}", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                if (saved.note.isNotBlank()) {
                    HorizontalDivider()
                    Text(saved.note, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                saved.scanImagePath?.let { path ->
                    TextButton(onClick = { openPrivateFile(context, path, "image/*") }) {
                        Icon(Icons.Default.Image, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open attached bill photo")
                    }
                }
            }
        }
        item {
            ScreenHeading(
                title = "Items received",
                subtitle = "${lines.size} line${if (lines.size == 1) "" else "s"} added to stock",
            )
        }
        item {
            SectionCard {
                if (lines.isEmpty()) Text("Loading items…", style = MaterialTheme.typography.bodySmall)
                lines.forEachIndexed { index, line ->
                    if (index > 0) HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(line.productName.ifBlank { "Item" }, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${line.qty.toString().removeSuffix(".0")} ${line.unit} × ${Money.format(line.unitCostPaise)} · GST ${line.gstRate.toString().removeSuffix(".0")}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (line.hsn.isNotBlank()) {
                                Text("HSN ${line.hsn}", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(Money.format(line.totalPaise), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
        item {
            SectionCard(title = "Purchase totals") {
                MoneyRow("Taxable value", saved.taxablePaise)
                MoneyRow("GST", saved.gstPaise)
                HorizontalDivider()
                MoneyRow("Total", saved.totalPaise, strong = true)
                MoneyRow("Paid", saved.paidPaise)
                MoneyRow(
                    "Balance", balance, strong = true,
                    valueColor = if (balance > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }
        item {
            PaymentHistoryCard(
                entries = payments.map { PaymentRow(it.amountPaise, it.dateMillis, it.mode, it.note) },
                totalPaise = saved.totalPaise,
                paidPaise = saved.paidPaise,
                title = "Payment history",
                paidLabel = "paid",
                onRecordPayment = if (saved.status == "RECORDED" && balance > 0) {
                    { showPayment = true }
                } else null,
            )
        }
        if (saved.status == "RECORDED") {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (balance > 0) {
                        Button(onClick = { showPayment = true }, modifier = Modifier.weight(1f)) {
                            Text("Record payment")
                        }
                    }
                    OutlinedButton(onClick = { showVoid = true }, modifier = Modifier.weight(1f)) {
                        Text("Void purchase")
                    }
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
                Text("Delete purchase")
            }
        }
        message?.let { text ->
            item { Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }

    if (showPayment) {
        PaymentDialog(
            title = "Record supplier payment",
            balancePaise = balance,
            onDismiss = { showPayment = false },
            onSave = { amount, date, mode, note ->
                showPayment = false
                scope.launch {
                    try {
                        repo.recordPurchasePayment(saved.id, amount, date, mode, note)
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
            title = "Void purchase?",
            message = "The purchase stays in your records, is marked void, and the received stock is reversed.",
            confirmLabel = "Confirm void",
            onDismiss = { showVoid = false },
            onConfirm = { reason ->
                showVoid = false
                scope.launch {
                    try {
                        repo.voidPurchase(saved.id, reason)
                        message = "Purchase voided and stock reversed."
                    } catch (e: IllegalArgumentException) {
                        message = e.message ?: "Could not void purchase."
                    }
                }
            },
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete purchase?") },
            text = {
                Text(
                    if (saved.status == "RECORDED") {
                        "This supplier bill, its items and payment history will be permanently deleted. " +
                            "The stock received will be removed from inventory. This cannot be undone."
                    } else {
                        "This void purchase and all of its records will be permanently deleted. This cannot be undone."
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
                                repo.deletePurchase(saved.id)
                                onDeleted()
                            } catch (e: IllegalArgumentException) {
                                message = e.message ?: "Could not delete purchase."
                            }
                        }
                    },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton({ showDelete = false }) { Text("Cancel") } },
        )
    }
}
