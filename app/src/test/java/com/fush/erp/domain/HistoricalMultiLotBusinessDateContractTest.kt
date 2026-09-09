package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoricalMultiLotBusinessDateContractTest {
    private fun source(relative: String): String {
        val candidates = listOf(File("src/main/java/$relative"), File("app/src/main/java/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText() ?: error("Source file not found: $relative")
    }

    @Test
    fun salesPlansAllLotCapacityBeforeWritingAllocations() {
        val sales = source("com/fush/erp/domain/SalesService.kt")
        val capacityPos = sales.indexOf("val capacities = prepared.map")
        val planPos = sales.indexOf("val plan = if (preferredShipmentLots.isEmpty())", capacityPos)
        val requirePos = sales.indexOf("require(plan.isComplete)", planPos)
        val allocationPos = sales.indexOf("insertAllocation", requirePos)
        assertTrue(capacityPos >= 0)
        assertTrue(planPos > capacityPos)
        assertTrue(requirePos > planPos)
        assertTrue(allocationPos > requirePos)
        assertTrue(sales.contains("SalesHistoricalAllocationMath.plan(requiredBaseQty, capacities)"))
        assertTrue(sales.contains("SalesHistoricalAllocationMath.plan(requiredForLot"))
        assertTrue(sales.contains("historicalSafeLotOutflowQty"))
        assertTrue(sales.contains("BusinessDatePolicy.endOfBusinessDay(movementDate)"))
        assertTrue(sales.contains("إجمالي الكمية الآمنة تاريخياً عبر التشغيلات"))
    }

    @Test
    fun salesPostingRemainsAtomic() {
        val sales = source("com/fush/erp/domain/SalesService.kt")
        assertTrue(sales.contains("suspend fun postSale(request: PostSaleRequest): SalePostResult = db.withTransaction"))
    }

    @Test
    fun historicalGuardUsesBusinessDayClosingBalancesWithoutRemovingValidation() {
        val advanced = source("com/fush/erp/domain/AdvancedInventoryService.kt")
        assertTrue(advanced.contains("suspend fun historicalSafeLotOutflowQty"))
        assertTrue(advanced.contains("WarehouseTransferMath.minimumClosingBalanceFromBusinessDay"))
        assertTrue(advanced.contains("WarehouseTransferMath.validateHistoricalQuantity"))
    }

    @Test
    fun otherHistoricalOutflowsUseBusinessDayAvailabilityWhereTheyQueryAsOfStock() {
        val purchase = source("com/fush/erp/domain/PurchaseService.kt")
        val production = source("com/fush/erp/domain/ProductionService.kt")
        val advanced = source("com/fush/erp/domain/AdvancedInventoryService.kt")
        assertTrue(purchase.contains("BusinessDatePolicy.endOfBusinessDay(request.returnDate)"))
        assertTrue(production.contains("BusinessDatePolicy.endOfBusinessDay(issueDate)"))
        assertTrue(advanced.contains("BusinessDatePolicy.endOfBusinessDay(transfer.transferDate)"))
    }
}
