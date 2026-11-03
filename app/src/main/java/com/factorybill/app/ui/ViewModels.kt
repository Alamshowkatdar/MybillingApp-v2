package com.factorybill.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.factorybill.app.core.*
import com.factorybill.app.data.*
import com.factorybill.app.pdf.InvoicePdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToLong

private fun <T> Flow<T>.state(scope: kotlinx.coroutines.CoroutineScope, init: T) =
    stateIn(scope, SharingStarted.WhileSubscribed(5000), init)

fun rupeesText(paise: Long) = "${paise / 100}.${(paise % 100).toString().padStart(2, '0')}"

class HomeViewModel(private val repo: Repository) : ViewModel() {
    val sales = repo.salesTotal.state(viewModelScope, 0L)
    val dues = repo.customerDues.state(viewModelScope, 0L)
    val supplierDues = repo.supplierDues.state(viewModelScope, 0L)
    val expenses = repo.expensesTotal.state(viewModelScope, 0L)
    val expenseRecords = repo.expenses.state(viewModelScope, emptyList())
    val negativeStock = repo.negativeStockCount.state(viewModelScope, 0)
    val invoices = repo.invoices.state(viewModelScope, emptyList())
    val purchases = repo.purchases.state(viewModelScope, emptyList())
    val employees = repo.employees.state(viewModelScope, emptyList())
    val salaryEntries = repo.salaryEntries.state(viewModelScope, emptyList())
    val products = repo.products.state(viewModelScope, emptyList())
    val company = repo.company.state(viewModelScope, null)

    suspend fun pdfFor(ctx: Context, invoiceId: Long, accent: Int): File? = withContext(Dispatchers.IO) {
        val inv = repo.invoice(invoiceId) ?: return@withContext null
        val cust = repo.customer(inv.customerId) ?: return@withContext null
        InvoicePdf.create(ctx, inv, repo.invoiceLines(invoiceId), cust, repo.companyOrDefault(), accent)
    }
}

class LineState(p: Product = Product(name = "")) {
    var product by mutableStateOf(p)
    var qty by mutableStateOf("1")
    var price by mutableStateOf(rupeesText(p.defaultPricePaise))
    var gst by mutableStateOf(p.defaultGstRate)

    fun use(product: Product) {
        this.product = product
        qty = "1"
        price = rupeesText(product.defaultPricePaise)
        gst = product.defaultGstRate
    }
}

class InvoiceFormViewModel(private val repo: Repository) : ViewModel() {
    val customers = repo.customers.state(viewModelScope, emptyList())
    val products = repo.products.state(viewModelScope, emptyList())
    private val company = repo.company.state(viewModelScope, null)

    var customerDraft by mutableStateOf(Customer(name = ""))
    var invoiceNumber by mutableStateOf("")
    var intraState by mutableStateOf(true)
    var pricesIncludeGst by mutableStateOf(false)
    var placeOfSupply by mutableStateOf("")
    var notes by mutableStateOf("")
    var invoiceDate by mutableLongStateOf(System.currentTimeMillis())
    var dueDate by mutableLongStateOf(0L)
    val lines = mutableStateListOf(LineState())
    var payment by mutableStateOf("")
    var paymentMode by mutableStateOf(PaymentMode.CASH)
    var paymentDate by mutableLongStateOf(System.currentTimeMillis())
    var message by mutableStateOf<String?>(null)
    var saving by mutableStateOf(false)
    var savedInvoiceId by mutableStateOf<Long?>(null)
    var scanImagePath by mutableStateOf<String?>(null)
    var editingId by mutableStateOf<Long?>(null)
    private var loadedKey: String? = null
    val isEditing: Boolean get() = editingId != null

    fun inputs() = lines.map {
        LineInput(it.qty.toDoubleOrNull() ?: 0.0, Money.parseRupees(it.price) ?: -1L, it.gst, pricesIncludeGst)
    }

