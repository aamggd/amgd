package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ShipmentAllocationMathTest {
    @Test fun `70k shipment with ten packs allocates 35k to first five`() {
        assertEquals(35_000.0, ShipmentAllocationMath.proportionalExpenseAllocation(70_000.0, 0.0, 10.0, 5.0), 0.001)
    }

    @Test fun `second five can consume only remaining 35k`() {
        assertEquals(35_000.0, ShipmentAllocationMath.proportionalExpenseAllocation(70_000.0, 35_000.0, 10.0, 5.0), 0.001)
    }

    @Test fun `allocation can never exceed remaining expense`() {
        assertEquals(10_000.0, ShipmentAllocationMath.proportionalExpenseAllocation(70_000.0, 60_000.0, 10.0, 5.0), 0.001)
    }
}
