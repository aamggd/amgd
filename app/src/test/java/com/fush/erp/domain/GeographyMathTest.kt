package com.fush.erp.domain

import com.fush.erp.data.entity.ProvincePolicyEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class GeographyMathTest {
    @Test
    fun `old currency rate derives from two dollar markets`() {
        val rate = GeographyMath.oldYerToNewYerRate(1554.62, 535.0)
        assertEquals(1554.62 / 535.0, rate, 1e-9)
        assertEquals(480000.0 / 1554.62 * 535.0, GeographyMath.newYerToOldYer(480000.0, 1554.62, 535.0), 1e-9)
    }

    @Test
    fun `aden policy adds ten thousand per carton`() {
        val policy = ProvincePolicyEntity(
            code = "ADEN", nameAr = "عدن", currencyCode = "YER_NEW",
            defaultTransportPerCartonBase = 10_000.0
        )
        val q = GeographyMath.quote(policy, cartons = 3.0, productAmountNewBase = 1_200_000.0)
        assertEquals(30_000.0, q.transportOriginal, 1e-9)
        assertEquals(1_230_000.0, q.totalOriginal, 1e-9)
        assertEquals(1.0, q.exchangeRateToBase, 1e-9)
    }

    @Test
    fun `sanaa converts product then adds old currency charges`() {
        val policy = ProvincePolicyEntity(
            code = "SANAA", nameAr = "صنعاء", currencyCode = "YER_OLD", requiresDailyFx = true
        )
        val q = GeographyMath.quote(
            policy = policy,
            cartons = 1.0,
            productAmountNewBase = 480_000.0,
            usdNewYer = 1554.62,
            usdOldYer = 535.0,
            transportOverrideOriginal = 5_000.0,
            feesOriginal = 2_000.0,
            riskMarginOriginal = 3_000.0
        )
        val productOld = 480_000.0 / 1554.62 * 535.0
        assertEquals(productOld, q.productOriginal, 1e-6)
        assertEquals(productOld + 10_000.0, q.totalOriginal, 1e-6)
        assertEquals(q.totalOriginal * (1554.62 / 535.0), q.totalBaseEquivalent, 1e-6)
    }
}
