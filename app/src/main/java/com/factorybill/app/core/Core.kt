package com.factorybill.app.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLEncoder

/** Money is ALWAYS stored as Long paise. Never use Float/Double for amounts. */
object Money {
    fun parseRupees(text: String): Long? {
        val t = text.trim().replace(",", "")
        if (t.isEmpty()) return null
        return try {
            val v = BigDecimal(t)
            if (v.signum() < 0) null else v.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (e: NumberFormatException) { null }
        catch (e: ArithmeticException) { null }
    }

    /** Indian digit grouping, e.g. 123456789 paise -> ₹12,34,567.89 */
    fun format(paise: Long): String {
        val neg = paise < 0
        val amount = BigDecimal.valueOf(paise).abs().toBigInteger()
        val major = amount.divide(java.math.BigInteger.valueOf(100)).toString()
        val minor = amount.remainder(java.math.BigInteger.valueOf(100)).toString().padStart(2, '0')
        val grouped = if (major.length <= 3) major
        else major.dropLast(3).reversed().chunked(2).joinToString(",").reversed() + "," + major.takeLast(3)
        return (if (neg) "-" else "") + "₹" + grouped + "." + minor
    }
}

object Gst {
    val STANDARD_RATES = listOf(0.0, 0.25, 3.0, 5.0, 12.0, 18.0, 28.0)
}

data class LineInput(
    val qty: Double,
    val unitPricePaise: Long,
    val gstRate: Double,
    val priceIncludesGst: Boolean = false,
)
data class LineResult(val taxablePaise: Long, val gstPaise: Long) {
    val totalPaise get() = Math.addExact(taxablePaise, gstPaise)
}
data class GstSplit(val cgstPaise: Long, val sgstPaise: Long, val igstPaise: Long)
data class InvoiceTotals(
    val lines: List<LineResult>,
    val taxablePaise: Long,
    val gstPaise: Long,
    val totalPaise: Long,
    val split: GstSplit,
    val paymentPaise: Long,
) { val balancePaise get() = totalPaise - paymentPaise }

/**
 * Amounts are rounded to the nearest paise per line using HALF_UP, then summed.
 * Inclusive prices extract GST as gross × rate / (100 + rate). Tax-exclusive prices
 * calculate GST as taxable value × rate / 100. Intra-state tax is split across CGST
 * and SGST; inter-state tax is charged as IGST.
 */
object InvoiceCalculator {
    fun line(l: LineInput): LineResult {
        require(l.qty.isFinite() && l.qty > 0.0) { "Quantity must be a finite value greater than 0" }
        require(l.unitPricePaise >= 0) { "Price cannot be negative" }
        require(l.gstRate.isFinite() && l.gstRate in 0.0..100.0) { "GST rate must be between 0 and 100" }
        val grossOrTaxable = BigDecimal.valueOf(l.qty).multiply(BigDecimal.valueOf(l.unitPricePaise))
            .setScale(0, RoundingMode.HALF_UP)
        val gst = if (l.priceIncludesGst) {
            grossOrTaxable.multiply(BigDecimal.valueOf(l.gstRate))
                .divide(BigDecimal.valueOf(100.0 + l.gstRate), 0, RoundingMode.HALF_UP)
        } else {
            grossOrTaxable.multiply(BigDecimal.valueOf(l.gstRate))
                .divide(BigDecimal(100), 0, RoundingMode.HALF_UP)
        }
        val taxable = if (l.priceIncludesGst) grossOrTaxable - gst else grossOrTaxable
        return try {
            val taxablePaise = taxable.longValueExact()
            val gstPaise = gst.longValueExact()
            Math.addExact(taxablePaise, gstPaise)
            LineResult(taxablePaise, gstPaise)
        } catch (e: ArithmeticException) {
            throw IllegalArgumentException("Line amount exceeds the supported limit")
        }
    }

