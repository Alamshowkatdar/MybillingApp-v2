package com.factorybill.app.data

import androidx.room.withTransaction
import com.factorybill.app.core.*
import java.io.File

data class DraftLine(
    val product: Product,
    val qty: Double,
    val unitPricePaise: Long,
    val gstRate: Double,
    val priceIncludesGst: Boolean = false,
)
data class InvoiceDraft(
    val customer: Customer, // id == 0 means "new customer, save with the invoice"
    val lines: List<DraftLine>,
    val paymentPaise: Long,
    val intraState: Boolean,
    val dateMillis: Long = 0,
    val dueDateMillis: Long = 0,
    val placeOfSupply: String = "",
    val pricesIncludeGst: Boolean = false,
    val notes: String = "",
    val paymentMode: PaymentMode = PaymentMode.CASH,
    val paymentDateMillis: Long = 0,
    val scanImagePath: String? = null, // only set after the user confirmed the OCR review
    val invoiceNumber: String = "",
)
data class PurchaseDraft(
    val supplier: Supplier,
    val lines: List<DraftLine>,
    val paidPaise: Long,
    val dateMillis: Long = 0,
    val dueDateMillis: Long = 0,
    val billNumber: String = "",
    val note: String = "",
    val pricesIncludeGst: Boolean = false,
    val paymentMode: PaymentMode = PaymentMode.CASH,
    val paymentDateMillis: Long = 0,
    val scanImagePath: String? = null,
)

/** All writes that touch money or stock run in ONE transaction and log inventory movements. */
/** Deletes a bill photo that this app stored privately for an invoice or purchase. */
private fun removeAttachmentFile(path: String?) {
    if (path.isNullOrBlank()) return
    runCatching {
        File(path).takeIf { it.isFile && it.parentFile?.name == "scans" }?.delete()
    }
}

class Repository(private val db: AppDatabase) {
    private val dao = db.dao()

    val customers = dao.customers()
    val products = dao.products()
    val suppliers = dao.suppliers()
    val invoices = dao.invoices()
    val invoicePayments = dao.allInvoicePayments()
    val purchases = dao.purchases()
    val purchasePayments = dao.allPurchasePayments()
    val expenses = dao.expenses()
    val employees = dao.employees()
    val salaryEntries = dao.allSalaryEntries()
    val movements = dao.movements()
    val company = dao.company()
    val salesTotal = dao.salesTotal()
    val customerDues = dao.customerDues()
    val supplierDues = dao.supplierDues()
    val expensesTotal = dao.expensesTotal()
    val negativeStockCount = dao.negativeStockCount()

    private fun validateCustomer(c: Customer) {
        require(c.name.isNotBlank()) { "Customer name is required" }
        require(Validation.phoneOk(c.phone)) { "Phone number looks invalid" }
        require(Validation.gstinFormatOk(c.gstin)) { "GSTIN format looks invalid (format check only, not verified)" }
    }

    suspend fun addCustomer(c: Customer): Long {
        validateCustomer(c)
        return dao.insertCustomer(c.copy(gstin = c.gstin.trim().uppercase()))
    }

    suspend fun updateCustomerRecord(c: Customer) {
        require(c.id > 0) { "Customer not found" }
        validateCustomer(c)
        dao.updateCustomer(c.copy(gstin = c.gstin.trim().uppercase()))
    }

    private suspend fun updateCustomer(c: Customer) {
        validateCustomer(c)
        dao.updateCustomer(c.copy(gstin = c.gstin.trim().uppercase()))
    }

    suspend fun addProduct(p: Product): Long = db.withTransaction {
        validateProduct(p)
        val id = dao.insertProduct(p)
        if (p.stockQty != 0.0) {
            dao.insertMovement(InventoryMovement(
                productId = id, delta = p.stockQty, reason = "OPENING_STOCK", refId = 0, timestamp = System.currentTimeMillis(),
            ))
        }
        id
    }

    suspend fun updateProductRecord(p: Product) {
        validateProduct(p)
        require(p.id > 0) { "Product not found" }
        dao.updateProduct(p)
    }

    private fun validateProduct(p: Product) {
        require(p.name.isNotBlank()) { "Product name is required" }
        require(p.defaultPricePaise >= 0) { "Price cannot be negative" }
        require(p.defaultGstRate.isFinite() && p.defaultGstRate in 0.0..100.0) { "GST rate must be between 0 and 100" }
        require(p.stockQty.isFinite()) { "Opening stock must be finite" }
    }

