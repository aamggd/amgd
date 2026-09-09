package com.fush.erp.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v202: local Yemen FX market engine. No destructive migration; all v201 data is preserved. */
val MIGRATION_49_50_LOCAL_FX_ENGINE = object : Migration(49, 50) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE fx_snapshots ADD COLUMN sarNewYer REAL DEFAULT NULL")
        db.execSQL("ALTER TABLE fx_snapshots ADD COLUMN sarOldYer REAL DEFAULT NULL")
        db.execSQL("ALTER TABLE fx_snapshots ADD COLUMN approvedRateType TEXT NOT NULL DEFAULT 'SELL'")
        db.execSQL("ALTER TABLE fx_snapshots ADD COLUMN sourceBatchId TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE fx_snapshots ADD COLUMN sourcePublishedAt INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE fx_snapshots ADD COLUMN fetchedAt INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE fx_snapshots ADD COLUMN primarySource TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE fx_snapshots ADD COLUMN primarySourceUrl TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE fx_snapshots ADD COLUMN comparisonSource TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE fx_snapshots ADD COLUMN maxVariancePercent REAL DEFAULT NULL")
        db.execSQL("ALTER TABLE fx_snapshots ADD COLUMN approvalOverrideReason TEXT NOT NULL DEFAULT ''")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS fx_market_rates (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                batchId TEXT NOT NULL,
                marketRegion TEXT NOT NULL,
                currencyCode TEXT NOT NULL,
                rateType TEXT NOT NULL,
                rateYer REAL NOT NULL,
                primarySource TEXT NOT NULL,
                primarySourceUrl TEXT NOT NULL,
                sourcePublishedAt INTEGER NOT NULL,
                comparisonRateYer REAL DEFAULT NULL,
                comparisonSource TEXT NOT NULL DEFAULT '',
                comparisonSourceUrl TEXT NOT NULL DEFAULT '',
                comparisonPublishedAt INTEGER DEFAULT NULL,
                variancePercent REAL DEFAULT NULL,
                sourceStatus TEXT NOT NULL DEFAULT 'PRIMARY_ONLY',
                fetchedAt INTEGER NOT NULL,
                rawHash TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_fx_market_rates_batchId ON fx_market_rates(batchId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_fx_market_rates_fetchedAt ON fx_market_rates(fetchedAt)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_fx_market_rates_batchId_marketRegion_currencyCode_rateType ON fx_market_rates(batchId, marketRegion, currencyCode, rateType)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS fx_rate_settings (
                id INTEGER NOT NULL PRIMARY KEY,
                defaultMarketRegion TEXT NOT NULL DEFAULT 'ADEN',
                defaultRateType TEXT NOT NULL DEFAULT 'SELL',
                staleAfterHours INTEGER NOT NULL DEFAULT 72,
                warningVariancePct REAL NOT NULL DEFAULT 2.0,
                highVariancePct REAL NOT NULL DEFAULT 4.0,
                updatedBy INTEGER DEFAULT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO fx_rate_settings(
                id, defaultMarketRegion, defaultRateType, staleAfterHours,
                warningVariancePct, highVariancePct, updatedBy, updatedAt
            ) VALUES (1, 'ADEN', 'SELL', 72, 2.0, 4.0, NULL, 0)
            """.trimIndent()
        )
    }
}
