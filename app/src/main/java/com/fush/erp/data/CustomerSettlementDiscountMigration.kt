package com.fush.erp.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v126: explicit, non-destructive persistence for collection settlement discounts.
 * Existing receipts/allocations remain financially unchanged because all new columns
 * are NOT NULL with zero/empty defaults.
 */
val MIGRATION_38_39_CUSTOMER_SETTLEMENT_DISCOUNT = object : Migration(38, 39) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE customer_receipts ADD COLUMN discountOriginal REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE customer_receipts ADD COLUMN discountBase REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE customer_receipts ADD COLUMN discountReason TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE customer_receipt_allocations ADD COLUMN discountOriginal REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE customer_receipt_allocations ADD COLUMN discountBase REAL NOT NULL DEFAULT 0.0")
    }
}
