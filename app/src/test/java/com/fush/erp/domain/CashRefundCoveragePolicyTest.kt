package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CashRefundCoveragePolicyTest {
    @Test fun allowsRefundOnlyWithinNetActualCash() {
        CashRefundCoveragePolicy.requireCovered(20_000.0, 0.0, 20_000.0, "sales")
        assertThrows(IllegalArgumentException::class.java) {
            CashRefundCoveragePolicy.requireCovered(20_000.0, 0.0, 50_000.0, "sales")
        }
    }

    @Test fun priorRefundsReduceRemainingCashCeiling() {
        assertEquals(5_000.0, CashRefundCoveragePolicy.availableBase(20_000.0, 15_000.0), 0.0001)
        assertThrows(IllegalArgumentException::class.java) {
            CashRefundCoveragePolicy.requireCovered(20_000.0, 15_000.0, 6_000.0, "sales")
        }
    }

    @Test fun reversalCannotLeaveRefundsUnsupported() {
        assertThrows(IllegalArgumentException::class.java) {
            CashRefundCoveragePolicy.requireCovered(0.0, 20_000.0, 0.0, "reversal")
        }
    }
}
