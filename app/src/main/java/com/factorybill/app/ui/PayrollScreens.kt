package com.factorybill.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.factorybill.app.core.*
import com.factorybill.app.data.*
import kotlinx.coroutines.launch

private fun salaryTypeLabel(type: SalaryType): String = when (type) {
    SalaryType.SALARY_ADDED -> "Salary accrued"
    SalaryType.ADVANCE -> "Advance paid"
    SalaryType.PAYMENT -> "Salary paid"
}

private fun salaryTypeShort(type: SalaryType): String = when (type) {
    SalaryType.SALARY_ADDED -> "Accrue"
    SalaryType.ADVANCE -> "Advance"
    SalaryType.PAYMENT -> "Pay"
}

private fun salaryTypeIcon(type: SalaryType) = when (type) {
    SalaryType.SALARY_ADDED -> Icons.Default.AddCircleOutline
    SalaryType.ADVANCE -> Icons.Default.AccountBalanceWallet
    SalaryType.PAYMENT -> Icons.Default.Payments
}

private fun entriesFor(entries: List<SalaryEntry>) = entries.map {
    SalaryLine(runCatching { SalaryType.valueOf(it.type) }.getOrDefault(SalaryType.ADVANCE), it.amountPaise)
}

@Composable
fun PayrollScreen(onOpenEmployee: (Long) -> Unit) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val employees by repo.employees.collectAsStateWithLifecycle(emptyList())
    val salaryEntries by repo.salaryEntries.collectAsStateWithLifecycle(emptyList())
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val currentMonth = Periods.monthKey(System.currentTimeMillis())
    val active = employees.filter { it.active }
    val grouped = remember(salaryEntries) { salaryEntries.groupBy { it.employeeId } }
    val totalDue = employees.sumOf { SalaryLedger.balance(entriesFor(grouped[it.id].orEmpty())) }
    val monthlyBill = active.sumOf { it.monthlySalaryPaise }
    val accruedThisMonth = salaryEntries.filter { it.type == SalaryType.SALARY_ADDED.name && it.period == currentMonth }
    val filtered = employees.filter {
        query.isBlank() || it.name.contains(query, true) || it.role.contains(query, true) || it.phone.contains(query, true)
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeading(
                title = "Payroll",
                subtitle = "${active.size} active employee${if (active.size == 1) "" else "s"}",
                trailing = {
                    FilledTonalButton(onClick = { showAdd = true }) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Employee")
                    }
                },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    "Salary due", Money.format(totalDue), Icons.Default.Groups, Modifier.weight(1f),
                    accent = if (totalDue > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                StatCard(
                    "Monthly payroll", Money.format(monthlyBill), Icons.Default.CalendarMonth, Modifier.weight(1f),
                )
            }
        }
        item {
            SectionCard(
                title = "This month",
                subtitle = Periods.monthLabel(currentMonth),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${accruedThisMonth.size} of ${active.size} salaries accrued", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (accruedThisMonth.size < active.size) {
                                "${active.size - accruedThisMonth.size} employee${if (active.size - accruedThisMonth.size == 1) " still needs" else "s still need"} a salary accrual."
                            } else {
                                "All active salaries are accrued for this month."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        if (accruedThisMonth.size >= active.size && active.isNotEmpty()) Icons.Default.TaskAlt else Icons.Default.PendingActions,
                        null,
                        tint = if (accruedThisMonth.size >= active.size && active.isNotEmpty()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item { SearchField(query, { query = it }, "Search employee, role or phone") }
        message?.let { text ->
            item { Text(text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
        }
        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    title = if (employees.isEmpty()) "No employees yet" else "No matching employees",
                    message = if (employees.isEmpty()) {
                        "Add your team to track monthly salary accruals, advances and payments in one ledger."
                    } else {
                        "Try a different name, role or phone number."
                    },
                    icon = Icons.Default.Groups,
                    actionLabel = if (employees.isEmpty()) "Add employee" else null,
                    onAction = if (employees.isEmpty()) ({ showAdd = true }) else null,
                )
            }
        }
        items(filtered, key = { it.id }) { employee ->
            val balance = SalaryLedger.balance(entriesFor(grouped[employee.id].orEmpty()))
            EmployeeRow(employee, balance, onClick = { onOpenEmployee(employee.id) })
        }
    }

    if (showAdd) {
        EmployeeFormDialog(
            initial = null,
            onDismiss = { showAdd = false },
            onSave = { employee ->
                showAdd = false
                scope.launch {
                    try {
                        repo.addEmployee(employee)
                        message = "Employee added. Open their profile to accrue salary or record payments."
                    } catch (e: IllegalArgumentException) {
                        message = e.message
                    }
                }
            },
        )
    }
}

