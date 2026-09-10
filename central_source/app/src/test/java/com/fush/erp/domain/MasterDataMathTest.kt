package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MasterDataMathTest {
    @Test
    fun arbitraryPositiveConversionIsAccepted() {
        assertEquals(24.0, MasterDataMath.validateConversionFactor(24.0, false), 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroConversionIsRejected() {
        MasterDataMath.validateConversionFactor(0.0, false)
    }

    @Test(expected = IllegalArgumentException::class)
    fun baseUnitMustRemainOne() {
        MasterDataMath.validateConversionFactor(12.0, true)
    }

    @Test
    fun barcodeIsTrimmedAndBlankBecomesNull() {
        assertEquals("ABC-123", MasterDataMath.normalizeBarcode("  ABC-123  "))
        assertNull(MasterDataMath.normalizeBarcode("   "))
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonEmptyStockBlocksDeactivation() {
        MasterDataMath.requireEmptyBalanceForDeactivation(0.001, "المخزن")
    }

    @Test
    fun zeroStockAllowsDeactivation() {
        MasterDataMath.requireEmptyBalanceForDeactivation(0.0, "المخزن")
    }
}
