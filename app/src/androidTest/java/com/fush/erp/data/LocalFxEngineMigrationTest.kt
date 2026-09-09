package com.fush.erp.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalFxEngineMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FushDatabase::class.java
    )

    @Test
    fun room49To50PreservesApprovedFxHistoryAndCreatesOnlineEngineTables() {
        helper.createDatabase(DB_NAME, 49).use { db49 ->
            db49.execSQL(
                """
                INSERT INTO fx_snapshots(
                    id, effectiveAt, usdNewYer, usdOldYer, oldYerToNewYer,
                    sourceNote, createdBy, createdAt
                ) VALUES(1, 1788472800000, 1565.0, 533.0, 2.9362101313, 'v201-history', 1, 1788472800000)
                """.trimIndent()
            )
            assertEquals(1L, scalar(db49, "SELECT COUNT(*) FROM fx_snapshots"))
        }

        helper.runMigrationsAndValidate(
            DB_NAME,
            50,
            true,
            MIGRATION_49_50_LOCAL_FX_ENGINE
        ).use { db50 ->
            assertEquals(50, db50.version)
            assertEquals(1L, scalar(db50, "SELECT COUNT(*) FROM fx_snapshots"))
            assertEquals("v201-history", text(db50, "SELECT sourceNote FROM fx_snapshots WHERE id=1"))
            assertEquals("SELL", text(db50, "SELECT approvedRateType FROM fx_snapshots WHERE id=1"))
            assertEquals("", text(db50, "SELECT primarySource FROM fx_snapshots WHERE id=1"))
            assertEquals(1L, scalar(db50, "SELECT COUNT(*) FROM fx_rate_settings WHERE id=1"))
            assertEquals("ADEN", text(db50, "SELECT defaultMarketRegion FROM fx_rate_settings WHERE id=1"))
            assertEquals("SELL", text(db50, "SELECT defaultRateType FROM fx_rate_settings WHERE id=1"))
            assertEquals(72L, scalar(db50, "SELECT staleAfterHours FROM fx_rate_settings WHERE id=1"))
            assertEquals(1L, scalar(db50, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='fx_market_rates'"))
            assertEquals(1L, scalar(db50, "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='index_fx_market_rates_batchId_marketRegion_currencyCode_rateType'"))
            assertTrue(scalar(db50, "SELECT COUNT(*) FROM pragma_table_info('fx_snapshots') WHERE name='sarNewYer'") == 1L)
            assertTrue(scalar(db50, "SELECT COUNT(*) FROM pragma_table_info('fx_snapshots') WHERE name='sourceBatchId'") == 1L)
        }
    }

    private fun scalar(db: androidx.sqlite.db.SupportSQLiteDatabase, sql: String): Long =
        db.query(sql).use { c -> check(c.moveToFirst()); c.getLong(0) }

    private fun text(db: androidx.sqlite.db.SupportSQLiteDatabase, sql: String): String =
        db.query(sql).use { c -> check(c.moveToFirst()); c.getString(0) }

    private companion object {
        const val DB_NAME = "local-fx-room49-to-50"
    }
}
