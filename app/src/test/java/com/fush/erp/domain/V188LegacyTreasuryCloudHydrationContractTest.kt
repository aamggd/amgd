package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V188LegacyTreasuryCloudHydrationContractTest {
    private fun source(relative: String): String {
        val candidates = listOf(File("src/main/java/$relative"), File("app/src/main/java/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText() ?: error("Source file not found: $relative")
    }

    @Test
    fun legacyTreasuryCloudJournalUsesTemporaryStagingAliasThenRestoresHistoricalType() {
        val engine = source("com/fush/erp/cloud/AccountingCloudSyncEngine.kt")
        val start = engine.indexOf("private suspend fun applyJournalFromCloud")
        val end = engine.indexOf("private suspend fun resolveLocalSourceId", start)
        assertTrue(start >= 0 && end > start)
        val block = engine.substring(start, end)

        assertTrue(block.contains("LegacyTreasuryCloudHydrationPolicy.requiresCompatibilityHydration(sourceType)"))
        assertTrue(block.contains("AccountingCloudHydrationSourceIdentityPolicy.sourceIdForHydration"))
        val identityPolicy = source("com/fush/erp/domain/AccountingCloudHydrationSourceIdentityPolicy.kt")
        assertTrue(identityPolicy.contains("LegacyTreasuryCloudHydrationPolicy.stableHistoricalSourceId"))
        assertTrue(block.contains("CloudPostedJournalHydrationPolicy.STAGING_ALIAS_SOURCE_TYPE"))
        assertFalse(block.contains("sourceType = if (legacyTreasuryHydration) \"MANUAL\" else sourceType"))
        assertFalse(block.contains("sourceType = if (legacyTreasuryHydration)"))
        assertTrue(block.contains("status = \"STAGING\""))
        assertTrue(block.contains("finalizeCloudHydrationToPosted"))
        assertFalse(block.contains("transitionStagingToPosted(entryId)"))
    }

    @Test
    fun databaseFailClosedGuardForNewLegacyTreasuryPostingIsNotWeakened() {
        val guards = source("com/fush/erp/data/AccountingIdempotencyDatabaseGuards.kt")
        assertTrue(guards.contains("trg_journal_unstable_source_blocked_insert"))
        assertTrue(guards.contains("ACCOUNTING_SOURCE_REQUIRES_STABLE_EVENT_ID"))
        assertFalse(guards.contains("NEW.status <> 'STAGING'"))
    }

    @Test
    fun v188IsSchemaPreservingUpdate() {
        val gradle = listOf(File("build.gradle.kts"), File("app/build.gradle.kts")).first { it.isFile }.readText()
        val db = source("com/fush/erp/data/FushDatabase.kt")
        val versionCode = Regex("versionCode\\s*=\\s*(\\d+)").find(gradle)?.groupValues?.get(1)?.toInt()
            ?: error("versionCode not found")
        assertTrue(versionCode >= 188)
        assertTrue(Regex("FUSH_DB_SCHEMA_VERSION\\s*=\\s*(\\d+)").find(db)?.groupValues?.get(1)?.toInt() ?: 0 >= 49)
        assertFalse(db.contains("fallbackToDestructiveMigration"))
    }
}