    fun previewInputs() = inputs().filter { Validation.validateLineInputs(listOf(it)).isEmpty() }
    fun previewTotals() = InvoiceCalculator.calculate(previewInputs(), intraState, paymentPaise() ?: 0L)
    fun paymentPaise(): Long? = if (payment.isBlank()) 0L else Money.parseRupees(payment)
    fun liveStock(l: LineState) = products.value.firstOrNull { it.id == l.product.id }?.stockQty ?: l.product.stockQty

    fun selectCustomer(c: Customer) {
        customerDraft = c.copy()
        company.value?.let { Validation.sameState(c.gstin, it.gstin) }?.let { intraState = it }
    }

    private fun guarded(block: suspend () -> Unit) = viewModelScope.launch {
        saving = true
        try {
            block()
        } catch (e: IllegalArgumentException) {
            message = e.message
        } catch (e: Exception) {
            message = e.message ?: "Something went wrong. Please try again."
        } finally {
            saving = false
        }
    }

    fun updateCustomer(update: (Customer) -> Customer) {
        customerDraft = update(customerDraft)
        company.value?.let { Validation.sameState(customerDraft.gstin, it.gstin) }?.let { intraState = it }
        message = null
    }

    fun addLine() {
        lines.add(LineState())
    }

    fun addLine(product: Product, qty: String = "1", price: String? = null, gst: Double? = null) {
        lines.add(LineState(product).apply {
            this.qty = qty
            this.price = price ?: rupeesText(product.defaultPricePaise)
            this.gst = gst ?: product.defaultGstRate
        })
    }

    fun resetAfterSave() {
        lines.clear()
        lines.add(LineState())
        payment = ""
        invoiceNumber = ""
        customerDraft = Customer(name = "")
        placeOfSupply = ""
        notes = ""
        dueDate = 0
        invoiceDate = System.currentTimeMillis()
        scanImagePath = null
        editingId = null
        loadedKey = null
        savedInvoiceId = null
        message = null
    }

    /** Called when the "new invoice" screen opens; keeps an in-progress draft if it is already open. */
    fun startNew() {
        if (loadedKey == "new") return
        resetAfterSave()
        loadedKey = "new"
    }

    fun loadForEdit(invoice: Invoice, customer: Customer, invoiceLines: List<InvoiceLine>) {
        val key = "edit:${invoice.id}"
        if (loadedKey == key) return
        editingId = invoice.id
        customerDraft = customer.copy()
        invoiceNumber = invoice.number
        intraState = invoice.intraState
        pricesIncludeGst = invoice.pricesIncludeGst
        placeOfSupply = invoice.placeOfSupply
        notes = invoice.notes
        invoiceDate = invoice.dateMillis
        dueDate = invoice.dueDateMillis
        payment = rupeesText(invoice.paymentPaise)
        paymentMode = PaymentMode.CASH
        paymentDate = invoice.dateMillis
        scanImagePath = invoice.scanImagePath
        lines.clear()
        invoiceLines.forEach { line ->
            val product = Product(
                id = line.productId,
                name = line.productName,
                hsn = line.hsn,
                unit = line.unit,
                defaultPricePaise = line.unitPricePaise,
                defaultGstRate = line.gstRate,
                stockQty = products.value.firstOrNull { it.id == line.productId }?.stockQty ?: 0.0,
            )
            lines.add(LineState(product).apply {
                qty = line.qty.toString().removeSuffix(".0")
                price = rupeesText(line.unitPricePaise)
                gst = line.gstRate
            })
        }
        if (lines.isEmpty()) lines.add(LineState())
        savedInvoiceId = null
        message = null
        loadedKey = key
    }

