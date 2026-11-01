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
    version = 6,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): FactoryDao

    companion object {
        /**
         * Every schema change MUST add a Migration here (and bump `version`), so existing data on the
         * phone is preserved across app updates. Never call fallbackToDestructiveMigration().
         * Example for a future version:
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

        val MIGRATIONS: Array<Migration> =
            arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "factorybill.db")
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
