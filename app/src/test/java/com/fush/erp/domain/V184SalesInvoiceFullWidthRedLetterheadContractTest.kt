package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class V184SalesInvoiceFullWidthRedLetterheadContractTest {
    private fun projectFile(relative: String): File {
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.firstOrNull { it.isFile } ?: error("Project file not found: $relative")
    }

    @Test
    fun salesInvoiceSelectsDedicatedFullWidthRedHeader() {
        val invoice = projectFile("src/main/java/com/fush/erp/ui/export/SalesInvoicePrintSupport.kt").readText()
        val support = projectFile("src/main/java/com/fush/erp/ui/export/ReportExportSupport.kt").readText()
        assertTrue(invoice.contains("headerStyle = ReportHeaderStyle.FUSH_RED_FULL_WIDTH"))
        assertTrue(support.contains("FUSH_RED_FULL_WIDTH"))
        assertTrue(support.contains("R.drawable.fush_invoice_header_red"))
        assertTrue(support.contains("RectF(0f, 0f, pageWidth.toFloat(), invoiceHeaderHeight)"))
        assertTrue(support.contains("Color.rgb(154, 76, 72)"))
        assertTrue(support.contains("full-width header"))
    }

    @Test
    fun suppliedRedLetterheadAssetIsPackaged() {
        val header = projectFile("src/main/res/drawable-nodpi/fush_invoice_header_red.png")
        assertTrue(header.length() > 50_000L)
    }

    @Test
    fun v184LetterheadIsRetainedInLaterSafeUpgrade() {
        val gradle = projectFile("build.gradle.kts").readText()
        val database = projectFile("src/main/java/com/fush/erp/data/FushDatabase.kt").readText()
        val versionCode = Regex("versionCode\\s*=\\s*(\\d+)").find(gradle)?.groupValues?.get(1)?.toInt() ?: 0
        assertTrue(versionCode >= 184)
        assertTrue(Regex("FUSH_DB_SCHEMA_VERSION\\s*=\\s*(\\d+)").find(database)?.groupValues?.get(1)?.toInt() ?: 0 >= 49)
        val migration = projectFile("src/main/java/com/fush/erp/data/V185ShipmentSalesLineMigration.kt").readText()
        assertTrue(migration.contains("Migration(48, 49)"))
    }
}
