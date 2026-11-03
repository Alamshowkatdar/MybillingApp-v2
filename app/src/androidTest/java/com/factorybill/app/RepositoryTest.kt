package com.factorybill.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.factorybill.app.core.SalaryType
import com.factorybill.app.data.AppDatabase
import com.factorybill.app.data.Customer
import com.factorybill.app.data.DraftLine
import com.factorybill.app.data.Employee
import com.factorybill.app.data.Expense
import com.factorybill.app.data.InvoiceDraft
import com.factorybill.app.data.PaymentMode
import com.factorybill.app.data.Product
import com.factorybill.app.data.PurchaseDraft
import com.factorybill.app.data.Repository
import com.factorybill.app.data.SalaryEntry
import com.factorybill.app.data.Supplier
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** End-to-end checks for the money and stock invariants that the UI relies on. */
@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: Repository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = Repository(db)
    }

    @After fun tearDown() = db.close()

    private fun line(qty: Double, price: Long, product: Product? = null) = DraftLine(
        product = product ?: Product(name = "Widget", defaultPricePaise = price, defaultGstRate = 18.0),
        qty = qty,
        unitPricePaise = price,
        gstRate = 18.0,
    )

    @Test fun invoicePaymentAndVoidKeepMoneyAndStockConsistent() = runBlocking {
        val productId = repo.addProduct(Product(name = "Widget", defaultPricePaise = 10000, defaultGstRate = 18.0, stockQty = 10.0))
        val product = db.dao().product(productId)!!

        val invoiceId = repo.issueInvoice(
            InvoiceDraft(
                customer = Customer(name = "Acme"),
                lines = listOf(line(2.0, 10000, product)),
                paymentPaise = 5000,
                intraState = true,
                paymentMode = PaymentMode.UPI,
            )
        )
        val invoice = db.dao().invoice(invoiceId)!!
        assertEquals(20000, invoice.taxablePaise)
        assertEquals(3600, invoice.gstPaise)
        assertEquals(23600, invoice.totalPaise)
        assertEquals(5000, invoice.paymentPaise)
        assertEquals("INV-0001", invoice.number)
        assertEquals(8.0, db.dao().product(productId)!!.stockQty, 0.0001)
        assertEquals(1, db.dao().invoicePaymentsOnce(invoiceId).size)
        assertEquals("UPI", db.dao().invoicePaymentsOnce(invoiceId).first().mode)

        repo.recordInvoicePayment(invoiceId, 18600, mode = PaymentMode.CASH, note = "Balance")
        val settled = db.dao().invoice(invoiceId)!!
        assertEquals(settled.totalPaise, settled.paymentPaise)
        assertEquals(2, db.dao().invoicePaymentsOnce(invoiceId).size)

        try {
            repo.recordInvoicePayment(invoiceId, 1)
            fail("Over-payment must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("balance", ignoreCase = true))
        }

        repo.voidInvoice(invoiceId, "Returned by customer")
        assertEquals("VOID", db.dao().invoice(invoiceId)!!.status)
        assertEquals(10.0, db.dao().product(productId)!!.stockQty, 0.0001)
        try {
            repo.recordInvoicePayment(invoiceId, 100)
            fail("Void invoices must not accept payments")
        } catch (expected: IllegalArgumentException) {
            assertNotNull(expected.message)
        }
    }

    @Test fun duplicateInvoiceNumberIsRejected() = runBlocking {
        val draft = InvoiceDraft(
            customer = Customer(name = "Acme"),
            lines = listOf(line(1.0, 10000)),
            paymentPaise = 0,
            intraState = true,
            invoiceNumber = "SALE/42",
        )
        repo.issueInvoice(draft)
        try {
            repo.issueInvoice(draft)
            fail("Duplicate invoice numbers must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("already in use"))
        }
    }

    @Test fun purchaseVoidReversesReceivedStock() = runBlocking {
        val productId = repo.addProduct(Product(name = "Steel", defaultPricePaise = 8000, defaultGstRate = 18.0, stockQty = 8.0))
        val product = db.dao().product(productId)!!
        val purchaseId = repo.savePurchase(
            PurchaseDraft(
                supplier = Supplier(name = "Supplier Co"),
                lines = listOf(line(5.0, 8000, product)),
                paidPaise = 10000,
                billNumber = "BILL-7",
            )
        )
        val purchase = db.dao().purchase(purchaseId)!!
        assertEquals(40000, purchase.taxablePaise)
        assertEquals(47200, purchase.totalPaise)
        assertEquals(10000, purchase.paidPaise)
        assertEquals("BILL-7", purchase.billNumber)
        assertEquals("PUR-0001", purchase.number)
        assertEquals(13.0, db.dao().product(productId)!!.stockQty, 0.0001)
        assertEquals(1, db.dao().purchasePaymentsOnce(purchaseId).size)

        repo.voidPurchase(purchaseId, "Wrong bill")
        assertEquals("VOID", db.dao().purchase(purchaseId)!!.status)
        assertEquals(8.0, db.dao().product(productId)!!.stockQty, 0.0001)
    }

    @Test fun salaryMonthCanOnlyBeAccruedOnce() = runBlocking {
        val employeeId = repo.addEmployee(
            Employee(name = "Riya", monthlySalaryPaise = 3000000, startDateMillis = System.currentTimeMillis())
        )
        repo.recordSalary(employeeId, SalaryType.SALARY_ADDED, 3000000, period = "2026-11")
        try {
            repo.recordSalary(employeeId, SalaryType.SALARY_ADDED, 3000000, period = "2026-11")
            fail("The same salary month must not be accrued twice")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("already accrued"))
        }
        repo.recordSalary(employeeId, SalaryType.ADVANCE, 500000, dateMillis = System.currentTimeMillis())
        repo.recordSalary(employeeId, SalaryType.PAYMENT, 1000000, dateMillis = System.currentTimeMillis())
        val balance = db.dao().salaryBalance(employeeId).first()
        assertEquals(1500000, balance)
    }

    @Test fun expenseValidationRejectsBadAmounts() = runBlocking {
        try {
            repo.addExpense(Expense(category = "Rent", dateMillis = System.currentTimeMillis(), amountPaise = 0, payee = ""))
            fail("Zero expenses must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("greater than 0"))
        }
        val id = repo.addExpense(
            Expense(category = "Rent", dateMillis = System.currentTimeMillis(), amountPaise = 150000, payee = "", mode = "UPI")
        )
        assertTrue(id > 0)
    }

    @Test fun salaryEntryCanBeEditedAndDeleted() = runBlocking {
        val employeeId = repo.addEmployee(
            Employee(name = "Amit", monthlySalaryPaise = 2000000, startDateMillis = System.currentTimeMillis())
        )
        val advanceId = repo.addSalaryEntry(
            SalaryEntry(
                employeeId = employeeId,
                type = SalaryType.ADVANCE.name,
                amountPaise = 400000,
                dateMillis = 1000L,
            )
        )
        repo.recordSalary(employeeId, SalaryType.SALARY_ADDED, 2000000, period = "2026-10")
        assertEquals(1600000, db.dao().salaryBalance(employeeId).first())

        val entry = db.dao().salaryEntry(advanceId)!!
        repo.updateSalaryEntry(entry.copy(amountPaise = 200000, note = "Updated"))
        assertEquals(1800000, db.dao().salaryBalance(employeeId).first())
        assertEquals("Updated", db.dao().salaryEntry(advanceId)!!.note)

        // Editing an accrual onto another already-accrued month is rejected.
        repo.recordSalary(employeeId, SalaryType.SALARY_ADDED, 2000000, period = "2026-11")
        val november = db.dao().salaryEntries(employeeId).first().first { it.period == "2026-11" }
        try {
            repo.updateSalaryEntry(november.copy(period = "2026-10"))
            fail("Duplicate accrual months must be rejected when editing")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("already accrued"))
        }

        // Deleting an entry recalculates the ledger balance.
        repo.deleteSalaryEntry(november.id)
        assertEquals(1800000, db.dao().salaryBalance(employeeId).first())
        assertEquals(
            1,
            db.dao().salaryEntries(employeeId).first().count { it.type == SalaryType.SALARY_ADDED.name },
        )
    }

    @Test fun invoiceCanBeEditedAndDeleted() = runBlocking {
        val productId = repo.addProduct(Product(name = "Widget", defaultPricePaise = 10000, defaultGstRate = 18.0, stockQty = 10.0))
        val product = db.dao().product(productId)!!
        val invoiceId = repo.issueInvoice(
            InvoiceDraft(
                customer = Customer(name = "Acme"),
                lines = listOf(line(2.0, 10000, product)),
                paymentPaise = 5000,
                intraState = true,
                paymentMode = PaymentMode.UPI,
            )
        )
        val original = db.dao().invoice(invoiceId)!!
        assertEquals(8.0, db.dao().product(productId)!!.stockQty, 0.0001)

        repo.updateInvoice(
            invoiceId,
            InvoiceDraft(
                customer = Customer(id = original.customerId, name = "Acme"),
                lines = listOf(line(3.0, 12000, product)),
                paymentPaise = 5000,
                intraState = true,
                invoiceNumber = "",
                scanImagePath = "/data/bill.jpg",
            ),
        )
        val edited = db.dao().invoice(invoiceId)!!
        assertEquals(original.number, edited.number)
        assertEquals(5000, edited.paymentPaise)
        assertEquals("/data/bill.jpg", edited.scanImagePath)
        assertEquals(36000, edited.taxablePaise)
        assertEquals(42480, edited.totalPaise)
        assertEquals(1, db.dao().invoiceLines(invoiceId).size)
        assertEquals(7.0, db.dao().product(productId)!!.stockQty, 0.0001)

        // A new total below the amount already paid must be rejected.
        try {
            repo.updateInvoice(
                invoiceId,
                InvoiceDraft(
                    customer = Customer(id = original.customerId, name = "Acme"),
                    lines = listOf(line(0.1, 1000, product)),
                    paymentPaise = 5000,
                    intraState = true,
                ),
            )
            fail("Editing below the paid amount must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("already paid", ignoreCase = true))
        }

        repo.deleteInvoice(invoiceId)
        assertEquals(null, db.dao().invoice(invoiceId))
        assertEquals(0, db.dao().invoiceLines(invoiceId).size)
        assertEquals(0, db.dao().invoicePaymentsOnce(invoiceId).size)
        assertEquals(10.0, db.dao().product(productId)!!.stockQty, 0.0001)
    }

    @Test fun purchaseCanBeEditedAndDeleted() = runBlocking {
        val productId = repo.addProduct(Product(name = "Steel", defaultPricePaise = 8000, defaultGstRate = 18.0, stockQty = 8.0))
        val product = db.dao().product(productId)!!
        val purchaseId = repo.savePurchase(
            PurchaseDraft(
                supplier = Supplier(name = "Supplier Co"),
                lines = listOf(line(5.0, 8000, product)),
                paidPaise = 10000,
                billNumber = "BILL-7",
            )
        )
        val original = db.dao().purchase(purchaseId)!!
        assertEquals(13.0, db.dao().product(productId)!!.stockQty, 0.0001)

        repo.updatePurchase(
            purchaseId,
            PurchaseDraft(
                supplier = Supplier(id = original.supplierId, name = "Supplier Co"),
                lines = listOf(line(2.0, 9000, product)),
                paidPaise = 10000,
                billNumber = "BILL-8",
                scanImagePath = "/data/purchase.jpg",
            ),
        )
        val edited = db.dao().purchase(purchaseId)!!
        assertEquals("BILL-8", edited.billNumber)
        assertEquals("/data/purchase.jpg", edited.scanImagePath)
        assertEquals(18000, edited.taxablePaise)
        assertEquals(21240, edited.totalPaise)
        assertEquals(10000, edited.paidPaise)
        assertEquals(1, db.dao().purchaseLines(purchaseId).size)
        assertEquals(10.0, db.dao().product(productId)!!.stockQty, 0.0001)

        repo.deletePurchase(purchaseId)
        assertEquals(null, db.dao().purchase(purchaseId))
        assertEquals(0, db.dao().purchaseLines(purchaseId).size)
        assertEquals(0, db.dao().purchasePaymentsOnce(purchaseId).size)
        assertEquals(8.0, db.dao().product(productId)!!.stockQty, 0.0001)
    }

    @Test fun deletingEmployeeRemovesLedger() = runBlocking {
        val employeeId = repo.addEmployee(
            Employee(name = "Noor", monthlySalaryPaise = 1500000, startDateMillis = System.currentTimeMillis())
        )
        repo.recordSalary(employeeId, SalaryType.SALARY_ADDED, 1500000, period = "2026-09")
        repo.recordSalary(employeeId, SalaryType.ADVANCE, 100000, dateMillis = System.currentTimeMillis())
        assertEquals(2, db.dao().salaryEntries(employeeId).first().size)
        val removed = repo.deleteEmployee(employeeId)
        assertEquals(2, removed)
        assertEquals(0, db.dao().salaryEntries(employeeId).first().size)
        assertEquals(null, db.dao().employee(employeeId))
    }
}
