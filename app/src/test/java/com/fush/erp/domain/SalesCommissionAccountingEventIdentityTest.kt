package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SalesCommissionAccountingEventIdentityTest {
    @Test
    fun commissionUsesNamespacedImmutableCommissionRowId() {
        assertEquals("commission:41", SalesCommissionAccountingEventIdentity.commission(41L))
        assertNotEquals("41", SalesCommissionAccountingEventIdentity.commission(41L))
    }

    @Test
    fun returnReversalUsesNamespacedImmutableSalesReturnId() {
        assertEquals("sales-return:73", SalesCommissionAccountingEventIdentity.returnReversal(73L))
        assertNotEquals("73", SalesCommissionAccountingEventIdentity.returnReversal(73L))
    }

    @Test
    fun receiptReversalUsesNamespacedReceiptAndInvoiceCompositeIdentity() {
        assertEquals(
            "receipt-reversal:91:invoice:13",
            SalesCommissionAccountingEventIdentity.receiptReversal(91L, 13L)
        )
        assertNotEquals("91:13", SalesCommissionAccountingEventIdentity.receiptReversal(91L, 13L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidIdentityIsRejected() {
        SalesCommissionAccountingEventIdentity.commission(0L)
    }
}
