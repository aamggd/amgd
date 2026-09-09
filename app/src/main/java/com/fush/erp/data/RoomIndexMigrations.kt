package com.fush.erp.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema 44 -> 45 is index-only hardening. It does not modify, copy, delete, or rewrite data.
 * The indexes cover foreign-key child columns that Room flags as otherwise requiring full scans
 * when parent rows are updated/deleted.
 */
val MIGRATION_44_45_FOREIGN_KEY_INDEX_HARDENING = object : Migration(44, 45) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_returns_warehouseId ON purchase_returns(warehouseId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_return_lines_unitId ON purchase_return_lines(unitId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_returns_warehouseId ON sales_returns(warehouseId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_return_lines_unitId ON sales_return_lines(unitId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_party_vouchers_treasuryAccountId ON party_vouchers(treasuryAccountId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_party_vouchers_offsetAccountId ON party_vouchers(offsetAccountId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_support_sessions_activatedBy ON support_sessions(activatedBy)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_support_snapshots_supportUserId ON support_snapshots(supportUserId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_support_validation_results_supportUserId ON support_validation_results(supportUserId)")
    }
}
