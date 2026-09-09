package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V208SalesFxPrecisionOverrideContractTest {
    private fun source(relative: String): String {
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Source file not found: $relative")
    }

    @Test
    fun canonicalEightDecimalRateDoesNotBecomeOverrideBecauseOfUiFormatting() {
        val approved = 2.91214953271028
        val uiCanonical = SalesExchangeRatePolicy.canonical(approved)
        assertTrue(SalesExchangeRatePolicy.matchesApproved(uiCanonical, approved))
        assertFalse(SalesExchangeRatePolicy.requiresOverride(uiCanonical, approved))
        // The old 4-decimal feedback value is genuinely different and must not silently pass.
        assertTrue(SalesExchangeRatePolicy.requiresOverride(2.9121, approved))
    }

    @Test
    fun releaseIsV208AndKeepsRoomSchema51() {
        val gradle = source("build.gradle.kts")
        assertTrue(Regex("versionCode\\s*=\\s*(\\d+)").find(gradle)!!.groupValues[1].toInt() >= 208)
        assertTrue(gradle.contains("versionName ="))
        val db = source("src/main/java/com/fush/erp/data/FushDatabase.kt")
        val schema = Regex("FUSH_DB_SCHEMA_VERSION\\s*=\\s*(\\d+)").find(db)?.groupValues?.get(1)?.toInt() ?: 0
        assertTrue(schema >= 51)
    }

    @Test
    fun salesPostingRequiresDedicatedPermissionAndReasonForDifferentRate() {
        val service = source("src/main/java/com/fush/erp/domain/SalesService.kt")
        assertTrue(service.contains("SalesExchangeRatePolicy.requiresOverride"))
        assertTrue(service.contains("SecurityPermissions.EXCHANGE_RATE_OVERRIDE"))
        assertTrue(service.contains("exchangeRateOverrideReason.trim().length >= 5"))
        assertTrue(service.contains("SALES_FX_RATE_OVERRIDE"))
    }

    @Test
    fun salesUiKeepsEightDecimalCanonicalRateAndOnlyEnablesManualChangeForAuthorizedUser() {
        val ui = source("src/main/java/com/fush/erp/ui/screens/SalesScreens.kt")
        assertTrue(ui.contains("salesRateRaw(approved)"))
        assertTrue(ui.contains("canOverrideExchangeRate"))
        assertTrue(ui.contains("تغيير سعر الصرف"))
        assertTrue(ui.contains("سبب تغيير سعر الصرف"))
        assertTrue(ui.contains("exchangeRateOverrideReason"))
    }
}
