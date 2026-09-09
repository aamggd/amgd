package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V187CloudJournalStagingHydrationContractTest {
    private fun source(relative: String): String {
        val candidates = listOf(File("src/main/java/$relative"), File("app/src/main/java/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText() ?: error("Source file not found: $relative")
    }

    @Test
    fun cloudJournalHydrationUsesStagingThenBalancedPostTransition() {
        val engine = source("com/fush/erp/cloud/AccountingCloudSyncEngine.kt")
        val start = engine.indexOf("private suspend fun applyJournalFromCloud")
        val end = engine.indexOf("private suspend fun resolveLocalSourceId", start)
        assertTrue(start >= 0 && end > start)
        val block = engine.substring(start, end)
        assertTrue(block.contains("status = \"STAGING\""))
        assertTrue(block.contains("insertLinesRaw"))
        assertTrue(block.contains("finalizeCloudHydrationToPosted"))
        assertTrue(block.contains("CloudPostedJournalHydrationPolicy.STAGING_ALIAS_SOURCE_TYPE"))
        assertTrue(block.contains("CLOUD_JOURNAL_FAILED_STAGING_TO_POSTED"))
        assertFalse(block.contains("status = \"POSTED\""))
    }

    @Test
    fun postedCloudJournalIsNotMutatedInPlace() {
        val engine = source("com/fush/erp/cloud/AccountingCloudSyncEngine.kt")
        assertTrue(engine.contains("CLOUD_POSTED_JOURNAL_IMMUTABLE_USE_REVERSAL"))
        assertTrue(engine.contains("existing.status.equals(\"STAGING\", ignoreCase = true)"))
    }

    @Test
    fun v187SafetyBaselineRemainsPreservedByLaterUpdates() {
        val gradle = listOf(File("build.gradle.kts"), File("app/build.gradle.kts")).first { it.isFile }.readText()
        val db = source("com/fush/erp/data/FushDatabase.kt")
        val versionCode = Regex("versionCode\\s*=\\s*(\\d+)").find(gradle)?.groupValues?.get(1)?.toInt()
            ?: error("versionCode not found")
        assertTrue(versionCode >= 187)
        assertTrue(Regex("FUSH_DB_SCHEMA_VERSION\\s*=\\s*(\\d+)").find(db)?.groupValues?.get(1)?.toInt() ?: 0 >= 49)
        assertFalse(db.contains("fallbackToDestructiveMigration"))
    }
}
