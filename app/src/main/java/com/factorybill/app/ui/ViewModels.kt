package com.factorybill.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
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

private fun <T> Flow<T>.state(scope: kotlinx.coroutines.CoroutineScope, init: T) =
    stateIn(scope, SharingStarted.WhileSubscribed(5000), init)

fun rupeesText(paise: Long) = "${paise / 100}.${(paise % 100).toString().padStart(2, '0')}"

class HomeViewModel(private val repo: Repository) : ViewModel() {
    val sales = repo.salesTotal.state(viewModelScope, 0L)
    val dues = repo.customerDues.state(viewModelScope, 0L)
    val supplierDues = repo.supplierDues.state(viewModelScope, 0L)
    val expenses = repo.expensesTotal.state(viewModelScope, 0L)
    val negativeStock = repo.negativeStockCount.state(viewModelScope, 0)
    val invoices = repo.invoices.state(viewModelScope, emptyList())

    suspend fun pdfFor(ctx: Context, invoiceId: Long, accent: Int): File? = withContext(Dispatchers.IO) {
        val inv = repo.invoice(invoiceId) ?: return@withContext null
        val cust = repo.customer(inv.customerId) ?: return@withContext null
        InvoicePdf.create(ctx, inv, repo.invoiceLines(invoiceId), cust, repo.companyOrDefault(), accent)
    }
}

class LineState(p: Product) {
    var product by mutableStateOf(p)
    var qty by mutableStateOf("1")
    var price by mutableStateOf(rupeesText(p.defaultPricePaise))
    var gst by mutableStateOf(p.defaultGstRate)
}

class InvoiceViewModel(private val repo: Repository) : ViewModel() {
    val customers = repo.customers.state(viewModelScope, emptyList())
    val products = repo.products.state(viewModelScope, emptyList())
    private val company = repo.company.state(viewModelScope, null)

    var customer by mutableStateOf<Customer?>(null)
    var intraState by mutableStateOf(true)
    var pricesIncludeGst by mutableStateOf(false)
    var placeOfSupply by mutableStateOf("")
    val lines = mutableStateListOf<LineState>()
    var payment by mutableStateOf("")
    var message by mutableStateOf<String?>(null)

    fun inputs() = lines.map { LineInput(it.qty.toDoubleOrNull() ?: 0.0, Money.parseRupees(it.price) ?: -1L, it.gst, pricesIncludeGst) }
    fun previewInputs() = inputs().filter { Validation.validateLineInputs(listOf(it)).isEmpty() }
    fun previewTotals() = InvoiceCalculator.calculate(previewInputs(), intraState, paymentPaise() ?: 0L)
    fun paymentPaise(): Long? = if (payment.isBlank()) 0L else Money.parseRupees(payment)
    fun liveStock(l: LineState) = products.value.firstOrNull { it.id == l.product.id }?.stockQty ?: l.product.stockQty

    fun selectCustomer(c: Customer) {
        customer = c
        company.value?.let { Validation.sameState(c.gstin, it.gstin) }?.let { intraState = it }
    }

    private fun guarded(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: IllegalArgumentException) { message = e.message }
    }

    fun addCustomer(c: Customer) = guarded { selectCustomer(c.copy(id = repo.addCustomer(c))) }
    fun addProduct(p: Product) = guarded { lines.add(LineState(p.copy(id = repo.addProduct(p)))) }
    fun addLine(product: Product, qty: String = "1", price: String? = null, gst: Double? = null) {
        lines.add(LineState(product).apply {
            this.qty = qty
            this.price = price ?: rupeesText(product.defaultPricePaise)
            this.gst = gst ?: product.defaultGstRate
        })
    }

    fun issue() {
        val c = customer ?: run { message = "Select or add a customer"; return }
        val pay = paymentPaise() ?: run { message = "Enter a valid payment amount"; return }
        val lineErrors = Validation.validateLineInputs(inputs())
        if (lineErrors.isNotEmpty()) { message = lineErrors.joinToString("; "); return }
        val draftLines = lines.map { DraftLine(it.product, it.qty.toDoubleOrNull() ?: 0.0, Money.parseRupees(it.price) ?: -1L, it.gst) }
        guarded {
            repo.issueInvoice(InvoiceDraft(c, draftLines, pay, intraState, placeOfSupply, pricesIncludeGst))
            lines.clear(); payment = ""; customer = null; placeOfSupply = ""; message = "Invoice issued"
        }
    }
}
