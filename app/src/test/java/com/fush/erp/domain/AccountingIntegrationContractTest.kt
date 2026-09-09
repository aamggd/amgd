package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountingIntegrationContractTest {
    @Test
    fun sourceTypesAreUniqueAndResolvable() {
        val events = AccountingIntegrationContract.events
        assertEquals(events.size, events.map { it.sourceType }.toSet().size)
        events.forEach {
            assertEquals(it, AccountingIntegrationContract.requireRegistered(it.sourceType))
            assertFalse(it.sourceReference.isBlank())
        }
    }

    @Test
    fun canonicalKeyIsDeterministicOnlyForRegisteredSource() {
        assertEquals("SALE:42", AccountingIntegrationContract.canonicalEventKey("sale", " 42 "))
        assertEquals("CUSTOMER_RECEIPT:7", AccountingIntegrationContract.canonicalEventKey("CUSTOMER_RECEIPT", "7"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownSourceTypeIsRejected() {
        AccountingIntegrationContract.canonicalEventKey("UNKNOWN_EVENT", "1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankSourceIdIsRejected() {
        AccountingIntegrationContract.canonicalEventKey("SALE", "  ")
    }

    @Test
    fun replayPolicyMatchesActualBusinessIdentityAndLifecycleGuards() {
        val gaps = AccountingIntegrationContract.p1ReferenceGaps().map { it.sourceType }.toSet()
        assertTrue("TREASURY_EXPENSE" in gaps)
        assertFalse("YEAR_END_CLOSE" in gaps)
        assertFalse("OPENING_STOCK" in gaps)
        assertFalse("FIXED_ASSET_ACQUISITION" in gaps)
        assertFalse("FIXED_ASSET_DISPOSAL" in gaps)
        assertFalse("PROD_ISSUE_CORR" in gaps)
        assertFalse("PROD_OUTPUT_CORR" in gaps)

        val safe = AccountingIntegrationContract.currentStableReplayCandidates().map { it.sourceType }.toSet()
        assertTrue("SALE" in safe)
        assertTrue("CUSTOMER_RECEIPT" in safe)
        assertTrue("SALES_COMMISSION" in safe)
        assertTrue("COMMISSION_REVERSAL" in safe)
        assertTrue("RECEIPT_COMMISSION_REVERSAL" in safe)
        assertTrue("ADDITIONAL_CHARGE" in safe)
        assertTrue("ADDITIONAL_CHARGE_PAYMENT" in safe)
        assertTrue("PURCHASE" in safe)
        assertTrue("SUPPLIER_PAYMENT" in safe)
        assertTrue("INVENTORY_COUNT" in safe)
        assertTrue("PRODUCTION_RECEIPT" in safe)

        assertEquals(AccountingReplayPolicy.STATE_GUARDED, AccountingIntegrationContract.requireRegistered("YEAR_END_CLOSE").replayPolicy)
        assertEquals(AccountingReplayPolicy.MANUAL_ONLY, AccountingIntegrationContract.requireRegistered("OPENING_STOCK").replayPolicy)
        assertEquals(AccountingReplayPolicy.MANUAL_ONLY, AccountingIntegrationContract.requireRegistered("FIXED_ASSET_ACQUISITION").replayPolicy)
        assertEquals(AccountingReplayPolicy.STATE_GUARDED, AccountingIntegrationContract.requireRegistered("FIXED_ASSET_DISPOSAL").replayPolicy)
        assertEquals(AccountingReplayPolicy.STATE_GUARDED, AccountingIntegrationContract.requireRegistered("PROD_ISSUE_CORR").replayPolicy)
        assertEquals(AccountingReplayPolicy.STATE_GUARDED, AccountingIntegrationContract.requireRegistered("PROD_REJECT_CORR").replayPolicy)
        assertEquals(AccountingReplayPolicy.STATE_GUARDED, AccountingIntegrationContract.requireRegistered("PROD_COST_CORR").replayPolicy)
        assertEquals(AccountingReplayPolicy.STATE_GUARDED, AccountingIntegrationContract.requireRegistered("PROD_OUTPUT_CORR").replayPolicy)
    }

    @Test
    fun historicalP1StableSnapshotRemainsFrozenAndReversible() {
        val expected = setOf(
            "CASH_COUNT_ADJUSTMENT", "FX_REVALUATION", "SALE", "CUSTOMER_RECEIPT",
            "SALES_RETURN", "PURCHASE", "PURCHASE_RETURN", "SUPPLIER_PAYMENT",
            "INVENTORY_COUNT", "PRODUCTION_ISSUE", "PRODUCTION_LABOR",
            "PRODUCTION_RECEIPT", "PRODUCTION_REJECT"
        )
        val snapshot = AccountingIntegrationContract.p1StableKeyCandidates()
        assertEquals(expected, snapshot.map { it.sourceType }.toSet())
        snapshot.forEach { spec ->
            assertEquals(AccountingReplayPolicy.STABLE_SOURCE, spec.replayPolicy)
            assertTrue(spec.reversalPolicy != AccountingReversalPolicy.NONE)
        }
    }

    @Test
    fun contractCoversAllOperationalDomainsInPlan() {
        val domains = AccountingIntegrationContract.events.map { it.domain }.toSet()
        assertNotNull(AccountingEventDomain.SALES.takeIf { it in domains })
        assertNotNull(AccountingEventDomain.PURCHASES.takeIf { it in domains })
        assertNotNull(AccountingEventDomain.INVENTORY.takeIf { it in domains })
        assertNotNull(AccountingEventDomain.PRODUCTION.takeIf { it in domains })
        assertNotNull(AccountingEventDomain.FIXED_ASSETS.takeIf { it in domains })
        assertNotNull(AccountingEventDomain.TREASURY.takeIf { it in domains })
        assertNotNull(AccountingEventDomain.ACCOUNTING.takeIf { it in domains })
    }
}
