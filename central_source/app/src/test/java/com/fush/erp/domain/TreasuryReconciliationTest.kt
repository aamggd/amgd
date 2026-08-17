package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TreasuryReconciliationTest {
    @Test
    fun cashCount_detectsBalancedShortageAndOverage() {
        assertEquals("BALANCED", TreasuryReconciliationMath.cashCount(100_000.0, 100_000.0).status)
        assertEquals(-2_000.0, TreasuryReconciliationMath.cashCount(100_000.0, 98_000.0).differenceBase, 0.001)
        assertEquals(1_500.0, TreasuryReconciliationMath.cashCount(100_000.0, 101_500.0).differenceBase, 0.001)
    }

    @Test
    fun bankReconciliation_acceptsOutstandingBookPayment() {
        val result = TreasuryReconciliationMath.bankReconciliation(
            openingBalanceBase = 10_000.0,
            closingBalanceBase = 12_000.0,
            statementLineAmounts = listOf(5_000.0, -3_000.0),
            bookClosingBalanceBase = 11_500.0,
            outstandingBookNetBase = -500.0
        )
        assertTrue(result.isBalanced)
        assertEquals(11_500.0, result.adjustedStatementClosingBase, 0.001)
        assertEquals(0.0, result.differenceBase, 0.001)
        assertEquals(0.0, result.arithmeticDifferenceBase, 0.001)
    }

    @Test
    fun bankReconciliation_rejectsBadStatementArithmetic() {
        val result = TreasuryReconciliationMath.bankReconciliation(
            openingBalanceBase = 10_000.0,
            closingBalanceBase = 12_500.0,
            statementLineAmounts = listOf(5_000.0, -3_000.0),
            bookClosingBalanceBase = 11_500.0,
            outstandingBookNetBase = -500.0
        )
        assertFalse(result.isBalanced)
        assertEquals(-500.0, result.arithmeticDifferenceBase, 0.001)
    }
}
