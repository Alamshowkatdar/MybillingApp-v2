package com.factorybill.app.ui

import android.content.Intent
import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.factorybill.app.core.*
import com.factorybill.app.data.*
import com.factorybill.app.pdf.InvoicePdf
import com.factorybill.app.scan.OcrScanner
import com.factorybill.app.scan.ScanResult
import com.factorybill.app.scan.ScanStore
import com.factorybill.app.scan.LocalAiBillScanner
import com.factorybill.app.scan.LocalAiScanResult
import com.factorybill.app.scan.CloudBillScanner
import com.factorybill.app.scan.CloudScanConfig
import com.factorybill.app.scan.CloudScanResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale

@Composable
private fun WorkflowField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    number: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text
        ),
    )
}

@Composable
private fun WorkflowField(label: String, value: String, onChange: (String) -> Unit) =
    WorkflowField(label, value, onChange, false)

@Composable
private fun DatePickerField(label: String, dateMillis: Long, onDateSelected: (Long) -> Unit) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance().apply { timeInMillis = dateMillis }
    OutlinedButton(
        onClick = {
            DatePickerDialog(
                context,
                { _, year, month, day ->
                    onDateSelected(Calendar.getInstance().apply {
                        set(year, month, day, 12, 0, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH),
            ).show()
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("$label · ${SimpleDateFormat("dd MMM yyyy", Locale("en", "IN")).format(Date(dateMillis))}")
    }
}

private fun todayMillis(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 12)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private class ReviewLine(
    name: String,
    qty: String,
    price: String,
    gst: Double,
) {
    var name by mutableStateOf(name)
    var qty by mutableStateOf(qty)
    var price by mutableStateOf(price)
    var gst by mutableStateOf(gst)
}

@Composable
fun ScanScreen(repo: Repository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var imageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf(false) }
    var rawText by remember { mutableStateOf("") }
    var scanSource by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var customer by remember { mutableStateOf("") }
    var invoiceNumber by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var gstin by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var payment by remember { mutableStateOf("") }
    var placeOfSupply by remember { mutableStateOf("") }
    var intraState by remember { mutableStateOf(true) }
    var pricesIncludeGst by remember { mutableStateOf(false) }
    var isPurchaseBill by remember { mutableStateOf(true) }
    var allowCloudScan by remember { mutableStateOf(false) }
    val lines = remember { mutableStateListOf<ReviewLine>() }

    fun process(uri: android.net.Uri) {
        imageUri = uri
        rawText = ""
        scanSource = ""
        message = null
        customer = ""
        invoiceNumber = ""
        phone = ""
        gstin = ""
        address = ""
        payment = ""
        placeOfSupply = ""
        lines.clear()
        busy = true
        scope.launch {
            fun applySuggestion(suggestion: OcrSuggestion, source: String) {
                rawText = suggestion.rawText
                scanSource = source
                customer = suggestion.customerName.orEmpty()
                phone = suggestion.phone.orEmpty()
                gstin = suggestion.gstin.orEmpty()
                address = suggestion.address.orEmpty()
                payment = suggestion.paymentPaise?.let(::rupeesText).orEmpty()
                lines.clear()
                suggestion.lines.forEach { item ->
                    lines += ReviewLine(
                        item.name,
                        item.qty?.toString().orEmpty(),
                        item.unitPricePaise?.let(::rupeesText).orEmpty(),
                        item.gstRate ?: 18.0,
                    )
                }
                message = if (lines.isEmpty()) {
                    "$source found no clear line items. Add items yourself and verify every value."
                } else {
                    "$source found ${lines.size} item${if (lines.size == 1) "" else "s"}. Verify all details before saving."
                }
            }

            val aiResult = LocalAiBillScanner.recognize(context, uri) { message = it }
            when (aiResult) {
                is LocalAiScanResult.Success -> {
                    applySuggestion(aiResult.suggestion, "Gemini Nano on this phone")
                }
                is LocalAiScanResult.Unavailable -> {
                    val reason = aiResult.reason
                    val cloudResult = if (allowCloudScan && CloudScanConfig.isConfigured) {
                        message = "Sending this bill image to Google Gemini through your secured Cloud Run service…"
                        CloudBillScanner.recognize(context, uri)
                    } else {
                        null
                    }
                    when (cloudResult) {
                        is CloudScanResult.Success -> applySuggestion(
                            cloudResult.suggestion,
                            "Google Gemini cloud (image sent to Google)",
                        )
                        is CloudScanResult.Unavailable -> {
                            message = "Cloud scan unavailable (${cloudResult.reason}). Trying offline text recognition…"
                            when (val ocr = OcrScanner.recognize(context, uri)) {
                                is ScanResult.Success -> applySuggestion(OcrParser.parse(ocr.text), "Offline text recognition")
                                ScanResult.NoText -> message = "Cloud scan unavailable (${cloudResult.reason}). On-device AI also failed ($reason), and offline OCR found no text. Enter details manually or try a clearer photo."
                                is ScanResult.Failure -> message = "Cloud scan unavailable (${cloudResult.reason}). On-device AI also failed ($reason). ${ocr.message}"
                            }
                        }
                        null -> {
                            message = "On-device AI unavailable ($reason). Trying offline text recognition…"
                            when (val ocr = OcrScanner.recognize(context, uri)) {
                                is ScanResult.Success -> applySuggestion(OcrParser.parse(ocr.text), "Offline text recognition")
                                ScanResult.NoText -> message = "On-device AI unavailable ($reason). No readable text found either. Use a clear, well-lit photo or enter details manually."
                                is ScanResult.Failure -> message = "On-device AI unavailable ($reason). ${ocr.message}"
                            }
                        }
                    }
                }
            }
            busy = false
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) process(uri)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val uri = imageUri
        if (captured && uri != null) process(uri)
        else {
            cameraFile?.delete()
            cameraFile = null
            imageUri = null
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Capture a bill", style = MaterialTheme.typography.headlineMedium)
            Text("Try Gemini Nano on your phone first. Cloud scanning is optional and requires your consent; review every suggestion before saving.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(isPurchaseBill, { isPurchaseBill = true }, label = { Text("Supplier purchase") })
                FilterChip(!isPurchaseBill, { isPurchaseBill = false }, label = { Text("Sales invoice") })
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = allowCloudScan,
                            onCheckedChange = { allowCloudScan = it },
                            enabled = CloudScanConfig.isConfigured,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Allow Google cloud scan", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (CloudScanConfig.isConfigured) {
                                    "Only used if on-device AI fails. The bill image will be sent to Google."
                                } else {
                                    "Cloud Run is not configured in this app build."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = { picker.launch("image/*") }) {
                    Icon(Icons.Default.Image, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Choose photo")
                }
                FilledTonalButton(onClick = {
                    try {
                        val (file, uri) = ScanStore.cameraTarget(context)
                        cameraFile = file
                        imageUri = uri
                        camera.launch(uri)
                    } catch (e: Exception) {
                        message = "Could not open the camera: ${e.message ?: "unknown error"}"
                    }
                }) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Take photo")
                }
            }
        }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(message ?: "Preparing on-device AI…") }
        message?.let { text -> item { Text(text, color = MaterialTheme.colorScheme.primary) } }
        if (imageUri != null && !busy) {
            item { Text("Review before ${if (isPurchaseBill) "recording purchase" else "issuing invoice"}", style = MaterialTheme.typography.titleLarge) }
            item {
                WorkflowField(if (isPurchaseBill) "Supplier name" else "Customer name", customer) { customer = it }
                if (!isPurchaseBill) {
                    WorkflowField("Invoice number (optional)", invoiceNumber) { invoiceNumber = it }
                    Text(
                        "Leave blank to assign the next invoice number automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                WorkflowField("Phone", phone) { phone = it }
                WorkflowField("GSTIN", gstin) { gstin = it.uppercase() }
                WorkflowField("Address", address) { address = it }
                if (!isPurchaseBill) WorkflowField("Place of supply", placeOfSupply) { placeOfSupply = it }
            }
            items(lines) { line ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        WorkflowField("Item name", line.name) { line.name = it }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(line.qty, { line.qty = it }, label = { Text("Qty") }, modifier = Modifier.weight(1f), singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            OutlinedTextField(line.price, { line.price = it }, label = { Text("Unit price ₹") }, modifier = Modifier.weight(1f), singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Gst.STANDARD_RATES.forEach { rate ->
                                FilterChip(line.gst == rate, { line.gst = rate }, label = { Text("${rate.toString().removeSuffix(".0")}%") })
                            }
                        }
                        TextButton(onClick = { lines.remove(line) }) { Text("Remove item") }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { lines += ReviewLine("", "1", "", 18.0) }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null); Text("Add item")
                }
            }
            item { WorkflowField(if (isPurchaseBill) "Amount paid ₹" else "Payment received ₹", payment, { payment = it }, number = true) }
            item {
                if (!isPurchaseBill) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(intraState, { intraState = it }); Spacer(Modifier.width(8.dp))
                        Text(if (intraState) "Same state · CGST + SGST" else "Inter-state · IGST")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(pricesIncludeGst, { pricesIncludeGst = it }); Spacer(Modifier.width(8.dp))
                    Text("Entered prices include GST")
                }
            }
            item {
                rawText.takeIf(String::isNotBlank)?.let {
                    Text(scanSource.ifBlank { "Recognized text" }, style = MaterialTheme.typography.titleSmall)
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Button(
                    enabled = !busy,
                    onClick = {
                        message = null
                        val parsedLines = mutableListOf<DraftLine>()
                        for (line in lines) {
                            val qty = line.qty.toDoubleOrNull()
                            val price = Money.parseRupees(line.price)
                            if (line.name.isBlank() || qty == null || price == null) {
                                message = "Enter an item name, a valid quantity and a valid price for every line."
                                return@Button
                            }
                            parsedLines += DraftLine(
                                Product(name = line.name.trim(), defaultPricePaise = price, defaultGstRate = line.gst),
                                qty,
                                price,
                                line.gst,
                                pricesIncludeGst,
                            )
                        }
                        val paid = Money.parseRupees(payment.ifBlank { "0" })
                        if (customer.isBlank() || parsedLines.isEmpty() || paid == null) {
                            message = "Enter a ${if (isPurchaseBill) "supplier" else "customer"}, at least one item and a valid payment amount."
                            return@Button
                        }
                        val source = imageUri
                        scope.launch {
                            val savedImage = source?.let { ScanStore.saveOriginal(context, it) }
                            if (savedImage == null) {
                                message = "Could not securely save the original image. Choose the image again and retry."
                                return@launch
                            }
                            try {
                                if (isPurchaseBill) {
                                    repo.savePurchase(
                                        PurchaseDraft(
                                            Supplier(name = customer.trim(), phone = phone.trim(), gstin = gstin.trim().uppercase(), address = address.trim()),
                                            parsedLines,
                                            paid,
                                            pricesIncludeGst = pricesIncludeGst,
                                            scanImagePath = savedImage,
                                        )
                                    )
                                } else {
                                    repo.issueInvoice(
                                        InvoiceDraft(
                                            Customer(name = customer.trim(), phone = phone.trim(), gstin = gstin.trim().uppercase(),
                                                billingAddress = address.trim(), shippingAddress = address.trim()),
                                            parsedLines,
                                            paid,
                                            intraState,
                                            placeOfSupply,
                                            pricesIncludeGst,
                                            savedImage,
                                            invoiceNumber = invoiceNumber,
                                        )
                                    )
                                }
                                cameraFile?.delete()
                                cameraFile = null
                                imageUri = null
                                lines.clear()
                                message = if (isPurchaseBill) {
                                    "Supplier purchase recorded, stock updated, and original scan saved on this device."
                                } else {
                                    "Invoice issued. The original scan is saved privately on this device."
                                }
                            } catch (e: IllegalArgumentException) {
                                ScanStore.discard(savedImage)
                                message = e.message ?: if (isPurchaseBill) "The purchase could not be recorded." else "The invoice could not be issued."
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (isPurchaseBill) "Confirm and record purchase" else "Confirm and issue invoice") }
            }
        }
    }
}

@Composable
fun OperationsScreen(repo: Repository) {
    val products by repo.products.collectAsStateWithLifecycle(emptyList())
    val suppliers by repo.suppliers.collectAsStateWithLifecycle(emptyList())
    val purchases by repo.purchases.collectAsStateWithLifecycle(emptyList())
    val expenses by repo.expenses.collectAsStateWithLifecycle(emptyList())
    val employees by repo.employees.collectAsStateWithLifecycle(emptyList())
    val movements by repo.movements.collectAsStateWithLifecycle(emptyList())
    var section by remember { mutableStateOf("Inventory") }
    var showPurchase by remember { mutableStateOf(false) }
    var showExpense by remember { mutableStateOf(false) }
    var showAddEmployee by remember { mutableStateOf(false) }
    var managingEmployee by remember { mutableStateOf<Employee?>(null) }
    var showAddProduct by remember { mutableStateOf(false) }
    var adjustProduct by remember { mutableStateOf<Product?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        Text("Operations", Modifier.padding(start = 20.dp, top = 16.dp), style = MaterialTheme.typography.headlineMedium)
        ScrollableTabRow(selectedTabIndex = listOf("Inventory", "Purchases", "Expenses", "Team").indexOf(section)) {
            listOf("Inventory", "Purchases", "Expenses", "Team").forEach { label ->
                Tab(section == label, { section = label }, text = { Text(label) })
            }
        }
        message?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.primary) }
        when (section) {
            "Inventory" -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column { Text("Products & stock", style = MaterialTheme.typography.titleLarge); Text("${products.size} items", style = MaterialTheme.typography.bodySmall) }
                    }
                }
                item { OutlinedButton(onClick = { showAddProduct = true }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text("Add product") } }
                if (products.isEmpty()) item { Text("Add products to start tracking stock.") }
                items(products, key = { it.id }) { product ->
                    Card(Modifier.fillMaxWidth().clickable { adjustProduct = product }) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(product.name, style = MaterialTheme.typography.titleMedium)
                                Text("HSN ${product.hsn.ifBlank { "—" }} · ${product.defaultGstRate}% GST",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${product.stockQty} ${product.unit}", style = MaterialTheme.typography.titleSmall)
                                Text(Money.format(product.defaultPricePaise), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                item { Text("Recent stock movements", style = MaterialTheme.typography.titleMedium) }
                items(movements.take(20)) { movement ->
                    val name = products.firstOrNull { it.id == movement.productId }?.name ?: "Product #${movement.productId}"
                    ListItem(headlineContent = { Text(name) }, supportingContent = { Text(movement.reason) },
                        trailingContent = { Text("${if (movement.delta > 0) "+" else ""}${movement.delta}") })
                }
            }
            "Purchases" -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Button(onClick = { showPurchase = true }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text("Record purchase") } }
                if (purchases.isEmpty()) item { Text("Supplier purchases will appear here.") }
                items(purchases, key = { it.id }) { purchase ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Purchase #${purchase.id}", style = MaterialTheme.typography.titleMedium)
                            Text(SimpleDateFormat("dd MMM yyyy", Locale("en", "IN")).format(Date(purchase.dateMillis)))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total ${Money.format(purchase.totalPaise)}")
                                Text("Due ${Money.format(purchase.totalPaise - purchase.paidPaise)}")
                            }
                            purchase.scanImagePath?.let {
                                Text("Original supplier bill saved on this device.", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            "Expenses" -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Button(onClick = { showExpense = true }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text("Add expense") } }
                if (expenses.isEmpty()) item { Text("Business expenses will appear here.") }
                items(expenses, key = { it.id }) { expense ->
                    ListItem(headlineContent = { Text(expense.category) },
                        supportingContent = { Text(listOf(expense.payee, expense.notes).filter(String::isNotBlank).joinToString(" · ")) },
                        trailingContent = { Text(Money.format(expense.amountPaise)) })
                }
            }
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Button(onClick = { showAddEmployee = true }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text("Add employee") } }
                if (employees.isEmpty()) item { Text("Add employees to keep a salary ledger.") }
                items(employees, key = { it.id }) { employee ->
                    var balance by remember(employee.id) { mutableLongStateOf(0L) }
                    LaunchedEffect(employee.id) { repo.salaryBalance(employee.id).collect { balance = it } }
                    Card(Modifier.fillMaxWidth().clickable { managingEmployee = employee }) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(employee.name, style = MaterialTheme.typography.titleMedium)
                                Text("Started ${employee.startDateMillis.takeIf { it > 0 }?.let(::formatDate) ?: "date not recorded"}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Monthly salary ${Money.format(employee.monthlySalaryPaise)} · due ${Money.format(balance)}",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Text("Manage", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }

    if (showAddProduct) ProductDialog(onDismiss = { showAddProduct = false }) { product ->
        showAddProduct = false
        scope.launch {
            try { repo.addProduct(product); message = "Product added." }
            catch (e: IllegalArgumentException) { message = e.message }
        }
    }
    if (showPurchase) PurchaseDialog(suppliers, products, onDismiss = { showPurchase = false }) { draft ->
        showPurchase = false
        scope.launch {
            try { repo.savePurchase(draft); message = "Purchase recorded and stock updated." }
            catch (e: IllegalArgumentException) { message = e.message }
        }
    }
    if (showExpense) ExpenseDialog(onDismiss = { showExpense = false }) { expense ->
        showExpense = false
        scope.launch {
            try { repo.addExpense(expense); message = "Expense saved." }
            catch (e: IllegalArgumentException) { message = e.message }
        }
    }
    if (showAddEmployee) EmployeeCreateDialog(onDismiss = { showAddEmployee = false }) { employee ->
        showAddEmployee = false
        scope.launch {
            try { repo.addEmployee(employee); message = "Employee added. Record salary accruals and payments from their profile." }
            catch (e: IllegalArgumentException) { message = e.message }
        }
    }
    managingEmployee?.let { employee ->
        EmployeePayrollDialog(repo, employee, onDismiss = { managingEmployee = null }) { type, amount, date, note ->
            scope.launch {
                try {
                    repo.recordSalary(employee.id, type, amount, note, date)
                    message = "Payroll entry saved."
                } catch (e: IllegalArgumentException) { message = e.message }
            }
        }
    }
    adjustProduct?.let { product ->
        StockAdjustmentDialog(product, onDismiss = { adjustProduct = null }) { delta, note ->
            adjustProduct = null
            scope.launch {
                try { repo.adjustStock(product.id, delta, note); message = "Stock updated." }
                catch (e: IllegalArgumentException) { message = e.message }
            }
        }
    }
}

@Composable
private fun PurchaseDialog(
    suppliers: List<Supplier>,
    products: List<Product>,
    onDismiss: () -> Unit,
    onSave: (PurchaseDraft) -> Unit,
) {
    var supplier by remember { mutableStateOf(suppliers.firstOrNull()) }
    var supplierName by remember { mutableStateOf("") }
    var product by remember { mutableStateOf(products.firstOrNull()) }
    var quantity by remember { mutableStateOf("1") }
    var cost by remember { mutableStateOf("") }
    var paid by remember { mutableStateOf("0") }
    var gst by remember { mutableStateOf(18.0) }
    var menu by remember { mutableStateOf(false) }
    var productMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record purchase") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkflowField("Supplier name", supplierName.ifBlank { supplier?.name.orEmpty() }) { supplierName = it; supplier = null }
                if (suppliers.isNotEmpty()) {
                    Box {
                        OutlinedButton(onClick = { menu = true }, Modifier.fillMaxWidth()) { Text(supplier?.name ?: "Choose saved supplier") }
                        DropdownMenu(menu, { menu = false }) { suppliers.forEach { s -> DropdownMenuItem({ Text(s.name) }, { supplier = s; supplierName = s.name; menu = false }) } }
                    }
                }
                Box {
                    OutlinedButton(onClick = { productMenu = true }, Modifier.fillMaxWidth()) { Text(product?.name ?: "Choose product") }
                    DropdownMenu(productMenu, { productMenu = false }) { products.forEach { p -> DropdownMenuItem({ Text(p.name) }, { product = p; cost = rupeesText(p.defaultPricePaise); gst = p.defaultGstRate; productMenu = false }) } }
                }
                WorkflowField("Quantity received", quantity, { quantity = it }, true)
                WorkflowField("Unit cost ₹", cost, { cost = it }, true)
                WorkflowField("Amount paid ₹", paid, { paid = it }, true)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Gst.STANDARD_RATES.forEach { rate -> FilterChip(gst == rate, { gst = rate }, label = { Text("${rate.toString().removeSuffix(".0")}%") }) }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val q = quantity.toDoubleOrNull()
                val price = Money.parseRupees(cost)
                val payment = Money.parseRupees(paid)
                val selected = product
                val name = supplierName.ifBlank { supplier?.name.orEmpty() }
                if (name.isBlank() || selected == null || q == null || price == null || payment == null) {
                    error = "Enter a supplier, choose a product and provide valid amounts."
                } else {
                    val savedSupplier = supplier?.takeIf { it.name.equals(name, ignoreCase = true) } ?: Supplier(name = name)
                    onSave(PurchaseDraft(savedSupplier, listOf(DraftLine(selected, q, price, gst)), payment))
                    onDismiss()
                }
            }) { Text("Save purchase") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ExpenseDialog(onDismiss: () -> Unit, onSave: (Expense) -> Unit) {
    var category by remember { mutableStateOf("") }
    var payee by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add expense") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WorkflowField("Category", category) { category = it }
            WorkflowField("Paid to", payee) { payee = it }
            WorkflowField("Amount ₹", amount, { amount = it }, true)
            WorkflowField("Notes", note) { note = it }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(onClick = {
            val paise = Money.parseRupees(amount)
            if (category.isBlank() || paise == null || paise <= 0) error = "Enter a category and a valid amount greater than zero."
            else onSave(Expense(category = category.trim(), dateMillis = System.currentTimeMillis(), amountPaise = paise, payee = payee.trim(), notes = note.trim()))
        }) { Text("Save expense") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

@Composable
private fun EmployeeCreateDialog(
    onDismiss: () -> Unit,
    onSave: (Employee) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var salary by remember { mutableStateOf("") }
    var startDate by remember { mutableLongStateOf(todayMillis()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add employee") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkflowField("Employee name", name) { name = it }
                WorkflowField("Monthly salary ₹", salary, { salary = it }, true)
                DatePickerField("Date started", startDate) { startDate = it }
                Text("Salary only becomes due when you record an accrual in the employee ledger.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val salaryPaise = Money.parseRupees(salary)
                if (name.isBlank() || salaryPaise == null) error = "Enter an employee name and a valid monthly salary."
                else onSave(Employee(name = name.trim(), monthlySalaryPaise = salaryPaise, startDateMillis = startDate))
            }) { Text("Save employee") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EmployeePayrollDialog(
    repo: Repository,
    employee: Employee,
    onDismiss: () -> Unit,
    onSaveEntry: (SalaryType, Long, Long, String) -> Unit,
) {
    val entries by repo.salaryEntries(employee.id).collectAsStateWithLifecycle(emptyList())
    val balance by repo.salaryBalance(employee.id).collectAsStateWithLifecycle(0L)
    var entryType by remember { mutableStateOf<SalaryType?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(employee.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Started ${employee.startDateMillis.takeIf { it > 0 }?.let(::formatDate) ?: "date not recorded"}")
                Text("Monthly salary ${Money.format(employee.monthlySalaryPaise)}")
                Text("Salary due ${Money.format(balance)}", style = MaterialTheme.typography.titleMedium,
                    color = if (balance > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SalaryType.values().forEach { type ->
                        FilledTonalButton(onClick = { entryType = type }, modifier = Modifier.weight(1f)) {
                            Text(salaryTypeLabel(type))
                        }
                    }
                }
                Text("Salary history", style = MaterialTheme.typography.titleMedium)
                if (entries.isEmpty()) {
                    Text("No salary or payment entries yet.", style = MaterialTheme.typography.bodySmall)
                } else {
                    entries.forEach { entry ->
                        val type = runCatching { SalaryType.valueOf(entry.type) }.getOrNull()
                        val sign = if (type == SalaryType.SALARY_ADDED) "+" else "−"
                        ListItem(
                            headlineContent = { Text("${type?.let(::salaryTypeLabel) ?: entry.type} · ${Money.format(entry.amountPaise)}") },
                            supportingContent = {
                                Text(listOf(formatDate(entry.dateMillis), entry.note).filter(String::isNotBlank).joinToString(" · "))
                            },
                            trailingContent = { Text(sign) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Done") } },
    )
    entryType?.let { type ->
        SalaryEntryDialog(
            employee = employee,
            type = type,
            onDismiss = { entryType = null },
            onSave = { amount, date, note ->
                entryType = null
                onSaveEntry(type, amount, date, note)
            },
        )
    }
}

@Composable
private fun SalaryEntryDialog(
    employee: Employee,
    type: SalaryType,
    onDismiss: () -> Unit,
    onSave: (Long, Long, String) -> Unit,
) {
    var amount by remember(type) {
        mutableStateOf(if (type == SalaryType.SALARY_ADDED) rupeesText(employee.monthlySalaryPaise) else "")
    }
    var date by remember(type) { mutableLongStateOf(todayMillis()) }
    var note by remember(type) { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${salaryTypeLabel(type)} · ${employee.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkflowField("Amount ₹", amount, { amount = it }, true)
                DatePickerField("Entry date", date) { date = it }
                WorkflowField("Note (optional)", note) { note = it }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = {
            val amountPaise = Money.parseRupees(amount)
            if (amountPaise == null || amountPaise <= 0) error = "Enter a valid amount greater than zero."
            else onSave(amountPaise, date, note.trim())
        }) { Text("Save entry") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

private fun salaryTypeLabel(type: SalaryType): String = when (type) {
    SalaryType.SALARY_ADDED -> "Accrue salary"
    SalaryType.ADVANCE -> "Advance taken"
    SalaryType.PAYMENT -> "Payment"
}

private fun formatDate(dateMillis: Long): String =
    SimpleDateFormat("dd MMM yyyy", Locale("en", "IN")).format(Date(dateMillis))

@Composable
private fun StockAdjustmentDialog(product: Product, onDismiss: () -> Unit, onSave: (Double, String) -> Unit) {
    var delta by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Adjust ${product.name}") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Current stock: ${product.stockQty} ${product.unit}")
            WorkflowField("Change in quantity (+ or −)", delta, { delta = it }, true)
            WorkflowField("Reason", note) { note = it }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(onClick = {
            val change = delta.toDoubleOrNull()
            if (change == null || !change.isFinite() || change == 0.0) error = "Enter a finite, non-zero adjustment."
            else onSave(change, note)
        }) { Text("Apply adjustment") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

@Composable
fun InvoiceDetailsScreen(repo: Repository, invoiceId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    var invoice by remember(invoiceId) { mutableStateOf<Invoice?>(null) }
    var customer by remember(invoiceId) { mutableStateOf<Customer?>(null) }
    var lines by remember(invoiceId) { mutableStateOf<List<InvoiceLine>>(emptyList()) }
    var showVoid by remember { mutableStateOf(false) }
    var showPayment by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(invoiceId) {
        invoice = repo.invoice(invoiceId)
        invoice?.let { saved ->
            customer = repo.customer(saved.customerId)
            lines = repo.invoiceLines(saved.id)
        }
    }
    val saved = invoice
    if (saved == null) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            TextButton(onBack) { Text("Back") }
            Text("Invoice not found", style = MaterialTheme.typography.titleLarge)
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onBack) { Text("←  Invoices") }; Text(saved.number, style = MaterialTheme.typography.headlineMedium) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(customer?.name ?: "Customer", style = MaterialTheme.typography.titleLarge)
                    Text(SimpleDateFormat("dd MMM yyyy", Locale("en", "IN")).format(Date(saved.dateMillis)))
                    customer?.gstin?.takeIf(String::isNotBlank)?.let { Text("GSTIN $it") }
                    saved.placeOfSupply.takeIf(String::isNotBlank)?.let { Text("Place of supply · $it") }
                    Text(if (saved.intraState) "Intra-state · CGST + SGST" else "Inter-state · IGST")
                    if (saved.status == "VOID") Text("VOID · ${saved.voidReason.orEmpty()}", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        item { Text("Items", style = MaterialTheme.typography.titleLarge) }
        items(lines, key = { it.id }) { line ->
            ListItem(
                headlineContent = { Text(line.productName) },
                supportingContent = { Text("HSN ${line.hsn.ifBlank { "—" }} · ${line.qty} ${line.unit} × ${Money.format(line.unitPricePaise)} · GST ${line.gstRate}%") },
                trailingContent = { Text(Money.format(line.totalPaise)) },
            )
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    TotalRow("Taxable value", saved.taxablePaise)
                    if (saved.intraState) {
                        TotalRow("CGST", saved.gstPaise / 2)
                        TotalRow("SGST", saved.gstPaise - saved.gstPaise / 2)
                    } else TotalRow("IGST", saved.gstPaise)
                    HorizontalDivider()
                    TotalRow("Invoice total", saved.totalPaise)
                    TotalRow("Received", saved.paymentPaise)
                    TotalRow("Balance due", saved.totalPaise - saved.paymentPaise)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    scope.launch {
                        val cust = customer ?: return@launch
                        val file = withContext(Dispatchers.IO) {
                            InvoicePdf.create(context, saved, lines, cust, repo.companyOrDefault(), accent)
                        }
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, "Share or print invoice"))
                    }
                }) { Icon(Icons.Default.Share, null); Text("Share PDF") }
                if (saved.status == "ISSUED" && saved.totalPaise > saved.paymentPaise) {
                    OutlinedButton(onClick = { showPayment = true }) { Text("Record payment") }
                }
                if (saved.status == "ISSUED") OutlinedButton(onClick = { showVoid = true }) { Text("Void invoice") }
            }
            saved.scanImagePath?.let { Text("Original bill scan saved privately on this device.", style = MaterialTheme.typography.bodySmall) }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    if (showVoid) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showVoid = false }, title = { Text("Void invoice?") },
            text = { Column { Text("The invoice record remains in your history and its stock movement is reversed.")
                WorkflowField("Reason", reason) { reason = it } } },
            confirmButton = { TextButton(onClick = {
                scope.launch {
                    try {
                        repo.voidInvoice(saved.id, reason)
                        invoice = repo.invoice(saved.id)
                        showVoid = false
                        message = "Invoice voided and stock reversed."
                    } catch (e: IllegalArgumentException) { message = e.message ?: "Could not void invoice." }
                }
            }) { Text("Confirm void") } },
            dismissButton = { TextButton(onClick = { showVoid = false }) { Text("Cancel") } })
    }
    if (showPayment) {
        var amount by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showPayment = false }, title = { Text("Record customer payment") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Balance due: ${Money.format(saved.totalPaise - saved.paymentPaise)}")
                WorkflowField("Amount received ₹", amount, { amount = it }, true)
            } },
            confirmButton = { TextButton(onClick = {
                val value = Money.parseRupees(amount)
                if (value == null || value <= 0) message = "Enter a valid payment amount greater than zero."
                else scope.launch {
                    try {
                        repo.recordInvoicePayment(saved.id, value)
                        invoice = repo.invoice(saved.id)
                        showPayment = false
                        message = "Payment recorded."
                    } catch (e: IllegalArgumentException) { message = e.message ?: "Could not record payment." }
                }
            }) { Text("Save payment") } },
            dismissButton = { TextButton(onClick = { showPayment = false }) { Text("Cancel") } })
    }
}