    fun calculate(lines: List<LineInput>, intraState: Boolean, paymentPaise: Long): InvoiceTotals {
        val results = lines.map(::line)
        val taxable = try { results.fold(0L) { total, line -> Math.addExact(total, line.taxablePaise) } }
        catch (e: ArithmeticException) { throw IllegalArgumentException("Invoice taxable value exceeds the supported limit") }
        val gst = try { results.fold(0L) { total, line -> Math.addExact(total, line.gstPaise) } }
        catch (e: ArithmeticException) { throw IllegalArgumentException("Invoice GST exceeds the supported limit") }
        val total = try { Math.addExact(taxable, gst) }
        catch (e: ArithmeticException) { throw IllegalArgumentException("Invoice total exceeds the supported limit") }
        val split = if (intraState) GstSplit(gst / 2, gst - gst / 2, 0) else GstSplit(0, 0, gst)
        return InvoiceTotals(results, taxable, gst, total, split, paymentPaise)
    }

    /** Stock shortage for a line; stock is allowed to go negative, this is only a warning. */
    fun shortage(stock: Double, qty: Double): Double = (qty - stock).coerceAtLeast(0.0)
}

object Validation {
    private val GSTIN = Regex("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$")
    private val PHONE = Regex("^(\\+?91)?[6-9][0-9]{9}$")
    private val UPI = Regex("^[A-Za-z0-9._\\-]{2,}@[A-Za-z0-9]{2,}$")
    private val INVOICE_NUMBER = Regex("^[A-Za-z0-9][A-Za-z0-9/-]{0,15}$")

    /** Pattern check only. Does NOT verify the GSTIN exists or its checksum. */
    fun gstinFormatOk(s: String) = s.isBlank() || GSTIN.matches(s.trim().uppercase())
    fun phoneOk(s: String) = s.isBlank() || PHONE.matches(s.replace(" ", "").replace("-", ""))
    fun upiOk(s: String) = UPI.matches(s.trim())
    fun invoiceNumberOk(s: String) = s.isBlank() || INVOICE_NUMBER.matches(s.trim())

    /** True/false when both GSTINs have a state code, null when it cannot be derived. */
    fun sameState(a: String, b: String): Boolean? {
        val x = a.trim().take(2); val y = b.trim().take(2)
        return if (x.length == 2 && y.length == 2 && x.all(Char::isDigit) && y.all(Char::isDigit)) x == y else null
    }

    fun validateLineInputs(lines: List<LineInput>): List<String> {
        val e = mutableListOf<String>()
        if (lines.isEmpty()) e += "Add at least one item"
        if (lines.any { !it.qty.isFinite() || it.qty <= 0.0 }) e += "Quantity must be a finite value greater than 0"
        if (lines.any { it.unitPricePaise < 0 }) e += "Price cannot be negative"
        if (lines.any { !it.gstRate.isFinite() || it.gstRate < 0.0 || it.gstRate > 100.0 }) e += "GST rate must be between 0 and 100"
        lines.filter { it.qty.isFinite() && it.qty > 0.0 && it.unitPricePaise >= 0 && it.gstRate.isFinite() && it.gstRate in 0.0..100.0 }
            .forEach { line ->
                try { InvoiceCalculator.line(line) }
                catch (error: IllegalArgumentException) {
                    if ("Line amount exceeds the supported limit" in error.message.orEmpty()) e += "Line amount exceeds the supported limit"
                    else throw error
                }
                catch (error: ArithmeticException) { e += "Line amount exceeds the supported limit" }
            }
        return e
    }

    fun validateLines(lines: List<LineInput>, paymentPaise: Long, totals: InvoiceTotals): List<String> {
        val e = validateLineInputs(lines).toMutableList()
        if (paymentPaise < 0) e += "Payment cannot be negative"
        if (paymentPaise > totals.totalPaise) e += "Payment cannot exceed the total"
        return e
    }
}

enum class SalaryType { SALARY_ADDED, ADVANCE, PAYMENT }
data class SalaryLine(val type: SalaryType, val amountPaise: Long)

object SalaryLedger {
    /** Balance due to the employee = salary added - advances taken - payments made. */
    fun balance(entries: List<SalaryLine>): Long = entries.sumOf {
        if (it.type == SalaryType.SALARY_ADDED) it.amountPaise else -it.amountPaise
    }
}

object Upi {
    fun uri(vpa: String, payeeName: String, amountPaise: Long, note: String): String {
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
        val amt = "${amountPaise / 100}.${(amountPaise % 100).toString().padStart(2, '0')}"
        return "upi://pay?pa=${vpa.trim()}&pn=${enc(payeeName)}&am=$amt&cu=INR&tn=${enc(note)}"
    }
}
