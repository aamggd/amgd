package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V189AccountantLegacyTreasurySyncRegressionTest {
    private fun source(relative: String): String {
        val candidates = listOf(File("src/main/java/$relative"), File("app/src/main/java/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText() ?: error("Source file not found: $relative")
    }

    @Test
    fun legacyTreasuryDownloadNeverUsesManualStagingAlias() {
        val engine = source("com/fush/erp/cloud/AccountingCloudSyncEngine.kt")
        val start = engine.indexOf("private suspend fun applyJournalFromCloud")
        val end = engine.indexOf("private suspend fun resolveLocalSourceId", start)
        assertTrue(start >= 0 && end > start)
        val block = engine.substring(start, end)

        assertTrue(block.contains("CloudPostedJournalHydrationPolicy.STAGING_ALIAS_SOURCE_TYPE"))
        assertFalse(block.contains("legacyTreasuryHydration) \"MANUAL\""))
        assertTrue(block.contains("finalizeCloudHydrationToPosted"))
        assertFalse(block.contains("transitionStagingToPosted(entryId)"))
    }

    @Test
    fun hydrationAliasIsFailClosedAndCannotRemainPosted() {
        val guards = source("com/fush/erp/data/AccountingIdempotencyDatabaseGuards.kt")
        assertTrue(guards.contains("trg_cloud_legacy_treasury_hydration_insert_shape"))
        assertTrue(guards.contains("CLOUD_LEGACY_TREASURY_HYDRATION_INVALID"))
        assertTrue(guards.contains("trg_cloud_legacy_treasury_hydration_never_posted"))
        assertTrue(guards.contains("CLOUD_LEGACY_TREASURY_HYDRATION_ALIAS_MUST_NOT_POST"))
        assertTrue(guards.contains("NOT LIKE 'cloud-legacy:%'"))
    }

    @Test
    fun v189KeepsSchemaAndUpdateIdentity() {
        val gradle = listOf(File("build.gradle.kts"), File("app/build.gradle.kts")).first { it.isFile }.readText()
        val db = source("com/fush/erp/data/FushDatabase.kt")
        val versionCode = Regex("versionCode\\s*=\\s*(\\d+)").find(gradle)?.groupValues?.get(1)?.toInt()
            ?: error("versionCode not found")
        assertTrue(versionCode >= 189)
        assertTrue(Regex("FUSH_DB_SCHEMA_VERSION\\s*=\\s*(\\d+)").find(db)?.groupValues?.get(1)?.toInt() ?: 0 >= 49)
        assertFalse(db.contains("fallbackToDestructiveMigration"))
    }
}