    fun issue() {
        if (saving) return
        val c = customerDraft
        if (c.name.isBlank()) {
            message = "Enter the customer name"
            return
        }
        if (!Validation.phoneOk(c.phone)) {
            message = "Enter a valid customer phone number"
            return
        }
        if (!Validation.gstinFormatOk(c.gstin)) {
            message = "Customer GSTIN format looks invalid"
            return
        }
        val pay = paymentPaise() ?: run {
            message = "Enter a valid payment amount"
            return
        }
        val lineErrors = Validation.validateLineInputs(inputs())
        if (lineErrors.isNotEmpty()) {
            message = lineErrors.joinToString("; ")
            return
        }
        if (lines.any { it.product.name.isBlank() }) {
            message = "Enter a product name for every invoice line"
            return
        }
        if (dueDate > 0 && dueDate < invoiceDate) {
            message = "Due date cannot be before the invoice date"
            return
        }
        val draftLines = lines.map { line ->
            val price = Money.parseRupees(line.price) ?: -1L
            DraftLine(
                line.product.copy(defaultPricePaise = price, defaultGstRate = line.gst, stockQty = 0.0),
                line.qty.toDoubleOrNull() ?: 0.0,
                price,
                line.gst,
            )
        }
        val draft = InvoiceDraft(
            customer = c,
            lines = draftLines,
            paymentPaise = pay,
            intraState = intraState,
            dateMillis = invoiceDate,
            dueDateMillis = dueDate,
            placeOfSupply = placeOfSupply,
            pricesIncludeGst = pricesIncludeGst,
            notes = notes,
            paymentMode = paymentMode,
            paymentDateMillis = paymentDate,
            scanImagePath = scanImagePath,
            invoiceNumber = invoiceNumber,
        )
        guarded {
            val editing = editingId
            val id = if (editing != null) repo.updateInvoice(editing, draft) else repo.issueInvoice(draft)
            savedInvoiceId = id
            message = if (editing != null) "Invoice updated" else "Invoice issued"
        }
    }
}

class PurchaseFormViewModel(private val repo: Repository) : ViewModel() {
    val suppliers = repo.suppliers.state(viewModelScope, emptyList())
    val products = repo.products.state(viewModelScope, emptyList())

    var supplierDraft by mutableStateOf(Supplier(name = ""))
    var billNumber by mutableStateOf("")
    var purchaseDate by mutableLongStateOf(System.currentTimeMillis())
    var dueDate by mutableLongStateOf(0L)
    var note by mutableStateOf("")
    var pricesIncludeGst by mutableStateOf(false)
    val lines = mutableStateListOf(LineState())
    var paid by mutableStateOf("")
    var paymentMode by mutableStateOf(PaymentMode.CASH)
    var paymentDate by mutableLongStateOf(System.currentTimeMillis())
    var message by mutableStateOf<String?>(null)
    var saving by mutableStateOf(false)
    var savedPurchaseId by mutableStateOf<Long?>(null)
    var scanImagePath by mutableStateOf<String?>(null)
    var editingId by mutableStateOf<Long?>(null)
    private var loadedKey: String? = null
    val isEditing: Boolean get() = editingId != null

    fun inputs() = lines.map {
        LineInput(it.qty.toDoubleOrNull() ?: 0.0, Money.parseRupees(it.price) ?: -1L, it.gst, pricesIncludeGst)
    }

    fun previewInputs() = inputs().filter { Validation.validateLineInputs(listOf(it)).isEmpty() }
    fun previewTotals() = InvoiceCalculator.calculate(previewInputs(), true, paidPaise() ?: 0L)
    fun paidPaise(): Long? = if (paid.isBlank()) 0L else Money.parseRupees(paid)

    fun updateSupplier(update: (Supplier) -> Supplier) {
        supplierDraft = update(supplierDraft)
        message = null
    }

    fun selectSupplier(s: Supplier) {
        supplierDraft = s.copy()
    }

    fun addLine() {
        lines.add(LineState())
    }

    fun resetAfterSave() {
        lines.clear()
        lines.add(LineState())
        supplierDraft = Supplier(name = "")
        billNumber = ""
        note = ""
        dueDate = 0
        purchaseDate = System.currentTimeMillis()
        paid = ""
        paymentMode = PaymentMode.CASH
        paymentDate = System.currentTimeMillis()
        scanImagePath = null
        editingId = null
        loadedKey = null
        savedPurchaseId = null
        message = null
    }

