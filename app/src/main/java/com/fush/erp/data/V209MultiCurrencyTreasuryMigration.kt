package com.fush.erp.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v209 keeps every treasury currency as a separate posting account while grouping those
 * currency ledgers under one logical treasury. This preserves historical journal links and
 * makes a cashbox/bank safely multi-currency without mixing original-currency balances.
 */
val MIGRATION_51_52_MULTI_CURRENCY_TREASURY = object : Migration(51, 52) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE treasury_accounts ADD COLUMN groupCode TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE treasury_accounts SET groupCode = code WHERE TRIM(groupCode) = ''")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_treasury_accounts_groupCode ON treasury_accounts(groupCode)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_treasury_accounts_groupCode_currencyCode ON treasury_accounts(groupCode, currencyCode)")
    }
}
