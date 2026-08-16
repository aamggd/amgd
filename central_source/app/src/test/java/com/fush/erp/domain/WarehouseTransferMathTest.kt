package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WarehouseTransferMathTest {
    @Test
    fun unitCost_preserves_lot_weighted_cost() {
        assertEquals(125.0, WarehouseTransferMath.unitCost(8.0, 1000.0), 0.000001)
    }

    @Test
    fun validateQuantity_accepts_exact_available_balance() {
        WarehouseTransferMath.validateQuantity(10.0, 10.0)
    }

    @Test
    fun validateQuantity_rejects_overdraw() {
        assertThrows(IllegalArgumentException::class.java) {
            WarehouseTransferMath.validateQuantity(10.01, 10.0)
        }
    }

    @Test
    fun validateQuantity_rejects_zero() {
        assertThrows(IllegalArgumentException::class.java) {
            WarehouseTransferMath.validateQuantity(0.0, 10.0)
        }
    }

    @Test
    fun minimumAvailableFrom_detects_later_historical_dip() {
        val points = listOf(
            WarehouseTransferBalancePoint(100L, 10.0),
            WarehouseTransferBalancePoint(200L, -8.0),
            WarehouseTransferBalancePoint(300L, 10.0)
        )
        assertEquals(2.0, WarehouseTransferMath.minimumAvailableFrom(points, 150L), 0.000001)
    }

    @Test
    fun minimumAvailableFrom_includes_balance_at_transfer_checkpoint_before_future_receipt() {
        val points = listOf(
            WarehouseTransferBalancePoint(100L, 10.0),
            WarehouseTransferBalancePoint(300L, 100.0),
            WarehouseTransferBalancePoint(400L, -90.0)
        )
        assertEquals(10.0, WarehouseTransferMath.minimumAvailableFrom(points, 200L), 0.000001)
    }

    @Test
    fun minimumAvailableFrom_uses_balance_after_existing_same_timestamp_rows() {
        val points = listOf(
            WarehouseTransferBalancePoint(100L, 10.0),
            WarehouseTransferBalancePoint(200L, -9.0),
            WarehouseTransferBalancePoint(200L, 9.0),
            WarehouseTransferBalancePoint(300L, -2.0)
        )
        assertEquals(8.0, WarehouseTransferMath.minimumAvailableFrom(points, 200L), 0.000001)
    }

    @Test
    fun validateHistoricalQuantity_rejects_backdated_transfer_that_breaks_later_balance() {
        assertThrows(IllegalArgumentException::class.java) {
            WarehouseTransferMath.validateHistoricalQuantity(5.0, 2.0)
        }
    }

    @Test
    fun validateHistoricalQuantity_accepts_safe_boundary() {
        WarehouseTransferMath.validateHistoricalQuantity(2.0, 2.0)
    }

    @Test
    fun reversalAvailability_requiresFullOriginalQuantity() {
        WarehouseTransferMath.validateReversalAvailability(10.0, 10.0)
        WarehouseTransferMath.validateReversalAvailability(10.0, 10.0000000001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun reversalAvailability_rejectsWhenDestinationWasConsumed() {
        WarehouseTransferMath.validateReversalAvailability(10.0, 9.5)
    }
}
