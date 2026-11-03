package com.factorybill.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.factorybill.app.BuildConfig
import com.factorybill.app.core.*
import com.factorybill.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------------------------
// More hub
// ---------------------------------------------------------------------------------------------

@Composable
fun MoreScreen(settings: SettingsStore, onNavigate: (String) -> Unit) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val company by repo.company.collectAsStateWithLifecycle(null)
    val customers by repo.customers.collectAsStateWithLifecycle(emptyList())
    val suppliers by repo.suppliers.collectAsStateWithLifecycle(emptyList())
    val products by repo.products.collectAsStateWithLifecycle(emptyList())
    val expenses by repo.expenses.collectAsStateWithLifecycle(emptyList())
    val invoices by repo.invoices.collectAsStateWithLifecycle(emptyList())
    val purchases by repo.purchases.collectAsStateWithLifecycle(emptyList())
    val employees by repo.employees.collectAsStateWithLifecycle(emptyList())
    val theme by settings.theme.collectAsStateWithLifecycle(ThemeMode.LIGHT)
    val accent by settings.accent.collectAsStateWithLifecycle(0)

    val receivable = invoices.filter { it.status == "ISSUED" }.sumOf { (it.totalPaise - it.paymentPaise).coerceAtLeast(0) }
    val payable = purchases.filter { it.status == "RECORDED" }.sumOf { (it.totalPaise - it.paidPaise).coerceAtLeast(0) }
    val negativeStock = products.count { it.stockQty < 0 }
    val companyName = company?.name?.trim()?.takeIf(String::isNotEmpty) ?: "MybillingApp"

    LazyColumn(
        contentPadding = PaddingValues(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ScreenHeading(title = "More", subtitle = "Business records, people and preferences")
        }
        item {
            Card(
                onClick = { onNavigate("settings/company") },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CompanyLogo(company?.logoPath, Modifier.size(52.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(companyName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            company?.gstin?.takeIf(String::isNotBlank) ?: "Company, GST, bank and invoice settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            SectionCard(title = "Business") {
                MoreRow(Icons.Default.Business, "Company & branding", "GST, bank details, logo and invoice design") {
                    onNavigate("settings/company")
                }
                HorizontalDivider()
                MoreRow(
                    Icons.Default.Groups,
                    "Customers",
                    "${customers.size} saved · ${Money.format(receivable)} receivable",
                ) { onNavigate("customers") }
                HorizontalDivider()
                MoreRow(
                    Icons.Default.LocalShipping,
                    "Suppliers",
                    "${suppliers.size} saved · ${Money.format(payable)} payable",
                ) { onNavigate("suppliers") }
            }
        }
        item {
            SectionCard(title = "Operations") {
                MoreRow(
                    Icons.Default.Inventory2,
                    "Inventory",
                    "${products.size} product${if (products.size == 1) "" else "s"}${if (negativeStock > 0) " · $negativeStock negative" else ""}",
                    tint = if (negativeStock > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                ) { onNavigate("inventory") }
                HorizontalDivider()
                MoreRow(Icons.Default.Payments, "Expenses", "${expenses.size} recorded") { onNavigate("expenses") }
                HorizontalDivider()
                MoreRow(Icons.Default.CameraAlt, "Scan a bill", "Read a supplier or sales bill with on-device OCR") {
                    onNavigate("scan")
                }
            }
        }
        item {
            SectionCard(title = "Team") {
                MoreRow(
                    Icons.Default.Badge,
                    "Payroll",
                    "${employees.count { it.active }} active employee${if (employees.count { it.active } == 1) "" else "s"}",
                ) { onNavigate("payroll") }
            }
        }
        item {
            SectionCard(title = "Appearance", subtitle = "Theme and accent colour") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = theme == mode,
                            onClick = { scope.launch { settings.setTheme(mode) } },
                            label = { Text(mode.label()) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AccentPalette.forEachIndexed { index, colour ->
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(colour)
                                .clickable { scope.launch { settings.setAccent(index) } },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (index == accent) {
                                Icon(Icons.Default.Check, "Selected accent", tint = Color.White, modifier = Modifier.size(19.dp))
                            }
                        }
                    }
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text("Private by design", style = MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        "All invoices, stock, payroll and expense records live in a private database on this device. Nothing is uploaded unless you explicitly use cloud scanning.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("MybillingApp™ · by Alam Showkat",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("© 2026 Alam Showkat · version ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

@Composable
private fun MoreRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(shape = CircleShape, color = tint.copy(alpha = 0.12f), modifier = Modifier.size(38.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun MoreBackHeader(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.invoke()
    }
}

// ---------------------------------------------------------------------------------------------
// Company profile & appearance
// ---------------------------------------------------------------------------------------------

@Composable
fun CompanySettingsScreen(settings: SettingsStore, onBack: () -> Unit) {
    val repo = LocalRepository.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val theme by settings.theme.collectAsStateWithLifecycle(ThemeMode.LIGHT)
    val accent by settings.accent.collectAsStateWithLifecycle(0)
    val saved by repo.company.collectAsStateWithLifecycle(null)
    var form by remember(saved) { mutableStateOf(saved ?: CompanyProfile()) }
    var message by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val imported = withContext(Dispatchers.IO) { CompanyLogoStore.import(context, uri) }
                    val previous = form.logoPath
                    form = form.copy(logoPath = imported)
                    if (previous != saved?.logoPath) {
                        withContext(Dispatchers.IO) { CompanyLogoStore.delete(context, previous) }
                    }
                    message = "Logo selected. Save company details to apply it."
                } catch (e: Exception) {
                    message = e.message ?: "Could not select this logo."
                }
            }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { MoreBackHeader("Company & branding", "Details printed on invoices and shown on the dashboard", onBack) }
        item {
            SectionCard(title = "Appearance", subtitle = "Theme and accent colour") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = theme == mode,
                            onClick = { scope.launch { settings.setTheme(mode) } },
                            label = { Text(mode.label()) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AccentPalette.forEachIndexed { index, colour ->
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(colour)
                                .clickable { scope.launch { settings.setAccent(index) } },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (index == accent) {
                                Icon(Icons.Default.Check, "Selected accent", tint = Color.White, modifier = Modifier.size(19.dp))
                            }
                        }
                    }
                }
            }
        }
        item {
            SectionCard(title = "Business identity", subtitle = "Name, GSTIN and contact details") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CompanyLogo(form.logoPath, Modifier.size(64.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Company logo", style = MaterialTheme.typography.titleSmall)
                        Text("Shown on the dashboard and printed on invoices.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row {
                            TextButton(onClick = { logoPicker.launch(arrayOf("image/*")) }) {
                                Icon(Icons.Default.Image, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (form.logoPath == null) "Choose logo" else "Change logo")
                            }
                            if (form.logoPath != null) {
                                TextButton(onClick = {
                                    form = form.copy(logoPath = null)
                                    message = "Logo will be removed when you save."
                                }) { Text("Remove") }
                            }
                        }
                    }
                }
                FormField("Company name *", form.name) { form = form.copy(name = it) }
                FormField("GSTIN", form.gstin) { form = form.copy(gstin = it.uppercase()) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormField("Phone", form.phone, { form = form.copy(phone = it) }, Modifier.weight(1f))
                    FormField("Email", form.email, { form = form.copy(email = it) }, Modifier.weight(1f))
                }
                FormField("Address", form.address, { form = form.copy(address = it) }, singleLine = false, minLines = 2)
            }
        }
        item {
            SectionCard(title = "Bank & payment", subtitle = "Printed on invoices and used for the UPI QR") {
                FormField("Bank name", form.bankName) { form = form.copy(bankName = it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormField("Account number", form.accountNumber, { form = form.copy(accountNumber = it) }, Modifier.weight(1f))
                    FormField("IFSC", form.ifsc, { form = form.copy(ifsc = it.uppercase()) }, Modifier.weight(1f))
                }
                FormField("UPI ID", form.upiId, { form = form.copy(upiId = it.trim()) },
                    supporting = "For example business@bank. Leave blank to hide the payment QR.")
            }
        }
        item {
            SectionCard(title = "Invoice preferences", subtitle = "Numbering, layout and footer") {
                FormField(
                    "Invoice number prefix",
                    form.invoicePrefix,
                    { form = form.copy(invoicePrefix = it) },
                    supporting = "Up to 8 letters, numbers, hyphens or slashes. Example: INV-",
                )
                Text("PDF template", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "classic" to "Classic A4",
                        "modern" to "Modern A4",
                        "compact" to "Compact A4",
                    ).forEach { (value, label) ->
                        FilterChip(form.template == value, { form = form.copy(template = value) }, label = { Text(label) })
                    }
                }
                FormField("Terms & conditions", form.termsAndConditions, { form = form.copy(termsAndConditions = it) },
                    singleLine = false, minLines = 2)
                FormField("Footer note", form.footerNote, { form = form.copy(footerNote = it) },
                    singleLine = false, minLines = 2,
                    supporting = "A short message printed at the bottom of every invoice.")
            }
        }
        item {
            message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = if (saving) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
            }
            Button(
                onClick = {
                    scope.launch {
                        saving = true
                        val oldLogo = saved?.logoPath
                        message = try {
                            repo.saveCompany(form)
                            if (oldLogo != form.logoPath) {
                                withContext(Dispatchers.IO) { CompanyLogoStore.delete(context, oldLogo) }
                            }
                            "Company details saved."
                        } catch (e: IllegalArgumentException) {
                            e.message
                        } catch (e: Exception) {
                            e.message ?: "Could not save company details."
                        } finally {
                            saving = false
                        }
                    }
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (saving) "Saving…" else "Save company details") }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Inventory
// ---------------------------------------------------------------------------------------------

@Composable
fun InventoryScreen(onBack: () -> Unit) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val products by repo.products.collectAsStateWithLifecycle(emptyList())
    val movements by repo.movements.collectAsStateWithLifecycle(emptyList())
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Product?>(null) }
    var adjusting by remember { mutableStateOf<Product?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val filtered = products.filter {
        query.isBlank() || it.name.contains(query, true) || it.hsn.contains(query, true)
    }
    val stockValue = products.sumOf { (it.stockQty * it.defaultPricePaise).toLong() }
    val negative = products.count { it.stockQty < 0 }
    val names = remember(products) { products.associate { it.id to it.name } }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MoreBackHeader(
                title = "Inventory",
                subtitle = "${products.size} product${if (products.size == 1) "" else "s"}",
                onBack = onBack,
                trailing = {
                    FilledTonalButton(onClick = { showAdd = true }) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("New")
                    }
                },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Stock value", Money.format(stockValue), Icons.Default.Inventory2, Modifier.weight(1f))
                StatCard(
                    "Negative stock", "$negative item${if (negative == 1) "" else "s"}",
                    Icons.Default.WarningAmber, Modifier.weight(1f),
                    accent = if (negative > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }
        item { SearchField(query, { query = it }, "Search product name or HSN") }
        message?.let { text ->
            item { Text(text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
        }
        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    title = if (products.isEmpty()) "No products yet" else "No matching products",
                    message = if (products.isEmpty()) {
                        "Add products once, then reuse them on invoices and purchases with stock tracked automatically."
                    } else {
                        "Try a different product name or HSN code."
                    },
                    icon = Icons.Default.Inventory2,
                    actionLabel = if (products.isEmpty()) "Add product" else null,
                    onAction = if (products.isEmpty()) ({ showAdd = true }) else null,
                )
            }
        }
        items(filtered, key = { it.id }) { product ->
            ProductRow(
                product = product,
                onClick = { editing = product },
                onAdjust = { adjusting = product },
            )
        }
        if (movements.isNotEmpty()) {
            item {
                ScreenHeading(title = "Recent stock movements", subtitle = "Latest ${minOf(movements.size, 15)} changes")
            }
            item {
                SectionCard {
                    movements.take(15).forEachIndexed { index, movement ->
                        if (index > 0) HorizontalDivider()
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (movement.delta >= 0) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                null,
                                Modifier.size(16.dp),
                                tint = if (movement.delta >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(names[movement.productId] ?: "Product #${movement.productId}",
                                    style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(movement.reason.replace('_', ' '), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                (if (movement.delta > 0) "+" else "") + movement.delta.toString().removeSuffix(".0"),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        ProductFormDialog(
            initial = null,
            onDismiss = { showAdd = false },
            onSave = { product ->
                showAdd = false
                scope.launch {
                    try {
                        repo.addProduct(product)
                        message = "Product added. It is now available on invoices and purchases."
                    } catch (e: IllegalArgumentException) {
                        message = e.message
                    }
                }
            },
        )
    }
    editing?.let { product ->
        ProductFormDialog(
            initial = product,
            onDismiss = { editing = null },
            onSave = { updated ->
                editing = null
                scope.launch {
                    try {
                        repo.updateProductRecord(updated)
                        message = "Product updated."
                    } catch (e: IllegalArgumentException) {
                        message = e.message
                    }
                }
            },
        )
    }
    adjusting?.let { product ->
        StockAdjustmentDialog(
            product = product,
            onDismiss = { adjusting = null },
            onSave = { delta, note ->
                adjusting = null
                scope.launch {
                    try {
                        repo.adjustStock(product.id, delta, note)
                        message = "Stock adjusted by ${if (delta > 0) "+" else ""}${delta.toString().removeSuffix(".0")}."
                    } catch (e: IllegalArgumentException) {
                        message = e.message
                    }
                }
            },
        )
    }
}

@Composable
private fun ProductRow(product: Product, onClick: () -> Unit, onAdjust: () -> Unit) {
    val short = product.stockQty < 0
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Surface(
                shape = CircleShape,
                color = if (short) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(42.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (short) Icons.Default.WarningAmber else Icons.Default.Inventory2,
                        null,
                        tint = if (short) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(product.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        Money.format(product.defaultPricePaise),
                        "${product.defaultGstRate.toString().removeSuffix(".0")}% GST",
                        product.hsn.takeIf(String::isNotBlank)?.let { "HSN $it" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${product.stockQty.toString().removeSuffix(".0")} ${product.unit} in stock",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (short) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
            TextButton(onClick = onAdjust) { Text("Adjust") }
        }
    }
}

@Composable
private fun ProductFormDialog(initial: Product?, onDismiss: () -> Unit, onSave: (Product) -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var hsn by remember { mutableStateOf(initial?.hsn.orEmpty()) }
    var unit by remember { mutableStateOf(initial?.unit ?: "pcs") }
    var price by remember { mutableStateOf(initial?.let { rupeesText(it.defaultPricePaise) }.orEmpty()) }
    var gst by remember { mutableStateOf(initial?.defaultGstRate ?: 18.0) }
    var opening by remember { mutableStateOf(initial?.stockQty?.toString()?.removeSuffix(".0").orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add product" else "Edit product") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FormField("Product / service name *", name) { name = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormField("HSN / SAC", hsn, { hsn = it }, Modifier.weight(1f))
                    FormField("Unit", unit, { unit = it }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormField(if (initial == null) "Selling price ₹ *" else "Selling price ₹", price, { price = it },
                        Modifier.weight(1f), number = true)
                    if (initial == null) {
                        FormField("Opening stock", opening, { opening = it }, Modifier.weight(1f), number = true)
                    } else {
                        OutlinedTextField(
                            value = "${initial.stockQty.toString().removeSuffix(".0")} ${initial.unit}",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Current stock") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = MaterialTheme.shapes.small,
                        )
                    }
                }
                Text("Default GST rate", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Gst.STANDARD_RATES.forEach { rate ->
                        FilterChip(gst == rate, { gst = rate }, label = { Text("${rate.toString().removeSuffix(".0")}%") })
                    }
                }
                if (initial != null) {
                    Text("Use Adjust stock on the product row to correct the quantity.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val pricePaise = Money.parseRupees(price)
                val openingQty = opening.toDoubleOrNull() ?: 0.0
                when {
                    name.isBlank() -> error = "Enter the product name."
                    pricePaise == null -> error = "Enter a valid selling price."
                    initial == null && (!openingQty.isFinite() || openingQty < 0) ->
                        error = "Opening stock must be zero or more."
                    else -> onSave(
                        (initial ?: Product(name = name)).copy(
                            name = name.trim(),
                            hsn = hsn.trim(),
                            unit = unit.trim().ifBlank { "pcs" },
                            defaultPricePaise = pricePaise,
                            defaultGstRate = gst,
                            stockQty = initial?.stockQty ?: openingQty,
                        )
                    )
                }
            }) { Text(if (initial == null) "Add product" else "Save changes") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun StockAdjustmentDialog(
    product: Product,
    onDismiss: () -> Unit,
    onSave: (delta: Double, note: String) -> Unit,
) {
    var change by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust ${product.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Current stock: ${product.stockQty.toString().removeSuffix(".0")} ${product.unit}",
                    style = MaterialTheme.typography.titleSmall)
                FormField("Change in quantity (+ or -)", change, { change = it }, number = true)
                FormField("Reason", note) { note = it }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val delta = change.trim().toDoubleOrNull()
                if (delta == null || !delta.isFinite() || delta == 0.0) {
                    error = "Enter a finite, non-zero quantity."
                } else onSave(delta, note.trim())
            }) { Text("Apply adjustment") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------------------------------------
// Expenses
// ---------------------------------------------------------------------------------------------

@Composable
fun ExpensesScreen(onBack: () -> Unit) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val expenses by repo.expenses.collectAsStateWithLifecycle(emptyList())
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    var showAdd by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val categories = remember(expenses) { expenses.map { it.category }.filter { it.isNotBlank() }.distinct().sorted() }
    val monthStart = remember { Periods.startOfMonth(System.currentTimeMillis()) }
    val monthEnd = remember { Periods.endOfMonth(System.currentTimeMillis()) }
    val monthTotal = expenses.filter { it.dateMillis in monthStart..monthEnd }.sumOf { it.amountPaise }
    val allTotal = expenses.sumOf { it.amountPaise }
    val filtered = expenses.filter { expense ->
        val matches = query.isBlank() || expense.category.contains(query, true) ||
            expense.payee.contains(query, true) || expense.notes.contains(query, true)
        matches && (filter == "All" || expense.category == filter)
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MoreBackHeader(
                title = "Expenses",
                subtitle = "${expenses.size} recorded · ${Money.format(monthTotal)} this month",
                onBack = onBack,
                trailing = {
                    FilledTonalButton(onClick = { showAdd = true }) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("New")
                    }
                },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("This month", Money.format(monthTotal), Icons.Default.CalendarMonth, Modifier.weight(1f))
                StatCard("All time", Money.format(allTotal), Icons.Default.Payments, Modifier.weight(1f))
            }
        }
        item { SearchField(query, { query = it }, "Search category, payee or note") }
        item { FilterRow(filter, listOf("All") + categories, { filter = it }) }
        message?.let { text ->
            item { Text(text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
        }
        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    title = if (expenses.isEmpty()) "No expenses yet" else "No matching expenses",
                    message = if (expenses.isEmpty()) {
                        "Track rent, utilities, transport and other business costs in one place."
                    } else {
                        "Try a different search or category."
                    },
                    icon = Icons.Default.Payments,
                    actionLabel = if (expenses.isEmpty()) "Add expense" else null,
                    onAction = if (expenses.isEmpty()) ({ showAdd = true }) else null,
                )
            }
        }
        items(filtered, key = { it.id }) { expense ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.size(42.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Payments, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(expense.category, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(
                                expense.payee.takeIf(String::isNotBlank),
                                formatDate(expense.dateMillis),
                                expense.mode.takeIf(String::isNotBlank)?.let { PaymentMode.label(it) },
                                expense.notes.takeIf(String::isNotBlank),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(Money.format(expense.amountPaise), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }

    if (showAdd) {
        ExpenseFormDialog(
            onDismiss = { showAdd = false },
            onSave = { expense ->
                showAdd = false
                scope.launch {
                    try {
                        repo.addExpense(expense)
                        message = "Expense saved."
                    } catch (e: IllegalArgumentException) {
                        message = e.message
                    }
                }
            },
        )
    }
}

@Composable
private fun ExpenseFormDialog(onDismiss: () -> Unit, onSave: (Expense) -> Unit) {
    var category by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableLongStateOf(todayMillis()) }
    var payee by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(PaymentMode.CASH) }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val common = listOf("Rent", "Utilities", "Transport", "Supplies", "Maintenance", "Salary", "Other")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add expense") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FormField("Category *", category) { category = it }
                ChoiceChips(common, category, { it }, { category = it })
                FormField("Amount ₹ *", amount, { amount = it }, number = true)
                DateField("Expense date", date) { date = it }
                FormField("Paid to (optional)", payee) { payee = it }
                Text("Payment mode", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                PaymentModeChips(mode, { mode = it })
                FormField("Notes (optional)", notes, { notes = it }, singleLine = false, minLines = 2)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amountPaise = Money.parseRupees(amount)
                when {
                    category.isBlank() -> error = "Enter an expense category."
                    amountPaise == null || amountPaise <= 0 -> error = "Enter a valid amount greater than zero."
                    date <= 0 -> error = "Choose an expense date."
                    else -> onSave(
                        Expense(
                            category = category.trim(),
                            dateMillis = date,
                            amountPaise = amountPaise,
                            payee = payee.trim(),
                            mode = mode.value,
                            notes = notes.trim(),
                        )
                    )
                }
            }) { Text("Save expense") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------------------------------------
// Customers
// ---------------------------------------------------------------------------------------------

@Composable
fun CustomersScreen(onBack: () -> Unit) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val customers by repo.customers.collectAsStateWithLifecycle(emptyList())
    val invoices by repo.invoices.collectAsStateWithLifecycle(emptyList())
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Customer?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val outstanding = remember(invoices) {
        invoices.filter { it.status == "ISSUED" }
            .groupBy { it.customerId }
            .mapValues { (_, list) -> list.sumOf { (it.totalPaise - it.paymentPaise).coerceAtLeast(0) } }
    }
    val totalOutstanding = outstanding.values.sum()
    val filtered = customers.filter {
        query.isBlank() || it.name.contains(query, true) || it.phone.contains(query, true) ||
            it.gstin.contains(query, true) || it.contactPerson.contains(query, true)
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MoreBackHeader(
                title = "Customers",
                subtitle = "${customers.size} saved · ${Money.format(totalOutstanding)} receivable",
                onBack = onBack,
                trailing = {
                    FilledTonalButton(onClick = { showAdd = true }) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("New")
                    }
                },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Customers", "${customers.size}", Icons.Default.Groups, Modifier.weight(1f))
                StatCard(
                    "Receivable", Money.format(totalOutstanding), Icons.Default.TrendingUp, Modifier.weight(1f),
                    accent = if (totalOutstanding > 0) Color(0xFFB26A00) else MaterialTheme.colorScheme.primary,
                )
            }
        }
        item { SearchField(query, { query = it }, "Search name, phone, GSTIN or contact") }
        message?.let { text ->
            item { Text(text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
        }
        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    title = if (customers.isEmpty()) "No customers yet" else "No matching customers",
                    message = if (customers.isEmpty()) {
                        "Customers are saved automatically when you issue an invoice, or add them here in advance."
                    } else {
                        "Try a different name, phone number or GSTIN."
                    },
                    icon = Icons.Default.Groups,
                    actionLabel = if (customers.isEmpty()) "Add customer" else null,
                    onAction = if (customers.isEmpty()) ({ showAdd = true }) else null,
                )
            }
        }
        items(filtered, key = { it.id }) { customer ->
            CustomerRow(
                customer = customer,
                outstandingPaise = outstanding[customer.id] ?: 0,
                onClick = { editing = customer },
            )
        }
    }

    if (showAdd) {
        CustomerFormDialog(
            initial = null,
            onDismiss = { showAdd = false },
            onSave = { customer ->
                showAdd = false
                scope.launch {
                    try {
                        repo.addCustomer(customer)
                        message = "Customer added."
                    } catch (e: IllegalArgumentException) {
                        message = e.message
                    }
                }
            },
        )
    }
    editing?.let { customer ->
        CustomerFormDialog(
            initial = customer,
            onDismiss = { editing = null },
            onSave = { updated ->
                editing = null
                scope.launch {
                    try {
                        repo.updateCustomerRecord(updated)
                        message = "Customer updated."
                    } catch (e: IllegalArgumentException) {
                        message = e.message
                    }
                }
            },
        )
    }
}

@Composable
private fun CustomerRow(customer: Customer, outstandingPaise: Long, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Avatar(customer.name)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(customer.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        customer.contactPerson.takeIf(String::isNotBlank),
                        customer.phone.takeIf(String::isNotBlank),
                        customer.gstin.takeIf(String::isNotBlank),
                    ).joinToString(" · ").ifBlank { "No contact details" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (outstandingPaise > 0) "${Money.format(outstandingPaise)} due" else "Settled",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (outstandingPaise > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun CustomerFormDialog(initial: Customer?, onDismiss: () -> Unit, onSave: (Customer) -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var contactPerson by remember { mutableStateOf(initial?.contactPerson.orEmpty()) }
    var phone by remember { mutableStateOf(initial?.phone.orEmpty()) }
    var email by remember { mutableStateOf(initial?.email.orEmpty()) }
    var gstin by remember { mutableStateOf(initial?.gstin.orEmpty()) }
    var billing by remember { mutableStateOf(initial?.billingAddress.orEmpty()) }
    var shipping by remember { mutableStateOf(initial?.shippingAddress.orEmpty()) }
    var notes by remember { mutableStateOf(initial?.notes.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add customer" else "Edit customer") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                FormField("Customer / business name *", name) { name = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormField("Contact person", contactPerson, { contactPerson = it }, Modifier.weight(1f))
                    FormField("Phone", phone, { phone = it }, Modifier.weight(1f))
                }
                FormField("Email", email) { email = it }
                FormField("GSTIN", gstin, { gstin = it.uppercase() })
                FormField("Billing address", billing, { billing = it }, singleLine = false, minLines = 2)
                FormField("Shipping address", shipping, { shipping = it }, singleLine = false, minLines = 2)
                FormField("Notes", notes, { notes = it }, singleLine = false, minLines = 2)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    name.isBlank() -> error = "Enter the customer name."
                    !Validation.phoneOk(phone) -> error = "Enter a valid phone number."
                    !Validation.gstinFormatOk(gstin) -> error = "GSTIN format looks invalid."
                    else -> onSave(
                        (initial ?: Customer(name = name)).copy(
                            name = name.trim(),
                            contactPerson = contactPerson.trim(),
                            phone = phone.trim(),
                            email = email.trim(),
                            gstin = gstin.trim().uppercase(),
                            billingAddress = billing.trim(),
                            shippingAddress = shipping.trim(),
                            notes = notes.trim(),
                        )
                    )
                }
            }) { Text(if (initial == null) "Add customer" else "Save changes") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------------------------------------
// Suppliers
// ---------------------------------------------------------------------------------------------

@Composable
fun SuppliersScreen(onBack: () -> Unit) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val suppliers by repo.suppliers.collectAsStateWithLifecycle(emptyList())
    val purchases by repo.purchases.collectAsStateWithLifecycle(emptyList())
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Supplier?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val payable = remember(purchases) {
        purchases.filter { it.status == "RECORDED" }
            .groupBy { it.supplierId }
            .mapValues { (_, list) -> list.sumOf { (it.totalPaise - it.paidPaise).coerceAtLeast(0) } }
    }
    val totalPayable = payable.values.sum()
    val filtered = suppliers.filter {
        query.isBlank() || it.name.contains(query, true) || it.phone.contains(query, true) ||
            it.gstin.contains(query, true) || it.contactPerson.contains(query, true)
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MoreBackHeader(
                title = "Suppliers",
                subtitle = "${suppliers.size} saved · ${Money.format(totalPayable)} payable",
                onBack = onBack,
                trailing = {
                    FilledTonalButton(onClick = { showAdd = true }) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("New")
                    }
                },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Suppliers", "${suppliers.size}", Icons.Default.LocalShipping, Modifier.weight(1f))
                StatCard(
                    "Payable", Money.format(totalPayable), Icons.Default.AccountBalanceWallet, Modifier.weight(1f),
                    accent = if (totalPayable > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }
        item { SearchField(query, { query = it }, "Search name, phone, GSTIN or contact") }
        message?.let { text ->
            item { Text(text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
        }
        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    title = if (suppliers.isEmpty()) "No suppliers yet" else "No matching suppliers",
                    message = if (suppliers.isEmpty()) {
                        "Suppliers are saved automatically when you record a purchase, or add them here in advance."
                    } else {
                        "Try a different name, phone number or GSTIN."
                    },
                    icon = Icons.Default.LocalShipping,
                    actionLabel = if (suppliers.isEmpty()) "Add supplier" else null,
                    onAction = if (suppliers.isEmpty()) ({ showAdd = true }) else null,
                )
            }
        }
        items(filtered, key = { it.id }) { supplier ->
            SupplierRow(
                supplier = supplier,
                payablePaise = payable[supplier.id] ?: 0,
                onClick = { editing = supplier },
            )
        }
    }

    if (showAdd) {
        SupplierFormDialog(
            initial = null,
            onDismiss = { showAdd = false },
            onSave = { supplier ->
                showAdd = false
                scope.launch {
                    try {
                        repo.addSupplier(supplier)
                        message = "Supplier added."
                    } catch (e: IllegalArgumentException) {
                        message = e.message
                    }
                }
            },
        )
    }
    editing?.let { supplier ->
        SupplierFormDialog(
            initial = supplier,
            onDismiss = { editing = null },
            onSave = { updated ->
                editing = null
                scope.launch {
                    try {
                        repo.updateSupplierRecord(updated)
                        message = "Supplier updated."
                    } catch (e: IllegalArgumentException) {
                        message = e.message
                    }
                }
            },
        )
    }
}

@Composable
private fun SupplierRow(supplier: Supplier, payablePaise: Long, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Avatar(supplier.name)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(supplier.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        supplier.contactPerson.takeIf(String::isNotBlank),
                        supplier.phone.takeIf(String::isNotBlank),
                        supplier.gstin.takeIf(String::isNotBlank),
                    ).joinToString(" · ").ifBlank { "No contact details" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (payablePaise > 0) "${Money.format(payablePaise)} payable" else "Settled",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (payablePaise > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SupplierFormDialog(initial: Supplier?, onDismiss: () -> Unit, onSave: (Supplier) -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var contactPerson by remember { mutableStateOf(initial?.contactPerson.orEmpty()) }
    var phone by remember { mutableStateOf(initial?.phone.orEmpty()) }
    var email by remember { mutableStateOf(initial?.email.orEmpty()) }
    var gstin by remember { mutableStateOf(initial?.gstin.orEmpty()) }
    var address by remember { mutableStateOf(initial?.address.orEmpty()) }
    var notes by remember { mutableStateOf(initial?.notes.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add supplier" else "Edit supplier") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                FormField("Supplier / business name *", name) { name = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormField("Contact person", contactPerson, { contactPerson = it }, Modifier.weight(1f))
                    FormField("Phone", phone, { phone = it }, Modifier.weight(1f))
                }
                FormField("Email", email) { email = it }
                FormField("GSTIN", gstin, { gstin = it.uppercase() })
                FormField("Address", address, { address = it }, singleLine = false, minLines = 2)
                FormField("Notes", notes, { notes = it }, singleLine = false, minLines = 2)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    name.isBlank() -> error = "Enter the supplier name."
                    !Validation.phoneOk(phone) -> error = "Enter a valid phone number."
                    !Validation.gstinFormatOk(gstin) -> error = "GSTIN format looks invalid."
                    else -> onSave(
                        (initial ?: Supplier(name = name)).copy(
                            name = name.trim(),
                            contactPerson = contactPerson.trim(),
                            phone = phone.trim(),
                            email = email.trim(),
                            gstin = gstin.trim().uppercase(),
                            address = address.trim(),
                            notes = notes.trim(),
                        )
                    )
                }
            }) { Text(if (initial == null) "Add supplier" else "Save changes") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
