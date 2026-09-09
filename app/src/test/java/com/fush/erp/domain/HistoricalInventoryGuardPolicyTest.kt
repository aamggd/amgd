package com.fush.erp.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HistoricalInventoryGuardPolicyTest {
    private fun source(path: String): String = File("src/main/java/$path").readText()

    @Test
    fun usableLotsReadsHistoricalLotBalanceAtRequestedDate() {
        val text = source("com/fush/erp/domain/AdvancedInventoryService.kt")
        val body = text.substringAfter("suspend fun usableLots").substringBefore("suspend fun usableBalance")
        assertTrue(body.contains("lotBalancesAt(warehouseId, itemId, at)"))
        assertFalse(body.contains("lotBalances(warehouseId, itemId)"))
    }

    @Test
    fun backdatedSalesAndPurchaseReturnsUseFutureBalanceGuard() {
        val sales = source("com/fush/erp/domain/SalesService.kt")
        val purchases = source("com/fush/erp/domain/PurchaseService.kt")
        assertTrue(sales.contains("advancedInventory.historicalSafeLotOutflowQty("))
        assertTrue(sales.contains("require(plan.isComplete)"))
        assertTrue(purchases.contains("advancedInventory.requireHistoricalLotOutflowAvailable("))
        assertTrue(purchases.contains("lotBalancesAt("))
    }
}
