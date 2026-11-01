package com.factorybill.app.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.factorybill.app.core.Money
import com.factorybill.app.core.Upi
import com.factorybill.app.core.Validation
import com.factorybill.app.data.CompanyProfile
import com.factorybill.app.data.CompanyLogoStore
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
    private const val A4_WIDTH_POINTS = 595
    private const val A4_HEIGHT_POINTS = 842
    private const val M = 36f
    private const val RIGHT = A4_WIDTH_POINTS - M
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
            page = doc.startPage(PdfDocument.PageInfo.Builder(A4_WIDTH_POINTS, A4_HEIGHT_POINTS, n).create()); y = M
        }
        fun need(h: Float) { if (y + h > A4_HEIGHT_POINTS - M - 24) newPage() }
        fun footer() { c.drawText("FactoryBill  ·  A4  ·  Page $n", RIGHT, A4_HEIGHT_POINTS - 24f, p(8f, color = Color.GRAY, align = Paint.Align.RIGHT)) }
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
        val headH = 78f * s
        val onBand = if (modern) Color.WHITE else Color.BLACK
        if (modern) pg.c.drawRect(0f, 0f, A4_WIDTH_POINTS.toFloat(), M + headH, Paint().apply { color = accent })
        pg.y = M
        val logo = co.logoPath?.let { CompanyLogoStore.decode(it, 256) }
        val companyLeft = if (logo == null) M else M + 50f
        if (logo != null) {
            val side = 42f * s
            val scale = minOf(side / logo.width, side / logo.height)
            val width = logo.width * scale
            val height = logo.height * scale
            val logoRect = RectF(
                M + (side - width) / 2f,
                pg.y + (side - height) / 2f,
                M + (side + width) / 2f,
                pg.y + (side + height) / 2f,
            )
            pg.c.drawBitmap(logo, null, logoRect, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        pg.c.drawText(co.name.ifBlank { "Your company name" }.take(30), companyLeft, pg.y + 16 * s, p(17f * s, true, onBand))
        pg.c.drawText(co.address, companyLeft, pg.y + 32 * s, p(9f * s, color = onBand))
        pg.c.drawText(listOf("GSTIN: ${co.gstin}", co.phone, co.email).filter { it.isNotBlank() && it != "GSTIN: " }.joinToString("  ·  "),
            companyLeft, pg.y + 45 * s, p(9f * s, color = onBand))
        pg.c.drawText("TAX INVOICE", RIGHT, pg.y + 13 * s, p(9f * s, true, onBand, Paint.Align.RIGHT))
        pg.c.drawText("INVOICE NO.", RIGHT, pg.y + 27 * s, p(7.5f * s, true, onBand, Paint.Align.RIGHT))
        pg.c.drawText(inv.number, RIGHT, pg.y + 44 * s, p(13f * s, true, onBand, Paint.Align.RIGHT))
        pg.c.drawText("DATE  ·  $dateText", RIGHT, pg.y + 59 * s, p(8f * s, color = onBand, align = Paint.Align.RIGHT))
        if (inv.status == "VOID") pg.c.drawText("VOID: ${inv.voidReason ?: ""}".take(60), RIGHT, pg.y + 73 * s, p(7f * s, true, if (modern) Color.WHITE else Color.RED, Paint.Align.RIGHT))
        logo?.recycle()
        pg.y = M + headH + 12
        if (!modern) { pg.c.drawLine(M, pg.y - 8, RIGHT, pg.y - 8, Paint().apply { strokeWidth = 1.5f }); }

        // Parties
        val small = p(9.5f * s)
        pg.c.drawText("Bill to", M, pg.y, p(9.5f * s, true))
        pg.c.drawText("Ship to", A4_WIDTH_POINTS / 2f, pg.y, p(9.5f * s, true))
        val bill = listOf(
            cust.name,
            cust.billingAddress,
            if (cust.gstin.isNotBlank()) "GSTIN: ${cust.gstin}" else "",
            inv.placeOfSupply.takeIf(String::isNotBlank)?.let { "Place of supply: $it" }.orEmpty(),
        )
        val ship = listOf(cust.shippingAddress.ifBlank { cust.billingAddress }, if (cust.phone.isNotBlank()) "Ph: ${cust.phone}" else "")
        bill.filter { it.isNotBlank() }.forEachIndexed { i, t -> pg.c.drawText(t.take(55), M, pg.y + 13 * (i + 1) * s, small) }
        ship.filter { it.isNotBlank() }.forEachIndexed { i, t -> pg.c.drawText(t.take(55), A4_WIDTH_POINTS / 2f, pg.y + 13 * (i + 1) * s, small) }
        pg.y += 13 * (maxOf(bill.count { it.isNotBlank() }, ship.count { it.isNotBlank() }) + 1) * s + 10

        // Table
        val colHsn = 206f; val colQty = 278f; val colRate = 347f; val colTax = 420f; val colGst = 486f
        fun header() {
            pg.c.drawRect(
                M, pg.y - 11, RIGHT, pg.y + 5,
                Paint().apply { color = if (modern) accent else Color.rgb(232, 237, 242) },
            )
            val headerText = if (modern) Color.WHITE else Color.BLACK
            val h = p(9f * s, true, headerText, Paint.Align.RIGHT)
            pg.c.drawText("Item", M + 3, pg.y, p(9f * s, true, headerText))
            pg.c.drawText("HSN", colHsn, pg.y, h); pg.c.drawText("Qty", colQty, pg.y, h)
            pg.c.drawText("Rate", colRate, pg.y, h); pg.c.drawText("Taxable", colTax, pg.y, h)
            pg.c.drawText("GST %", colGst, pg.y, h); pg.c.drawText("Amount", RIGHT, pg.y, h)
            pg.y += 16 * s
        }
        header()
        val cell = p(9f * s); val cellR = p(9f * s, align = Paint.Align.RIGHT)
        lines.forEachIndexed { index, l ->
            if (pg.y + 16 > A4_HEIGHT_POINTS - M - 24) { pg.newPage(); pg.y = M + 10; header() }
            if (!modern && index % 2 == 1) {
                pg.c.drawRect(M, pg.y - 10, RIGHT, pg.y + 4, Paint().apply { color = Color.rgb(248, 250, 252) })
            }
            pg.c.drawText(l.productName.take(22), M + 3, pg.y, cell)
            pg.c.drawText(l.hsn.take(12), colHsn, pg.y, cellR)
            pg.c.drawText("${l.qty.toString().removeSuffix(".0")} ${l.unit}".trim(), colQty, pg.y, cellR)
            pg.c.drawText(Money.format(l.unitPricePaise), colRate, pg.y, cellR)
            pg.c.drawText(Money.format(l.taxablePaise), colTax, pg.y, cellR)
            pg.c.drawText("${l.gstRate.toString().removeSuffix(".0")}%", colGst, pg.y, cellR)
            pg.c.drawText(Money.format(l.totalPaise), RIGHT, pg.y, cellR)
            pg.y += 15 * s
        }

        // Totals
        pg.need(128f); pg.y += 10
        pg.c.drawRoundRect(
            RectF(362f, pg.y - 12, RIGHT, pg.y + 77 * s),
            8f,
            8f,
            Paint().apply { color = if (modern) accent else Color.rgb(244, 247, 250) },
        )
        fun row(label: String, value: Long, bold: Boolean = false) {
            val textColor = if (modern) Color.WHITE else Color.BLACK
            pg.c.drawText(label, 378f, pg.y, p(10f * s, bold, textColor)); pg.c.drawText(Money.format(value), RIGHT - 8f, pg.y, p(10f * s, bold, textColor, align = Paint.Align.RIGHT)); pg.y += 15 * s
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
