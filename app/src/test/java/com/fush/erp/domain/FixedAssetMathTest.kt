package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FixedAssetMathTest {
    @Test fun straightLineMonthlyDepreciation() {
        assertEquals(1500.0, FixedAssetMath.monthlyStraightLine(20000.0, 2000.0, 12), 0.000001)
    }

    @Test fun lastPeriodIsCappedAtResidualValue() {
        assertEquals(500.0, FixedAssetMath.depreciationForPeriod(20000.0, 2000.0, 12, 17500.0), 0.000001)
        assertEquals(0.0, FixedAssetMath.depreciationForPeriod(20000.0, 2000.0, 12, 18000.0), 0.000001)
    }

    @Test fun netBookValueAndDisposalGainLoss() {
        val nbv = FixedAssetMath.netBookValue(20000.0, 12000.0)
        assertEquals(8000.0, nbv, 0.000001)
        assertEquals(2000.0, FixedAssetMath.disposalGainLoss(10000.0, nbv), 0.000001)
        assertEquals(-3000.0, FixedAssetMath.disposalGainLoss(5000.0, nbv), 0.000001)
    }
}
