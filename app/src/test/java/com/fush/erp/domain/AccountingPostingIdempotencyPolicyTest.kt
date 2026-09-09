package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountingPostingIdempotencyPolicyTest {
    @Test
    fun replaySafeSourcesAreOnlyRegisteredStableBusinessEvents() {
        val expected = AccountingIntegrationContract.currentStableReplayCandidates()
            .mapTo(linkedSetOf()) { it.sourceType }
            .apply {
                // v197 compatibility: historical databases legitimately contain multiple
                // PRODUCTION_ISSUE rows per order because older correction flows reused the
                // original source type. New corrections use PROD_ISSUE_CORR.
                remove("PRODUCTION_ISSUE")
                addAll(TreasuryMovementType.entries.map { it.sourceType })
            }

        assertEquals(expected, AccountingPostingIdempotencyPolicy.replaySafeSourceTypes)
    }

    @Test
    fun stateGuardedEventsAreNotFalseDeduplicatedBySourceKey() {
        val stateGuarded = AccountingIntegrationContract.events
            .filter { it.replayPolicy == AccountingReplayPolicy.STATE_GUARDED }
            .mapTo(linkedSetOf()) { it.sourceType }

        assertTrue("YEAR_END_CLOSE" in stateGuarded)
        assertTrue("FIXED_ASSET_DISPOSAL" in stateGuarded)
        assertTrue("PROD_ISSUE_CORR" in stateGuarded)
        stateGuarded.forEach { sourceType ->
            assertFalse(sourceType in AccountingPostingIdempotencyPolicy.replaySafeSourceTypes)
            assertFalse(sourceType in AccountingPostingIdempotencyPolicy.blockedUnstableSourceTypes)
        }
    }

    @Test
    fun unstableSourcesAreExplicitlyFailClosedAtDatabaseBoundary() {
        val expected = AccountingIntegrationContract.events
            .filter { it.replayPolicy == AccountingReplayPolicy.NEEDS_STABLE_EVENT_ID }
            .mapTo(linkedSetOf()) { it.sourceType }

        assertEquals(expected, AccountingPostingIdempotencyPolicy.blockedUnstableSourceTypes)
        assertTrue("TREASURY_RECEIPT" in expected)
        assertFalse("FIXED_ASSET_ACQUISITION" in expected)
        assertFalse("OPENING_STOCK" in expected)
        assertFalse("YEAR_END_CLOSE" in expected)
        assertFalse("FIXED_ASSET_DISPOSAL" in expected)
        assertFalse("PROD_ISSUE_CORR" in expected)
        assertFalse("PROD_REJECT_CORR" in expected)
        assertFalse("PROD_COST_CORR" in expected)
        assertFalse("PROD_OUTPUT_CORR" in expected)
    }

    @Test
    fun manualSourcesRemainRegisteredButAreNotReplayDeduplicated() {
        val manualSources = setOf("MANUAL", "OPENING_STOCK", "FIXED_ASSET_ACQUISITION")
        manualSources.forEach { sourceType ->
            assertTrue(sourceType in AccountingPostingIdempotencyPolicy.registeredSourceTypes)
            assertTrue(sourceType !in AccountingPostingIdempotencyPolicy.replaySafeSourceTypes)
            assertTrue(sourceType !in AccountingPostingIdempotencyPolicy.blockedUnstableSourceTypes)
        }
    }

    @Test
    fun canonicalTreasuryVoucherSourcesAreReplaySafeWithStableOperationIds() {
        val expected = TreasuryMovementType.entries.mapTo(linkedSetOf()) { it.sourceType }

        assertEquals(expected, AccountingPostingIdempotencyPolicy.treasuryVoucherReplaySafeSourceTypes)
        expected.forEach { sourceType ->
            assertTrue(sourceType in AccountingPostingIdempotencyPolicy.registeredSourceTypes)
            assertTrue(sourceType in AccountingPostingIdempotencyPolicy.replaySafeSourceTypes)
            assertTrue(sourceType !in AccountingPostingIdempotencyPolicy.blockedUnstableSourceTypes)
        }
    }

    @Test
    fun transferAndOtherIncomeResolveToRegisteredCanonicalTreasurySources() {
        val transfer = TreasuryMovementTypePolicy.forVoucher("TRANSFER")
        val otherIncome = TreasuryMovementTypePolicy.forVoucher("INCOME")
        val otherReceipt = TreasuryMovementTypePolicy.forVoucher("RECEIPT", "NONE")

        assertEquals("TRANSFER", transfer.sourceType)
        assertEquals("ADJUSTMENT", otherIncome.sourceType)
        assertEquals("ADJUSTMENT", otherReceipt.sourceType)
        assertTrue(transfer.sourceType in AccountingPostingIdempotencyPolicy.registeredSourceTypes)
        assertTrue(otherIncome.sourceType in AccountingPostingIdempotencyPolicy.registeredSourceTypes)
        assertTrue(otherReceipt.sourceType in AccountingPostingIdempotencyPolicy.registeredSourceTypes)
        assertTrue(transfer.sourceType in AccountingPostingIdempotencyPolicy.replaySafeSourceTypes)
        assertTrue(otherIncome.sourceType in AccountingPostingIdempotencyPolicy.replaySafeSourceTypes)
        assertTrue(otherReceipt.sourceType in AccountingPostingIdempotencyPolicy.replaySafeSourceTypes)
    }
}
