package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CashRefundSettlementPolicyTest {
    @Test
    fun uncollectedCreditSaleRejectsCashRefund() {
        assertThrows(IllegalArgumentException::class.java) {
            CashRefundSettlementPolicy.requireCashRefund(
                requestedRefundBase = 100.0,
                netCollectedBase = 0.0,
                cashRefundedBase = 0.0
            )
        }
    }

    @Test
    fun partiallyCollectedSaleLimitsRefundToActuallyCollectedAmount() {
        assertEquals(
            40.0,
            CashRefundSettlementPolicy.requireCashRefund(40.0, 40.0, 0.0),
            0.0
        )
        assertThrows(IllegalArgumentException::class.java) {
            CashRefundSettlementPolicy.requireCashRefund(40.01, 40.0, 0.0)
        }
    }

    @Test
    fun fullyCollectedSaleAllowsFullRefund() {
        assertEquals(
            100.0,
            CashRefundSettlementPolicy.requireCashRefund(100.0, 100.0, 0.0),
            0.0
        )
    }

    @Test
    fun priorRefundIsConsumedAndDuplicateRefundIsPrevented() {
        assertEquals(
            40.0,
            CashRefundSettlementPolicy.refundableCollectedBase(100.0, 60.0),
            0.0
        )
        CashRefundSettlementPolicy.requireCashRefund(40.0, 100.0, 60.0)
        assertThrows(IllegalArgumentException::class.java) {
            CashRefundSettlementPolicy.requireCashRefund(40.01, 100.0, 60.0)
        }
    }

    @Test
    fun receiptReversalCannotUndercutCashAlreadyRefunded() {
        assertThrows(IllegalArgumentException::class.java) {
            CashRefundSettlementPolicy.requireReceiptReversal(
                netCollectedBeforeBase = 100.0,
                reversingAllocationBase = 70.0,
                cashRefundedBase = 40.0
            )
        }
        assertEquals(
            50.0,
            CashRefundSettlementPolicy.requireReceiptReversal(
                netCollectedBeforeBase = 100.0,
                reversingAllocationBase = 50.0,
                cashRefundedBase = 40.0
            ),
            0.0
        )
    }

    @Test
    fun partialCashRefundKeepsArCashAndNetSaleReconciled() {
        val invoiceBase = 100.0
        val collectedBase = 40.0
        val cashRefundBase = 30.0
        val customerCreditReturnBase = 0.0
        val totalReturnBase = cashRefundBase + customerCreditReturnBase
        CashRefundSettlementPolicy.requireCashRefund(cashRefundBase, collectedBase, 0.0)

        val netSaleBase = invoiceBase - totalReturnBase
        val netCashBase = collectedBase - cashRefundBase
        val arBase = invoiceBase - collectedBase - customerCreditReturnBase
        assertEquals(netSaleBase, netCashBase + arBase, 0.0)
    }

    @Test
    fun fullSettlementAcrossCashRefundAndArCreditReconcilesToZero() {
        val invoiceBase = 100.0
        val collectedBase = 40.0
        val cashRefundBase = 40.0
        val customerCreditReturnBase = 60.0
        CashRefundSettlementPolicy.requireCashRefund(cashRefundBase, collectedBase, 0.0)

        val netSaleBase = invoiceBase - cashRefundBase - customerCreditReturnBase
        val netCashBase = collectedBase - cashRefundBase
        val arBase = invoiceBase - collectedBase - customerCreditReturnBase
        assertEquals(0.0, netSaleBase, 0.0)
        assertEquals(0.0, netCashBase, 0.0)
        assertEquals(0.0, arBase, 0.0)
        assertEquals(netSaleBase, netCashBase + arBase, 0.0)
    }

    @Test
    fun cashRefundReturnJournalEquationIsBalanced() {
        val salesReturnBase = 75.0
        val refundTreasuryCreditBase = 75.0
        val inventoryDebitBase = 25.0
        val cogsCreditBase = 25.0
        val debit = salesReturnBase + inventoryDebitBase
        val credit = refundTreasuryCreditBase + cogsCreditBase
        assertEquals(debit, credit, 0.0)
    }
}
