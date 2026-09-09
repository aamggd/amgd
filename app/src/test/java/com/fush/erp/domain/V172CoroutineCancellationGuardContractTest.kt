package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V172CoroutineCancellationGuardContractTest {
    private fun source(relative: String): String {
        val candidates = listOf(File("src/main/java/$relative"), File("app/src/main/java/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Cannot locate $relative from ${File(".").absolutePath}")
    }

    @Test
    fun receiptPrintEffectDoesNotCancelItselfBeforePrinting() {
        val screen = source("com/fush/erp/ui/screens/SalesScreens.kt")
        val start = screen.indexOf("LaunchedEffect(pendingReceiptPrintId)")
        val end = screen.indexOf("val cashLabel", start)
        assertTrue(start >= 0 && end > start)
        val block = screen.substring(start, end)
        val printIndex = block.indexOf("ReportExportSupport.printPreview")
        val clearIndex = block.indexOf("pendingReceiptPrintId = null")
        assertTrue(printIndex >= 0)
        assertTrue(clearIndex > printIndex)
        assertTrue(block.contains("catch (e: CancellationException)"))
        assertTrue(block.contains("throw e"))
    }

    @Test
    fun salesCoroutineCancellationIsNotConvertedToUserFacingError() {
        val screen = source("com/fush/erp/ui/screens/SalesScreens.kt")
        assertTrue(screen.contains("import kotlinx.coroutines.CancellationException"))
        assertTrue(screen.split("catch (e: CancellationException)").size - 1 >= 8)
        assertFalse(screen.contains("pendingReceiptPrintId = null\n        try"))
    }
}
