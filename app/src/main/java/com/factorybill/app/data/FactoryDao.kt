package com.factorybill.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FactoryDao {
    // Customers / products / suppliers
    @Insert suspend fun insertCustomer(c: Customer): Long
    @Update suspend fun updateCustomer(c: Customer)
    @Query("SELECT * FROM customers ORDER BY name COLLATE NOCASE") fun customers(): Flow<List<Customer>>
    @Query("SELECT * FROM customers WHERE id = :id") suspend fun customer(id: Long): Customer?

    @Insert suspend fun insertProduct(p: Product): Long
    @Update suspend fun updateProduct(p: Product)
    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE") fun products(): Flow<List<Product>>
    @Query("UPDATE products SET stockQty = stockQty + :delta WHERE id = :id") suspend fun addStock(id: Long, delta: Double)
    @Query("SELECT * FROM products WHERE id = :id") suspend fun product(id: Long): Product?

    @Insert suspend fun insertSupplier(s: Supplier): Long
    @Update suspend fun updateSupplier(s: Supplier)
    @Query("SELECT * FROM suppliers ORDER BY name COLLATE NOCASE") fun suppliers(): Flow<List<Supplier>>
    @Query("SELECT * FROM suppliers WHERE id = :id") suspend fun supplier(id: Long): Supplier?

    // Invoices
    @Insert suspend fun insertInvoice(i: Invoice): Long
    @Update suspend fun updateInvoice(i: Invoice)
    @Query("DELETE FROM invoices WHERE id = :id") suspend fun deleteInvoice(id: Long): Int
    @Insert suspend fun insertInvoiceLine(l: InvoiceLine)
    @Query("DELETE FROM invoice_lines WHERE invoiceId = :id") suspend fun deleteInvoiceLines(id: Long): Int
    @Query("DELETE FROM invoice_payments WHERE invoiceId = :id") suspend fun deleteInvoicePayments(id: Long): Int
    @Query("DELETE FROM inventory_movements WHERE refId = :id AND reason IN ('INVOICE','INVOICE_VOID','INVOICE_EDIT')")
    suspend fun deleteInvoiceMovements(id: Long): Int
    @Query("SELECT COUNT(*) FROM invoices") suspend fun invoiceCount(): Int
    @Query("SELECT EXISTS(SELECT 1 FROM invoices WHERE number = :number COLLATE NOCASE)")
    suspend fun invoiceNumberExists(number: String): Boolean
    @Query("SELECT EXISTS(SELECT 1 FROM invoices WHERE number = :number COLLATE NOCASE AND id != :excludeId)")
    suspend fun invoiceNumberExistsExcept(number: String, excludeId: Long): Boolean
    @Query("SELECT * FROM invoices ORDER BY dateMillis DESC, id DESC") fun invoices(): Flow<List<Invoice>>
    @Query("SELECT * FROM invoices WHERE id = :id") suspend fun invoice(id: Long): Invoice?
    @Query("SELECT * FROM invoices WHERE id = :id") fun invoiceFlow(id: Long): Flow<Invoice?>
    @Query("SELECT * FROM invoice_lines WHERE invoiceId = :id ORDER BY id") suspend fun invoiceLines(id: Long): List<InvoiceLine>
    @Query("UPDATE invoices SET status = 'VOID', voidReason = :reason WHERE id = :id AND status = 'ISSUED'")
    suspend fun markVoid(id: Long, reason: String): Int
    @Query("UPDATE invoices SET paymentPaise = paymentPaise + :amountPaise WHERE id = :id AND status = 'ISSUED' AND :amountPaise > 0 AND paymentPaise + :amountPaise <= totalPaise")
    suspend fun addInvoicePayment(id: Long, amountPaise: Long): Int
    @Insert suspend fun insertInvoicePayment(p: InvoicePayment): Long
    @Query("SELECT * FROM invoice_payments WHERE invoiceId = :id ORDER BY dateMillis DESC, id DESC")
    fun invoicePayments(id: Long): Flow<List<InvoicePayment>>
    @Query("SELECT * FROM invoice_payments WHERE invoiceId = :id ORDER BY dateMillis DESC, id DESC")
    suspend fun invoicePaymentsOnce(id: Long): List<InvoicePayment>
    @Query("SELECT * FROM invoice_payments ORDER BY dateMillis DESC, id DESC") fun allInvoicePayments(): Flow<List<InvoicePayment>>

    // Purchases / expenses
    @Insert suspend fun insertPurchase(p: Purchase): Long
    @Update suspend fun updatePurchase(p: Purchase)
    @Query("DELETE FROM purchases WHERE id = :id") suspend fun deletePurchase(id: Long): Int
    @Insert suspend fun insertPurchaseLine(l: PurchaseLine)
    @Query("DELETE FROM purchase_lines WHERE purchaseId = :id") suspend fun deletePurchaseLines(id: Long): Int
    @Query("DELETE FROM purchase_payments WHERE purchaseId = :id") suspend fun deletePurchasePayments(id: Long): Int
    @Query("DELETE FROM inventory_movements WHERE refId = :id AND reason IN ('PURCHASE','PURCHASE_VOID','PURCHASE_EDIT')")
    suspend fun deletePurchaseMovements(id: Long): Int
    @Query("SELECT * FROM purchases ORDER BY dateMillis DESC, id DESC") fun purchases(): Flow<List<Purchase>>
    @Query("SELECT * FROM purchases WHERE id = :id") suspend fun purchase(id: Long): Purchase?
    @Query("SELECT * FROM purchases WHERE id = :id") fun purchaseFlow(id: Long): Flow<Purchase?>
    @Query("SELECT * FROM purchase_lines WHERE purchaseId = :id ORDER BY id") suspend fun purchaseLines(id: Long): List<PurchaseLine>
    @Query("SELECT COUNT(*) FROM purchases") suspend fun purchaseCount(): Int
    @Query("SELECT EXISTS(SELECT 1 FROM purchases WHERE number = :number COLLATE NOCASE)")
    suspend fun purchaseNumberExists(number: String): Boolean
    @Query("UPDATE purchases SET paidPaise = paidPaise + :amountPaise WHERE id = :id AND status = 'RECORDED' AND :amountPaise > 0 AND paidPaise + :amountPaise <= totalPaise")
    suspend fun addPurchasePayment(id: Long, amountPaise: Long): Int
    @Query("UPDATE purchases SET status = 'VOID', voidReason = :reason WHERE id = :id AND status = 'RECORDED'")
    suspend fun markPurchaseVoid(id: Long, reason: String): Int
    @Insert suspend fun insertPurchasePayment(p: PurchasePayment): Long
    @Query("SELECT * FROM purchase_payments WHERE purchaseId = :id ORDER BY dateMillis DESC, id DESC")
    fun purchasePayments(id: Long): Flow<List<PurchasePayment>>
    @Query("SELECT * FROM purchase_payments WHERE purchaseId = :id ORDER BY dateMillis DESC, id DESC")
    suspend fun purchasePaymentsOnce(id: Long): List<PurchasePayment>
    @Query("SELECT * FROM purchase_payments ORDER BY dateMillis DESC, id DESC") fun allPurchasePayments(): Flow<List<PurchasePayment>>

    @Insert suspend fun insertExpense(e: Expense): Long
    @Query("SELECT * FROM expenses ORDER BY dateMillis DESC, id DESC") fun expenses(): Flow<List<Expense>>

    // Employees / salary ledger
    @Insert suspend fun insertEmployee(e: Employee): Long
    @Update suspend fun updateEmployee(e: Employee)
    @Query("DELETE FROM employees WHERE id = :id") suspend fun deleteEmployee(id: Long)
    @Query("SELECT * FROM employees ORDER BY active DESC, name COLLATE NOCASE") fun employees(): Flow<List<Employee>>
    @Query("SELECT * FROM employees WHERE id = :id") suspend fun employee(id: Long): Employee?
    @Query("SELECT * FROM employees WHERE id = :id") fun employeeFlow(id: Long): Flow<Employee?>
    @Insert suspend fun insertSalaryEntry(s: SalaryEntry): Long
    @Update suspend fun updateSalaryEntry(s: SalaryEntry)
    @Query("SELECT * FROM salary_entries WHERE id = :id") suspend fun salaryEntry(id: Long): SalaryEntry?
    @Query("DELETE FROM salary_entries WHERE id = :id") suspend fun deleteSalaryEntry(id: Long)
    @Query("DELETE FROM salary_entries WHERE employeeId = :id") suspend fun deleteSalaryEntriesForEmployee(id: Long): Int
    @Query("SELECT * FROM salary_entries WHERE employeeId = :id ORDER BY dateMillis DESC, id DESC")
    fun salaryEntries(id: Long): Flow<List<SalaryEntry>>
    @Query("SELECT * FROM salary_entries ORDER BY dateMillis DESC, id DESC")
    fun allSalaryEntries(): Flow<List<SalaryEntry>>
    @Query("SELECT COALESCE(SUM(CASE type WHEN 'SALARY_ADDED' THEN amountPaise ELSE -amountPaise END), 0) FROM salary_entries WHERE employeeId = :id")
    fun salaryBalance(id: Long): Flow<Long>
    @Query("SELECT EXISTS(SELECT 1 FROM salary_entries WHERE employeeId = :id AND type = 'SALARY_ADDED' AND period = :period)")
    suspend fun salaryAccrued(id: Long, period: String): Boolean
    @Query("SELECT EXISTS(SELECT 1 FROM salary_entries WHERE employeeId = :id AND type = 'SALARY_ADDED' AND period = :period AND id != :excludeId)")
    suspend fun salaryAccruedExcept(id: Long, period: String, excludeId: Long): Boolean

    // Inventory
    @Insert suspend fun insertMovement(m: InventoryMovement)
    @Query("SELECT * FROM inventory_movements ORDER BY id DESC LIMIT 300") fun movements(): Flow<List<InventoryMovement>>

    // Company profile
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveCompany(c: CompanyProfile)
    @Query("SELECT * FROM company_profile WHERE id = 1") fun company(): Flow<CompanyProfile?>
    @Query("SELECT * FROM company_profile WHERE id = 1") suspend fun companyOnce(): CompanyProfile?

    // Dashboard summaries (void records are excluded)
    @Query("SELECT COALESCE(SUM(totalPaise), 0) FROM invoices WHERE status = 'ISSUED'") fun salesTotal(): Flow<Long>
    @Query("SELECT COALESCE(SUM(totalPaise - paymentPaise), 0) FROM invoices WHERE status = 'ISSUED'") fun customerDues(): Flow<Long>
    @Query("SELECT COALESCE(SUM(totalPaise - paidPaise), 0) FROM purchases WHERE status = 'RECORDED'") fun supplierDues(): Flow<Long>
    @Query("SELECT COALESCE(SUM(amountPaise), 0) FROM expenses") fun expensesTotal(): Flow<Long>
    @Query("SELECT COUNT(*) FROM products WHERE stockQty < 0") fun negativeStockCount(): Flow<Int>
}
