package com.factorybill.app.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.factorybill.app.core.Money
import com.factorybill.app.core.Upi
import com.factorybill.app.core.Validation
import com.factorybill.app.data.CompanyProfile
import com.factorybill.app.data.Customer
import com.factorybill.app.data.Invoice
import com.factorybill.app.data.InvoiceLine
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object InvoicePdf {
    private const val W = 595
    private const val H = 842
    private const val M = 36f
    private const val RIGHT = W - M
    private fun p(size: Float, bold: Boolean = false, color: Int = Color.BLACK, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color; textAlign = align
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

    private class Pager(val doc: PdfDocument) {
        var n = 0
        lateinit var page: PdfDocument.Page
        val c: Canvas get() = page.canvas
        var y = M
        fun newPage() {
            if (n > 0) { footer(); doc.finishPage(page) }
            n++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, n).create()); y = M
        }
        fun need(h: Float) { if (y + h > H - M - 24) newPage() }
        fun footer() { c.drawText("FactoryBill  ·  Page $n", RIGHT, H - 24f, p(8f, color = Color.GRAY, align = Paint.Align.RIGHT)) }
        fun finish() { footer(); doc.finishPage(page) }
    }

    fun qr(content: String, size: Int = 220): Bitmap {
        val m = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) for (y in 0 until size) bmp.setPixel(x, y, if (m[x, y]) Color.BLACK else Color.WHITE)
        return bmp
    }

    fun create(ctx: Context, inv: Invoice, lines: List<InvoiceLine>, cust: Customer, co: CompanyProfile, accent: Int): File {
        val doc = PdfDocument()
        val pg = Pager(doc).also { it.newPage() }
        val compact = co.template == "compact"
        val modern = co.template == "modern"
        val s = if (compact) 0.85f else 1f
        val dateText = SimpleDateFormat("dd MMM yyyy", Locale("en", "IN")).format(Date(inv.dateMillis))

        // Header
        val headH = 70f * s
        val onBand = if (modern) Color.WHITE else Color.BLACK
        if (modern) pg.c.drawRect(0f, 0f, W.toFloat(), M + headH, Paint().apply { color = accent })
        pg.y = M
        pg.c.drawText(co.name.ifBlank { "Your company name" }, M, pg.y + 16 * s, p(18f * s, true, onBand))
        pg.c.drawText(co.address, M, pg.y + 32 * s, p(9f * s, color = onBand))
        pg.c.drawText(listOf("GSTIN: ${co.gstin}", co.phone, co.email).filter { it.isNotBlank() && it != "GSTIN: " }.joinToString("  ·  "),
            M, pg.y + 45 * s, p(9f * s, color = onBand))
        pg.c.drawText("TAX INVOICE", RIGHT, pg.y + 16 * s, p(14f * s, true, onBand, Paint.Align.RIGHT))
        pg.c.drawText("${inv.number}   $dateText", RIGHT, pg.y + 32 * s, p(9f * s, color = onBand, align = Paint.Align.RIGHT))
        if (inv.status == "VOID") pg.c.drawText("VOID: ${inv.voidReason ?: ""}".take(60), RIGHT, pg.y + 45 * s, p(9f * s, true, Color.RED, Paint.Align.RIGHT))
        pg.y = M + headH + 12
        if (!modern) { pg.c.drawLine(M, pg.y - 8, RIGHT, pg.y - 8, Paint().apply { strokeWidth = 1.5f }); }

        // Parties
        val small = p(9.5f * s)
        pg.c.drawText("Bill to", M, pg.y, p(9.5f * s, true))
        pg.c.drawText("Ship to", W / 2f, pg.y, p(9.5f * s, true))
        val bill = listOf(
            cust.name,
            cust.billingAddress,
            if (cust.gstin.isNotBlank()) "GSTIN: ${cust.gstin}" else "",
            inv.placeOfSupply.takeIf(String::isNotBlank)?.let { "Place of supply: $it" }.orEmpty(),
        )
        val ship = listOf(cust.shippingAddress.ifBlank { cust.billingAddress }, if (cust.phone.isNotBlank()) "Ph: ${cust.phone}" else "")
        bill.filter { it.isNotBlank() }.forEachIndexed { i, t -> pg.c.drawText(t.take(55), M, pg.y + 13 * (i + 1) * s, small) }
        ship.filter { it.isNotBlank() }.forEachIndexed { i, t -> pg.c.drawText(t.take(55), W / 2f, pg.y + 13 * (i + 1) * s, small) }
        pg.y += 13 * (maxOf(bill.count { it.isNotBlank() }, ship.count { it.isNotBlank() }) + 1) * s + 10

        // Table
        val colHsn = 206f; val colQty = 278f; val colRate = 347f; val colTax = 420f; val colGst = 486f
        fun header() {
            pg.c.drawRect(M, pg.y - 11, RIGHT, pg.y + 5, Paint().apply { color = if (modern) accent else Color.LTGRAY; alpha = 60 })
            val h = p(9f * s, true, align = Paint.Align.RIGHT)
            pg.c.drawText("Item", M + 3, pg.y, p(9f * s, true))
            pg.c.drawText("HSN", colHsn, pg.y, h); pg.c.drawText("Qty", colQty, pg.y, h)
            pg.c.drawText("Rate", colRate, pg.y, h); pg.c.drawText("Taxable", colTax, pg.y, h)
            pg.c.drawText("GST %", colGst, pg.y, h); pg.c.drawText("Amount", RIGHT, pg.y, h)
            pg.y += 16 * s
        }
        header()
        val cell = p(9f * s); val cellR = p(9f * s, align = Paint.Align.RIGHT)
        lines.forEach { l ->
            if (pg.y + 16 > H - M - 24) { pg.newPage(); pg.y = M + 10; header() }
            pg.c.drawText(l.productName.take(22), M + 3, pg.y, cell)
            pg.c.drawText(l.hsn.take(12), colHsn, pg.y, cellR)
            pg.c.drawText(l.qty.toString().removeSuffix(".0"), colQty, pg.y, cellR)
            pg.c.drawText(Money.format(l.unitPricePaise), colRate, pg.y, cellR)
            pg.c.drawText(Money.format(l.taxablePaise), colTax, pg.y, cellR)
            pg.c.drawText("${l.gstRate.toString().removeSuffix(".0")}%", colGst, pg.y, cellR)
            pg.c.drawText(Money.format(l.totalPaise), RIGHT, pg.y, cellR)
            pg.y += 15 * s
        }

        // Totals
        pg.need(120f); pg.y += 8
        fun row(label: String, value: Long, bold: Boolean = false) {
            pg.c.drawText(label, 380f, pg.y, p(10f * s, bold)); pg.c.drawText(Money.format(value), RIGHT, pg.y, p(10f * s, bold, align = Paint.Align.RIGHT)); pg.y += 15 * s
        }
        row("Taxable value", inv.taxablePaise)
        if (inv.intraState) { row("CGST", inv.gstPaise / 2); row("SGST", inv.gstPaise - inv.gstPaise / 2) } else row("IGST", inv.gstPaise)
        row("Total", inv.totalPaise, true); row("Received", inv.paymentPaise)
        val balance = inv.totalPaise - inv.paymentPaise
        row("Balance due", balance, true)

        // Bank details + UPI QR (QR only when money is still due and the UPI ID looks valid)
        pg.need(130f); pg.y += 12
        val top = pg.y
        pg.c.drawText("Bank details", M, pg.y, p(10f * s, true))
        listOf(co.bankName, "A/c: ${co.accountNumber}", "IFSC: ${co.ifsc}", "UPI: ${co.upiId}").forEachIndexed { i, t -> pg.c.drawText(t, M, top + 14 * (i + 1) * s, small) }
        if (balance > 0 && Validation.upiOk(co.upiId)) {
            val bmp = qr(Upi.uri(co.upiId, co.name, balance, inv.number))
            pg.c.drawBitmap(Bitmap.createScaledBitmap(bmp, 96, 96, false), RIGHT - 96, top - 8, null)
            pg.c.drawText("Scan to pay ${Money.format(balance)}", RIGHT - 48, top + 100, p(8f, align = Paint.Align.CENTER))
        }
        pg.finish()

        val file = File(File(ctx.cacheDir, "invoices").apply { mkdirs() }, "${inv.number}.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }
}
