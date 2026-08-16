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
    fun discountPolicyMatchesStudy() {
        assertEquals(2.0, SalesMath.maxAllowedDiscountPct("CASH", 480.0), 0.0001)
        assertEquals(3.0, SalesMath.maxAllowedDiscountPct("CASH", 2400.0), 0.0001)
        assertEquals(0.0, SalesMath.maxAllowedDiscountPct("CREDIT", 480.0), 0.0001)
        assertEquals(1.0, SalesMath.maxAllowedDiscountPct("CREDIT", 2400.0), 0.0001)
    }

    @Test
    fun creditCannotExceedThirtyDays() {
        SalesMath.validateCreditDays(30)
        assertThrows(IllegalArgumentException::class.java) { SalesMath.validateCreditDays(31) }
    }

    @Test
    fun floorUsesNetPriceAfterDiscount() {
        val line = SalesDraftLine(1, 1, 1.0, 480.0, 360000.0)
        assertEquals(750.0, SalesMath.effectiveBaseUnitPriceBase(line, 0.0, 1.0), 0.0001)
    }

    @Test
    fun commissionOnlyUsesCollectedAmount() {
        assertEquals(40000.0, SalesMath.commissionBase(400000.0), 0.0001)
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
    fun returnCannotExceedRemainingSaleQuantity() {
        SalesMath.validateReturn(3.0, 10.0, 6.0)
        assertThrows(IllegalArgumentException::class.java) { SalesMath.validateReturn(5.0, 10.0, 6.0) }
    }
}
