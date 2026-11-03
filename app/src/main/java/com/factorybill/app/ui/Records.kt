package com.factorybill.app.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.factorybill.app.core.Money
import com.factorybill.app.data.PaymentMode
import com.factorybill.app.data.Customer
import com.factorybill.app.data.Invoice
import com.factorybill.app.data.InvoiceLine
import com.factorybill.app.data.Repository
import com.factorybill.app.pdf.InvoicePdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal data class PaymentRow(
    val amountPaise: Long,
    val dateMillis: Long,
    val mode: String,
    val note: String,
)

@Composable
internal fun PaymentDialog(
    title: String,
    balancePaise: Long,
    onDismiss: () -> Unit,
    onSave: (amountPaise: Long, dateMillis: Long, mode: PaymentMode, note: String) -> Unit,
) {
    var amount by remember { mutableStateOf(rupeesText(balancePaise)) }
    var date by remember { mutableLongStateOf(todayMillis()) }
    var mode by remember { mutableStateOf(PaymentMode.CASH) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Balance ${Money.format(balancePaise)}", style = MaterialTheme.typography.titleSmall)
                FormField("Amount ₹", amount, { amount = it }, number = true)
                DateField("Payment date", date) { date = it }
                Text("Payment mode", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PaymentModeChips(mode, { mode = it })
                FormField("Reference / note (optional)", note) { note = it }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val value = Money.parseRupees(amount)
                when {
                    value == null || value <= 0 -> error = "Enter a valid amount greater than zero."
                    value > balancePaise -> error = "Amount cannot exceed the balance of ${Money.format(balancePaise)}."
                    date <= 0 -> error = "Choose a payment date."
                    else -> onSave(value, date, mode, note.trim())
                }
            }) { Text("Save payment") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun ReasonDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                FormField("Reason", reason) { reason = it }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (reason.isBlank()) error = "A reason is required."
                else onConfirm(reason.trim())
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun PaymentHistoryCard(
    entries: List<PaymentRow>,
    totalPaise: Long,
    paidPaise: Long,
    title: String = "Payments",
    paidLabel: String = "received",
    onRecordPayment: (() -> Unit)? = null,
) {
    val balance = (totalPaise - paidPaise).coerceAtLeast(0)
    val progress = if (totalPaise > 0) (paidPaise.toFloat() / totalPaise).coerceIn(0f, 1f) else 0f
    SectionCard(
        title = title,
        subtitle = "${Money.format(paidPaise)} $paidLabel of ${Money.format(totalPaise)}",
        trailing = onRecordPayment?.let {
            { TextButton(onClick = it) { Text("Record") } }
        },
    ) {
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Balance due", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Money.format(balance), style = MaterialTheme.typography.titleSmall,
                color = if (balance > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
        if (entries.isEmpty()) {
            Text("No payments recorded yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            entries.forEach { entry ->
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ReceiptLong, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(PaymentMode.label(entry.mode), style = MaterialTheme.typography.bodyMedium)
                        Text(listOf(formatDate(entry.dateMillis), entry.note).filter(String::isNotBlank).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(Money.format(entry.amountPaise), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

internal suspend fun shareInvoicePdf(
    context: Context,
    repo: Repository,
    invoice: Invoice,
    lines: List<InvoiceLine>,
    customer: Customer,
    accent: Int,
): Boolean = withContext(Dispatchers.IO) {
    val company = repo.companyOrDefault()
    val file = InvoicePdf.create(context, invoice, lines, customer, company, accent)
    withContext(Dispatchers.Main) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Invoice ${invoice.number}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share or print invoice"))
    }
    true
}

internal fun openPrivateFile(context: Context, path: String, mime: String) {
    val file = File(path)
    if (!file.isFile) {
        Toast.makeText(context, "That file is no longer available.", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open file"))
    } catch (e: Exception) {
        Toast.makeText(context, "No app found to open this file.", Toast.LENGTH_SHORT).show()
    }
}
