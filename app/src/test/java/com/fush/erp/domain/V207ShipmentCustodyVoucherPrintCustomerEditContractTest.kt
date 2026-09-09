package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class V207ShipmentCustodyVoucherPrintCustomerEditContractTest {
    private fun source(relative: String): String {
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Source file not found: $relative")
    }

    @Test
    fun releaseIsV207AndKeepsRoomSchema51() {
        val gradle = source("build.gradle.kts")
        val versionCode = Regex("versionCode\\s*=\\s*(\\d+)").find(gradle)?.groupValues?.get(1)?.toInt() ?: error("versionCode missing")
        assertTrue(versionCode >= 207)
        assertTrue(gradle.contains("versionName ="))
        val db = source("src/main/java/com/fush/erp/data/FushDatabase.kt")
        val schema = Regex("FUSH_DB_SCHEMA_VERSION\\s*=\\s*(\\d+)").find(db)?.groupValues?.get(1)?.toInt() ?: 0
        assertTrue(schema >= 51)
    }

    @Test
    fun newShipmentMovesPhysicalStockIntoSystemCustody() {
        val service = source("src/main/java/com/fush/erp/domain/ShipmentService.kt")
        assertTrue(service.contains("SHIPMENT_CUSTODY_WAREHOUSE_CODE"))
        assertTrue(service.contains("SHIPMENT_TRANSFER_OUT"))
        assertTrue(service.contains("SHIPMENT_TRANSFER_IN"))
        assertTrue(service.contains("allocateShipmentPlanStockForSaleInsideTransaction"))
        assertTrue(service.contains("SHIPMENT_SALE_OUT"))
    }

    @Test
    fun deletingUnusedShipmentReturnsCustodyStockToSourceWarehouse() {
        val service = source("src/main/java/com/fush/erp/domain/ShipmentService.kt")
        assertTrue(service.contains("SHIPMENT_DELETE_RETURN_OUT"))
        assertTrue(service.contains("SHIPMENT_DELETE_RETURN_IN"))
        assertTrue(service.contains("allShipmentCustodyInboundMovements"))
    }

    @Test
    fun receiptPrintingUsesInvoiceBrandingAndActivityContextRemainsDiscoverable() {
        val receipt = source("src/main/java/com/fush/erp/ui/export/SalesReceiptPrintSupport.kt")
        assertTrue(receipt.contains("ReportHeaderStyle.FUSH_RED_FULL_WIDTH"))
        assertTrue(receipt.contains("singlePagePreferred = true"))

        val activity = source("src/main/java/com/fush/erp/MainActivity.kt")
        assertTrue(activity.contains("object : ContextWrapper(this@MainActivity)"))
    }

    @Test
    fun customerMasterCanBeEditedWithoutChangingCustomerCode() {
        val policy = source("src/main/java/com/fush/erp/domain/SecurityPolicy.kt")
        val catalog = source("src/main/java/com/fush/erp/domain/PermissionCatalog.kt")
        val service = source("src/main/java/com/fush/erp/domain/SalesService.kt")
        val ui = source("src/main/java/com/fush/erp/ui/screens/PartyScreens.kt")

        assertTrue(policy.contains("CUSTOMERS_EDIT"))
        assertTrue(catalog.contains("تعديل بيانات العملاء"))
        assertTrue(service.contains("db.requireUserPermission(updatedBy, SecurityPermissions.CUSTOMERS_EDIT)"))
        assertTrue(ui.contains("EditCustomerPartyDialog"))
        assertTrue(ui.contains("تعديل بيانات العميل"))
        assertTrue(ui.contains("كود العميل ثابت ولا يتغير"))
        assertTrue(service.contains("val row = old.copy("))
    }

    @Test
    fun existingSalesPostRolesReceiveCustomerEditOnce() {
        val security = source("src/main/java/com/fush/erp/domain/SecurityService.kt")
        assertTrue(security.contains("CUSTOMER_EDIT_V207"))
        assertTrue(security.contains("RolePermissionEntity(role.code, SecurityPermissions.CUSTOMERS_EDIT)"))
    }
}
