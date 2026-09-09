package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class V168PrintBrandingHeaderContractTest {
    private fun projectFile(relative: String): File {
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.firstOrNull { it.isFile } ?: error("Project file not found: $relative")
    }

    @Test
    fun printExportsUseFushBrandHeaderOnEveryPdfPage() {
        val source = projectFile("src/main/java/com/fush/erp/ui/export/ReportExportSupport.kt").readText()
        assertTrue(source.contains("R.drawable.fush_print_logo"))
        assertTrue(source.contains("BRAND_HEADER_HEIGHT"))
        assertTrue(source.contains("facebook.com/share/1BW7Ur6jTP/"))
        assertTrue(source.contains("fun startPage()"))
        assertTrue(source.contains("canvas.drawBitmap(brandBitmap"))
    }

    @Test
    fun printBrandAssetIsBundledInApplicationResources() {
        val asset = projectFile("src/main/res/drawable-nodpi/fush_print_logo.png")
        assertTrue(asset.length() > 10_000L)
    }
}
