package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V190ClosedPeriodCloudHydrationRegressionTest {
    private fun source(relative: String): String {
        val candidates = listOf(File("src/main/java/$relative"), File("app/src/main/java/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText() ?: error("Source file not found: $relative")
    }

    @Test
    fun closedPeriodExceptionIsRestrictedToAtomicLegacyCloudFinalization() {
        val guard = source("com/fush/erp/data/AccountingPeriodDatabaseGuard.kt")
        assertTrue(guard.contains("DROP TRIGGER IF EXISTS"))
        assertTrue(guard.contains("trg_journal_entries_open_period_insert"))
        assertTrue(guard.contains("trg_journal_entries_open_period_update"))
        assertTrue(guard.contains("OLD.sourceType"))
        assertTrue(guard.contains("CLOUD_LEGACY_TREASURY_HYDRATION"))
        assertTrue(guard.contains("OLD.status"))
        assertTrue(guard.contains("'STAGING'"))
        assertTrue(guard.contains("NEW.status"))
        assertTrue(guard.contains("'POSTED'"))
        assertTrue(guard.contains("NEW.sourceId = OLD.sourceId"))
        assertTrue(guard.contains("LEGACY_CLOUD_PREFIX"))
        assertTrue(guard.contains("ACCOUNTING_PERIOD_NOT_OPEN"))

        // Direct local POSTED inserts remain fully fail-closed. The legacy closed-period INSERT guard
        // may admit only the internal cloud STAGING envelope; the POSTED insert guard has no cloud exception.
        val insertStart = guard.indexOf("CREATE TRIGGER trg_journal_entries_open_period_insert")
        val updateStart = guard.indexOf("CREATE TRIGGER trg_journal_entries_open_period_update")
        assertTrue(insertStart >= 0 && updateStart > insertStart)
        val insertBlock = guard.substring(insertStart, updateStart)
        assertFalse(insertBlock.contains("CLOUD_LEGACY_TREASURY_HYDRATION"))
    }

    @Test
    fun cloudEngineKeepsAliasUntilSingleAtomicPostingUpdate() {
        val engine = source("com/fush/erp/cloud/AccountingCloudSyncEngine.kt")
        val start = engine.indexOf("private suspend fun applyJournalFromCloud")
        val end = engine.indexOf("private suspend fun resolveLocalSourceId", start)
        assertTrue(start >= 0 && end > start)
        val block = engine.substring(start, end)

        assertTrue(block.contains("CloudPostedJournalHydrationPolicy.STAGING_ALIAS_SOURCE_TYPE"))
        assertTrue(block.contains("finalizeCloudHydrationToPosted"))
        assertTrue(block.contains("originalSourceType = sourceType"))
        assertTrue(block.contains("AccountingCloudHydrationSourceIdentityPolicy.sourceIdForHydration"))
        assertFalse(block.contains("stagingHeader.copy(\n                            sourceType = sourceType"))
    }

    @Test
    fun hydrationAliasCannotBeFinalizedIntoArbitraryPostedJournal() {
        val guards = source("com/fush/erp/data/AccountingIdempotencyDatabaseGuards.kt")
        assertTrue(guards.contains("trg_cloud_legacy_treasury_hydration_finalize_shape"))
        assertTrue(guards.contains("CLOUD_LEGACY_TREASURY_HYDRATION_FINALIZE_INVALID"))
        assertTrue(guards.contains("TREASURY_TRANSFER"))
        assertTrue(guards.contains("TREASURY_RECEIPT"))
        assertTrue(guards.contains("TREASURY_PAYMENT"))
        assertTrue(guards.contains("TREASURY_EXPENSE"))
        assertTrue(guards.contains("TREASURY_INCOME"))
        assertTrue(guards.contains("NEW.sourceId <> OLD.sourceId"))
    }

    @Test
    fun v190FixRemainsPresentInV191SchemaPreservingUpdate() {
        val gradle = listOf(File("build.gradle.kts"), File("app/build.gradle.kts")).first { it.isFile }.readText()
        val db = source("com/fush/erp/data/FushDatabase.kt")
        assertTrue(Regex("versionCode\\s*=\\s*(\\d+)").find(gradle)?.groupValues?.get(1)?.toInt() ?: 0 >= 191)
        assertTrue(gradle.contains("versionName ="))
        assertTrue(Regex("FUSH_DB_SCHEMA_VERSION\\s*=\\s*(\\d+)").find(db)?.groupValues?.get(1)?.toInt() ?: 0 >= 49)
        assertFalse(db.contains("fallbackToDestructiveMigration"))
    }
}
