package com.fush.erp.domain

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class ExpiryThresholdHardcodeRegressionTest {
    @Test fun inventory_expiry_logic_has_no_fixed_30_or_90_day_thresholds() {
        val files = listOf(
            "src/main/java/com/fush/erp/ui/screens/AdvancedInventoryScreens.kt",
            "src/main/java/com/fush/erp/ui/screens/HomeShell.kt",
            "src/main/java/com/fush/erp/domain/InventoryReportMath.kt"
        )
        files.forEach { path ->
            val text = File(path).readText()
            assertFalse("fixed 30-day expiry threshold found in $path", Regex("expiry.*30|30.*expiry", RegexOption.IGNORE_CASE).containsMatchIn(text))
            assertFalse("fixed 90-day expiry threshold found in $path", Regex("expiry.*90|90.*expiry", RegexOption.IGNORE_CASE).containsMatchIn(text))
        }
    }
}
