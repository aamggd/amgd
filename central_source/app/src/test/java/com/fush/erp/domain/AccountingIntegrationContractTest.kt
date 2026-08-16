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
    fun p1DoesNotBlindlyTreatEveryCurrentSourceIdAsIdempotent() {
        val gaps = AccountingIntegrationContract.p1ReferenceGaps().map { it.sourceType }.toSet()
        assertTrue("SALES_COMMISSION" in gaps)
        assertTrue("COMMISSION_REVERSAL" in gaps)
        assertTrue("PROD_ISSUE_CORR" in gaps)
        assertTrue("PROD_OUTPUT_CORR" in gaps)
        assertTrue("TREASURY_EXPENSE" in gaps)
        assertTrue("YEAR_END_CLOSE" in gaps)
        assertTrue("FIXED_ASSET_DISPOSAL" in gaps)

        val safe = AccountingIntegrationContract.p1StableKeyCandidates().map { it.sourceType }.toSet()
        assertTrue("SALE" in safe)
        assertTrue("CUSTOMER_RECEIPT" in safe)
        assertTrue("PURCHASE" in safe)
        assertTrue("SUPPLIER_PAYMENT" in safe)
        assertTrue("INVENTORY_COUNT" in safe)
        assertTrue("PRODUCTION_RECEIPT" in safe)
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
