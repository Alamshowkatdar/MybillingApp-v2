package com.factorybill.app.data

import androidx.room.withTransaction
import com.factorybill.app.core.*

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
    val placeOfSupply: String = "",
    val pricesIncludeGst: Boolean = false,
    val scanImagePath: String? = null, // only set after the user confirmed the OCR review
    val invoiceNumber: String = "",
)
data class PurchaseDraft(
    val supplier: Supplier,
    val lines: List<DraftLine>,
    val paidPaise: Long,
    val note: String = "",
    val pricesIncludeGst: Boolean = false,
    val scanImagePath: String? = null,
)

/** All writes that touch money or stock run in ONE transaction and log inventory movements. */
class Repository(private val db: AppDatabase) {
    private val dao = db.dao()

    val customers = dao.customers()
    val products = dao.products()
    val suppliers = dao.suppliers()
    val invoices = dao.invoices()
    val purchases = dao.purchases()
    val expenses = dao.expenses()
    val employees = dao.employees()
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

    private suspend fun updateCustomer(c: Customer) {
        validateCustomer(c)
        dao.updateCustomer(c.copy(gstin = c.gstin.trim().uppercase()))
    }

    suspend fun addProduct(p: Product): Long = db.withTransaction {
        require(p.name.isNotBlank()) { "Product name is required" }
        require(p.defaultPricePaise >= 0) { "Price cannot be negative" }
        require(p.defaultGstRate.isFinite() && p.defaultGstRate in 0.0..100.0) { "GST rate must be between 0 and 100" }
        require(p.stockQty.isFinite()) { "Opening stock must be finite" }
        val id = dao.insertProduct(p)
        if (p.stockQty != 0.0) {
            dao.insertMovement(InventoryMovement(
                productId = id, delta = p.stockQty, reason = "OPENING_STOCK", refId = 0, timestamp = System.currentTimeMillis(),
            ))
        }
        id
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
        return dao.insertSupplier(s)
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
            var sequence = dao.invoiceCount() + 1
            var candidate: String
            do {
                candidate = "INV-%04d".format(sequence++)
            } while (dao.invoiceNumberExists(candidate))
            candidate
        }

        val customerId = if (d.customer.id == 0L) addCustomer(d.customer) else {
            updateCustomer(d.customer)
            d.customer.id
        }
        val now = System.currentTimeMillis()
        val invoiceId = dao.insertInvoice(
            Invoice(
                number = number, dateMillis = now, customerId = customerId, intraState = d.intraState,
                taxablePaise = totals.taxablePaise, gstPaise = totals.gstPaise, totalPaise = totals.totalPaise,
                paymentPaise = d.paymentPaise, pricesIncludeGst = d.pricesIncludeGst,
                placeOfSupply = d.placeOfSupply.trim(), scanImagePath = d.scanImagePath,
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

    suspend fun recordInvoicePayment(invoiceId: Long, amountPaise: Long) = db.withTransaction {
        require(amountPaise > 0) { "Payment must be greater than zero" }
        require(dao.addInvoicePayment(invoiceId, amountPaise) == 1) {
            "Payment exceeds the balance due, or the invoice is not open"
        }
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
        val purchaseId = dao.insertPurchase(
            Purchase(supplierId = supplierId, dateMillis = now, taxablePaise = totals.taxablePaise, gstPaise = totals.gstPaise,
                totalPaise = totals.totalPaise, paidPaise = d.paidPaise, note = d.note, scanImagePath = d.scanImagePath)
        )
        d.lines.forEachIndexed { i, l ->
            val r = totals.lines[i]
            val productId = if (l.product.id == 0L) dao.insertProduct(l.product.copy(stockQty = 0.0)) else l.product.id
            dao.insertPurchaseLine(PurchaseLine(purchaseId = purchaseId, productId = productId, qty = l.qty,
                unitCostPaise = l.unitPricePaise, gstRate = l.gstRate, taxablePaise = r.taxablePaise, gstPaise = r.gstPaise, totalPaise = r.totalPaise))
            dao.addStock(productId, l.qty)
            dao.insertMovement(InventoryMovement(productId = productId, delta = l.qty, reason = "PURCHASE", refId = purchaseId, timestamp = now))
        }
        purchaseId
    }

    suspend fun addExpense(e: Expense): Long {
        require(e.amountPaise > 0) { "Amount must be greater than 0" }
        require(e.category.isNotBlank()) { "Category is required" }
        return dao.insertExpense(e)
    }

    suspend fun addEmployee(e: Employee): Long {
        require(e.name.isNotBlank()) { "Employee name is required" }
        require(e.monthlySalaryPaise >= 0) { "Salary cannot be negative" }
        require(e.startDateMillis > 0) { "Start date is required" }
        return dao.insertEmployee(e)
    }

    suspend fun addSalaryEntry(s: SalaryEntry): Long {
        require(s.amountPaise > 0) { "Amount must be greater than 0" }
        require(runCatching { SalaryType.valueOf(s.type) }.isSuccess) { "Unknown entry type" }
        require(dao.employee(s.employeeId) != null) { "Employee not found" }
        return dao.insertSalaryEntry(s)
    }

    suspend fun recordSalary(
        employeeId: Long,
        type: SalaryType,
        amountPaise: Long,
        note: String = "",
        dateMillis: Long = System.currentTimeMillis(),
    ) {
        addSalaryEntry(SalaryEntry(employeeId = employeeId, type = type.name, amountPaise = amountPaise, dateMillis = dateMillis, note = note))
    }

    fun salaryEntries(employeeId: Long) = dao.salaryEntries(employeeId)
    fun salaryBalance(employeeId: Long) = dao.salaryBalance(employeeId)

    suspend fun saveCompany(c: CompanyProfile) {
        require(Validation.gstinFormatOk(c.gstin)) { "GSTIN format looks invalid (format check only)" }
        require(c.upiId.isBlank() || Validation.upiOk(c.upiId)) { "UPI ID format looks invalid" }
        dao.saveCompany(c.copy(id = 1))
    }

    suspend fun companyOrDefault() = dao.companyOnce() ?: CompanyProfile()
    suspend fun invoice(id: Long) = dao.invoice(id)
    suspend fun invoiceLines(id: Long) = dao.invoiceLines(id)
    suspend fun customer(id: Long) = dao.customer(id)
}
