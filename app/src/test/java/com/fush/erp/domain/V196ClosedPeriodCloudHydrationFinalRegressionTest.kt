package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V196ClosedPeriodCloudHydrationFinalRegressionTest {
    private fun source(relative: String): String {
        val candidates = listOf(File("src/main/java/$relative"), File("app/src/main/java/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText() ?: error("Source file not found: $relative")
    }

    @Test
    fun everyCloudPostedJournalUsesInternalStagingAliasBeforePosting() {
        val engine = source("com/fush/erp/cloud/AccountingCloudSyncEngine.kt")
        val start = engine.indexOf("private suspend fun applyJournalFromCloud")
        val end = engine.indexOf("private suspend fun resolveLocalSourceId", start)
        assertTrue(start >= 0 && end > start)
        val block = engine.substring(start, end)
        assertTrue(block.contains("CloudPostedJournalHydrationPolicy.STAGING_ALIAS_SOURCE_TYPE"))
        assertTrue(block.contains("CloudPostedJournalHydrationPolicy.stagingSourceId(entryNo)"))
        assertTrue(block.contains("finalizeCloudHydrationToPosted"))
        assertFalse(block.contains("transitionStagingToPosted(entryId)"))
    }

    @Test
    fun closedPeriodExceptionIsCloudAliasOnlyAndInsertRemainsFailClosed() {
        val guard = source("com/fush/erp/data/AccountingPeriodDatabaseGuard.kt")
        assertTrue(guard.contains("CLOUD_POSTED_ALIAS"))
        assertTrue(guard.contains("CLOUD_POSTED_PREFIX"))
        assertTrue(guard.contains("trg_journal_entries_closed_period"))
        assertTrue(guard.contains("DROP TRIGGER IF EXISTS"))
        assertTrue(guard.contains("الفترة المحاسبية مقفلة"))
        assertTrue(guard.contains("OLD.status"))
        assertTrue(guard.contains("'STAGING'"))
        assertTrue(guard.contains("NEW.status"))
        assertTrue(guard.contains("'POSTED'"))
        assertTrue(guard.contains("NEW.entryDate = OLD.entryDate"))
        val insertStart = guard.indexOf("CREATE TRIGGER trg_journal_entries_open_period_insert")
        val updateStart = guard.indexOf("CREATE TRIGGER trg_journal_entries_open_period_update")
        assertTrue(insertStart >= 0 && updateStart > insertStart)
        assertFalse(guard.substring(insertStart, updateStart).contains("CLOUD_POSTED_ALIAS"))
    }

    @Test
    fun manualJournalGuardsAllowOnlyAtomicCloudReplicationException() {
        val guard = source("com/fush/erp/data/AccountingJournalApprovalDatabaseGuard.kt")
        assertTrue(guard.contains("DROP TRIGGER IF EXISTS"))
        assertTrue(guard.contains("trg_manual_journal_status_transition"))
        assertTrue(guard.contains("trg_manual_journal_open_period_post"))
        assertTrue(guard.contains("cloudAlias"))
        assertTrue(guard.contains("cloudPrefix"))
        assertTrue(guard.contains("NEW.entryDate = OLD.entryDate"))
    }

    @Test
    fun genericHydrationAliasCannotRemainPostedOrFinalizeToUnknownSource() {
        val guard = source("com/fush/erp/data/AccountingIdempotencyDatabaseGuards.kt")
        assertTrue(guard.contains("trg_cloud_posted_journal_hydration_insert_shape"))
        assertTrue(guard.contains("trg_cloud_posted_journal_hydration_finalize_shape"))
        assertTrue(guard.contains("trg_cloud_posted_journal_hydration_never_posted"))
        assertTrue(guard.contains("businessRegistered"))
        assertTrue(guard.contains("CLOUD_POSTED_JOURNAL_HYDRATION_FINALIZE_INVALID"))
    }

    @Test
    fun v196IsSchemaPreservingUpdate() {
        val gradle = listOf(File("build.gradle.kts"), File("app/build.gradle.kts")).first { it.isFile }.readText()
        val db = source("com/fush/erp/data/FushDatabase.kt")
        assertTrue(Regex("versionCode\\s*=\\s*(19[6-9]|[2-9][0-9]{2,})").containsMatchIn(gradle))
        assertTrue(gradle.contains("versionName = \"0.15.4."))
        assertTrue(Regex("FUSH_DB_SCHEMA_VERSION\\s*=\\s*(\\d+)").find(db)?.groupValues?.get(1)?.toInt() ?: 0 >= 49)
        assertFalse(db.contains("fallbackToDestructiveMigration"))
    }
}
