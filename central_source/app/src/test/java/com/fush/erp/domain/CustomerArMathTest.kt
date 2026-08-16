package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomerArMathTest {
    @Test
    fun receiptSplit_usesHistoricalInvoiceRateForReceivableAndCurrentRateForCash() {
        val split = CustomerArMath.receiptSplit(
            amountOriginal = 100.0,
            invoiceExchangeRate = 1500.0,
            receiptExchangeRate = 1600.0
        )
        assertEquals(150000.0, split.allocatedBase, 0.000001)
        assertEquals(160000.0, split.cashBase, 0.000001)
        assertEquals(10000.0, split.fxDifferenceBase, 0.000001)
    }

    @Test
    fun receiptSplit_negativeDifference_isFxLossOnCollection() {
        val split = CustomerArMath.receiptSplit(100.0, 1600.0, 1500.0)
        assertEquals(160000.0, split.allocatedBase, 0.000001)
        assertEquals(150000.0, split.cashBase, 0.000001)
        assertEquals(-10000.0, split.fxDifferenceBase, 0.000001)
    }

    @Test
    fun outstandingBase_neverDropsBelowZero() {
        assertEquals(0.0, CustomerArMath.outstandingBase(100.0, 20.0, 90.0), 0.000001)
        assertEquals(50.0, CustomerArMath.outstandingBase(100.0, 20.0, 30.0), 0.000001)
    }
}
