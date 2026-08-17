package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AeAcc010PurchaseReturnCashRefundTest {
    private val tolerance = 0.000001

    @Test
    fun unpaidCreditPurchaseHasNoCashRefundCapacity() {
        assertEquals(
            0.0,
            SupplierApMath.cashRefundableBase("CREDIT", 1000.0, 0.0, 0.0),
            tolerance
        )
    }

    @Test
    fun partiallyPaidPurchaseRefundIsBoundedByNetPaidAmount() {
        assertEquals(
            400.0,
            SupplierApMath.cashRefundableBase("CREDIT", 1000.0, 400.0, 0.0),
            tolerance
        )
        assertEquals(
            150.0,
            SupplierApMath.cashRefundableBase("CREDIT", 1000.0, 400.0, 250.0),
            tolerance
        )
    }

    @Test
    fun fullyPaidPurchaseCanRefundOnlyUpToInvoiceCashBackedAmount() {
        assertEquals(
            1000.0,
            SupplierApMath.cashRefundableBase("CREDIT", 1000.0, 1000.0, 0.0),
            tolerance
        )
        assertEquals(
            0.0,
            SupplierApMath.cashRefundableBase("CREDIT", 1000.0, 1000.0, 1000.0),
            tolerance
        )
    }

    @Test
    fun cashPurchaseUsesOriginalCashPaymentAsRefundCapacity() {
        assertEquals(
            700.0,
            SupplierApMath.cashRefundableBase("CASH", 1000.0, 0.0, 300.0),
            tolerance
        )
    }

    @Test
    fun paymentReversalCannotInvalidateAnExistingCashRefund() {
        assertFalse(SupplierApMath.canReverseSupplierPayment(500.0, 500.0, 300.0))
        assertTrue(SupplierApMath.canReverseSupplierPayment(800.0, 500.0, 300.0))
    }

    @Test
    fun apTreasuryAndReturnedInventoryRemainReconciledForCreditPurchase() {
        val invoiceBase = 1000.0
        val paidBase = 400.0
        val cashRefundBase = 300.0
        val apOutstanding = SupplierApMath.outstandingBase(invoiceBase, 0.0, paidBase)
        val netCashPaidAfterRefund = paidBase - cashRefundBase
        val inventoryRemainingAfterReturn = invoiceBase - cashRefundBase
        assertEquals(inventoryRemainingAfterReturn, apOutstanding + netCashPaidAfterRefund, tolerance)
    }
}
