package com.factorybill.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        Customer::class, Product::class, Invoice::class, InvoiceLine::class, Supplier::class,
        Purchase::class, PurchaseLine::class, Expense::class, Employee::class,
        SalaryEntry::class, InventoryMovement::class, CompanyProfile::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): FactoryDao

    companion object {
        /**
         * Every schema change MUST add a Migration here (and bump `version`), so existing data on the
         * phone is preserved across app updates. Never call fallbackToDestructiveMigration().
         * Example for a future v2:
         *   val MIGRATION_1_2 = object : Migration(1, 2) {
         *       override fun migrate(db: SupportSQLiteDatabase) {
         *           db.execSQL("ALTER TABLE customers ADD COLUMN email TEXT NOT NULL DEFAULT ''")
         *       }
         *   }
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE invoices ADD COLUMN pricesIncludeGst INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE invoices ADD COLUMN placeOfSupply TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE invoice_lines ADD COLUMN hsn TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "factorybill.db")
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
