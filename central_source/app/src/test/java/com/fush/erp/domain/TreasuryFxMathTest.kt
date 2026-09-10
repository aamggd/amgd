package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TreasuryFxMathTest {
    @Test
    fun revaluation_changes_base_value_without_changing_original_quantity() {
        val r = TreasuryFxMath.revaluation(
            originalBalance = 100.0,
            carryingBalanceBeforeBase = 150_000.0,
            rateToBase = 1_600.0
        )
        assertEquals(100.0, r.originalBalance, 0.000001)
        assertEquals(160_000.0, r.targetBalanceBase, 0.001)
        assertEquals(10_000.0, r.differenceBase, 0.001)
        assertTrue(r.needsJournal)
    }

    @Test
    fun foreign_cash_count_values_quantity_variance_at_count_date_rate() {
        val r = TreasuryFxMath.cashCountOriginal(100.0, 98.0, 1_600.0)
        assertEquals(-2.0, r.differenceOriginal, 0.000001)
        assertEquals(-3_200.0, r.varianceBase, 0.001)
        assertEquals("VARIANCE", r.status)
    }

    @Test
    fun foreign_bank_reconciliation_works_in_original_currency() {
        val r = TreasuryFxMath.bankReconciliationOriginal(
            currencyCode = "USD",
            openingBalanceOriginal = 100.0,
            closingBalanceOriginal = 120.0,
            statementLineAmountsOriginal = listOf(50.0, -30.0),
            bookClosingBalanceOriginal = 115.0,
            outstandingBookNetOriginal = -5.0
        )
        assertTrue(r.isBalanced)
        assertEquals(115.0, r.adjustedStatementClosingOriginal, 0.000001)
        assertEquals(0.0, r.differenceOriginal, 0.000001)
    }

    @Test
    fun foreign_bank_reconciliation_rejects_wrong_statement_arithmetic() {
        val r = TreasuryFxMath.bankReconciliationOriginal(
            currencyCode = "USD",
            openingBalanceOriginal = 100.0,
            closingBalanceOriginal = 125.0,
            statementLineAmountsOriginal = listOf(50.0, -30.0),
            bookClosingBalanceOriginal = 115.0,
            outstandingBookNetOriginal = -5.0
        )
        assertFalse(r.isBalanced)
        assertEquals(-5.0, r.arithmeticDifferenceOriginal, 0.000001)
    }
}
