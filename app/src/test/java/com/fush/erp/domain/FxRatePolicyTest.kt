package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FxRatePolicyTest {
    @Test
    fun varianceUsesPrimaryAsAccountingReference() {
        assertEquals(0.0, fxVariancePercent(533.0, 533.0), 0.000001)
        assertEquals(1.0, fxVariancePercent(100.0, 101.0), 0.000001)
        assertEquals(4.0, fxVariancePercent(100.0, 104.0), 0.000001)
    }

    @Test
    fun nonPositiveRatesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { fxVariancePercent(0.0, 1.0) }
        assertThrows(IllegalArgumentException::class.java) { fxVariancePercent(1.0, 0.0) }
    }
}
