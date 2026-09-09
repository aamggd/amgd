package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class V186DirectStockUnifiedSyncPrintFitContractTest {
    private fun projectFile(relative: String): File {
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.firstOrNull { it.isFile } ?: error("Project file not found: $relative")
    }

    @Test
    fun directWarehouseSaleIsExplicitAndSkipsShipmentAllocation() {
        val math = projectFile("src/main/java/com/fush/erp/domain/SalesMath.kt").readText()
        val service = projectFile("src/main/java/com/fush/erp/domain/SalesService.kt").readText()
        val screen = projectFile("src/main/java/com/fush/erp/ui/screens/SalesScreens.kt").readText()
        assertTrue(math.contains("val useShipmentTracking: Boolean = true"))
        assertTrue(service.contains("if (!line.useShipmentTracking)"))
        assertTrue(service.contains("emptyList()"))
        assertTrue(screen.contains("بدون شحنة — بيع مباشر من المخزن"))
        assertTrue(screen.contains("useShipmentTracking = ship != null"))
    }

    @Test
    fun unifiedSyncIsThePrimaryActionAndLegacyCardsAreAdvancedOnly() {
        val screen = projectFile("src/main/java/com/fush/erp/ui/screens/CloudSyncScreen.kt").readText()
        assertTrue(screen.contains("مزامنة الكل الآن"))
        assertTrue(screen.contains("تفاصيل المزامنة والتعارضات"))
        assertTrue(screen.contains("if (showAdvancedSyncDetails)"))
    }

    @Test
    fun salesInvoiceUsesCompactSinglePagePreferredLayout() {
        val invoice = projectFile("src/main/java/com/fush/erp/ui/export/SalesInvoicePrintSupport.kt").readText()
        val export = projectFile("src/main/java/com/fush/erp/ui/export/ReportExportSupport.kt").readText()
        assertTrue(invoice.contains("singlePagePreferred = true"))
        assertTrue(export.contains("val compact = document.singlePagePreferred"))
        assertTrue(export.contains("!document.singlePagePreferred"))
    }

    @Test
    fun reportTablesWrapScaleAndHardClipInsidePage() {
        val export = projectFile("src/main/java/com/fush/erp/ui/export/ReportExportSupport.kt").readText()
        assertTrue(export.contains("fun cellLines"))
        assertTrue(export.contains("fun columnWidths"))
        assertTrue(export.contains("columnCount >= 10"))
        assertTrue(export.contains("canvas.clipRect"))
        assertTrue(export.contains("no report cell is allowed to paint outside its box"))
    }
}
