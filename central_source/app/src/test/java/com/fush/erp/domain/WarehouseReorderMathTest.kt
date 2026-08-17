package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WarehouseReorderMathTest {
    @Test
    fun usableQuantity_excludesExpiredAndControlledLots() {
        val at = 1_000L
        val lots = listOf(
            WarehouseReorderMath.Lot(10.0, expiryDate = 2_000L, controlStatus = "ACCEPTED"),
            WarehouseReorderMath.Lot(7.0, expiryDate = 900L, controlStatus = "ACCEPTED"),
            WarehouseReorderMath.Lot(5.0, expiryDate = 2_000L, controlStatus = "QUARANTINE"),
            WarehouseReorderMath.Lot(3.0, expiryDate = null, controlStatus = "BLOCKED"),
            WarehouseReorderMath.Lot(4.0, expiryDate = null, controlStatus = "ACCEPTED")
        )
        assertEquals(14.0, WarehouseReorderMath.usableQuantity(lots, at), 0.000001)
    }

    @Test
    fun thresholdIsEvaluatedPerWarehouseBalance() {
        assertTrue(WarehouseReorderMath.needsReorder(4.0, 5.0))
        assertTrue(WarehouseReorderMath.needsReorder(5.0, 5.0))
        assertFalse(WarehouseReorderMath.needsReorder(12.0, 5.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeThresholdIsRejected() {
        WarehouseReorderMath.validateLevel(-1.0)
    }
}
