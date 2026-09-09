package com.fush.erp.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SalesAuxiliaryCloudAndReportV179ContractTest {
    private fun root(): File {
        val wd = File(System.getProperty("user.dir"))
        return if (File(wd, "src/main").exists()) wd.parentFile else wd
    }
    private fun read(path: String) = File(root(), path).readText()

    @Test fun `sales report separates item sales charges recoverables and shipment actual cost`() {
        val report = read("app/src/main/java/com/fush/erp/ui/screens/ReportsScreen.kt")
        assertTrue(report.contains("فصل الربحية والرسوم"))
        assertTrue(report.contains("الرسوم والتكاليف الإضافية — منفصلة عن مبيعات الأصناف"))
        assertTrue(report.contains("تتبع الشحنة ↔ الفاتورة ↔ سند الصرف ↔ حصة التكلفة"))
        assertTrue(report.contains("رسوم قابلة للاسترداد — رصيد آخر الفترة"))
        assertTrue(report.contains("إذا كان مصروف الشحنة محدداً على العميل، تسترد الفاتورة تلقائياً الحصة الفعلية المخصصة من نفس المصروف دون إنشاء مصروف ثانٍ"))
    }

    @Test fun `sales reconciliation includes company expense service revenue and recoverable control account`() {
        val entities = read("app/src/main/java/com/fush/erp/data/entity/ReportEntities.kt")
        val dao = read("app/src/main/java/com/fush/erp/data/dao/ReportDao.kt")
        assertTrue(entities.contains("companyExpensesBase"))
        assertTrue(entities.contains("serviceRevenueBase"))
        assertTrue(entities.contains("recoverableClosingBase"))
        assertTrue(dao.contains("accountingTreatment='COMPANY_EXPENSE'"))
        assertTrue(dao.contains("accountingTreatment='SERVICE_REVENUE'"))
        assertTrue(dao.contains("a.code='1310'"))
    }

    @Test fun `sales auxiliary cloud sync hydrates room directly and runs after accounting`() {
        val engine = read("app/src/main/java/com/fush/erp/cloud/SalesAuxiliaryCloudSyncEngine.kt")
        val shell = read("app/src/main/java/com/fush/erp/ui/screens/HomeShell.kt")
        assertTrue(engine.contains("Direct DAO hydration" ) || engine.contains("hydrates Room entities directly"))
        assertTrue(engine.contains("insertExpense(row)"))
        assertTrue(engine.contains("insertCharge(row)"))
        assertFalse(engine.contains("ShipmentService("))
        assertFalse(engine.contains("AdditionalChargesService("))
        assertFalse(engine.contains("AccountingService("))
        val accountingPos = shell.indexOf("syncAccounting(user)")
        val auxPos = shell.indexOf("syncSalesAuxiliary(user)")
        assertTrue(accountingPos >= 0 && auxPos > accountingPos)
    }

    @Test fun `supabase auxiliary sync is conflict safe with explicit resolution`() {
        val sql = read("V179_SALES_AUXILIARY_CLOUD_SYNC.sql")
        val engine = read("app/src/main/java/com/fush/erp/cloud/SalesAuxiliaryCloudSyncEngine.kt")
        assertTrue(sql.contains("fush_tx_sales_aux_documents"))
        assertTrue(sql.contains("fush_sales_aux_sync_conflicts"))
        assertTrue(sql.contains("fush_publish_sales_aux_batch"))
        assertTrue(sql.contains("fush_resolve_sales_aux_conflict"))
        assertTrue(sql.contains("'KEEP_LOCAL','KEEP_CLOUD'"))
        assertTrue(engine.contains("SalesAuxiliaryConflictResolution.KEEP_LOCAL"))
        assertTrue(engine.contains("SalesAuxiliaryConflictResolution.USE_CLOUD"))
    }
}
