package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SettlementAllocationMathTest {
    @Test
    fun `fifo allocation spans oldest invoices and preserves historical rates`() {
        val plan = SettlementAllocationMath.allocateOldest(
            totalOriginal = 150.0,
            balances = listOf(
                SettlementAllocationMath.InvoiceBalance(1, 100_000.0, 1_000.0),
                SettlementAllocationMath.InvoiceBalance(2, 120_000.0, 1_200.0)
            )
        )
        assertEquals(2, plan.allocations.size)
        assertEquals(100.0, plan.allocations[0].amountOriginal, 1e-9)
        assertEquals(100_000.0, plan.allocations[0].allocatedBase, 1e-9)
        assertEquals(50.0, plan.allocations[1].amountOriginal, 1e-9)
        assertEquals(60_000.0, plan.allocations[1].allocatedBase, 1e-9)
        assertEquals(160_000.0, plan.totalAllocatedBase, 1e-9)
        val cash = SettlementAllocationMath.cashBase(plan.totalOriginal, 1_300.0)
        assertEquals(195_000.0, cash, 1e-9)
        assertEquals(35_000.0, SettlementAllocationMath.fxDifference(cash, plan.totalAllocatedBase), 1e-9)
    }

    @Test
    fun `allocation rejects amount above open invoices`() {
        assertThrows(IllegalArgumentException::class.java) {
            SettlementAllocationMath.allocateOldest(
                201.0,
                listOf(
                    SettlementAllocationMath.InvoiceBalance(1, 100_000.0, 1_000.0),
                    SettlementAllocationMath.InvoiceBalance(2, 120_000.0, 1_200.0)
                )
            )
        }
    }
}
