package com.fush.erp.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FushAiDraftActionsMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FushDatabase::class.java
    )

    @Test
    fun room52To53AddsIsolatedAiDraftStoreWithoutTouchingOperationalTables() {
        helper.createDatabase(DB_NAME, 52).close()
        helper.runMigrationsAndValidate(DB_NAME, 53, true, MIGRATION_52_53_FUSH_AI_DRAFT_ACTIONS).use { db53 ->
            assertEquals(53, db53.version)
            assertEquals(1L, scalar(db53, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='fush_ai_drafts'"))
            assertEquals(1L, scalar(db53, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='sales_invoices'"))
            assertEquals(1L, scalar(db53, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='treasury_accounts'"))
            assertEquals(1L, scalar(db53, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='production_orders'"))
        }
    }

    private fun scalar(db: androidx.sqlite.db.SupportSQLiteDatabase, sql: String): Long =
        db.query(sql).use { c -> check(c.moveToFirst()); c.getLong(0) }

    private companion object { const val DB_NAME = "fush-ai-drafts-room52-to-53" }
}
