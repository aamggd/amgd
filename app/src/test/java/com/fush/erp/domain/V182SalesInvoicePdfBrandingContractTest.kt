package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V182SalesInvoicePdfBrandingContractTest {
    private fun projectFile(relative: String): File {
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.firstOrNull { it.isFile } ?: error("Project file not found: $relative")
    }

    @Test
    fun salesInvoiceDialogProvidesPdfShareAndPrintActions() {
        val source = projectFile("src/main/java/com/fush/erp/ui/screens/SalesScreens.kt").readText()
        assertTrue(source.contains("طباعة وتصدير الفاتورة"))
        assertTrue(source.contains("SalesInvoicePrintSupport.document"))
        assertTrue(source.contains("ReportExportSupport.exportPdf"))
        assertTrue(source.contains("ReportExportSupport.sharePdf"))
        assertTrue(source.contains("ReportExportSupport.printPreview"))
    }

    @Test
    fun customerInvoiceUsesFushLogoAndHeader() {
        val source = projectFile("src/main/java/com/fush/erp/ui/export/ReportExportSupport.kt").readText()
        assertTrue(source.contains("R.drawable.fush_print_logo"))
        assertTrue(source.contains("BRAND_HEADER_HEIGHT"))
        assertTrue(source.contains("canvas.drawBitmap(brandBitmap"))
        assertTrue(source.contains("Color.rgb(9, 76, 92)"))
        val logo = projectFile("src/main/res/drawable-nodpi/fush_print_logo.png")
        assertTrue(logo.length() > 10_000L)
    }

    @Test
    fun customerInvoiceDoesNotExposeInternalCostsOrCommission() {
        val source = projectFile("src/main/java/com/fush/erp/ui/export/SalesInvoicePrintSupport.kt").readText()
        assertTrue(source.contains("Customer-facing sales invoice PDF/print model"))
        assertFalse(source.contains("unitCostBase"))
        assertFalse(source.contains("commissionBase"))
        assertFalse(source.contains("allocatedShipmentCostBase"))
    }
}
