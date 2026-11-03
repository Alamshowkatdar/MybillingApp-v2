package com.factorybill.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        Customer::class, Product::class, Invoice::class, InvoiceLine::class, InvoicePayment::class,
        Supplier::class, Purchase::class, PurchaseLine::class, PurchasePayment::class,
        Expense::class, Employee::class, SalaryEntry::class, InventoryMovement::class, CompanyProfile::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): FactoryDao

    companion object {
        /**
         * Every schema change MUST add a Migration here (and bump `version`), so existing data on the
         * phone is preserved across app updates. Never call fallbackToDestructiveMigration().
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE invoices ADD COLUMN pricesIncludeGst INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE invoices ADD COLUMN placeOfSupply TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE invoice_lines ADD COLUMN hsn TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE invoice_lines ADD COLUMN unit TEXT NOT NULL DEFAULT 'pcs'")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE employees ADD COLUMN startDateMillis INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE purchases ADD COLUMN scanImagePath TEXT")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE company_profile ADD COLUMN logoPath TEXT")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Richer customer, supplier and employee records.
                db.execSQL("ALTER TABLE customers ADD COLUMN email TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE customers ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE suppliers ADD COLUMN contactPerson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE suppliers ADD COLUMN email TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE suppliers ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE employees ADD COLUMN phone TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE employees ADD COLUMN role TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE employees ADD COLUMN notes TEXT NOT NULL DEFAULT ''")

                // Invoice audit fields, due dates and notes.
                db.execSQL("ALTER TABLE invoices ADD COLUMN createdAtMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE invoices ADD COLUMN dueDateMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE invoices ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE invoices SET createdAtMillis = dateMillis WHERE createdAtMillis = 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_dateMillis ON invoices(dateMillis)")

                // Purchase numbering, supplier bill number, due dates, void state and line snapshots.
                db.execSQL("ALTER TABLE purchases ADD COLUMN number TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE purchases ADD COLUMN billNumber TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE purchases ADD COLUMN createdAtMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE purchases ADD COLUMN dueDateMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE purchases ADD COLUMN status TEXT NOT NULL DEFAULT 'RECORDED'")
                db.execSQL("ALTER TABLE purchases ADD COLUMN voidReason TEXT")
                db.execSQL("UPDATE purchases SET number = 'PUR-' || substr('0000' || id, -4) WHERE number = ''")
                db.execSQL("UPDATE purchases SET createdAtMillis = dateMillis WHERE createdAtMillis = 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_purchases_dateMillis ON purchases(dateMillis)")
                db.execSQL("ALTER TABLE purchase_lines ADD COLUMN productName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE purchase_lines ADD COLUMN hsn TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE purchase_lines ADD COLUMN unit TEXT NOT NULL DEFAULT 'pcs'")
                db.execSQL("UPDATE purchase_lines SET productName = COALESCE((SELECT name FROM products WHERE products.id = purchase_lines.productId), 'Item') WHERE productName = ''")
                db.execSQL("UPDATE purchase_lines SET hsn = COALESCE((SELECT hsn FROM products WHERE products.id = purchase_lines.productId), '') WHERE hsn = ''")
                db.execSQL("UPDATE purchase_lines SET unit = COALESCE((SELECT unit FROM products WHERE products.id = purchase_lines.productId), 'pcs') WHERE unit = 'pcs'")

                // Expense payment channel and date index.
                db.execSQL("ALTER TABLE expenses ADD COLUMN mode TEXT NOT NULL DEFAULT 'CASH'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_dateMillis ON expenses(dateMillis)")

                // Salary month and date index.
                db.execSQL("ALTER TABLE salary_entries ADD COLUMN period TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_salary_entries_dateMillis ON salary_entries(dateMillis)")

                // Company invoicing preferences.
                db.execSQL("ALTER TABLE company_profile ADD COLUMN invoicePrefix TEXT NOT NULL DEFAULT 'INV-'")
                db.execSQL("ALTER TABLE company_profile ADD COLUMN termsAndConditions TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE company_profile ADD COLUMN footerNote TEXT NOT NULL DEFAULT ''")

                // Payment history tables.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS invoice_payments (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "invoiceId INTEGER NOT NULL, amountPaise INTEGER NOT NULL, " +
                        "dateMillis INTEGER NOT NULL, mode TEXT NOT NULL, note TEXT NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_invoice_payments_invoiceId ON invoice_payments(invoiceId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_invoice_payments_dateMillis ON invoice_payments(dateMillis)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS purchase_payments (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "purchaseId INTEGER NOT NULL, amountPaise INTEGER NOT NULL, " +
                        "dateMillis INTEGER NOT NULL, mode TEXT NOT NULL, note TEXT NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_payments_purchaseId ON purchase_payments(purchaseId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_payments_dateMillis ON purchase_payments(dateMillis)")

                // Preserve the aggregate payments that existed before history was introduced.
                db.execSQL(
                    "INSERT INTO invoice_payments(invoiceId, amountPaise, dateMillis, mode, note) " +
                        "SELECT id, paymentPaise, dateMillis, 'CASH', 'Opening payment record' " +
                        "FROM invoices WHERE paymentPaise > 0"
                )
                db.execSQL(
                    "INSERT INTO purchase_payments(purchaseId, amountPaise, dateMillis, mode, note) " +
                        "SELECT id, paidPaise, dateMillis, 'CASH', 'Opening payment record' " +
                        "FROM purchases WHERE paidPaise > 0"
                )
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
        )

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "factorybill.db")
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
