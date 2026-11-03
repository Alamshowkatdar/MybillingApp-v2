package com.factorybill.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// All money fields are Long paise. Quantities are Double.
// Business records are append-only: invoices and purchases are voided, never deleted.

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val contactPerson: String = "",
    val phone: String = "",
    val email: String = "",
    val gstin: String = "",
    val billingAddress: String = "",
    val shippingAddress: String = "",
    val notes: String = "",
)

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val hsn: String = "",
    val unit: String = "pcs",
    val defaultPricePaise: Long = 0,
    val defaultGstRate: Double = 18.0,
    val stockQty: Double = 0.0, // may be negative: invoicing is allowed when stock is short
)

@Entity(tableName = "invoices", indices = [Index("customerId"), Index("dateMillis")])
data class Invoice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val dateMillis: Long,
    val createdAtMillis: Long = 0,
    val dueDateMillis: Long = 0,
    val customerId: Long,
    val intraState: Boolean,
    val taxablePaise: Long,
    val gstPaise: Long,
    val totalPaise: Long,
    val paymentPaise: Long,
    val pricesIncludeGst: Boolean = false,
    val placeOfSupply: String = "",
    val notes: String = "",
    val scanImagePath: String? = null, // app-private copy of the original scanned bill
    val status: String = "ISSUED",     // ISSUED | VOID (records are never deleted)
    val voidReason: String? = null,
)

@Entity(tableName = "invoice_lines", indices = [Index("invoiceId")])
data class InvoiceLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long,
    val productId: Long,
    val productName: String,
    val hsn: String = "",
    val unit: String = "pcs",
    val qty: Double,
    val unitPricePaise: Long,
    val gstRate: Double,
    val taxablePaise: Long,
    val gstPaise: Long,
    val totalPaise: Long,
)

@Entity(tableName = "invoice_payments", indices = [Index("invoiceId"), Index("dateMillis")])
data class InvoicePayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long,
    val amountPaise: Long,
    val dateMillis: Long,
    val mode: String = PaymentMode.CASH.value,
    val note: String = "",
)

@Entity(tableName = "suppliers")
data class Supplier(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val contactPerson: String = "",
    val phone: String = "",
    val email: String = "",
    val gstin: String = "",
    val address: String = "",
    val notes: String = "",
)

@Entity(tableName = "purchases", indices = [Index("supplierId"), Index("dateMillis")])
data class Purchase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String = "",
    val billNumber: String = "",
    val supplierId: Long,
    val dateMillis: Long,
    val createdAtMillis: Long = 0,
    val dueDateMillis: Long = 0,
    val taxablePaise: Long,
    val gstPaise: Long,
    val totalPaise: Long,
    val paidPaise: Long,
    val note: String = "",
    val scanImagePath: String? = null,
    val status: String = "RECORDED", // RECORDED | VOID
    val voidReason: String? = null,
)

@Entity(tableName = "purchase_lines", indices = [Index("purchaseId")])
data class PurchaseLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val purchaseId: Long,
    val productId: Long,
    val productName: String = "",
    val hsn: String = "",
    val unit: String = "pcs",
    val qty: Double,
    val unitCostPaise: Long,
    val gstRate: Double,
    val taxablePaise: Long,
    val gstPaise: Long,
    val totalPaise: Long,
)

@Entity(tableName = "purchase_payments", indices = [Index("purchaseId"), Index("dateMillis")])
data class PurchasePayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val purchaseId: Long,
    val amountPaise: Long,
    val dateMillis: Long,
    val mode: String = PaymentMode.CASH.value,
    val note: String = "",
)

@Entity(tableName = "expenses", indices = [Index("dateMillis")])
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val dateMillis: Long,
    val amountPaise: Long,
    val payee: String,
    val mode: String = PaymentMode.CASH.value,
    val notes: String = "",
)

@Entity(tableName = "employees")
data class Employee(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val monthlySalaryPaise: Long,
    val startDateMillis: Long = 0,
    val phone: String = "",
    val role: String = "",
    val notes: String = "",
    val active: Boolean = true,
)

@Entity(tableName = "salary_entries", indices = [Index("employeeId"), Index("dateMillis")])
data class SalaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: Long,
    val type: String, // SALARY_ADDED | ADVANCE | PAYMENT (see core.SalaryType)
    val amountPaise: Long,
    val dateMillis: Long,
    val period: String = "", // YYYY-MM for salary accruals
    val note: String = "",
)

@Entity(tableName = "inventory_movements", indices = [Index("productId")])
data class InventoryMovement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val delta: Double,
    val reason: String, // INVOICE | INVOICE_VOID | PURCHASE | PURCHASE_VOID | ADJUSTMENT
    val refId: Long,
    val timestamp: Long,
)

@Entity(tableName = "company_profile")
data class CompanyProfile(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    val gstin: String = "",
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    val bankName: String = "",
    val accountNumber: String = "",
    val ifsc: String = "",
    val upiId: String = "",
    val template: String = "classic", // classic | modern | compact
    val logoPath: String? = null,
    val invoicePrefix: String = "INV-",
    val termsAndConditions: String = "",
    val footerNote: String = "",
)

/** Payment channels shown in records and PDFs. */
enum class PaymentMode(val value: String, val label: String) {
    CASH("CASH", "Cash"),
    UPI("UPI", "UPI"),
    BANK("BANK", "Bank transfer"),
    CHEQUE("CHEQUE", "Cheque"),
    CARD("CARD", "Card"),
    OTHER("OTHER", "Other");

    companion object {
        fun label(value: String): String = entries.firstOrNull { it.value == value }?.label ?: value
    }
}