    suspend fun adjustStock(productId: Long, delta: Double, note: String = "") = db.withTransaction {
        require(delta.isFinite() && delta != 0.0) { "Stock adjustment must be a finite non-zero quantity" }
        val product = dao.product(productId) ?: throw IllegalArgumentException("Product not found")
        require((product.stockQty + delta).isFinite()) { "Adjusted stock exceeds the supported limit" }
        dao.addStock(productId, delta)
        dao.insertMovement(
            InventoryMovement(
                productId = productId,
                delta = delta,
                reason = "ADJUSTMENT${note.trim().takeIf(String::isNotEmpty)?.let { ": $it" } ?: ""}",
                refId = 0,
                timestamp = System.currentTimeMillis(),
            )
        )
    }

    suspend fun addSupplier(s: Supplier): Long {
        require(s.name.isNotBlank()) { "Supplier name is required" }
        require(Validation.phoneOk(s.phone)) { "Supplier phone number looks invalid" }
        require(Validation.gstinFormatOk(s.gstin)) { "Supplier GSTIN format looks invalid" }
        return dao.insertSupplier(s.copy(gstin = s.gstin.trim().uppercase()))
    }

    suspend fun updateSupplierRecord(s: Supplier) {
        require(s.id > 0) { "Supplier not found" }
        require(s.name.isNotBlank()) { "Supplier name is required" }
        require(Validation.phoneOk(s.phone)) { "Supplier phone number looks invalid" }
        require(Validation.gstinFormatOk(s.gstin)) { "Supplier GSTIN format looks invalid" }
        dao.updateSupplier(s.copy(gstin = s.gstin.trim().uppercase()))
    }

    suspend fun issueInvoice(d: InvoiceDraft): Long = db.withTransaction {
        val inputs = d.lines.map { LineInput(it.qty, it.unitPricePaise, it.gstRate, d.pricesIncludeGst) }
        val inputErrors = Validation.validateLineInputs(inputs)
        require(inputErrors.isEmpty()) { inputErrors.joinToString("; ") }
        val totals = InvoiceCalculator.calculate(inputs, d.intraState, d.paymentPaise)
        val errors = Validation.validateLines(inputs, d.paymentPaise, totals)
        require(errors.isEmpty()) { errors.joinToString("; ") }

        val requestedNumber = d.invoiceNumber.trim()
        require(Validation.invoiceNumberOk(requestedNumber)) {
            "Invoice number must be up to 16 letters, numbers, hyphens or slashes."
        }
        val number = if (requestedNumber.isNotEmpty()) {
            require(!dao.invoiceNumberExists(requestedNumber)) { "That invoice number is already in use." }
            requestedNumber
        } else {
            val prefix = (dao.companyOnce()?.invoicePrefix ?: "INV-").trim().ifBlank { "INV-" }
            var sequence = dao.invoiceCount() + 1
            var candidate: String
            do {
                candidate = prefix + sequence.toString().padStart(4, '0')
                sequence++
            } while (dao.invoiceNumberExists(candidate))
            candidate
        }

        val customerId = if (d.customer.id == 0L) addCustomer(d.customer) else {
            updateCustomer(d.customer)
            d.customer.id
        }
        val now = System.currentTimeMillis()
        val invoiceDate = d.dateMillis.takeIf { it > 0 } ?: now
        val invoiceId = dao.insertInvoice(
            Invoice(
                number = number, dateMillis = invoiceDate, createdAtMillis = now,
                dueDateMillis = d.dueDateMillis.takeIf { it > 0 } ?: 0,
                customerId = customerId, intraState = d.intraState,
                taxablePaise = totals.taxablePaise, gstPaise = totals.gstPaise, totalPaise = totals.totalPaise,
                paymentPaise = d.paymentPaise, pricesIncludeGst = d.pricesIncludeGst,
                placeOfSupply = d.placeOfSupply.trim(), notes = d.notes.trim(), scanImagePath = d.scanImagePath,
            )
        )
        d.lines.forEachIndexed { i, l ->
            val r = totals.lines[i]
            val productId = if (l.product.id == 0L) addProduct(l.product.copy(stockQty = 0.0)) else l.product.id
            dao.insertInvoiceLine(
                InvoiceLine(
                    invoiceId = invoiceId, productId = productId, productName = l.product.name,
                    hsn = l.product.hsn, unit = l.product.unit, qty = l.qty,
                    unitPricePaise = l.unitPricePaise, gstRate = l.gstRate,
                    taxablePaise = r.taxablePaise, gstPaise = r.gstPaise, totalPaise = r.totalPaise,
                )
            )
            dao.addStock(productId, -l.qty) // stock may go negative by design
            dao.insertMovement(InventoryMovement(productId = productId, delta = -l.qty, reason = "INVOICE", refId = invoiceId, timestamp = now))
        }
        if (d.paymentPaise > 0) {
            dao.insertInvoicePayment(
                InvoicePayment(
                    invoiceId = invoiceId,
                    amountPaise = d.paymentPaise,
                    dateMillis = d.paymentDateMillis.takeIf { it > 0 } ?: invoiceDate,
                    mode = d.paymentMode.value,
                    note = "Payment recorded with invoice",
                )
            )
        }
        invoiceId
    }

