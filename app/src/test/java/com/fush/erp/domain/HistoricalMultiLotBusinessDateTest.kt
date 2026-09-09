package com.fush.erp.domain

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoricalMultiLotBusinessDateTest {
    private val zone = ZoneId.of("Asia/Aden")

    private fun at(day: Int, hour: Int = 0): Long =
        LocalDate.of(2026, 8, day).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun lot(key: String, no: String, qty: Double, safe: Double, unitCost: Double = 1.0) =
        HistoricalLotCapacity(key, no, qty, safe, unitCost)

    @Test
    fun multiLotHistoricalAllocation_takesSafeFromFirstThenContinuesFefo() {
        val plan = SalesHistoricalAllocationMath.plan(
            48.0,
            listOf(lot("A", "F60-260816-01", 92.0, 20.0), lot("B", "F60-260819-01", 361.0, 361.0))
        )
        assertTrue(plan.isComplete)
        assertEquals(2, plan.takes.size)
        assertEquals("A", plan.takes[0].key)
        assertEquals(20.0, plan.takes[0].quantityBase, 0.000001)
        assertEquals("B", plan.takes[1].key)
        assertEquals(28.0, plan.takes[1].quantityBase, 0.000001)
        assertEquals(48.0, plan.allocatedQtyBase, 0.000001)
    }

    @Test
    fun exactSafeBoundary_passes() {
        val plan = SalesHistoricalAllocationMath.plan(381.0, listOf(lot("A", "A", 100.0, 20.0), lot("B", "B", 400.0, 361.0)))
        assertTrue(plan.isComplete)
        assertEquals(381.0, plan.totalSafeQtyBase, 0.000001)
        assertEquals(381.0, plan.allocatedQtyBase, 0.000001)
    }

    @Test
    fun overSafeTotal_reportsShortageWithoutInventingCapacity() {
        val plan = SalesHistoricalAllocationMath.plan(382.0, listOf(lot("A", "A", 100.0, 20.0), lot("B", "B", 400.0, 361.0)))
        assertFalse(plan.isComplete)
        assertEquals(381.0, plan.totalSafeQtyBase, 0.000001)
        assertEquals(381.0, plan.allocatedQtyBase, 0.000001)
        assertEquals(1.0, plan.shortageQtyBase, 0.000001)
    }

    @Test
    fun firstLotUnsafe_doesNotAbortAndUsesNextLot() {
        val plan = SalesHistoricalAllocationMath.plan(50.0, listOf(lot("A", "A", 100.0, 0.0), lot("B", "B", 100.0, 100.0)))
        assertTrue(plan.isComplete)
        assertEquals(1, plan.takes.size)
        assertEquals("B", plan.takes.single().key)
        assertEquals(50.0, plan.takes.single().quantityBase, 0.000001)
    }

    @Test
    fun fefoOrder_isPreservedByInputOrder() {
        val plan = SalesHistoricalAllocationMath.plan(25.0, listOf(lot("EARLY", "EARLY", 50.0, 20.0), lot("LATE", "LATE", 50.0, 50.0)))
        assertEquals(listOf("EARLY", "LATE"), plan.takes.map { it.key })
        assertEquals(listOf(20.0, 5.0), plan.takes.map { it.quantityBase })
    }

    @Test
    fun cogsMultiLot_usesActualAllocatedLotCosts() {
        val plan = SalesHistoricalAllocationMath.plan(48.0, listOf(lot("A", "A", 92.0, 20.0, 289.0), lot("B", "B", 361.0, 361.0, 300.0)))
        assertEquals(14180.0, plan.totalCostBase, 0.000001)
    }

    @Test
    fun sameBusinessDay_usesClosingBalanceNotHiddenIntradayDip() {
        val points = listOf(
            WarehouseTransferBalancePoint(at(21, 12), 100.0),
            WarehouseTransferBalancePoint(at(22, 5), -80.0),
            WarehouseTransferBalancePoint(at(22, 6), 80.0),
            WarehouseTransferBalancePoint(at(23, 9), -10.0)
        )
        assertEquals(90.0, WarehouseTransferMath.minimumClosingBalanceFromBusinessDay(points, at(22), zone), 0.000001)
        // Timestamp semantics would have observed 20 during 22 Aug; business-day semantics intentionally does not.
    }

    @Test
    fun laterDayNegativeGuard_limitsToLowestLaterClosingBalance() {
        val points = listOf(
            WarehouseTransferBalancePoint(at(21, 12), 100.0),
            WarehouseTransferBalancePoint(at(23, 8), -40.0)
        )
        val safe = WarehouseTransferMath.minimumClosingBalanceFromBusinessDay(points, at(22), zone)
        assertEquals(60.0, safe, 0.000001)
        WarehouseTransferMath.validateHistoricalQuantity(60.0, safe)
        assertThrows(IllegalArgumentException::class.java) {
            WarehouseTransferMath.validateHistoricalQuantity(60.01, safe)
        }
    }

    @Test
    fun futureReceiptCannotCreateCapacityForEarlierBusinessDay() {
        val points = listOf(WarehouseTransferBalancePoint(at(23, 8), 100.0))
        assertEquals(0.0, WarehouseTransferMath.minimumClosingBalanceFromBusinessDay(points, at(22), zone), 0.000001)
    }

    @Test
    fun businessDatePolicy_sameDayIgnoresClockTime() {
        assertTrue(BusinessDatePolicy.sameBusinessDay(at(22, 0), at(22, 23), zone))
        assertTrue(BusinessDatePolicy.onOrAfter(at(22, 0), at(22, 23), zone))
        assertFalse(BusinessDatePolicy.onOrAfter(at(21, 23), at(22, 0), zone))
    }
}
