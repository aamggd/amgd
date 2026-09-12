package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupplierStockProvenancePlannerTest {
    @Test
    fun `sale splits one sku across supplier sources without mixing availability`() {
        val plan = SupplierStockProvenancePlanner.consume(
            12.0,
            listOf(
                SupplierStockSourceBalance(10L, 1L, 5.0, 100.0, 1000L, "EXACT"),
                SupplierStockSourceBalance(20L, 2L, 10.0, 110.0, 2000L, "EXACT")
            )
        )
        assertTrue(plan.isComplete)
        assertEquals(2, plan.takes.size)
        assertEquals(5.0, plan.takes[0].quantityBase, 1e-9)
        assertEquals(1L, plan.takes[0].supplierId)
        assertEquals(7.0, plan.takes[1].quantityBase, 1e-9)
        assertEquals(2L, plan.takes[1].supplierId)
    }

    @Test
    fun `planner reports shortage instead of borrowing from nonexistent supplier stock`() {
        val plan = SupplierStockProvenancePlanner.consume(
            8.0,
            listOf(SupplierStockSourceBalance(10L, 1L, 5.0, 100.0, 1000L, "EXACT"))
        )
        assertFalse(plan.isComplete)
        assertEquals(3.0, plan.shortageQtyBase, 1e-9)
    }

    @Test
    fun `restore never exceeds quantity consumed from original source allocation`() {
        val plan = SupplierStockProvenancePlanner.restore(
            6.0,
            listOf(
                SupplierStockRestorableAllocation(100L, 10L, 1L, 4.0, 100.0),
                SupplierStockRestorableAllocation(200L, 20L, 2L, 5.0, 110.0)
            )
        )
        assertTrue(plan.isComplete)
        assertEquals(4.0, plan.takes[0].quantityBase, 1e-9)
        assertEquals(2.0, plan.takes[1].quantityBase, 1e-9)
    }

    @Test
    fun `unattributed source remains unattributed`() {
        val plan = SupplierStockProvenancePlanner.consume(
            2.0,
            listOf(SupplierStockSourceBalance(30L, null, 3.0, 90.0, 0L, "UNATTRIBUTED"))
        )
        assertTrue(plan.isComplete)
        assertEquals(null, plan.takes.single().supplierId)
        assertEquals("UNATTRIBUTED", plan.takes.single().attributionStatus)
    }
}
