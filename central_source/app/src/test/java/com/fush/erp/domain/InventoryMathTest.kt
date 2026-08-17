package com.fush.erp.domain

import org.junit.Assert.*
import org.junit.Test

class InventoryMathTest {
    @Test fun variance_is_counted_minus_system() {
        assertEquals(5.0, InventoryMath.variance(100.0, 105.0), 1e-9)
        assertEquals(-7.0, InventoryMath.variance(100.0, 93.0), 1e-9)
    }

    @Test fun negative_count_is_rejected() {
        assertThrows(IllegalArgumentException::class.java) { InventoryMath.variance(10.0, -1.0) }
    }

    @Test fun reorder_is_triggered_at_or_below_level() {
        assertTrue(InventoryMath.needsReorder(25.0, 25.0))
        assertTrue(InventoryMath.needsReorder(24.99, 25.0))
        assertFalse(InventoryMath.needsReorder(25.01, 25.0))
    }

    @Test fun expiry_alerts_are_classified() {
        val now = 1_000_000_000L
        assertTrue(InventoryMath.isExpired(now - 1, now))
        assertTrue(InventoryMath.isNearExpiry(now + 10 * 86_400_000L, now, 30))
        assertFalse(InventoryMath.isNearExpiry(now + 40 * 86_400_000L, now, 30))
    }
}
