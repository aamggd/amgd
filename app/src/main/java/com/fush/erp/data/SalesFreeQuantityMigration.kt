package com.fush.erp.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v134: explicit paid/free sales quantities and representative free-quantity allowance.
 * Existing rows are preserved with zero free quantity / zero allowance.
 */
val MIGRATION_39_40_SALES_FREE_QUANTITY = object : Migration(39, 40) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sales_representatives ADD COLUMN freeQtyLimitPct REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE sales_invoices ADD COLUMN freeQtyLimitPctSnapshot REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE sales_invoices ADD COLUMN freeQtyApprovedBy INTEGER")
        db.execSQL("ALTER TABLE sales_invoices ADD COLUMN freeQtyApprovalReason TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE sales_lines ADD COLUMN freeQuantity REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE sales_lines ADD COLUMN freeBaseQuantity REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE sales_allocations ADD COLUMN freeQuantityBase REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE sales_return_lines ADD COLUMN freeQuantity REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE sales_return_lines ADD COLUMN freeBaseQuantity REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE sales_return_allocations ADD COLUMN freeQuantityBase REAL NOT NULL DEFAULT 0.0")
    }
}