    /** Invoices are never deleted. Voiding keeps the record, stores a reason, and reverses stock. */
    suspend fun voidInvoice(invoiceId: Long, reason: String) = db.withTransaction {
        require(reason.isNotBlank()) { "A reason is required to void an invoice" }
        require(dao.markVoid(invoiceId, reason.trim()) == 1) { "Invoice not found or already void" }
        val now = System.currentTimeMillis()
        dao.invoiceLines(invoiceId).forEach { l ->
            dao.addStock(l.productId, l.qty)
            dao.insertMovement(InventoryMovement(productId = l.productId, delta = l.qty, reason = "INVOICE_VOID", refId = invoiceId, timestamp = now))
        }
    }

    /** Replaces the invoice lines/header while keeping payments already recorded. */
    suspend fun updateInvoice(invoiceId: Long, d: InvoiceDraft): Long = db.withTransaction {
        val existing = dao.invoice(invoiceId) ?: throw IllegalArgumentException("Invoice not found")
        require(existing.status == "ISSUED") { "Void invoices cannot be edited" }

        val inputs = d.lines.map { LineInput(it.qty, it.unitPricePaise, it.gstRate, d.pricesIncludeGst) }
        val inputErrors = Validation.validateLineInputs(inputs)
        require(inputErrors.isEmpty()) { inputErrors.joinToString("; ") }
        val totals = InvoiceCalculator.calculate(inputs, d.intraState, existing.paymentPaise)
        val errors = Validation.validateLines(inputs, existing.paymentPaise, totals).map {
            if (it == "Payment cannot exceed the total") {
                "New total cannot be less than the amount already paid"
            } else it
        }
        require(errors.isEmpty()) { errors.joinToString("; ") }

        val requestedNumber = d.invoiceNumber.trim()
        require(Validation.invoiceNumberOk(requestedNumber)) {
            "Invoice number must be up to 16 letters, numbers, hyphens or slashes."
        }
        if (requestedNumber.isNotEmpty()) {
            require(!dao.invoiceNumberExistsExcept(requestedNumber, invoiceId)) {
                "That invoice number is already in use."
            }
        }
        val number = requestedNumber.ifEmpty { existing.number }

        val customerId = if (d.customer.id == 0L) addCustomer(d.customer) else {
            updateCustomer(d.customer)
            d.customer.id
        }

        val now = System.currentTimeMillis()
        val invoiceDate = d.dateMillis.takeIf { it > 0 } ?: existing.dateMillis
        dao.invoiceLines(invoiceId).forEach { l ->
            dao.addStock(l.productId, l.qty)
            dao.insertMovement(InventoryMovement(productId = l.productId, delta = l.qty, reason = "INVOICE_EDIT", refId = invoiceId, timestamp = now))
        }
        dao.deleteInvoiceLines(invoiceId)
        dao.updateInvoice(
            existing.copy(
                number = number,
                dateMillis = invoiceDate,
                dueDateMillis = d.dueDateMillis.takeIf { it > 0 } ?: 0,
                customerId = customerId,
                intraState = d.intraState,
                taxablePaise = totals.taxablePaise,
                gstPaise = totals.gstPaise,
                totalPaise = totals.totalPaise,
                pricesIncludeGst = d.pricesIncludeGst,
                placeOfSupply = d.placeOfSupply.trim(),
                notes = d.notes.trim(),
                scanImagePath = d.scanImagePath,
            )
        )
        if (d.scanImagePath != existing.scanImagePath) removeAttachmentFile(existing.scanImagePath)
        d.lines.forEachIndexed { i, l ->
            val r = totals.lines[i]
            val productId = if (l.product.id == 0L) addProduct(l.product.copy(stockQty = 0.0)) else l.product.id
            dao.insertInvoiceLine(
                InvoiceLine(
                    invoiceId = invoiceId, productId = productId, productName = l.product.name,
                    hsn = l.product.hsn, unit = l.product.unit, qty = l.qty,
                    unitPricePaise = l.unitPricePaise, gstRate = l.gstRate,
                    taxablePaise = r.taxablePaise, gstPaise = r.gstPaise, totalPaise = r.totalPaise,
                )
            )
            dao.addStock(productId, -l.qty)
            dao.insertMovement(InventoryMovement(productId = productId, delta = -l.qty, reason = "INVOICE_EDIT", refId = invoiceId, timestamp = now))
        }
        invoiceId
    }

