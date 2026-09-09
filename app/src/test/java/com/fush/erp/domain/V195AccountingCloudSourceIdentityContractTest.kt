package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V195AccountingCloudSourceIdentityContractTest {
    private fun source(relative: String): String {
        val candidates = listOf(File("src/main/java/$relative"), File("app/src/main/java/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText() ?: error("Source file not found: $relative")
    }

    @Test
    fun cloudEngineNeverFeedsRawNullableResolverResultIntoJournalInsert() {
        val engine = source("com/fush/erp/cloud/AccountingCloudSyncEngine.kt")
        val start = engine.indexOf("private suspend fun applyJournalFromCloud")
        val end = engine.indexOf("private suspend fun resolveLocalSourceId", start)
        assertTrue(start >= 0 && end > start)
        val block = engine.substring(start, end)

        assertTrue(block.contains("AccountingCloudHydrationSourceIdentityPolicy.sourceIdForHydration"))
        assertTrue(block.contains("resolvedLocalSourceId = resolveLocalSourceId"))
        assertTrue(block.contains("sourceId = sourceId"))
        assertTrue(block.contains("sourceType in AccountingPostingIdempotencyPolicy.registeredSourceTypes"))
        assertTrue(block.contains("sourceType != LegacyTreasuryCloudHydrationPolicy.STAGING_ALIAS_SOURCE_TYPE"))
        assertFalse(block.contains("} else null\n        db.withTransaction"))
    }

    @Test
    fun databaseSourceIdGuardRemainsFailClosed() {
        val guard = source("com/fush/erp/data/AccountingIdempotencyDatabaseGuards.kt")
        assertTrue(guard.contains("ACCOUNTING_SOURCE_ID_REQUIRED"))
        assertTrue(guard.contains("NEW.sourceId IS NULL"))
        assertTrue(guard.contains("LENGTH(TRIM(NEW.sourceId)) = 0"))
    }

    @Test
    fun schemaAndAppIdentityRemainUpgradeCompatible() {
        val gradle = listOf(File("build.gradle.kts"), File("app/build.gradle.kts")).first { it.isFile }.readText()
        val db = source("com/fush/erp/data/FushDatabase.kt")
        val versionCode = Regex("versionCode\\s*=\\s*(\\d+)").find(gradle)?.groupValues?.get(1)?.toInt()
            ?: error("versionCode not found")
        assertTrue(versionCode >= 195)
        assertTrue(gradle.contains("versionName ="))
        assertTrue(Regex("FUSH_DB_SCHEMA_VERSION\\s*=\\s*(\\d+)").find(db)?.groupValues?.get(1)?.toInt() ?: 0 >= 49)
        assertFalse(db.contains("fallbackToDestructiveMigration"))
    }
}
