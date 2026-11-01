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
    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE") fun products(): Flow<List<Product>>
    @Query("UPDATE products SET stockQty = stockQty + :delta WHERE id = :id") suspend fun addStock(id: Long, delta: Double)

    @Insert suspend fun insertSupplier(s: Supplier): Long
    @Query("SELECT * FROM suppliers ORDER BY name COLLATE NOCASE") fun suppliers(): Flow<List<Supplier>>

    // Invoices
    @Insert suspend fun insertInvoice(i: Invoice): Long
    @Insert suspend fun insertInvoiceLine(l: InvoiceLine)
    @Query("SELECT COUNT(*) FROM invoices") suspend fun invoiceCount(): Int
    @Query("SELECT * FROM invoices ORDER BY id DESC") fun invoices(): Flow<List<Invoice>>
    @Query("SELECT * FROM invoices WHERE id = :id") suspend fun invoice(id: Long): Invoice?
    @Query("SELECT * FROM invoice_lines WHERE invoiceId = :id") suspend fun invoiceLines(id: Long): List<InvoiceLine>
    @Query("UPDATE invoices SET status = 'VOID', voidReason = :reason WHERE id = :id AND status = 'ISSUED'")
    suspend fun markVoid(id: Long, reason: String): Int
    @Query("UPDATE invoices SET paymentPaise = paymentPaise + :amountPaise WHERE id = :id AND status = 'ISSUED' AND :amountPaise > 0 AND paymentPaise + :amountPaise <= totalPaise")
    suspend fun addInvoicePayment(id: Long, amountPaise: Long): Int

    // Purchases / expenses
    @Insert suspend fun insertPurchase(p: Purchase): Long
    @Insert suspend fun insertPurchaseLine(l: PurchaseLine)
    @Query("SELECT * FROM purchases ORDER BY id DESC") fun purchases(): Flow<List<Purchase>>
    @Insert suspend fun insertExpense(e: Expense): Long
    @Query("SELECT * FROM expenses ORDER BY dateMillis DESC, id DESC") fun expenses(): Flow<List<Expense>>

    // Employees / salary ledger
    @Insert suspend fun insertEmployee(e: Employee): Long
    @Query("SELECT * FROM employees ORDER BY name COLLATE NOCASE") fun employees(): Flow<List<Employee>>
    @Insert suspend fun insertSalaryEntry(s: SalaryEntry): Long
    @Query("SELECT * FROM salary_entries WHERE employeeId = :id ORDER BY dateMillis DESC, id DESC")
    fun salaryEntries(id: Long): Flow<List<SalaryEntry>>
    @Query("SELECT COALESCE(SUM(CASE type WHEN 'SALARY_ADDED' THEN amountPaise ELSE -amountPaise END), 0) FROM salary_entries WHERE employeeId = :id")
    fun salaryBalance(id: Long): Flow<Long>

    // Inventory
    @Insert suspend fun insertMovement(m: InventoryMovement)
    @Query("SELECT * FROM inventory_movements ORDER BY id DESC LIMIT 200") fun movements(): Flow<List<InventoryMovement>>

    // Company profile
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveCompany(c: CompanyProfile)
    @Query("SELECT * FROM company_profile WHERE id = 1") fun company(): Flow<CompanyProfile?>
    @Query("SELECT * FROM company_profile WHERE id = 1") suspend fun companyOnce(): CompanyProfile?

    // Dashboard summaries
    @Query("SELECT COALESCE(SUM(totalPaise), 0) FROM invoices WHERE status = 'ISSUED'") fun salesTotal(): Flow<Long>
    @Query("SELECT COALESCE(SUM(totalPaise - paymentPaise), 0) FROM invoices WHERE status = 'ISSUED'") fun customerDues(): Flow<Long>
    @Query("SELECT COALESCE(SUM(totalPaise - paidPaise), 0) FROM purchases") fun supplierDues(): Flow<Long>
    @Query("SELECT COALESCE(SUM(amountPaise), 0) FROM expenses") fun expensesTotal(): Flow<Long>
    @Query("SELECT COUNT(*) FROM products WHERE stockQty < 0") fun negativeStockCount(): Flow<Int>
    @Query("SELECT * FROM products WHERE id = :id") suspend fun product(id: Long): Product?
}
