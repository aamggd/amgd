package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V185UnifiedSyncShipmentAllocationContractTest {
    private fun source(relative: String): String {
        val candidates = listOf(File("src/main/java/$relative"), File("app/src/main/java/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText() ?: error("Source file not found: $relative")
    }

    @Test
    fun tenPacksSeventyThousandAllocatesThirtyFiveThousandThenRemainder() {
        val first = ShipmentAllocationMath.automaticExpenseAllocation(70_000.0, 0.0, 10.0, 5.0, 5.0)
        val second = ShipmentAllocationMath.automaticExpenseAllocation(70_000.0, first, 10.0, 5.0, 0.0)
        assertEquals(35_000.0, first, 0.000001)
        assertEquals(35_000.0, second, 0.000001)
        assertEquals(70_000.0, first + second, 0.000001)
    }

    @Test
    fun twentyPacksSplitsEightPlusTwelveAcrossTwoShipments() {
        val takes = ShipmentAllocationMath.splitQuantityAcrossAvailability(20.0, listOf(8.0, 12.0))
        assertEquals(listOf(8.0, 12.0), takes)
        val partial = ShipmentAllocationMath.splitQuantityAcrossAvailability(10.0, listOf(8.0, 12.0))
        assertEquals(listOf(8.0, 2.0), partial)
    }

    @Test
    fun shipmentExpenseBearerControlsCustomerChargeWithoutSecondExpense() {
        val service = source("com/fush/erp/domain/ShipmentService.kt")
        val sales = source("com/fush/erp/domain/SalesService.kt")
        assertTrue(service.contains("val bearer: String = \"COMPANY\""))
        assertTrue(service.contains("customerChargeBase = if (expense.bearer == \"CUSTOMER\")"))
        assertTrue(sales.contains("shipmentCustomerChargeOriginal"))
        assertTrue(sales.contains("byCode(\"6430\")"))
        assertTrue(sales.contains("actualShipmentExpenses.customerChargeBase"))
        assertFalse(sales.contains("postActualExpense("))
    }

    @Test
    fun salesLineShipmentSelectionSupportsMultiShipmentAndLineLevelTrace() {
        val service = source("com/fush/erp/domain/ShipmentService.kt")
        val math = source("com/fush/erp/domain/SalesMath.kt")
        val migration = source("com/fush/erp/data/V185ShipmentSalesLineMigration.kt")
        assertTrue(math.contains("preferredShipmentId"))
        assertTrue(service.contains("ShipmentAllocationMath.splitQuantityAcrossAvailability"))
        assertTrue(service.contains("candidates.filter { it.shipmentId != preferredShipmentId }"))
        assertTrue(service.contains("salesLineId = salesLineId"))
        assertTrue(migration.contains("salesLineId"))
        assertTrue(migration.contains("customerChargeBase"))
        assertTrue(migration.contains("bearer"))
    }

    @Test
    fun syncTransportIsOrganizationMembershipBasedAcrossDomains() {
        val files = listOf(
            "AccountingCloudSyncEngine.kt",
            "SalesReceivablesCloudSyncEngine.kt",
            "PurchaseDocumentsCloudSyncEngine.kt",
            "SupplierPaymentsTreasuryCloudSyncEngine.kt",
            "InventoryProductionCloudSyncEngine.kt",
            "SalesAuxiliaryCloudSyncEngine.kt",
        )
        files.forEach { name ->
            val text = source("com/fush/erp/cloud/$name")
            assertTrue("$name must allow active-member transport", text.contains("= true //") || text.contains("canPublish = true"))
        }
        val sql = listOf(File("../V185_ORGANIZATION_MEMBERSHIP_UNIFIED_SYNC.sql"), File("V185_ORGANIZATION_MEMBERSHIP_UNIFIED_SYNC.sql")).first { it.isFile }.readText()
        assertTrue(sql.contains("m.is_active = true"))
        assertTrue(sql.contains("fush_can_publish_accounting"))
        assertTrue(sql.contains("fush_can_publish_sales_aux"))
        assertFalse(sql.contains("v185_active_member_delete"))
        assertFalse(sql.contains("for delete to authenticated"))
    }
}
