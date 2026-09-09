package com.fush.erp.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IntegratedSalesReportV175ContractTest {
    private fun source(path: String): String = File("src/main/java/$path").readText()

    @Test fun reportLoadsIntegratedSalesData() {
        val dao = source("com/fush/erp/data/dao/ReportDao.kt")
        val reports = source("com/fush/erp/ui/screens/ReportsScreen.kt")
        assertTrue(dao.contains("suspend fun salesInvoiceAccounting"))
        assertTrue(dao.contains("suspend fun productProfitability"))
        assertTrue(dao.contains("suspend fun salesRepPerformance"))
        assertTrue(dao.contains("suspend fun salesReturnDetails"))
        assertTrue(dao.contains("suspend fun salesMonthlyTrend"))
        assertTrue(dao.contains("suspend fun salesReconciliation"))
        assertTrue(reports.contains("salesAgingRows = AgingReportMath.build"))
    }

    @Test fun exportContainsRequestedErpSections() {
        val reports = source("com/fush/erp/ui/screens/ReportsScreen.kt")
        listOf(
            "تفاصيل فواتير المبيعات",
            "ربحية المبيعات حسب المنتج",
            "تحليل أداء مندوبي المبيعات",
            "أعمار الذمم المدينة",
            "تحليل مرتجعات المبيعات حسب العميل والمنتج والمندوب",
            "المبيعات النقدية مقابل الآجلة",
            "الاتجاه الزمني الشهري للمبيعات",
            "مطابقة المبيعات والذمم مع الأستاذ العام",
            "متوسط قيمة الفاتورة"
        ).forEach { assertTrue(it, reports.contains(it)) }
    }

    @Test fun geographyAliasesAreNormalizedWithoutDatabaseMigration() {
        val reports = source("com/fush/erp/ui/screens/ReportsScreen.kt")
        assertTrue(reports.contains("normalizeSalesGeographyLabel"))
        assertTrue(reports.contains("تعز/بيرباشا"))
        assertTrue(reports.contains("تم توحيد أسماء المحافظات والمناطق داخل التقرير فقط"))
    }
}
