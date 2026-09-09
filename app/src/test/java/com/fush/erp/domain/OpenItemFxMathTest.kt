package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenItemFxMathTest {
    @Test fun arGainWhenClosingRateIncreases() {
        val r=OpenItemFxMath.revalue(OpenItemFxExposure("AR","USD",100.0,15000.0),160.0)
        assertEquals(1000.0,r.deltaBase,0.0001)
    }
    @Test fun apLossWhenClosingRateIncreasesUsesPositiveLiabilityDelta() {
        val r=OpenItemFxMath.revalue(OpenItemFxExposure("AP","USD",100.0,15000.0),160.0)
        assertEquals(1000.0,r.deltaBase,0.0001)
    }
    @Test fun noDeltaAtBookRate() {
        val r=OpenItemFxMath.revalue(OpenItemFxExposure("AR","USD",100.0,16000.0),160.0)
        assertEquals(0.0,r.deltaBase,0.0001)
    }
}