    /** Permanently removes an invoice, its lines and payments, restoring stock unless it was void. */
    suspend fun deleteInvoice(invoiceId: Long) = db.withTransaction {
        val existing = dao.invoice(invoiceId) ?: throw IllegalArgumentException("Invoice not found")
        val lines = dao.invoiceLines(invoiceId)
        if (existing.status == "ISSUED") {
            lines.forEach { dao.addStock(it.productId, it.qty) }
        }
        dao.deleteInvoiceLines(invoiceId)
        dao.deleteInvoicePayments(invoiceId)
        dao.deleteInvoiceMovements(invoiceId)
        dao.deleteInvoice(invoiceId)
        removeAttachmentFile(existing.scanImagePath)
    }

    suspend fun recordInvoicePayment(
        invoiceId: Long,
        amountPaise: Long,
        dateMillis: Long = System.currentTimeMillis(),
        mode: PaymentMode = PaymentMode.CASH,
        note: String = "",
    ) = db.withTransaction {
        require(amountPaise > 0) { "Payment must be greater than zero" }
        require(dateMillis > 0) { "Payment date is required" }
        require(dao.addInvoicePayment(invoiceId, amountPaise) == 1) {
            "Payment exceeds the balance due, or the invoice is not open"
        }
        dao.insertInvoicePayment(
            InvoicePayment(
                invoiceId = invoiceId, amountPaise = amountPaise, dateMillis = dateMillis,
                mode = mode.value, note = note.trim(),
            )
        )
    }

    suspend fun savePurchase(d: PurchaseDraft): Long = db.withTransaction {
        val inputs = d.lines.map { LineInput(it.qty, it.unitPricePaise, it.gstRate, d.pricesIncludeGst) }
        val inputErrors = Validation.validateLineInputs(inputs)
        require(inputErrors.isEmpty()) { inputErrors.joinToString("; ") }
        val totals = InvoiceCalculator.calculate(inputs, true, d.paidPaise)
        val errors = Validation.validateLines(inputs, d.paidPaise, totals)
        require(errors.isEmpty()) { errors.joinToString("; ").replace("Payment", "Amount paid") }

        val supplierId = if (d.supplier.id == 0L) addSupplier(d.supplier) else d.supplier.id
        val now = System.currentTimeMillis()
        val purchaseDate = d.dateMillis.takeIf { it > 0 } ?: now
        var sequence = dao.purchaseCount() + 1
        var number: String
        do {
            number = "PUR-" + sequence.toString().padStart(4, '0')
            sequence++
        } while (dao.purchaseNumberExists(number))
        val purchaseId = dao.insertPurchase(
            Purchase(
                number = number, billNumber = d.billNumber.trim(), supplierId = supplierId,
                dateMillis = purchaseDate, createdAtMillis = now,
                dueDateMillis = d.dueDateMillis.takeIf { it > 0 } ?: 0,
                taxablePaise = totals.taxablePaise, gstPaise = totals.gstPaise,
                totalPaise = totals.totalPaise, paidPaise = d.paidPaise, note = d.note.trim(),
                scanImagePath = d.scanImagePath,
            )
        )
        d.lines.forEachIndexed { i, l ->
            val r = totals.lines[i]
            val productId = if (l.product.id == 0L) {
                dao.insertProduct(l.product.copy(stockQty = 0.0))
            } else l.product.id
            dao.insertPurchaseLine(
                PurchaseLine(
                    purchaseId = purchaseId, productId = productId, productName = l.product.name,
                    hsn = l.product.hsn, unit = l.product.unit, qty = l.qty,
                    unitCostPaise = l.unitPricePaise, gstRate = l.gstRate,
                    taxablePaise = r.taxablePaise, gstPaise = r.gstPaise, totalPaise = r.totalPaise,
                )
            )
            dao.addStock(productId, l.qty)
            dao.insertMovement(InventoryMovement(productId = productId, delta = l.qty, reason = "PURCHASE", refId = purchaseId, timestamp = now))
        }
        if (d.paidPaise > 0) {
            dao.insertPurchasePayment(
                PurchasePayment(
                    purchaseId = purchaseId,
                    amountPaise = d.paidPaise,
                    dateMillis = d.paymentDateMillis.takeIf { it > 0 } ?: purchaseDate,
                    mode = d.paymentMode.value,
                    note = "Payment recorded with purchase",
                )
            )
        }
        purchaseId
    }

