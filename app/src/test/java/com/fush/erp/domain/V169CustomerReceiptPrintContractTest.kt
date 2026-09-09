package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class V169CustomerReceiptPrintContractTest {
    private fun projectFile(relative: String): File {
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.firstOrNull { it.isFile } ?: error("Project file not found: $relative")
    }

    @Test
    fun customerReceiptHasDedicatedPrintableDocument() {
        val source = projectFile("src/main/java/com/fush/erp/ui/export/SalesReceiptPrintSupport.kt").readText()
        assertTrue(source.contains("سند قبض / تحصيل عميل"))
        assertTrue(source.contains("تخصيص السند على الفواتير"))
        assertTrue(source.contains("الخزينة / البنك"))
        assertTrue(source.contains("توقيع المستلم"))
        assertTrue(source.contains("ReportExportDocument"))
    }

    @Test
    fun collectionFlowPrintsAfterPostingAndAllowsReprint() {
        val source = projectFile("src/main/java/com/fush/erp/ui/screens/SalesScreens.kt").readText()
        assertTrue(source.contains("pendingReceiptPrintId = result.receiptId"))
        assertTrue(source.contains("ReportExportSupport.printPreview"))
        assertTrue(source.contains("Text(\"طباعة السند\")"))
        assertTrue(source.contains("onPrintReceipt(receipt.id)"))
    }
}
