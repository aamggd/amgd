package com.fush.erp.ui.screens

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionOrderDialogUsabilityContractTest {
    private fun productionUi(): String {
        val relative = "com/fush/erp/ui/screens/ProductionScreens.kt"
        val candidates = listOf(File("src/main/java/$relative"), File("app/src/main/java/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Source file not found: $relative")
    }

    @Test
    fun `new production order dialog is scrollable so required labor field stays reachable`() {
        val ui = productionUi()
        assertTrue(ui.contains("heightIn(max = 560.dp)"))
        assertTrue(ui.contains("verticalScroll(rememberScrollState())"))
        assertTrue(ui.contains("عمولة/أجر موظف الإنتاج لهذه الدفعة بالريال"))
    }

    @Test
    fun `disabled create button explains the missing required value`() {
        val ui = productionUi()
        assertTrue(ui.contains("val createBlockReason = when"))
        assertTrue(ui.contains("إذا لم توجد أجور، أدخل 0"))
        assertTrue(ui.contains("لإتاحة زر إنشاء:"))
        assertTrue(ui.contains("enabled = createBlockReason == null"))
    }
}
