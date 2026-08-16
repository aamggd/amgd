package com.fush.erp.domain

import com.fush.erp.data.entity.ExecutiveReportRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodComparisonMathTest {
    private fun executive(
        sales: Double = 0.0,
        returns: Double = 0.0,
        collections: Double = 0.0,
        purchases: Double = 0.0,
        purchaseReturns: Double = 0.0,
        inventory: Double = 0.0,
        receivables: Double = 0.0,
        overdue: Double = 0.0,
        maintenance: Double = 0.0
    ) = ExecutiveReportRow(
        grossSalesBase = sales,
        salesReturnsBase = returns,
        collectionsBase = collections,
        grossPurchasesBase = purchases,
        purchaseReturnsBase = purchaseReturns,
        inventoryValueBase = inventory,
        receivablesBase = receivables,
        overdueBase = overdue,
        productionOrders = 0,
        acceptedQtyBase = 0.0,
        accepted60QtyBase = 0.0,
        accepted200QtyBase = 0.0,
        scrapQtyBase = 0.0,
        openNonConformances = 0,
        maintenanceCostBase = maintenance
    )

    private fun pnl(revenue: Double, expenses: Double) = ProfitLossReport(
        revenue = revenue,
        expenses = expenses,
        netProfit = revenue - expenses,
        revenueByAccount = emptyList(),
        expenseByAccount = emptyList()
    )

    @Test
    fun `comparison calculates differences and percentages without inventing zero baseline percentage`() {
        val report = PeriodComparisonMath.build(
            currentExecutive = executive(sales = 120.0, returns = 20.0, collections = 80.0),
            previousExecutive = executive(sales = 80.0, returns = 0.0, collections = 0.0),
            currentProfitLoss = pnl(150.0, 100.0),
            previousProfitLoss = pnl(100.0, 80.0),
            currentFrom = 1000L,
            currentTo = 1999L,
            previousFrom = 0L,
            previousTo = 999L
        )

        val sales = report.metrics.first { it.label == "صافي المبيعات" }
        assertEquals(100.0, sales.currentBase, 0.001)
        assertEquals(80.0, sales.previousBase, 0.001)
        assertEquals(20.0, sales.differenceBase, 0.001)
        assertEquals(25.0, sales.percentChange!!, 0.001)

        val collections = report.metrics.first { it.label == "صافي التحصيل" }
        assertNull(collections.percentChange)
    }

    @Test
    fun `zero against zero is stable zero percent`() {
        assertEquals(0.0, PeriodComparisonMath.percentChange(0.0, 0.0)!!, 0.001)
    }

    @Test
    fun `rolling period previous range is contiguous and equal length`() {
        val currentFrom = 3_000_000L
        val currentTo = 4_000_000L
        val previous = PeriodComparisonMath.previousRange("30 يوم", currentFrom, currentTo)!!
        assertEquals(currentFrom - 1L, previous.second)
        assertEquals(currentTo - currentFrom, previous.second - previous.first)
    }

    @Test
    fun `all history has no invented previous period`() {
        assertNull(PeriodComparisonMath.previousRange("كل الفترة", 0L, 5_000L))
        assertTrue(PeriodComparisonMath.unavailable(0L, 5_000L).metrics.isEmpty())
    }
}
