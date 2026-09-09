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

    @Test
    fun settlementSplit_cash900_discount100_clears1000WithoutFakeFx() {
        val split = CustomerArMath.settlementSplit(
            cashOriginal = 900.0,
            discountOriginal = 100.0,
            invoiceExchangeRate = 1.0,
            receiptExchangeRate = 1.0
        )
        assertEquals(900.0, split.cashReceivableBase, 0.000001)
        assertEquals(100.0, split.discountBase, 0.000001)
        assertEquals(1000.0, split.settledReceivableBase, 0.000001)
        assertEquals(900.0, split.treasuryCashBase, 0.000001)
        assertEquals(0.0, split.fxDifferenceBase, 0.000001)
    }

    @Test
    fun settlementSplit_fxDifferenceAppliesToCashLegOnly() {
        val split = CustomerArMath.settlementSplit(
            cashOriginal = 900.0,
            discountOriginal = 100.0,
            invoiceExchangeRate = 1500.0,
            receiptExchangeRate = 1600.0
        )
        assertEquals(1_350_000.0, split.cashReceivableBase, 0.000001)
        assertEquals(150_000.0, split.discountBase, 0.000001)
        assertEquals(1_500_000.0, split.settledReceivableBase, 0.000001)
        assertEquals(1_440_000.0, split.treasuryCashBase, 0.000001)
        assertEquals(90_000.0, split.fxDifferenceBase, 0.000001)
    }

    @Test
    fun settlementSplit_crossCurrencyUsesReceiptRateToSettleReceivableWithoutFakeFx() {
        val split = CustomerArMath.settlementSplit(
            cashOriginal = 100.0,
            discountOriginal = 0.0,
            invoiceExchangeRate = 1.0,
            receiptExchangeRate = 2.91215,
            sameCurrency = false,
        )
        assertEquals(291.215, split.cashReceivableBase, 0.000001)
        assertEquals(291.215, split.treasuryCashBase, 0.000001)
        assertEquals(291.215, split.settledReceivableBase, 0.000001)
        assertEquals(0.0, split.fxDifferenceBase, 0.000001)
    }

}
