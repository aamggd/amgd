package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SalesMathTest {
    @Test
    fun cartonConversionAndTotalsAreCorrect() {
        val line = SalesDraftLine(1, 1, 2.0, 480.0, 384000.0)
        assertEquals(960.0, line.baseQuantity, 0.0001)
        assertEquals(768000.0, line.grossOriginal, 0.0001)
    }

    @Test
    fun standardSaleDiscountMustStayBelowOneHundredPercent() {
        SalesMath.validateDiscount("CASH", 1.0, 0.0)
        SalesMath.validateDiscount("CASH", 1.0, 25.0)
        SalesMath.validateDiscount("CREDIT", 1.0, 99.99)
        assertThrows(IllegalArgumentException::class.java) { SalesMath.validateDiscount("CASH", 1.0, 100.0) }
        assertThrows(IllegalArgumentException::class.java) { SalesMath.validateDiscount("CASH", 1.0, 101.0) }
    }

    @Test
    fun discountMathAndEffectivePriceRejectOneHundredPercent() {
        val line = SalesDraftLine(1, 1, 1.0, 1.0, 1_000.0)
        assertThrows(IllegalArgumentException::class.java) { SalesMath.discountOriginal(1_000.0, 100.0) }
        assertThrows(IllegalArgumentException::class.java) { SalesMath.effectiveBaseUnitPriceBase(line, 100.0, 1.0) }
        assertThrows(IllegalArgumentException::class.java) { SalesMath.totalOriginal(1_000.0, 1_000.0, 0.0, 0.0, 0.0) }
        assertEquals(0.1, SalesMath.discountOriginal(1_000.0, 0.01), 0.000001)
    }

    @Test
    fun creditTermComesFromCustomerConfiguration() {
        SalesMath.validateCreditDays(365)
        assertThrows(IllegalArgumentException::class.java) { SalesMath.validateCreditDays(0) }
    }

    @Test
    fun floorUsesNetPriceAfterDiscount() {
        val line = SalesDraftLine(1, 1, 1.0, 480.0, 360000.0)
        assertEquals(750.0, SalesMath.effectiveBaseUnitPriceBase(line, 0.0, 1.0), 0.0001)
    }

    @Test
    fun commissionOnlyUsesCollectedAmount() {
        assertEquals(40000.0, SalesMath.commissionBase(400000.0, 10.0), 0.0001)
    }

    @Test
    fun priceValidityRequiresActiveAndDateInsidePeriod() {
        val from = 1_000L
        val to = 2_000L
        assertEquals(true, SalesMath.isPriceValidAt(from, to, true, 1_500L))
        assertEquals(false, SalesMath.isPriceValidAt(from, to, false, 1_500L))
        assertEquals(false, SalesMath.isPriceValidAt(from, to, true, 2_001L))
    }

    @Test
    fun invalidPricePeriodIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SalesMath.validatePricePeriod(2_000L, 1_999L)
        }
    }

    @Test
    fun manualSalesPriceMayDifferFromConfiguredReference() {
        val manual = SalesDraftLine(1, 1, 2.0, 24.0, 23_500.0)
        SalesMath.validateLine(manual)
        assertEquals(47_000.0, manual.grossOriginal, 0.0001)
    }

    @Test
    fun zeroManualSalesPriceIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SalesMath.validateLine(SalesDraftLine(1, 1, 1.0, 1.0, 0.0))
        }
    }

    @Test
    fun paidReturnAllowsZeroFreeComponent() {
        SalesMath.validateReturnComponent(4.5, 10.0, 0.0, "كمية المرتجع المباعة")
        SalesMath.validateReturnComponent(0.0, 0.0, 0.0, "كمية المرتجع المجانية")
    }

    @Test
    fun freeOnlyReturnAllowsZeroPaidComponent() {
        SalesMath.validateReturnComponent(0.0, 10.0, 0.0, "كمية المرتجع المباعة")
        SalesMath.validateReturnComponent(2.0, 3.0, 0.0, "كمية المرتجع المجانية")
    }

    @Test
    fun returnComponentStillRejectsOverReturnAndNegativeValues() {
        assertThrows(IllegalArgumentException::class.java) {
            SalesMath.validateReturnComponent(5.0, 4.0, 0.0, "كمية المرتجع المباعة")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SalesMath.validateReturnComponent(-1.0, 4.0, 0.0, "كمية المرتجع المجانية")
        }
    }

    @Test
    fun returnCannotExceedRemainingSaleQuantity() {
        SalesMath.validateReturn(3.0, 10.0, 6.0)
        assertThrows(IllegalArgumentException::class.java) { SalesMath.validateReturn(5.0, 10.0, 6.0) }
    }
}
