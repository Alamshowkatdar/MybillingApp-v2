package com.factorybill.app.core

import java.math.BigDecimal
import java.math.RoundingMode

data class SuggestedLine(
    val name: String,
    val qty: Double?,
    val unitPricePaise: Long?,
    val gstRate: Double?,
    val rawLine: String,
)

data class OcrSuggestion(
    val customerName: String?,
    val phone: String?,
    val gstin: String?,
    val address: String?,
    val lines: List<SuggestedLine>,
    val paymentPaise: Long?,
    val rawText: String,
)

/**
 * Heuristic parser for OCR text. Output is only a SUGGESTION: the UI must show every field in an
 * editable review screen and must never issue an invoice from it without explicit confirmation.
 * Handwriting will often be misread; treat everything as unverified.
 */
object OcrParser {
    private val GSTIN = Regex("[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]")
    private val PHONE = Regex("(?<!\\d)(?:\\+?91[ -]?)?([6-9]\\d{4}[ -]?\\d{5})(?!\\d)")
    private val NAME_LABEL = Regex("^(?i:bill(?:ed)?\\s*to|to|m/s\\.?|customer|party|name)(?:\\s*[:.\\-]\\s*|\\s+)(.+)$")
    private val NAME_LABEL_ALONE = Regex("^(?i:bill(?:ed)?\\s*to|to|m/s\\.?)\\s*[:.\\-]?$")
    private val PIN = Regex("\\b\\d{6}\\b")
    private val GST_PCT = Regex("(\\d+(?:\\.\\d+)?)\\s*%")
    private val PAYMENT = Regex("(?i)(?:part\\s*payment|payment|paid|received|advance)[^\\d\\n]*([\\d,]+(?:\\.\\d+)?)")
    private val ITEM = Regex(
        "^(?<name>[A-Za-z][A-Za-z0-9 .\\-/()]*?)\\s+(?<qty>\\d+(?:\\.\\d+)?)\\s*(?i:pcs|nos|no|kg|x|×|units?)?" +
            "\\s+(?:@\\s*)?(?<rate>\\d+(?:\\.\\d+)?)(?:\\s+(?<amt>\\d+(?:\\.\\d+)?))?\\s*$"
    )
    private val NOT_ITEM = Regex(
        "^(?i:sub\\s*total|total|grand|gst|cgst|sgst|igst|tax|paid|received|advance|balance|due|amount|" +
            "date|invoice|bill|ph|phone|mob|mobile|gstin|pin|discount|round)\\b"
    )

    fun parse(raw: String): OcrSuggestion {
        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val gstin = GSTIN.find(raw.uppercase())?.value
        val phone = PHONE.find(raw)?.groupValues?.get(1)?.filter(Char::isDigit)
        val payment = lines.firstNotNullOfOrNull { l ->
            if (l.lowercase().startsWith("balance")) null
            else PAYMENT.find(l)?.groupValues?.get(1)?.let(Money::parseRupees)
        }

        var nameIdx = -1
        var name: String? = null
        for ((i, l) in lines.withIndex()) {
            if (NAME_LABEL_ALONE.matches(l) && i + 1 < lines.size) { name = lines[i + 1]; nameIdx = i + 1; break }
            val m = NAME_LABEL.find(l)
            if (m != null) { name = m.groupValues[1].trim(); nameIdx = i; break }
        }

        val items = lines.mapNotNull(::parseItem)
        val itemRaw = items.map { it.rawLine }.toSet()
        val addr = if (nameIdx >= 0) {
            lines.drop(nameIdx + 1).take(3)
                .filter { it !in itemRaw && (it.contains(',') || PIN.containsMatchIn(it) || it.startsWith("address", true)) }
                .map { it.replace(Regex("(?i)^address\\s*[:\\-]?\\s*"), "") }
                .take(2).joinToString(", ").ifBlank { null }
        } else null

        return OcrSuggestion(name, phone, gstin, addr, items, payment, raw)
    }

    private fun parseItem(line: String): SuggestedLine? {
        if (NOT_ITEM.containsMatchIn(line)) return null
        val pct = GST_PCT.find(line)
        val cleaned = if (pct != null) line.replace(pct.value, "").trim() else line
        val m = ITEM.find(cleaned) ?: return null
        val rate = m.groups["rate"]!!.value
        return SuggestedLine(
            name = m.groups["name"]!!.value.trim(),
            qty = m.groups["qty"]!!.value.toDoubleOrNull(),
            unitPricePaise = BigDecimal(rate).multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toLong(),
            gstRate = pct?.groupValues?.get(1)?.toDoubleOrNull(),
            rawLine = line,
        )
    }
}
