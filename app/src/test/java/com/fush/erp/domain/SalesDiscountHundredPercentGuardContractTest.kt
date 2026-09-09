package com.fush.erp.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SalesDiscountHundredPercentGuardContractTest {
    private fun source(relative: String): String {
        val candidates = listOf(File("src/main/java/$relative"), File("app/src/main/java/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Cannot locate $relative from ${File(".").absolutePath}")
    }

    @Test
    fun standardSaleDiscountUsesExclusiveHundredPercentCeiling() {
        val math = source("com/fush/erp/domain/SalesMath.kt")
        assertTrue(math.contains("discountPct >= 0.0 && discountPct < 100.0"))
        assertFalse(math.contains("fun maxAllowedDiscountPct"))
    }

    @Test
    fun uiAndPostingServiceRejectZeroValueStandardSale() {
        val ui = source("com/fush/erp/ui/screens/SalesScreens.kt")
        val service = source("com/fush/erp/domain/SalesService.kt")
        assertTrue(ui.contains("previewDiscountPct?.let { it >= 0.0 && it < 100.0"))
        assertTrue(ui.contains("خصم 100% غير مسموح"))
        assertTrue(service.contains("require(totalOriginal > 0.0)"))
        assertTrue(service.contains("require(totalBase > 0.0)"))
    }
}