    /** Called when the "new purchase" screen opens; keeps an in-progress draft if it is already open. */
    fun startNew() {
        if (loadedKey == "new") return
        resetAfterSave()
        loadedKey = "new"
    }

    fun loadForEdit(purchase: Purchase, supplier: Supplier, purchaseLines: List<PurchaseLine>) {
        val key = "edit:${purchase.id}"
        if (loadedKey == key) return
        editingId = purchase.id
        supplierDraft = supplier.copy()
        billNumber = purchase.billNumber
        note = purchase.note
        purchaseDate = purchase.dateMillis
        dueDate = purchase.dueDateMillis
        paid = rupeesText(purchase.paidPaise)
        paymentMode = PaymentMode.CASH
        paymentDate = purchase.dateMillis
        val rawLineTotal = purchaseLines.sumOf { (it.unitCostPaise * it.qty).roundToLong() }
        pricesIncludeGst = kotlin.math.abs(rawLineTotal - purchase.taxablePaise) > 2
        scanImagePath = purchase.scanImagePath
        lines.clear()
        purchaseLines.forEach { line ->
            val product = Product(
                id = line.productId,
                name = line.productName,
                hsn = line.hsn,
                unit = line.unit,
                defaultPricePaise = line.unitCostPaise,
                defaultGstRate = line.gstRate,
                stockQty = products.value.firstOrNull { it.id == line.productId }?.stockQty ?: 0.0,
            )
            lines.add(LineState(product).apply {
                qty = line.qty.toString().removeSuffix(".0")
                price = rupeesText(line.unitCostPaise)
                gst = line.gstRate
            })
        }
        if (lines.isEmpty()) lines.add(LineState())
        savedPurchaseId = null
        message = null
        loadedKey = key
    }

    fun save() {
        if (saving) return
        val supplier = supplierDraft
        if (supplier.name.isBlank()) {
            message = "Enter the supplier name"
            return
        }
        if (!Validation.phoneOk(supplier.phone)) {
            message = "Enter a valid supplier phone number"
            return
        }
        if (!Validation.gstinFormatOk(supplier.gstin)) {
            message = "Supplier GSTIN format looks invalid"
            return
        }
        val pay = paidPaise() ?: run {
            message = "Enter a valid amount paid"
            return
        }
        val lineErrors = Validation.validateLineInputs(inputs())
        if (lineErrors.isNotEmpty()) {
            message = lineErrors.joinToString("; ")
            return
        }
        if (lines.any { it.product.name.isBlank() }) {
            message = "Enter a product name for every purchase line"
            return
        }
        if (dueDate > 0 && dueDate < purchaseDate) {
            message = "Due date cannot be before the purchase date"
            return
        }
        val draftLines = lines.map { line ->
            val price = Money.parseRupees(line.price) ?: -1L
            DraftLine(
                line.product.copy(defaultPricePaise = price, defaultGstRate = line.gst, stockQty = 0.0),
                line.qty.toDoubleOrNull() ?: 0.0,
                price,
                line.gst,
            )
        }
        viewModelScope.launch {
            saving = true
            try {
                val draft = PurchaseDraft(
                    supplier = supplier,
                    lines = draftLines,
                    paidPaise = pay,
                    dateMillis = purchaseDate,
                    dueDateMillis = dueDate,
                    billNumber = billNumber,
                    note = note,
                    pricesIncludeGst = pricesIncludeGst,
                    paymentMode = paymentMode,
                    paymentDateMillis = paymentDate,
                    scanImagePath = scanImagePath,
                )
                val editing = editingId
                val id = if (editing != null) repo.updatePurchase(editing, draft) else repo.savePurchase(draft)
                savedPurchaseId = id
                message = if (editing != null) "Purchase updated" else "Purchase recorded"
            } catch (e: IllegalArgumentException) {
                message = e.message
            } catch (e: Exception) {
                message = e.message ?: "Something went wrong. Please try again."
            } finally {
                saving = false
            }
        }
    }
}