    /** Purchases are kept as history. Voiding reverses the stock received and marks the record void. */
    suspend fun voidPurchase(purchaseId: Long, reason: String) = db.withTransaction {
        require(reason.isNotBlank()) { "A reason is required to void a purchase" }
        require(dao.markPurchaseVoid(purchaseId, reason.trim()) == 1) { "Purchase not found or already void" }
        val now = System.currentTimeMillis()
        dao.purchaseLines(purchaseId).forEach { l ->
            dao.addStock(l.productId, -l.qty)
            dao.insertMovement(InventoryMovement(productId = l.productId, delta = -l.qty, reason = "PURCHASE_VOID", refId = purchaseId, timestamp = now))
        }
    }

    /** Replaces the purchase lines/header while keeping payments already recorded. */
    suspend fun updatePurchase(purchaseId: Long, d: PurchaseDraft): Long = db.withTransaction {
        val existing = dao.purchase(purchaseId) ?: throw IllegalArgumentException("Purchase not found")
        require(existing.status == "RECORDED") { "Void purchases cannot be edited" }

        val inputs = d.lines.map { LineInput(it.qty, it.unitPricePaise, it.gstRate, d.pricesIncludeGst) }
        val inputErrors = Validation.validateLineInputs(inputs)
        require(inputErrors.isEmpty()) { inputErrors.joinToString("; ") }
        val totals = InvoiceCalculator.calculate(inputs, true, existing.paidPaise)
        val errors = Validation.validateLines(inputs, existing.paidPaise, totals).map {
            if (it == "Payment cannot exceed the total") {
                "New total cannot be less than the amount already paid"
            } else it.replace("Payment", "Amount paid")
        }
        require(errors.isEmpty()) { errors.joinToString("; ") }

        val supplierId = if (d.supplier.id == 0L) addSupplier(d.supplier) else {
            updateSupplierRecord(d.supplier)
            d.supplier.id
        }

        val now = System.currentTimeMillis()
        val purchaseDate = d.dateMillis.takeIf { it > 0 } ?: existing.dateMillis
        dao.purchaseLines(purchaseId).forEach { l ->
            dao.addStock(l.productId, -l.qty)
            dao.insertMovement(InventoryMovement(productId = l.productId, delta = -l.qty, reason = "PURCHASE_EDIT", refId = purchaseId, timestamp = now))
        }
        dao.deletePurchaseLines(purchaseId)
        dao.updatePurchase(
            existing.copy(
                billNumber = d.billNumber.trim(),
                supplierId = supplierId,
                dateMillis = purchaseDate,
                dueDateMillis = d.dueDateMillis.takeIf { it > 0 } ?: 0,
                taxablePaise = totals.taxablePaise,
                gstPaise = totals.gstPaise,
                totalPaise = totals.totalPaise,
                note = d.note.trim(),
                scanImagePath = d.scanImagePath,
            )
        )
        if (d.scanImagePath != existing.scanImagePath) removeAttachmentFile(existing.scanImagePath)
        d.lines.forEachIndexed { i, l ->
            val r = totals.lines[i]
            val productId = if (l.product.id == 0L) {
                dao.insertProduct(l.product.copy(stockQty = 0.0))
            } else l.product.id
            dao.insertPurchaseLine(
                PurchaseLine(
                    purchaseId = purchaseId, productId = productId, productName = l.product.name,
                    hsn = l.product.hsn, unit = l.product.unit, qty = l.qty,
                    unitCostPaise = l.unitPricePaise, gstRate = l.gstRate,
                    taxablePaise = r.taxablePaise, gstPaise = r.gstPaise, totalPaise = r.totalPaise,
                )
            )
            dao.addStock(productId, l.qty)
            dao.insertMovement(InventoryMovement(productId = productId, delta = l.qty, reason = "PURCHASE_EDIT", refId = purchaseId, timestamp = now))
        }
        purchaseId
    }

