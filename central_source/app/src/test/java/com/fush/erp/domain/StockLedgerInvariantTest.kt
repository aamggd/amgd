package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StockLedgerInvariantTest {
    @Test
    fun balance_is_sum_of_movements_up_to_cutoff() {
        val points = listOf(
            StockLedgerPoint(100L, 100.0),
            StockLedgerPoint(200L, -20.0),
            StockLedgerPoint(300L, 15.0),
        )

        assertEquals(100.0, StockLedgerInvariant.balance(points, 150L), 1e-9)
        assertEquals(80.0, StockLedgerInvariant.balance(points, 250L), 1e-9)
        assertEquals(95.0, StockLedgerInvariant.balance(points, 300L), 1e-9)
    }

    @Test
    fun later_movements_do_not_change_historical_balance() {
        val original = listOf(
            StockLedgerPoint(100L, 100.0),
            StockLedgerPoint(200L, -20.0),
        )
        val withLaterMovement = original + StockLedgerPoint(400L, -50.0)

        assertEquals(
            StockLedgerInvariant.balance(original, 250L),
            StockLedgerInvariant.balance(withLaterMovement, 250L),
            1e-9,
        )
    }

    @Test
    fun same_timestamp_movements_are_all_included() {
        val points = listOf(
            StockLedgerPoint(100L, 10.0),
            StockLedgerPoint(100L, -2.5),
            StockLedgerPoint(100L, 1.5),
        )

        assertEquals(9.0, StockLedgerInvariant.balance(points, 100L), 1e-9)
    }

    @Test
    fun near_zero_balance_is_normalized_to_zero() {
        val points = listOf(
            StockLedgerPoint(100L, 1.0),
            StockLedgerPoint(200L, -1.0 + 1e-12),
        )

        assertEquals(0.0, StockLedgerInvariant.balance(points), 0.0)
    }

    @Test
    fun invalid_movement_values_are_rejected() {
        assertThrows(IllegalArgumentException::class.java) {
            StockLedgerInvariant.balance(listOf(StockLedgerPoint(100L, Double.NaN)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            StockLedgerInvariant.balance(listOf(StockLedgerPoint(-1L, 1.0)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            StockLedgerInvariant.balance(emptyList(), -1L)
        }
    }
}
