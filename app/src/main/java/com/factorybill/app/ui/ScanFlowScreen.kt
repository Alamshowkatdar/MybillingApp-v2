package com.factorybill.app.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
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
import com.factorybill.app.scan.OcrScanner
import com.factorybill.app.scan.ScanResult
import com.factorybill.app.scan.ScanStore
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun ScanFlowScreen(
    repo: Repository,
    onOpenInvoice: (Long) -> Unit = {},
    onOpenPurchase: (Long) -> Unit = {},
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val products by repo.products.collectAsStateWithLifecycle(emptyList())
    val company by repo.company.collectAsStateWithLifecycle(null)

    var mode by remember { mutableStateOf("SALES") } // SALES | PURCHASE
    var imageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var cameraFile by remember { mutableStateOf<java.io.File?>(null) }
    var busy by remember { mutableStateOf(false) }
    var rawText by remember { mutableStateOf("") }
    var sourceLabel by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var partyName by remember { mutableStateOf("") }
    var partyPhone by remember { mutableStateOf("") }
    var partyGstin by remember { mutableStateOf("") }
    var partyAddress by remember { mutableStateOf("") }
    var invoiceNumber by remember { mutableStateOf("") }
    var billNumber by remember { mutableStateOf("") }
    var payment by remember { mutableStateOf("") }
    var placeOfSupply by remember { mutableStateOf("") }
    var intraState by remember { mutableStateOf(true) }
    var pricesIncludeGst by remember { mutableStateOf(false) }
    val lines = remember { mutableStateListOf<ReviewLine>() }
    var savedPath by remember { mutableStateOf<String?>(null) }

    fun reset() {
        imageUri = null
        cameraFile?.delete()
        cameraFile = null
        rawText = ""
        sourceLabel = ""
        partyName = ""
        partyPhone = ""
        partyGstin = ""
        partyAddress = ""
        invoiceNumber = ""
        billNumber = ""
        payment = ""
        placeOfSupply = ""
        lines.clear()
        savedPath = null
        message = null
    }

    fun process(uri: android.net.Uri) {
        imageUri = uri
        reset()
        imageUri = uri
        busy = true
        message = "Reading the bill..."
        scope.launch {
            when (val result = OcrScanner.recognize(context, uri)) {
                is ScanResult.Success -> {
                    rawText = result.text
                    sourceLabel = "Offline OCR (on-device)"
                    val suggestion = com.factorybill.app.core.OcrParser.parse(result.text)
                    partyName = suggestion.customerName.orEmpty()
                    partyPhone = suggestion.phone.orEmpty()
                    partyGstin = suggestion.gstin.orEmpty()
                    partyAddress = suggestion.address.orEmpty()
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
                    if (lines.isEmpty()) {
                        message = "No clear line items were found. Add items manually and verify all amounts before saving."
                    } else {
                        message = "Found ${lines.size} item${if (lines.size == 1) "" else "s"}. Verify details before saving."
                    }
                }
                ScanResult.NoText -> {
                    message = "No readable text found. Use a sharper photo or enter details manually."
                }
                is ScanResult.Failure -> {
                    message = result.message
                }
            }
            busy = false
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) process(uri) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val uri = imageUri
        if (captured && uri != null) process(uri)
    }

    LazyColumn(
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
                Box(Modifier.weight(1f)) {
                    ScreenHeading(
                        title = "Scan bill",
                        subtitle = "Extract supplier or sales bills and review before saving",
                    )
                }
            }
        }
        item {
            SectionCard {
                ScanModeChips(
                    options = listOf("SALES" to "Sales invoice", "PURCHASE" to "Supplier purchase"),
                    selected = mode,
                    label = { it.second },
                    onSelect = { mode = it.first },
                )
                Text("Bills stay private on this device until you save them. Review every suggested value.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { picker.launch("image/*") }) {
                        Icon(Icons.Default.Image, null); Spacer(Modifier.width(6.dp)); Text("Choose photo")
                    }
                    FilledTonalButton(onClick = {
                        try {
                            val (file, uri) = ScanStore.cameraTarget(context)
                            cameraFile = file
                            imageUri = uri
                            camera.launch(uri)
                        } catch (e: Exception) {
                            message = "Could not open camera: ${e.message}"
                        }
                    }) {
                        Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(6.dp)); Text("Take photo")
                    }
                    if (imageUri != null) TextButton(onClick = { reset() }) { Text("Clear") }
                }
            }
        }
        if (busy) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()); message?.let { Text(it, style = MaterialTheme.typography.bodySmall) } }
        }
        message?.let { text -> if (!busy) item { Text(text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) } }

        if (imageUri != null && !busy) {
            item {
                SectionCard(title = if (mode == "PURCHASE") "Supplier details" else "Customer details") {
                    FormField(if (mode == "PURCHASE") "Supplier name *" else "Customer name *", partyName) { partyName = it }
                    if (mode == "SALES") {
                        FormField("Invoice number (optional)", invoiceNumber) { invoiceNumber = it }
                        Text("Leave blank to auto-assign using your invoice prefix.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        FormField("Supplier bill number (optional)", billNumber) { billNumber = it }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FormField("Phone", partyPhone, { partyPhone = it }, Modifier.weight(1f))
                        FormField("GSTIN", partyGstin, { partyGstin = it.uppercase() }, Modifier.weight(1f))
                    }
                    FormField("Address", partyAddress, { partyAddress = it }, singleLine = false, minLines = 2)
                    if (mode == "SALES") FormField("Place of supply", placeOfSupply) { placeOfSupply = it }
                }
            }
            item { ScreenHeading(title = "Line items", subtitle = "Edit quantities, prices and GST before saving") }
            itemsIndexed(lines) { idx, line ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Item ${idx + 1}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = { lines.remove(line) }) { Text("Remove") }
                        }
                        FormField("Item name", line.name) { line.name = it }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FormField("Qty", line.qty, { line.qty = it }, Modifier.weight(1f), number = true)
                            FormField("Unit price ₹", line.price, { line.price = it }, Modifier.weight(1f), number = true)
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Gst.STANDARD_RATES.forEach { rate ->
                                FilterChip(line.gst == rate, { line.gst = rate }, label = { Text("${rate.toString().removeSuffix(".0")}%") })
                            }
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { lines += ReviewLine("", "1", "", 18.0) }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Add item")
                }
            }
            item {
                SectionCard {
                    FormField(if (mode == "PURCHASE") "Amount paid ₹" else "Payment received ₹", payment, { payment = it }, number = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(pricesIncludeGst, { pricesIncludeGst = it }); Spacer(Modifier.width(8.dp))
                        Column { Text("Prices include GST"); Text("GST will be extracted from entered rates.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    if (mode == "SALES") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(intraState, { intraState = it }); Spacer(Modifier.width(8.dp))
                            Text(if (intraState) "Intra-state · CGST + SGST" else "Inter-state · IGST")
                        }
                    }
                }
            }
            if (rawText.isNotBlank()) {
                item {
                    SectionCard(title = "Extracted text", subtitle = sourceLabel.ifBlank { "OCR" }) {
                        Text(rawText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Button(
                    onClick = {
                        message = null
                        scope.launch {
                            val src = imageUri ?: return@launch
                            val path = savedPath ?: ScanStore.saveOriginal(context, src)
                            if (path == null) {
                                message = "Could not save the original bill image. Try again."
                                return@launch
                            }
                            savedPath = path
                            val draftLines = mutableListOf<DraftLine>()
                            for (line in lines) {
                                val qty = line.qty.toDoubleOrNull()
                                val price = Money.parseRupees(line.price)
                                if (line.name.isBlank() || qty == null || price == null) {
                                    message = "Enter name, quantity and price for every line item."
                                    return@launch
                                }
                                draftLines += DraftLine(
                                    Product(name = line.name.trim(), defaultPricePaise = price, defaultGstRate = line.gst),
                                    qty, price, line.gst, pricesIncludeGst,
                                )
                            }
                            val paid = Money.parseRupees(payment.ifBlank { "0" })
                            if (partyName.isBlank() || draftLines.isEmpty() || paid == null) {
                                message = "Enter the ${if (mode == "PURCHASE") "supplier" else "customer"}, at least one item and a valid payment amount."
                                return@launch
                            }
                            try {
                                if (mode == "PURCHASE") {
                                    val id = repo.savePurchase(
                                        PurchaseDraft(
                                            supplier = Supplier(name = partyName.trim(), phone = partyPhone.trim(), gstin = partyGstin.trim().uppercase(), address = partyAddress.trim()),
                                            lines = draftLines,
                                            paidPaise = paid,
                                            billNumber = billNumber,
                                            pricesIncludeGst = pricesIncludeGst,
                                            scanImagePath = path,
                                        )
                                    )
                                    reset()
                                    onOpenPurchase(id)
                                } else {
                                    val id = repo.issueInvoice(
                                        InvoiceDraft(
                                            customer = Customer(name = partyName.trim(), phone = partyPhone.trim(), gstin = partyGstin.trim().uppercase(), billingAddress = partyAddress.trim(), shippingAddress = partyAddress.trim()),
                                            lines = draftLines,
                                            paymentPaise = paid,
                                            intraState = intraState,
                                            placeOfSupply = placeOfSupply,
                                            pricesIncludeGst = pricesIncludeGst,
                                            scanImagePath = path,
                                            invoiceNumber = invoiceNumber,
                                        )
                                    )
                                    reset()
                                    onOpenInvoice(id)
                                }
                            } catch (e: IllegalArgumentException) {
                                message = e.message
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ReceiptLong, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (mode == "PURCHASE") "Save purchase" else "Issue invoice")
                }
            }
        }
    }
}

private class ReviewLine(name: String, qty: String, price: String, gst: Double) {
    var name by mutableStateOf(name)
    var qty by mutableStateOf(qty)
    var price by mutableStateOf(price)
    var gst by mutableStateOf(gst)
}

@Composable
private fun ScanModeChips(
    options: List<Pair<String, String>>,
    selected: String,
    label: (Pair<String, String>) -> String,
    onSelect: (Pair<String, String>) -> Unit,
) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        options.forEach { option ->
            FilterChip(selected == option.first, { onSelect(option) }, label = { Text(label(option)) })
        }
    }
}