    /** Permanently removes a purchase, its lines and payments, reversing received stock unless void. */
    suspend fun deletePurchase(purchaseId: Long) = db.withTransaction {
        val existing = dao.purchase(purchaseId) ?: throw IllegalArgumentException("Purchase not found")
        val lines = dao.purchaseLines(purchaseId)
        if (existing.status == "RECORDED") {
            lines.forEach { dao.addStock(it.productId, -it.qty) }
        }
        dao.deletePurchaseLines(purchaseId)
        dao.deletePurchasePayments(purchaseId)
        dao.deletePurchaseMovements(purchaseId)
        dao.deletePurchase(purchaseId)
        removeAttachmentFile(existing.scanImagePath)
    }

    suspend fun recordPurchasePayment(
        purchaseId: Long,
        amountPaise: Long,
        dateMillis: Long = System.currentTimeMillis(),
        mode: PaymentMode = PaymentMode.CASH,
        note: String = "",
    ) = db.withTransaction {
        require(amountPaise > 0) { "Payment must be greater than zero" }
        require(dateMillis > 0) { "Payment date is required" }
        require(dao.addPurchasePayment(purchaseId, amountPaise) == 1) {
            "Payment exceeds the balance due, or the purchase is not open"
        }
        dao.insertPurchasePayment(
            PurchasePayment(
                purchaseId = purchaseId, amountPaise = amountPaise, dateMillis = dateMillis,
                mode = mode.value, note = note.trim(),
            )
        )
    }

    suspend fun addExpense(e: Expense): Long {
        require(e.amountPaise > 0) { "Amount must be greater than 0" }
        require(e.category.isNotBlank()) { "Category is required" }
        require(e.dateMillis > 0) { "Expense date is required" }
        require(PaymentMode.entries.any { it.value == e.mode }) { "Unknown payment mode" }
        return dao.insertExpense(e)
    }

    suspend fun addEmployee(e: Employee): Long {
        validateEmployee(e)
        return dao.insertEmployee(e)
    }

    suspend fun updateEmployeeRecord(e: Employee) {
        require(e.id > 0) { "Employee not found" }
        validateEmployee(e)
        dao.updateEmployee(e)
    }

    private fun validateEmployee(e: Employee) {
        require(e.name.isNotBlank()) { "Employee name is required" }
        require(e.monthlySalaryPaise >= 0) { "Salary cannot be negative" }
        require(e.startDateMillis > 0) { "Start date is required" }
        require(Validation.phoneOk(e.phone)) { "Employee phone number looks invalid" }
    }

    suspend fun addSalaryEntry(s: SalaryEntry): Long {
        require(s.amountPaise > 0) { "Amount must be greater than zero" }
        require(s.dateMillis > 0) { "Entry date is required" }
        val type = runCatching { SalaryType.valueOf(s.type) }.getOrElse { throw IllegalArgumentException("Unknown entry type") }
        require(dao.employee(s.employeeId) != null) { "Employee not found" }
        val period = s.period.ifBlank { Periods.monthKey(s.dateMillis) }
        require(Periods.validMonthKey(period)) { "Salary period must be a valid month" }
        if (type == SalaryType.SALARY_ADDED) {
            require(!dao.salaryAccrued(s.employeeId, period)) {
                "Salary for ${Periods.monthLabel(period)} is already accrued for this employee"
            }
        }
        return dao.insertSalaryEntry(s.copy(period = period))
    }

