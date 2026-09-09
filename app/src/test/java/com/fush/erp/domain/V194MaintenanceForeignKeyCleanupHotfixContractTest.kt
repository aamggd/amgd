package com.fush.erp.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class V194MaintenanceForeignKeyCleanupHotfixContractTest {
    private fun read(path: String): String = File(path).readText()

    @Test
    fun cleanupUsesPhysicalForeignKeyGraphInsteadOfPostedOnlyBusinessViews() {
        val service = read("src/main/java/com/fush/erp/domain/SupportService.kt")
        assertTrue(service.contains("SELECT id FROM sales_returns WHERE salesInvoiceId=?"))
        assertTrue(service.contains("SELECT DISTINCT receiptId FROM customer_receipt_allocations WHERE invoiceId=?"))
        assertTrue(service.contains("findRestrictingForeignKeyDependents"))
        assertTrue(service.contains("PRAGMA foreign_key_list"))
        assertTrue(service.contains("DELETE FROM inventory_cost_layers WHERE stockMovementId IN"))
        assertTrue(service.contains("DROP TRIGGER IF EXISTS trg_inventory_cost_layer_no_delete"))
        assertTrue(service.contains("installInventoryCostLayerSupport(sqlite)"))
        assertFalse(service.substringAfter("suspend fun deleteTestSalesInvoiceBundle").substringBefore("suspend fun deleteTestShipmentExpense").contains("returnsForInvoice(invoiceId)"))
    }

    @Test
    fun cleanupDeletesRestrictChildrenBeforeInvoice() {
        val service = read("src/main/java/com/fush/erp/domain/SupportService.kt")
        val block = service.substringAfter("suspend fun deleteTestSalesInvoiceBundle").substringBefore("suspend fun deleteTestShipmentExpense")
        val invoiceDelete = block.indexOf("DELETE FROM sales_invoices WHERE id=?")
        listOf(
            "DELETE FROM sales_returns WHERE id=?",
            "DELETE FROM sales_commissions WHERE invoiceId=?",
            "DELETE FROM customer_receipt_allocations WHERE receiptId=? AND invoiceId=?",
            "DELETE FROM sales_shipment_expense_invoice_allocations WHERE invoiceId=?",
            "DELETE FROM sales_shipment_invoice_item_allocations WHERE invoiceId=?",
            "DELETE FROM sales_additional_charge_settlements WHERE invoiceId=?",
        ).forEach { sql ->
            assertTrue("$sql must precede invoice delete", block.indexOf(sql) in 0 until invoiceDelete)
        }
    }

    @Test
    fun v194MetadataIsUpdateOverV193WithoutSchemaBump() {
        val gradle = read("build.gradle.kts")
        val versionCode = Regex("versionCode\\s*=\\s*(\\d+)").find(gradle)?.groupValues?.get(1)?.toInt()
            ?: error("versionCode not found")
        assertTrue(versionCode >= 194)
        val db = read("src/main/java/com/fush/erp/data/FushDatabase.kt")
        assertTrue(Regex("FUSH_DB_SCHEMA_VERSION\\s*=\\s*(\\d+)").find(db)?.groupValues?.get(1)?.toInt() ?: 0 >= 49)
    }
}
