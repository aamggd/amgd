package com.fush.erp.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ShipmentCostAllocationV179ContractTest {
    private fun root(): File {
        val wd = File(System.getProperty("user.dir"))
        return if (File(wd, "src/main").exists()) wd.parentFile else wd
    }
    private fun read(path: String) = File(root(), path).readText()

    @Test fun `shipment structure is many to many and room migration is safe`() {
        val db = read("app/src/main/java/com/fush/erp/data/FushDatabase.kt")
        val entities = read("app/src/main/java/com/fush/erp/data/entity/ShipmentEntities.kt")
        val migration = read("app/src/main/java/com/fush/erp/data/ShipmentTrackingMigration.kt")
        assertTrue((Regex("FUSH_DB_SCHEMA_VERSION\\s*=\\s*(\\d+)").find(db)?.groupValues?.get(1)?.toInt() ?: 0) >= 48)
        assertTrue(entities.contains("sales_shipment_invoice_item_allocations"))
        assertTrue(entities.contains("sales_shipment_expense_invoice_allocations"))
        assertTrue(entities.contains("Index(value=[\"shipmentExpenseId\",\"invoiceId\"], unique = true)"))
        assertTrue(migration.contains("Migration(47, 48)"))
        assertTrue(File(root(), "app/schemas/com.fush.erp.data.FushDatabase/47.json").isFile)
        assertTrue(File(root(), "app/schemas/com.fush.erp.data.FushDatabase/48.json").isFile)
        assertFalse(migration.contains("DROP TABLE"))
        val v185Migration = read("app/src/main/java/com/fush/erp/data/V185ShipmentSalesLineMigration.kt")
        assertTrue(v185Migration.contains("Migration(48, 49)"))
        assertFalse(v185Migration.contains("DROP TABLE"))
    }

    @Test fun `actual shipment expense posts once and invoice allocation is analytical`() {
        val service = read("app/src/main/java/com/fush/erp/domain/ShipmentService.kt")
        assertTrue(service.contains("byCode(\"6430\")"))
        assertTrue(service.contains("type = \"EXPENSE\""))
        assertTrue(service.contains("referenceType = \"SHIPMENT\""))
        assertTrue(service.contains("insertExpenseAllocation"))
        assertTrue(service.contains("Analytical only"))
    }

    @Test fun `invoice profitability prefers shipment allocation and avoids legacy double count`() {
        val dao = read("app/src/main/java/com/fush/erp/data/dao/GeographyDao.kt")
        assertTrue(dao.contains("sales_shipment_expense_invoice_allocations"))
        assertTrue(dao.contains("NULLIF"))
        assertTrue(dao.contains("invoice_geographic_costs"))
    }

    @Test fun `sales invoice UI shows shipment actual cost separately from customer charge`() {
        val ui = read("app/src/main/java/com/fush/erp/ui/screens/SalesScreens.kt")
        assertTrue(ui.contains("الشحنات المرتبطة والتكلفة الفعلية"))
        assertTrue(ui.contains("هذه الحصة تتحملها الشركة ولا تضاف إلى العميل."))
        assertTrue(ui.contains("أضيف للعميل"))
        assertTrue(ui.contains("التكلفة الفعلية للشحنة مستقلة تماماً عن أي رسم يُحمّل للعميل"))
    }
}