    suspend fun recordSalary(
        employeeId: Long,
        type: SalaryType,
        amountPaise: Long,
        note: String = "",
        dateMillis: Long = System.currentTimeMillis(),
        period: String = "",
    ) {
        addSalaryEntry(
            SalaryEntry(
                employeeId = employeeId, type = type.name, amountPaise = amountPaise,
                dateMillis = dateMillis, period = period, note = note,
            )
        )
    }

    fun salaryEntries(employeeId: Long) = dao.salaryEntries(employeeId)
    fun salaryBalance(employeeId: Long) = dao.salaryBalance(employeeId)

    suspend fun updateSalaryEntry(entry: SalaryEntry) {
        require(entry.id > 0) { "Salary entry not found" }
        require(entry.amountPaise > 0) { "Amount must be greater than zero" }
        require(entry.dateMillis > 0) { "Entry date is required" }
        val type = runCatching { SalaryType.valueOf(entry.type) }
            .getOrElse { throw IllegalArgumentException("Unknown entry type") }
        val existing = dao.salaryEntry(entry.id) ?: throw IllegalArgumentException("Salary entry not found")
        require(existing.employeeId == entry.employeeId) { "Salary entry does not belong to this employee" }
        require(dao.employee(entry.employeeId) != null) { "Employee not found" }
        val period = entry.period.ifBlank { Periods.monthKey(entry.dateMillis) }
        require(Periods.validMonthKey(period)) { "Salary period must be a valid month" }
        if (type == SalaryType.SALARY_ADDED) {
            require(!dao.salaryAccruedExcept(entry.employeeId, period, entry.id)) {
                "Salary for ${Periods.monthLabel(period)} is already accrued for this employee"
            }
        }
        dao.updateSalaryEntry(
            entry.copy(type = type.name, period = period, note = entry.note.trim())
        )
    }

    suspend fun deleteSalaryEntry(entryId: Long) {
        require(entryId > 0) { "Salary entry not found" }
        require(dao.salaryEntry(entryId) != null) { "Salary entry not found" }
        dao.deleteSalaryEntry(entryId)
    }

    /** Deletes the employee and their whole salary ledger. Returns the number of entries removed. */
    suspend fun deleteEmployee(employeeId: Long): Int = db.withTransaction {
        require(employeeId > 0) { "Employee not found" }
        require(dao.employee(employeeId) != null) { "Employee not found" }
        val removedEntries = dao.deleteSalaryEntriesForEmployee(employeeId)
        dao.deleteEmployee(employeeId)
        removedEntries
    }

    suspend fun saveCompany(c: CompanyProfile) {
        require(Validation.gstinFormatOk(c.gstin)) { "GSTIN format looks invalid (format check only)" }
        require(c.upiId.isBlank() || Validation.upiOk(c.upiId)) { "UPI ID format looks invalid" }
        require(Validation.invoicePrefixOk(c.invoicePrefix)) {
            "Invoice prefix may contain up to 8 letters, numbers, hyphens or slashes."
        }
        dao.saveCompany(c.copy(id = 1, invoicePrefix = c.invoicePrefix.trim().ifBlank { "INV-" }))
    }

    suspend fun companyOrDefault() = dao.companyOnce() ?: CompanyProfile()
    suspend fun invoice(id: Long) = dao.invoice(id)
    fun invoiceFlow(id: Long) = dao.invoiceFlow(id)
    suspend fun invoiceLines(id: Long) = dao.invoiceLines(id)
    suspend fun invoicePaymentsOnce(id: Long) = dao.invoicePaymentsOnce(id)
    fun invoicePayments(id: Long) = dao.invoicePayments(id)
    suspend fun customer(id: Long) = dao.customer(id)
    suspend fun purchase(id: Long) = dao.purchase(id)
    fun purchaseFlow(id: Long) = dao.purchaseFlow(id)
    suspend fun purchaseLines(id: Long) = dao.purchaseLines(id)
    fun purchasePayments(id: Long) = dao.purchasePayments(id)
    suspend fun purchasePaymentsOnce(id: Long) = dao.purchasePaymentsOnce(id)
    suspend fun supplier(id: Long) = dao.supplier(id)
    fun employeeFlow(id: Long) = dao.employeeFlow(id)
}
