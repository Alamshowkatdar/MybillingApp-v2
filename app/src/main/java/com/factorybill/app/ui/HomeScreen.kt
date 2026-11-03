package com.factorybill.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.factorybill.app.core.Money
import com.factorybill.app.core.Periods
import com.factorybill.app.core.PaymentState
import com.factorybill.app.core.SalaryLedger
import com.factorybill.app.core.SalaryLine
import com.factorybill.app.core.SalaryType
import com.factorybill.app.core.paymentState
import com.factorybill.app.data.Employee
import com.factorybill.app.data.Invoice
import com.factorybill.app.data.Purchase
import com.factorybill.app.data.SalaryEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onInvoiceSelect: (Long) -> Unit = {},
    onPurchaseSelect: (Long) -> Unit = {},
    onNavigate: (String) -> Unit = {},
) {
    val sales by vm.sales.collectAsStateWithLifecycle()
    val dues by vm.dues.collectAsStateWithLifecycle()
    val supplierDues by vm.supplierDues.collectAsStateWithLifecycle()
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    val expenseRecords by vm.expenseRecords.collectAsStateWithLifecycle()
    val negativeStock by vm.negativeStock.collectAsStateWithLifecycle()
    val invoices by vm.invoices.collectAsStateWithLifecycle()
    val purchases by vm.purchases.collectAsStateWithLifecycle()
    val employees: List<Employee> by vm.employees.collectAsStateWithLifecycle()
    val salaryEntries: List<SalaryEntry> by vm.salaryEntries.collectAsStateWithLifecycle()
    val company by vm.company.collectAsStateWithLifecycle(null)

    val now = remember { System.currentTimeMillis() }
    val monthStart = remember(now) { Periods.startOfMonth(now) }
    val monthEnd = remember(now) { Periods.endOfMonth(now) }
    val monthInvoices = invoices.filter { it.dateMillis in monthStart..monthEnd && it.status == "ISSUED" }
    val monthSales = monthInvoices.sumOf { it.totalPaise }
    val monthPurchases = purchases.filter { it.dateMillis in monthStart..monthEnd && it.status == "RECORDED" }.sumOf { it.totalPaise }
    val monthExpenses = expenseRecords.filter { it.dateMillis in monthStart..monthEnd }.sumOf { it.amountPaise }
    val salaryDue = employees.filter { it.active }.sumOf { employee ->
        SalaryLedger.balance(salaryEntries.filter { it.employeeId == employee.id }.map {
            SalaryLine(runCatching { SalaryType.valueOf(it.type) }.getOrDefault(SalaryType.ADVANCE), it.amountPaise)
        })
    }
    val today = remember { SimpleDateFormat("EEEE, d MMMM", Locale("en", "IN")).format(Date()) }

    LazyColumn(
        contentPadding = PaddingValues(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(today, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        company?.name?.trim()?.takeIf(String::isNotEmpty) ?: "MybillingApp",
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "by Alam Showkat™",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CompanyLogo(company?.logoPath, Modifier.size(46.dp))
            }
        }

        item {
            HeroSummary(
                eyebrow = "Sales this month",
                value = Money.format(monthSales),
                caption = "${monthInvoices.size} invoice${if (monthInvoices.size == 1) "" else "s"} · ${Money.format(sales)} lifetime",
                action = {
                    TextButton(
                        onClick = { onNavigate("invoice/new") },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
                    ) {
                        Text("New invoice")
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(17.dp))
                    }
                },
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Receivables", Money.format(dues), Icons.Default.TrendingUp, Modifier.weight(1f),
                    accent = if (dues > 0) Color(0xFFB26A00) else MaterialTheme.colorScheme.primary,
                    onClick = { onNavigate("invoices") })
                StatCard("Payables", Money.format(supplierDues), Icons.Default.AccountBalanceWallet, Modifier.weight(1f),
                    accent = if (supplierDues > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    onClick = { onNavigate("purchases") })
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Purchases · month", Money.format(monthPurchases), Icons.Default.ShoppingCart, Modifier.weight(1f),
                    onClick = { onNavigate("purchases") })
                StatCard("Expenses · month", Money.format(monthExpenses), Icons.Default.Payments, Modifier.weight(1f),
                    onClick = { onNavigate("expenses") })
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Salary due", Money.format(salaryDue), Icons.Default.Groups, Modifier.weight(1f),
                    accent = if (salaryDue > 0) Color(0xFFB26A00) else MaterialTheme.colorScheme.primary,
                    onClick = { onNavigate("payroll") })
                StatCard("Stock alerts", "${negativeStock} item${if (negativeStock == 1) "" else "s"}", Icons.Default.Inventory2, Modifier.weight(1f),
                    accent = if (negativeStock > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    onClick = { onNavigate("inventory") })
            }
        }

        if (negativeStock > 0) {
            item {
                Card(
                    onClick = { onNavigate("inventory") },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.WarningAmber, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Column(Modifier.weight(1f)) {
                            Text("Stock needs attention", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text("$negativeStock product${if (negativeStock == 1) " has" else "s have"} negative stock. Review and adjust.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledAction(Icons.Default.Add, "Invoice", { onNavigate("invoice/new") }, Modifier.weight(1f))
                LabeledAction(Icons.Default.ShoppingCart, "Purchase", { onNavigate("purchase/new") }, Modifier.weight(1f))
                LabeledAction(Icons.Default.CameraAlt, "Scan bill", { onNavigate("scan") }, Modifier.weight(1f))
                LabeledAction(Icons.Default.ReceiptLong, "Expense", { onNavigate("expenses") }, Modifier.weight(1f))
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Recent invoices", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { onNavigate("invoices") }) { Text("View all"); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp)) }
            }
        }
        if (invoices.isEmpty()) {
            item {
                EmptyState(
                    title = "No invoices yet",
                    message = "Create your first GST invoice to see recent activity here.",
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    actionLabel = "Create invoice",
                    onAction = { onNavigate("invoice/new") },
                )
            }
        } else {
            items(invoices.take(4), key = { "invoice-${it.id}" }) { invoice ->
                InvoiceMiniRow(invoice, onClick = { onInvoiceSelect(invoice.id) })
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Recent purchases", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { onNavigate("purchases") }) { Text("View all"); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp)) }
            }
        }
        if (purchases.isEmpty()) {
            item {
                EmptyState(
                    title = "No purchases recorded",
                    message = "Record supplier bills to keep stock and payables up to date.",
                    icon = Icons.Default.ShoppingCart,
                    actionLabel = "Record purchase",
                    onAction = { onNavigate("purchase/new") },
                )
            }
        } else {
            items(purchases.take(3), key = { "purchase-${it.id}" }) { purchase ->
                PurchaseMiniRow(purchase, onClick = { onPurchaseSelect(purchase.id) })
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Your business records are stored privately on this device.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun InvoiceMiniRow(invoice: Invoice, onClick: () -> Unit) {
    val state = paymentState(invoice.totalPaise, invoice.paymentPaise, invoice.status == "VOID")
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(38.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(invoice.number, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatDate(invoice.dateMillis), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(Money.format(invoice.totalPaise), style = MaterialTheme.typography.titleSmall)
                StatusPill(state)
            }
        }
    }
}

@Composable
private fun PurchaseMiniRow(purchase: Purchase, onClick: () -> Unit) {
    val state = paymentState(purchase.totalPaise, purchase.paidPaise, purchase.status == "VOID")
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.size(38.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.ShoppingCart, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(19.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(purchase.number.ifBlank { "Purchase #${purchase.id}" }, style = MaterialTheme.typography.titleSmall)
                Text(listOfNotNull(purchase.billNumber.takeIf(String::isNotBlank)?.let { "Bill $it" }, formatShortDate(purchase.dateMillis)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(Money.format(purchase.totalPaise), style = MaterialTheme.typography.titleSmall)
                StatusPill(state)
            }
        }
    }
}
