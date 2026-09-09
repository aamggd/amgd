package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class V205CustomerAddressDescriptionContractTest {
    private fun source(relative: String): String {
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Source file not found: $relative")
    }

    @Test
    fun releaseRemainsAnUpdateOverV205AndSchemaDoesNotNeedAnotherMigration() {
        val gradle = source("build.gradle.kts")
        val versionCode = Regex("versionCode\\s*=\\s*(\\d+)").find(gradle)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        assertTrue(versionCode >= 205)
        assertTrue(gradle.contains("versionName = "))
        val db = source("src/main/java/com/fush/erp/data/FushDatabase.kt")
        val schema = Regex("FUSH_DB_SCHEMA_VERSION\\s*=\\s*(\\d+)").find(db)?.groupValues?.get(1)?.toInt() ?: 0
        assertTrue(schema >= 51)
    }

    @Test
    fun customerCreateDialogCapturesFreeTextAddressDescription() {
        val ui = source("src/main/java/com/fush/erp/ui/screens/PartyScreens.kt")
        assertTrue(ui.contains("وصف العنوان / تفاصيل العنوان"))
        assertTrue(ui.contains("مثال: شارع جمال، أمام مستشفى الثورة، جوار صيدلية..."))
        assertTrue(ui.contains("onSave(name, phone, address,"))
    }

    @Test
    fun createCustomerPersistsAddressIntoExistingCustomerAddressField() {
        val service = source("src/main/java/com/fush/erp/domain/SalesService.kt")
        val start = service.indexOf("suspend fun createCustomer(")
        val end = service.indexOf("private suspend fun allocateCompanySafeCustomerCode", start)
        val block = service.substring(start, end)
        assertTrue(block.contains("address: String = \"\""))
        assertTrue(block.contains("address = address.trim()"))
    }

    @Test
    fun bothCustomerCreationFlowsForwardAddressToService() {
        val parties = source("src/main/java/com/fush/erp/ui/screens/PartyScreens.kt")
        val sales = source("src/main/java/com/fush/erp/ui/screens/SalesScreens.kt")
        assertTrue(parties.contains("name, phone, address, governorate"))
        assertTrue(parties.contains("address = address"))
        assertTrue(sales.contains("name, phone, address, governorate"))
        assertTrue(sales.contains("address = address"))
    }
}
