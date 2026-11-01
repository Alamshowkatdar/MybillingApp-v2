package com.factorybill.app

import com.factorybill.app.core.OcrParser
import org.junit.Assert.*
import org.junit.Test

class OcrParserTest {
    private val sample = """
        Bill To: Kaveri Engineering
        14 Peenya Ind. Area, Bengaluru 560058
        Ph 98123 45678
        Steel bracket 40mm 60 120 18%
        Hex bolt M10 200 8 1600
        Powder coat 8 950
        Date 12 10 2026
        Total 8760
        Advance paid 5000
    """.trimIndent()

    @Test fun extractsCustomerPhoneAddressPayment() {
        val s = OcrParser.parse(sample)
        assertEquals("Kaveri Engineering", s.customerName)
        assertEquals("9812345678", s.phone)
        assertTrue(s.address!!.contains("Peenya"))
        assertEquals(500_000L, s.paymentPaise)
    }
    @Test fun extractsItemsAndIgnoresTotalsAndDates() {
        val s = OcrParser.parse(sample)
        assertEquals(3, s.lines.size)
        assertEquals("Steel bracket 40mm", s.lines[0].name); assertEquals(60.0, s.lines[0].qty!!, 0.0)
        assertEquals(12_000L, s.lines[0].unitPricePaise); assertEquals(18.0, s.lines[0].gstRate!!, 0.0)
        assertEquals(200.0, s.lines[1].qty!!, 0.0); assertEquals(800L, s.lines[1].unitPricePaise)
        assertNull(s.lines[2].gstRate)
    }
    @Test fun emptyOrNoiseGivesNothingConfident() {
        val s = OcrParser.parse("   \n ~~ \n")
        assertNull(s.customerName); assertTrue(s.lines.isEmpty()); assertNull(s.paymentPaise)
    }
}
