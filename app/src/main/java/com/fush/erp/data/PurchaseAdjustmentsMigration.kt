package com.fush.erp.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds invoice-level purchase adjustments without rewriting existing purchase data.
 * Existing invoices keep zero values for all new fields.
 */
val MIGRATION_45_46_PURCHASE_INVOICE_ADJUSTMENTS = object : Migration(45, 46) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE purchase_invoices ADD COLUMN discountOriginal REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE purchase_invoices ADD COLUMN freightOriginal REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE purchase_invoices ADD COLUMN customsOriginal REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE purchase_invoices ADD COLUMN otherChargesOriginal REAL NOT NULL DEFAULT 0.0")
    }
}
