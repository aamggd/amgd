package com.fush.erp.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DetailedPurchasesReportV173ContractTest {
    private fun source(path: String): String = File("src/main/java/$path").readText()

    @Test
    fun purchaseReportLoadsInvoiceLineDetails() {
        val dao = source("com/fush/erp/data/dao/ReportDao.kt")
        val reports = source("com/fush/erp/ui/screens/ReportsScreen.kt")
        assertTrue(dao.contains("suspend fun purchaseInvoiceDetails"))
        assertTrue(dao.contains("pl.unitPriceOriginal AS unitPriceOriginal"))
        assertTrue(dao.contains("pl.quantity AS quantity"))
        assertTrue(reports.contains("purchaseDetails = reportDao.purchaseInvoiceDetails(from, to)"))
    }

    @Test
    fun purchaseExportContainsInvoiceAndLineTables() {
        val reports = source("com/fush/erp/ui/screens/ReportsScreen.kt")
        assertTrue(reports.contains("تفاصيل فواتير المشتريات"))
        assertTrue(reports.contains("أصناف وكميات وأسعار كل فاتورة"))
        assertTrue(reports.contains("سعر الوحدة"))
        assertTrue(reports.contains("إجمالي السطر"))
        assertTrue(reports.contains("رسوم ونقل"))
    }
}
