package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V177AccountingBidirectionalCloudSyncContractTest {
    private fun source(relative: String): String {
        val candidates = listOf(File("src/main/java/$relative"), File("app/src/main/java/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText() ?: error("Source file not found: $relative")
    }

    @Test
    fun releaseIdentityKeepsRoom46AndUpdateSafety() {
        val gradle = listOf(File("app/build.gradle.kts"), File("build.gradle.kts")).first { it.isFile }.readText()
        val db = source("com/fush/erp/data/FushDatabase.kt")
        assertTrue(gradle.contains("applicationId = \"com.fush.erp.recovery\""))
        val versionCode = Regex("versionCode\\s*=\\s*(\\d+)").find(gradle)?.groupValues?.get(1)?.toInt() ?: 0
        assertTrue(versionCode >= 177)
        assertTrue((Regex("FUSH_DB_SCHEMA_VERSION\\s*=\\s*(\\d+)").find(db)?.groupValues?.get(1)?.toInt() ?: 0) >= 46)
        assertFalse(db.contains("fallbackToDestructiveMigration"))
    }

    @Test
    fun journalAndTreasurySyncUseStableBusinessKeysAndAtomicPayloads() {
        val engine = source("com/fush/erp/cloud/AccountingCloudSyncEngine.kt")
        listOf(
            "entry_no", "lines", "account_code", "debit_scaled", "credit_scaled",
            "voucher_no", "treasury_code", "offset_account_code", "journal_entry_no",
            "fush_publish_accounting_batch"
        ).forEach { assertTrue("Missing accounting sync contract: $it", engine.contains(it)) }
        assertTrue(engine.contains("debitTotal != creditTotal"))
        assertTrue(engine.contains("AccountingPrecision.amountToDouble"))
    }

    @Test
    fun syncHasExplicitConflictResolutionAndNoSilentLastWriteWins() {
        val engine = source("com/fush/erp/cloud/AccountingCloudSyncEngine.kt")
        val models = source("com/fush/erp/cloud/AccountingCloudSyncModels.kt")
        assertTrue(models.contains("KEEP_LOCAL"))
        assertTrue(models.contains("USE_CLOUD"))
        assertTrue(engine.contains("resolveConflict"))
        assertTrue(engine.contains("fush_resolve_accounting_conflict"))
        assertTrue(engine.contains("journalDifferences"))
        assertTrue(engine.contains("voucherDifferences"))
        assertFalse(engine.contains("resolution=merge-duplicates"))
    }

    @Test
    fun treasuryTruthIsReconciledAgainstCloudGeneralLedger() {
        val engine = source("com/fush/erp/cloud/AccountingCloudSyncEngine.kt")
        assertTrue(engine.contains("treasuryBalanceCheck"))
        assertTrue(engine.contains("treasuryBookBalance"))
        assertTrue(engine.contains("treasuryBalanceDifferenceBase"))
    }

    @Test
    fun repositoryUiAndForegroundSyncExposeAccountingDomain() {
        val repository = source("com/fush/erp/cloud/CloudSyncRepository.kt")
        val screen = source("com/fush/erp/ui/screens/CloudSyncScreen.kt")
        val home = source("com/fush/erp/ui/screens/HomeShell.kt")
        assertTrue(repository.contains("syncAccounting"))
        assertTrue(repository.contains("resolveAccountingConflict"))
        assertTrue(screen.contains("cloud_accounting_sync_now"))
        assertTrue(screen.contains("AccountingConflictCard"))
        assertTrue(home.contains("syncAccounting(user)"))
    }

    @Test
    fun supabaseMigrationImplementsImmutableCompareAndConflictLedger() {
        val candidates = listOf(
            File("V177_ACCOUNTING_BIDIRECTIONAL_SYNC.sql"),
            File("supabase/V177_ACCOUNTING_BIDIRECTIONAL_SYNC.sql"),
            File("../V177_ACCOUNTING_BIDIRECTIONAL_SYNC.sql"),
            File("../supabase/V177_ACCOUNTING_BIDIRECTIONAL_SYNC.sql")
        )
        val sql = candidates.first { it.isFile }.readText()
        assertTrue(sql.contains("fush_tx_gl_journals"))
        assertTrue(sql.contains("fush_tx_treasury_vouchers"))
        assertTrue(sql.contains("fush_accounting_sync_conflicts"))
        assertTrue(sql.contains("existing = p"))
        assertTrue(sql.contains("KEEP_LOCAL"))
        assertTrue(sql.contains("KEEP_CLOUD"))
        assertTrue(sql.contains("debit_total"))
        assertTrue(sql.contains("credit_total"))
    }
}
