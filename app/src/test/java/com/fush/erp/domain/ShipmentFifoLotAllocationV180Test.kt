package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShipmentFifoLotAllocationV180Test {
    @Test fun `requested quantity uses oldest lot first`() {
        val lots = listOf(
            ShipmentService.ShipmentLotAvailability("LOT-001", 30.0),
            ShipmentService.ShipmentLotAvailability("LOT-002", 50.0),
            ShipmentService.ShipmentLotAvailability("LOT-003", 100.0)
        )
        val out = ShipmentService.allocateOldestAvailableLots(lots, 20.0)
        assertEquals(listOf(ShipmentService.ShipmentLotAllocation("LOT-001", 20.0)), out)
    }

    @Test fun `quantity spills into next lot only after oldest is exhausted`() {
        val lots = listOf(
            ShipmentService.ShipmentLotAvailability("LOT-001", 30.0),
            ShipmentService.ShipmentLotAvailability("LOT-002", 50.0),
            ShipmentService.ShipmentLotAvailability("LOT-003", 100.0)
        )
        val out = ShipmentService.allocateOldestAvailableLots(lots, 60.0)
        assertEquals(2, out.size)
        assertEquals("LOT-001", out[0].lotNo)
        assertEquals(30.0, out[0].quantityBase, 0.000001)
        assertEquals("LOT-002", out[1].lotNo)
        assertEquals(30.0, out[1].quantityBase, 0.000001)
    }

    @Test fun `insufficient available inventory is rejected`() {
        val failure = runCatching {
            ShipmentService.allocateOldestAvailableLots(
                listOf(ShipmentService.ShipmentLotAvailability("LOT-001", 10.0)),
                12.0
            )
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message?.contains("المخزون المتاح غير كاف") == true)
    }
}
