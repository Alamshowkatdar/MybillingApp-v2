package com.factorybill.app

import com.factorybill.app.core.*
import org.junit.Assert.*
import org.junit.Test

class CoreTest {
    @Test fun lineGst() {
        val r = InvoiceCalculator.line(LineInput(2.0, 10_000, 18.0))
        assertEquals(20_000, r.taxablePaise); assertEquals(3_600, r.gstPaise); assertEquals(23_600, r.totalPaise)
    }
    @Test fun inclusivePriceExtractsGstWithoutChangingGross() {
        val line = InvoiceCalculator.line(LineInput(1.0, 11_800, 18.0, priceIncludesGst = true))
        assertEquals(10_000, line.taxablePaise)
        assertEquals(1_800, line.gstPaise)
        assertEquals(11_800, line.totalPaise)
    }
    @Test fun inclusivePriceRoundsTaxToPaise() {
        val line = InvoiceCalculator.line(LineInput(1.0, 100, 5.0, priceIncludesGst = true))
        assertEquals(95, line.taxablePaise)
        assertEquals(5, line.gstPaise)
    }
    @Test fun roundsHalfUp() {
        assertEquals(500, InvoiceCalculator.line(LineInput(3.0, 3_333, 5.0)).gstPaise) // 499.95 -> 500
        assertEquals(1, InvoiceCalculator.line(LineInput(1.0, 1, 50.0)).gstPaise)       // 0.5 -> 1
    }
    @Test fun intraStateSplitOddPaise() {
        val t = InvoiceCalculator.calculate(listOf(LineInput(1.0, 3_340, 18.0)), true, 0)
        assertEquals(601, t.gstPaise); assertEquals(300, t.split.cgstPaise); assertEquals(301, t.split.sgstPaise); assertEquals(0, t.split.igstPaise)
    }
    @Test fun interStateIsAllIgst() {
        val t = InvoiceCalculator.calculate(listOf(LineInput(2.0, 10_000, 18.0)), false, 0)
        assertEquals(3_600, t.split.igstPaise); assertEquals(0, t.split.cgstPaise)
    }
    @Test fun balanceDue() {
        val t = InvoiceCalculator.calculate(listOf(LineInput(2.0, 10_000, 18.0)), true, 10_000)
        assertEquals(23_600, t.totalPaise); assertEquals(13_600, t.balancePaise)
    }
    @Test fun multipleLinesSum() {
        val t = InvoiceCalculator.calculate(listOf(LineInput(1.0, 10_000, 18.0), LineInput(4.0, 2_500, 5.0)), true, 0)
        assertEquals(20_000, t.taxablePaise); assertEquals(1_800 + 500, t.gstPaise)
    }
    @Test fun shortageWarning() {
        assertEquals(15.0, InvoiceCalculator.shortage(10.0, 25.0), 0.0)
        assertEquals(0.0, InvoiceCalculator.shortage(30.0, 25.0), 0.0)
    }
    @Test fun validation() {
        val ok = listOf(LineInput(1.0, 100, 18.0))
        val tot = InvoiceCalculator.calculate(ok, true, 0)
        assertTrue(Validation.validateLines(ok, 0, tot).isEmpty())
        assertTrue(Validation.validateLines(ok, tot.totalPaise + 1, tot).isNotEmpty())
        assertTrue(Validation.validateLines(emptyList(), 0, InvoiceCalculator.calculate(emptyList(), true, 0)).isNotEmpty())
        val zeroQty = listOf(LineInput(0.0, 100, 18.0))
        assertTrue(Validation.validateLineInputs(zeroQty).isNotEmpty())
        assertTrue(Validation.validateLineInputs(listOf(LineInput(Double.NaN, 100, 18.0))).isNotEmpty())
        assertTrue(Validation.validateLineInputs(listOf(LineInput(1.0, 100, Double.POSITIVE_INFINITY))).isNotEmpty())
    }
    @Test fun gstinPhoneUpi() {
        assertTrue(Validation.gstinFormatOk("27ABCDE1234F1Z5")); assertTrue(Validation.gstinFormatOk(""))
        assertFalse(Validation.gstinFormatOk("27ABCDE1234F1X5"))
        assertTrue(Validation.phoneOk("98123 45678")); assertFalse(Validation.phoneOk("12345"))
        assertTrue(Validation.upiOk("shop@okbank")); assertFalse(Validation.upiOk("no-at-sign"))
        assertTrue(Validation.invoiceNumberOk("")); assertTrue(Validation.invoiceNumberOk(" AB-102/26 "))
        assertFalse(Validation.invoiceNumberOk("INV 102")); assertFalse(Validation.invoiceNumberOk("INV-12345678901234"))
        assertEquals(true, Validation.sameState("27ABCDE1234F1Z5", "27AAAAA0000A1Z5"))
        assertEquals(false, Validation.sameState("29ABCDE1234F1Z5", "27AAAAA0000A1Z5"))
        assertNull(Validation.sameState("", "27AAAAA0000A1Z5"))
    }
    @Test fun salaryBalance() {
        val e = listOf(SalaryLine(SalaryType.SALARY_ADDED, 2_000_000), SalaryLine(SalaryType.ADVANCE, 300_000), SalaryLine(SalaryType.PAYMENT, 1_000_000))
        assertEquals(700_000, SalaryLedger.balance(e))
    }
    @Test fun moneyFormatAndParse() {
        assertEquals("₹12,34,567.89", Money.format(123_456_789)); assertEquals("₹0.05", Money.format(5)); assertEquals("-₹2,500.50", Money.format(-250_050))
        assertEquals(12_550L, Money.parseRupees("125.5")); assertNull(Money.parseRupees("abc")); assertNull(Money.parseRupees("-5"))
        assertNull(Money.parseRupees("92233720368547758.08"))
        assertTrue(Money.format(Long.MIN_VALUE).startsWith("-₹"))
    }
    @Test fun upiUri() {
        val u = Upi.uri("shop@okbank", "Acme Works", 23_600, "INV-0001")
        assertTrue(u.startsWith("upi://pay?pa=shop@okbank")); assertTrue(u.contains("am=236.00")); assertTrue(u.contains("pn=Acme%20Works"))
    }
}
