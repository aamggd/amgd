package com.fush.erp.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AccountingPurchasesReportV174ContractTest {
    private fun source(path: String): String = File("src/main/java/$path").readText()

    @Test
    fun reportLoadsAccountingPurchasesData() {
        val dao = source("com/fush/erp/data/dao/ReportDao.kt")
        val reports = source("com/fush/erp/ui/screens/ReportsScreen.kt")
        assertTrue(dao.contains("suspend fun purchaseInvoiceAccounting"))
        assertTrue(dao.contains("suspend fun purchaseReturnsForReport"))
        assertTrue(dao.contains("suspend fun purchaseItemAnalysis"))
        assertTrue(dao.contains("suspend fun purchaseReconciliation"))
        assertTrue(reports.contains("purchaseAccounting = reportDao.purchaseInvoiceAccounting(from, to, to)"))
        assertTrue(reports.contains("supplierAgingRows = container.db.purchaseDao().supplierAging(to)"))
    }

    @Test
    fun invoiceReportShowsRequestedAccountingFields() {
        val reports = source("com/fush/erp/ui/screens/ReportsScreen.kt")
        assertTrue(reports.contains("تفاصيل الفواتير — البيانات الأساسية"))
        assertTrue(reports.contains("تفاصيل الفواتير — التحليل المالي"))
        assertTrue(reports.contains("الاستحقاق"))
        assertTrue(reports.contains("المدفوع أساسي"))
        assertTrue(reports.contains("المتبقي أساسي"))
        assertTrue(reports.contains("الضريبة*"))
    }

    @Test
    fun reportAddsItemPeriodCashCreditAgingAndReturnAnalysis() {
        val reports = source("com/fush/erp/ui/screens/ReportsScreen.kt")
        assertTrue(reports.contains("تحليل المشتريات حسب الصنف"))
        assertTrue(reports.contains("تحليل المشتريات حسب الفترة"))
        assertTrue(reports.contains("المشتريات النقدية مقابل الآجلة"))
        assertTrue(reports.contains("أرصدة الموردين وأعمار الديون"))
        assertTrue(reports.contains("مرتجعات المشتريات وربطها بالفواتير الأصلية"))
    }

    @Test
    fun reportReconcilesOperationsWithGeneralLedger() {
        val dao = source("com/fush/erp/data/dao/ReportDao.kt")
        val reports = source("com/fush/erp/ui/screens/ReportsScreen.kt")
        assertTrue(dao.contains("a.code='1200'"))
        assertTrue(dao.contains("a.code='2100'"))
        assertTrue(dao.contains("je.sourceType IN ('PURCHASE','PURCHASE_RETURN')"))
        assertTrue(reports.contains("مطابقة المشتريات والحسابات الدائنة مع الأستاذ العام"))
        assertTrue(reports.contains("فرق يحتاج مراجعة"))
    }
}
