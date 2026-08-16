package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountingP1IntegrityPolicyTest {
    @Test
    fun duplicateProtectionMatchesOnlyP0StableSources() {
        val expected = setOf(
            "CASH_COUNT_ADJUSTMENT",
            "FX_REVALUATION",
            "SALE",
            "CUSTOMER_RECEIPT",
            "SALES_RETURN",
            "PURCHASE",
            "PURCHASE_RETURN",
            "SUPPLIER_PAYMENT",
            "INVENTORY_COUNT",
            "PRODUCTION_ISSUE",
            "PRODUCTION_LABOR",
            "PRODUCTION_RECEIPT",
            "PRODUCTION_REJECT"
        )
        assertEquals(expected, AccountingP1IntegrityPolicy.duplicateProtectedSourceTypes)
    }

    @Test
    fun repeatableOrManualEventsAreNotFalselyDeduplicated() {
        assertFalse(AccountingP1IntegrityPolicy.isDuplicateProtected("MANUAL"))
        assertFalse(AccountingP1IntegrityPolicy.isDuplicateProtected("REVERSAL"))
        assertFalse(AccountingP1IntegrityPolicy.isDuplicateProtected("SALES_COMMISSION"))
        assertFalse(AccountingP1IntegrityPolicy.isDuplicateProtected("PROD_COST_CORR"))
        assertFalse(AccountingP1IntegrityPolicy.isDuplicateProtected("TREASURY_EXPENSE"))
    }

    @Test
    fun stableEventKeyIsNormalizedAndRequiresIdentity() {
        assertEquals(
            "SALE:42",
            AccountingP1IntegrityPolicy.stableEventKeyOrNull(" sale ", " 42 ")
        )
        assertNull(AccountingP1IntegrityPolicy.stableEventKeyOrNull("SALE", "  "))
        assertNull(AccountingP1IntegrityPolicy.stableEventKeyOrNull("MANUAL", "42"))
    }

    @Test
    fun doubleEntryValidatorStillRejectsImbalance() {
        AccountingValidator.validate(
            listOf(
                DraftJournalLine(1, 250.0, 0.0),
                DraftJournalLine(2, 0.0, 250.0)
            )
        )
        assertThrows(IllegalArgumentException::class.java) {
            AccountingValidator.validate(
                listOf(
                    DraftJournalLine(1, 250.0, 0.0),
                    DraftJournalLine(2, 0.0, 249.0)
                )
            )
        }
    }

    @Test
    fun stableAutomaticEventsHaveAReversalPath() {
        AccountingIntegrationContract.p1StableKeyCandidates().forEach { spec ->
            assertTrue(
                "${spec.sourceType} must have a reversal path",
                spec.reversalPolicy != AccountingReversalPolicy.NONE
            )
        }
    }
}
