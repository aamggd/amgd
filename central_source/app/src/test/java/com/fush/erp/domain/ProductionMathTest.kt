package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProductionMathTest {
    @Test
    fun `recipe quantities scale with planned output`() {
        assertEquals(5.0, ProductionMath.scaleQuantity(2.5, 360.0, 720.0), 1e-9)
        assertEquals(150.0, ProductionMath.scaleQuantity(75.0, 360.0, 720.0), 1e-9)
    }

    @Test
    fun fixedBatchRecipeKeepsSameMaterialsWhenExpectedYieldChanges() {
        assertEquals(2.5, ProductionMath.fixedBatchComponentQuantity(2.5, 360.0), 1e-9)
        assertEquals(2.5, ProductionMath.fixedBatchComponentQuantity(2.5, 372.0), 1e-9)
        assertEquals(2.5, ProductionMath.fixedBatchComponentQuantity(2.5, 350.0), 1e-9)
    }

    @Test
    fun `actual unit cost includes materials and labor`() {
        val unit = ProductionMath.actualUnitCost(72_776.58, 30_000.0, 360.0)
        assertEquals(285.4905, unit, 1e-4)
    }

    @Test
    fun `accepted plus rejected cannot exceed actual output`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProductionMath.validateOutput(actualOutput = 360.0, accepted = 350.0, rejected = 20.0, scrap = 0.0)
        }
    }

    @Test
    fun `variance percentage is calculated against standard`() {
        assertEquals(5.0, ProductionMath.variancePct(378.0, 360.0), 1e-9)
    }

    @Test
    fun `accepted batch correction is split between inventory and net sold cogs`() {
        val split = ProductionMath.splitAcceptedBatchCostCorrection(36_000.0, acceptedQtyBase = 360.0, onHandQtyBase = 120.0)
        assertEquals(12_000.0, split.inventoryReductionBase, 1e-9)
        assertEquals(24_000.0, split.cogsReductionBase, 1e-9)
    }

    @Test
    fun `accepted batch correction rejects impossible on hand quantity`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProductionMath.splitAcceptedBatchCostCorrection(10_000.0, acceptedQtyBase = 360.0, onHandQtyBase = 361.0)
        }
    }


    @Test
    fun rejectsProductionMaterialOutsideBom() {
        val bom = listOf(ProductionBomLink(1, 101), ProductionBomLink(2, 102))
        val order = listOf(ProductionBomLink(1, 101), ProductionBomLink(2, 999))
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            ProductionMath.validateBomIntegrity(bom, order)
        }
    }

    @Test
    fun acceptsExactBomMaterialLinks() {
        val bom = listOf(ProductionBomLink(1, 101), ProductionBomLink(2, 102))
        val order = listOf(ProductionBomLink(2, 102), ProductionBomLink(1, 101))
        ProductionMath.validateBomIntegrity(bom, order)
    }

    @Test
    fun lotTrackedMaterialRequiresLotNumber() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            ProductionMath.validateIssueLotTracking(true, false, null, null)
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            ProductionMath.validateIssueLotTracking(true, false, "   ", null)
        }
        ProductionMath.validateIssueLotTracking(true, false, "LOT-001", null)
    }

    @Test
    fun expiryTrackedMaterialRequiresExpiryDate() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            ProductionMath.validateIssueLotTracking(false, true, null, null)
        }
        ProductionMath.validateIssueLotTracking(false, true, null, 1_800_000_000_000L)
    }

    @Test
    fun expiryTrackingErrorNamesTheMaterialWhenContextIsProvided() {
        val error = org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            ProductionMath.validateIssueLotTracking(
                lotTracked = false,
                expiryTracked = true,
                lotNo = null,
                expiryDate = null,
                itemLabel = "البطاط (ITM-0001)"
            )
        }
        assertEquals(
            "المادة: البطاط (ITM-0001) — تتطلب تاريخ صلاحية صالح قبل صرفها للإنتاج",
            error.message
        )
    }
    @Test
    fun `labor cost input requires explicit per batch value`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProductionMath.parseDirectLaborCostInput("   ")
        }
    }

    @Test
    fun `labor cost accepts arbitrary per batch values including zero`() {
        assertEquals(12_750.0, ProductionMath.parseDirectLaborCostInput("12750"), 1e-9)
        assertEquals(0.0, ProductionMath.parseDirectLaborCostInput("0"), 1e-9)
    }

    @Test
    fun `labor cost rejects negative per batch value`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProductionMath.parseDirectLaborCostInput("-1")
        }
    }

    @Test
    fun `quality decision accepts values on inclusive limits`() {
        assertEquals("PASS", ProductionMath.qualityDecision(5.0, 5.0, 10.0))
        assertEquals("PASS", ProductionMath.qualityDecision(10.0, 5.0, 10.0))
    }

    @Test
    fun `quality decision rejects value outside limits`() {
        assertEquals("FAIL", ProductionMath.qualityDecision(4.99, 5.0, 10.0))
        assertEquals("FAIL", ProductionMath.qualityDecision(10.01, 5.0, 10.0))
    }

    @Test
    fun `quality specification requires at least one limit and valid target`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProductionMath.validateQualitySpecification(null, null, null, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProductionMath.validateQualitySpecification(10.0, 5.0, null, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProductionMath.validateQualitySpecification(5.0, 10.0, 11.0, 1)
        }
    }

    @Test
    fun `quality sample must meet required minimum`() {
        ProductionMath.validateQualitySampleSize(5, 5)
        assertThrows(IllegalArgumentException::class.java) {
            ProductionMath.validateQualitySampleSize(4, 5)
        }
    }

    @Test
    fun `quality sample passes only when every reading is within limits`() {
        val values = listOf(59.0, 59.5, 60.0, 60.5, 61.0)
        val summary = ProductionMath.summarizeQualitySamples(values, 59.0, 61.0)
        assertEquals(5, summary.passedCount)
        assertEquals(0, summary.failedCount)
        assertEquals("PASS", ProductionMath.qualitySampleDecision(values, 59.0, 61.0))
    }

    @Test
    fun `quality sample fails when one reading is outside limits`() {
        val values = listOf(59.5, 60.0, 61.1, 60.2)
        val summary = ProductionMath.summarizeQualitySamples(values, 59.0, 61.0)
        assertEquals(3, summary.passedCount)
        assertEquals(1, summary.failedCount)
        assertEquals("FAIL", ProductionMath.qualitySampleDecision(values, 59.0, 61.0))
    }

    @Test
    fun `packaging pack count rounds up by 24 bottles`() {
        assertEquals(16.0, ProductionMath.packagingPackCount(384.0, 24), 1e-9)
        assertEquals(18.0, ProductionMath.packagingPackCount(410.0, 24), 1e-9)
        assertEquals(1.0, ProductionMath.packagingPackCount(1.0, 24), 1e-9)
        assertEquals(0.0, ProductionMath.packagingPackCount(0.0, 24), 1e-9)
    }

    @Test
    fun `piece packaging correction rejects fractional bottle quantity`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProductionMath.requireWholePieceQuantity(410.5, "عدد العبوات النهائي")
        }
    }

}