@Composable
private fun EmployeeRow(employee: Employee, balancePaise: Long, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Avatar(employee.name, size = 42.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(employee.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!employee.active) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text("Inactive", Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Text(
                    listOfNotNull(
                        employee.role.takeIf(String::isNotBlank),
                        "Monthly ${Money.format(employee.monthlySalaryPaise)}",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (balancePaise > 0) "Salary due ${Money.format(balancePaise)}"
                    else if (balancePaise < 0) "Advance ${Money.format(-balancePaise)}" else "No balance due",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (balancePaise > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun EmployeeDetailScreen(repo: Repository, employeeId: Long, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val employee by repo.employeeFlow(employeeId).collectAsStateWithLifecycle(null)
    val entries by repo.salaryEntries(employeeId).collectAsStateWithLifecycle(emptyList())
    val balance by repo.salaryBalance(employeeId).collectAsStateWithLifecycle(0L)
    var showEntry by remember { mutableStateOf<SalaryType?>(null) }
    var showEdit by remember { mutableStateOf(false) }
    var entryToEdit by remember { mutableStateOf<SalaryEntry?>(null) }
    var entryEditError by remember { mutableStateOf<String?>(null) }
    var entryToDelete by remember { mutableStateOf<SalaryEntry?>(null) }
    var showDeleteEmployee by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val person = employee
    if (person == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val currentMonth = Periods.monthKey(System.currentTimeMillis())
    val accruedThisMonth = entries.any { it.type == SalaryType.SALARY_ADDED.name && it.period == currentMonth }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Avatar(person.name, size = 42.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(person.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        person.role.ifBlank { if (person.active) "Active employee" else "Inactive employee" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { showEdit = true }) { Icon(Icons.Default.Edit, "Edit employee") }
            }
        }
        item {
            HeroSummary(
                eyebrow = "Salary balance",
                value = Money.format(balance.coerceAtLeast(0)),
                caption = if (balance > 0) "Due to ${person.name}" else if (balance < 0) {
                    "Advance of ${Money.format(-balance)}"
                } else {
                    "Fully settled"
                },
            )
        }
        item {
            SectionCard(title = "Employment") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DetailValue("Monthly salary", Money.format(person.monthlySalaryPaise), Modifier.weight(1f))
                    DetailValue(
                        "Started",
                        if (person.startDateMillis > 0) formatDate(person.startDateMillis) else "Not set",
                        Modifier.weight(1f),
                    )
                }
                if (person.phone.isNotBlank() || person.notes.isNotBlank()) {
                    HorizontalDivider()
                    if (person.phone.isNotBlank()) {
                        Text("Phone ${person.phone}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (person.notes.isNotBlank()) {
                        Text(person.notes, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        if (accruedThisMonth) Icons.Default.TaskAlt else Icons.Default.PendingActions,
                        null,
                        Modifier.size(18.dp),
                        tint = if (accruedThisMonth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (accruedThisMonth) "Salary accrued for ${Periods.monthLabel(currentMonth)}"
                        else "Salary not yet accrued for ${Periods.monthLabel(currentMonth)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SalaryAction(
                    label = "Accrue",
                    icon = Icons.Default.AddCircleOutline,
                    enabled = !accruedThisMonth,
                    onClick = { showEntry = SalaryType.SALARY_ADDED },
                    modifier = Modifier.weight(1f),
                )
                SalaryAction(
                    label = "Advance",
                    icon = Icons.Default.AccountBalanceWallet,
                    onClick = { showEntry = SalaryType.ADVANCE },
                    modifier = Modifier.weight(1f),
                )
                SalaryAction(
                    label = "Pay",
                    icon = Icons.Default.Payments,
                    onClick = { showEntry = SalaryType.PAYMENT },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            ScreenHeading(title = "Salary ledger", subtitle = "${entries.size} entr${if (entries.size == 1) "y" else "ies"}")
        }
        if (entries.isEmpty()) {
            item {
                EmptyState(
                    title = "No salary entries",
                    message = "Accrue this month's salary, then record advances and payments as they happen.",
                    icon = Icons.Default.ReceiptLong,
                )
            }
        } else {
            item {
                SectionCard {
                    entries.forEachIndexed { index, entry ->
                        if (index > 0) HorizontalDivider()
                        val type = runCatching { SalaryType.valueOf(entry.type) }.getOrDefault(SalaryType.ADVANCE)
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    entryEditError = null
                                    entryToEdit = entry
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(32.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(salaryTypeIcon(type), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(salaryTypeLabel(type), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    listOfNotNull(
                                        formatDate(entry.dateMillis),
                                        entry.period.takeIf(String::isNotBlank)?.let { Periods.monthLabel(it) },
                                        entry.note.takeIf(String::isNotBlank),
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    (if (type == SalaryType.SALARY_ADDED) "+" else "−") + Money.format(entry.amountPaise),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (type == SalaryType.SALARY_ADDED) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit entry",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (entries.isNotEmpty()) {
            item {
                Text(
                    "Tap any ledger entry to edit or delete it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            TextButton(
                onClick = { showDeleteEmployee = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Delete employee")
            }
        }
        message?.let { text ->
            item { Text(text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
        }
    }

    showEntry?.let { type ->
        SalaryEntryDialog(
            employee = person,
            type = type,
            currentMonth = currentMonth,
            onDismiss = { showEntry = null },
            onSave = { amount, date, period, note ->
                showEntry = null
                scope.launch {
                    try {
                        repo.recordSalary(
                            employeeId = person.id,
                            type = type,
                            amountPaise = amount,
                            note = note,
                            dateMillis = date,
                            period = period,
                        )
                        message = "${salaryTypeLabel(type)} saved."
                    } catch (e: IllegalArgumentException) {
                        message = e.message ?: "Could not save this salary entry."
                    }
                }
            },
        )
    }
    if (showEdit) {
        EmployeeFormDialog(
            initial = person,
            onDismiss = { showEdit = false },
            onSave = { updated ->
                showEdit = false
                scope.launch {
                    try {
                        repo.updateEmployeeRecord(updated)
                        message = "Employee details updated."
                    } catch (e: IllegalArgumentException) {
                        message = e.message ?: "Could not update this employee."
                    }
                }
            },
        )
    }
    entryToEdit?.let { entry ->
        SalaryEntryEditDialog(
            employee = person,
            entry = entry,
            error = entryEditError,
            onDismiss = {
                entryToEdit = null
                entryEditError = null
            },
            onDelete = {
                entryToEdit = null
                entryEditError = null
                entryToDelete = entry
            },
            onSave = { updated ->
                scope.launch {
                    try {
                        repo.updateSalaryEntry(updated)
                        entryToEdit = null
                        entryEditError = null
                        message = "Salary entry updated."
                    } catch (e: IllegalArgumentException) {
                        entryEditError = e.message ?: "Could not update this entry."
                    }
                }
            },
        )
    }
    entryToDelete?.let { entry ->
        val type = runCatching { SalaryType.valueOf(entry.type) }.getOrDefault(SalaryType.ADVANCE)
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Delete salary entry") },
            text = {
                Text(
                    "${salaryTypeLabel(type)} of ${Money.format(entry.amountPaise)} on " +
                        "${formatDate(entry.dateMillis)} will be removed permanently."
                )
            },
            confirmButton = {
                TextButton(
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        entryToDelete = null
                        scope.launch {
                            try {
                                repo.deleteSalaryEntry(entry.id)
                                message = "Salary entry deleted."
                            } catch (e: IllegalArgumentException) {
                                message = e.message ?: "Could not delete this entry."
                            }
                        }
                    },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton({ entryToDelete = null }) { Text("Cancel") } },
        )
    }
    if (showDeleteEmployee) {
        AlertDialog(
            onDismissRequest = { showDeleteEmployee = false },
            title = { Text("Delete employee") },
            text = {
                Text("Delete ${person.name} and all salary entries in their ledger? This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showDeleteEmployee = false
                        scope.launch {
                            try {
                                repo.deleteEmployee(person.id)
                                onBack()
                            } catch (e: IllegalArgumentException) {
                                message = e.message ?: "Could not delete this employee."
                            }
                        }
                    },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton({ showDeleteEmployee = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DetailValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SalaryAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            Modifier.padding(vertical = 12.dp, horizontal = 6.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(icon, null, tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun SalaryEntryDialog(
    employee: Employee,
    type: SalaryType,
    currentMonth: String,
    onDismiss: () -> Unit,
    onSave: (amountPaise: Long, dateMillis: Long, period: String, note: String) -> Unit,
) {
    var amount by remember(type) {
        mutableStateOf(if (type == SalaryType.SALARY_ADDED) rupeesText(employee.monthlySalaryPaise) else "")
    }
    var date by remember(type) { mutableLongStateOf(todayMillis()) }
    var period by remember(type) { mutableStateOf(currentMonth) }
    var note by remember(type) { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val periods = remember(currentMonth) { listOf(currentMonth, Periods.monthKey(Periods.shiftMonth(System.currentTimeMillis(), -1))) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${salaryTypeShort(type)} · ${employee.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FormField("Amount ₹", amount, { amount = it }, number = true)
                DateField("Entry date", date) { date = it }
                if (type == SalaryType.SALARY_ADDED) {
                    Text("Salary month", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ChoiceChips(periods, period, { Periods.monthLabel(it) }, { period = it })
                    Text("Each month can be accrued only once per employee.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FormField("Note (optional)", note) { note = it }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amountPaise = Money.parseRupees(amount)
                when {
                    amountPaise == null || amountPaise <= 0 -> error = "Enter a valid amount greater than zero."
                    date <= 0 -> error = "Choose an entry date."
                    type == SalaryType.SALARY_ADDED && !Periods.validMonthKey(period) -> error = "Choose a valid salary month."
                    else -> onSave(amountPaise, date, if (type == SalaryType.SALARY_ADDED) period else "", note.trim())
                }
            }) { Text("Save entry") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SalaryEntryEditDialog(
    employee: Employee,
    entry: SalaryEntry,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (SalaryEntry) -> Unit,
    onDelete: () -> Unit,
) {
    val type = runCatching { SalaryType.valueOf(entry.type) }.getOrDefault(SalaryType.ADVANCE)
    var amount by remember(entry.id) { mutableStateOf(rupeesText(entry.amountPaise)) }
    var date by remember(entry.id) {
        mutableLongStateOf(entry.dateMillis.takeIf { it > 0 } ?: todayMillis())
    }
    var period by remember(entry.id) { mutableStateOf(entry.period) }
    var note by remember(entry.id) { mutableStateOf(entry.note) }
    var localError by remember(entry.id) { mutableStateOf<String?>(null) }
    val periods = remember(entry.id) {
        listOfNotNull(
            Periods.monthKey(System.currentTimeMillis()),
            Periods.monthKey(Periods.shiftMonth(System.currentTimeMillis(), -1)),
            entry.period.takeIf { Periods.validMonthKey(it) },
        ).distinct()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit · ${salaryTypeLabel(type)}") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(employee.name, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                FormField("Amount ₹", amount, { amount = it }, number = true)
                DateField("Entry date", date) { date = it }
                if (type == SalaryType.SALARY_ADDED) {
                    Text("Salary month", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ChoiceChips(periods, period, { Periods.monthLabel(it) }, { period = it })
                    Text("Each month can be accrued only once per employee.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FormField("Note (optional)", note) { note = it }
                (localError ?: error)?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete entry")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amountPaise = Money.parseRupees(amount)
                when {
                    amountPaise == null || amountPaise <= 0 -> localError = "Enter a valid amount greater than zero."
                    date <= 0 -> localError = "Choose an entry date."
                    type == SalaryType.SALARY_ADDED && !Periods.validMonthKey(period) -> localError = "Choose a valid salary month."
                    else -> {
                        localError = null
                        onSave(
                            entry.copy(
                                amountPaise = amountPaise,
                                dateMillis = date,
                                period = if (type == SalaryType.SALARY_ADDED) period else Periods.monthKey(date),
                                note = note.trim(),
                            )
                        )
                    }
                }
            }) { Text("Save changes") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EmployeeFormDialog(
    initial: Employee?,
    onDismiss: () -> Unit,
    onSave: (Employee) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var salary by remember { mutableStateOf(initial?.monthlySalaryPaise?.let(::rupeesText).orEmpty()) }
    var startDate by remember { mutableLongStateOf(initial?.startDateMillis?.takeIf { it > 0 } ?: todayMillis()) }
    var phone by remember { mutableStateOf(initial?.phone.orEmpty()) }
    var role by remember { mutableStateOf(initial?.role.orEmpty()) }
    var notes by remember { mutableStateOf(initial?.notes.orEmpty()) }
    var active by remember { mutableStateOf(initial?.active ?: true) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add employee" else "Edit employee") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                FormField("Employee name *", name) { name = it }
                FormField("Monthly salary ₹ *", salary, { salary = it }, number = true)
                DateField("Start date", startDate) { startDate = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormField("Role", role, { role = it }, Modifier.weight(1f))
                    FormField("Phone", phone, { phone = it }, Modifier.weight(1f))
                }
                FormField("Notes", notes, { notes = it }, singleLine = false, minLines = 2)
                if (initial != null) {
                    SettingSwitch(active, { active = it }, "Active employee", "Inactive employees stay in your records but are excluded from payroll totals.")
                }
                Text(
                    "Salary becomes due only when you accrue it in the employee ledger.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val salaryPaise = Money.parseRupees(salary)
                when {
                    name.isBlank() -> error = "Enter the employee name."
                    salaryPaise == null -> error = "Enter a valid monthly salary."
                    startDate <= 0 -> error = "Choose a start date."
                    else -> onSave(
                        (initial ?: Employee(name = name, monthlySalaryPaise = salaryPaise)).copy(
                            name = name.trim(),
                            monthlySalaryPaise = salaryPaise,
                            startDateMillis = startDate,
                            phone = phone.trim(),
                            role = role.trim(),
                            notes = notes.trim(),
                            active = active,
                        )
                    )
                }
            }) { Text(if (initial == null) "Add employee" else "Save changes") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
