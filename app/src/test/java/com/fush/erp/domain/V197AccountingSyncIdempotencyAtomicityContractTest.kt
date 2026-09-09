package com.fush.erp.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class V197AccountingSyncIdempotencyAtomicityContractTest {
    private fun source(path: String): String = File("src/main/java/$path").readText()

    @Test
    fun legacyProductionIssueIsNotProtectedByInvalidUniqueSourceKey() {
        assertFalse("PRODUCTION_ISSUE" in AccountingPostingIdempotencyPolicy.replaySafeSourceTypes)
    }

    @Test
    fun additionalMaterialIssueUsesCorrectionSourceGoingForward() {
        val production = source("com/fush/erp/domain/ProductionService.kt")
        assertTrue(production.contains("postAdditionalMaterialIssueJournal(order, totalAddedCost, reason, createdBy, now)"))
        val helper = production.substringAfter("private suspend fun postAdditionalMaterialIssueJournal").substringBefore("private suspend fun postMaterialIssueJournal")
        assertTrue(helper.contains("sourceType = \"PROD_ISSUE_CORR\""))
        assertFalse(helper.contains("sourceType = \"PRODUCTION_ISSUE\""))
    }

    @Test
    fun cloudPublishesAllJournalsBeforeAnyVoucherBatch() {
        val engine = source("com/fush/erp/cloud/AccountingCloudSyncEngine.kt")
        val block = engine.substringAfter("private fun publishBatch").substringBefore("private fun publishAccountingChunks")
        val journalPhase = block.indexOf("journals = journals, vouchers = emptyList()")
        val voucherPhase = block.indexOf("journals = emptyList(), vouchers = vouchers")
        assertTrue(journalPhase >= 0)
        assertTrue(voucherPhase > journalPhase)
    }

    @Test
    fun duplicateSourceIsConvertedToConflictBeforeSqliteAbort() {
        val engine = source("com/fush/erp/cloud/AccountingCloudSyncEngine.kt")
        val block = engine.substringAfter("AccountingCloudHydrationSourceIdentityPolicy.sourceIdForHydration").substringBefore("db.withTransaction")
        assertTrue(block.contains("AccountingPostingIdempotencyPolicy.replaySafeSourceTypes"))
        assertTrue(block.contains("bySourceNormalized"))
        assertTrue(block.contains("source_identity"))
    }
}
