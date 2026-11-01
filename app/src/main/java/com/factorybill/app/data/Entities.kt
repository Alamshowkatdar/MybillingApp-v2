package com.factorybill.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// All money fields are Long paise. Quantities are Double.

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val contactPerson: String = "",
    val phone: String = "",
    val gstin: String = "",
    val billingAddress: String = "",
    val shippingAddress: String = "",
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

@Entity(tableName = "invoices", indices = [Index("customerId")])
data class Invoice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val dateMillis: Long,
    val customerId: Long,
    val intraState: Boolean,
    val taxablePaise: Long,
    val gstPaise: Long,
    val totalPaise: Long,
    val paymentPaise: Long,
    val pricesIncludeGst: Boolean = false,
    val placeOfSupply: String = "",
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

@Entity(tableName = "suppliers")
data class Supplier(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val gstin: String = "",
    val address: String = "",
)

@Entity(tableName = "purchases", indices = [Index("supplierId")])
data class Purchase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val supplierId: Long,
    val dateMillis: Long,
    val taxablePaise: Long,
    val gstPaise: Long,
    val totalPaise: Long,
    val paidPaise: Long,
    val note: String = "",
)

@Entity(tableName = "purchase_lines", indices = [Index("purchaseId")])
data class PurchaseLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val purchaseId: Long,
    val productId: Long,
    val qty: Double,
    val unitCostPaise: Long,
    val gstRate: Double,
    val taxablePaise: Long,
    val gstPaise: Long,
    val totalPaise: Long,
)

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val dateMillis: Long,
    val amountPaise: Long,
    val payee: String,
    val notes: String = "",
)

@Entity(tableName = "employees")
data class Employee(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val monthlySalaryPaise: Long,
    val active: Boolean = true,
)

@Entity(tableName = "salary_entries", indices = [Index("employeeId")])
data class SalaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: Long,
    val type: String, // SALARY_ADDED | ADVANCE | PAYMENT (see core.SalaryType)
    val amountPaise: Long,
    val dateMillis: Long,
    val note: String = "",
)

@Entity(tableName = "inventory_movements", indices = [Index("productId")])
data class InventoryMovement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val delta: Double,
    val reason: String, // INVOICE | INVOICE_VOID | PURCHASE | ADJUSTMENT
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
)
