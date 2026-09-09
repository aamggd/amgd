package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class V209MultiCurrencyTreasuryContractTest {
    private fun source(path: String) = File(path).takeIf { it.exists() } ?: File("../$path")

    @Test
    fun treasurySupportsLogicalGroupWithSeparateCurrencyLedgers() {
        val entity = source("app/src/main/java/com/fush/erp/data/entity/AccountingEntities.kt").readText()
        val service = source("app/src/main/java/com/fush/erp/domain/AccountingService.kt").readText()
        assertTrue(entity.contains("val groupCode: String"))
        assertTrue(entity.contains("Index(value = [\"groupCode\", \"currencyCode\"], unique = true)"))
        assertTrue(service.contains("suspend fun addCurrencyToTreasuryGroup"))
        assertTrue(service.contains("AccountEntity("))
        assertTrue(service.contains("TREASURY_ADD_CURRENCY"))
        val addCurrency = service.substringAfter("suspend fun addCurrencyToTreasuryGroup").substringBefore("suspend fun postManualJournal")
        assertTrue(addCurrency.contains("SecurityPermissions.TREASURY_POST"))
        assertTrue(addCurrency.contains("SecurityPermissions.ACCOUNTING_POST"))
    }

    @Test
    fun treasuryCanBeEditedAndReceiptExplainsHowToAddMissingCurrency() {
        val service = source("app/src/main/java/com/fush/erp/domain/AccountingService.kt").readText()
        val accountingUi = source("app/src/main/java/com/fush/erp/ui/screens/AccountingScreens.kt").readText()
        val salesUi = source("app/src/main/java/com/fush/erp/ui/screens/SalesScreens.kt").readText()
        assertTrue(service.contains("suspend fun updateTreasuryGroup"))
        assertTrue(accountingUi.contains("تعديل الخزينة"))
        assertTrue(accountingUi.contains("إضافة عملة"))
        assertTrue(salesUi.contains("الحسابات > الخزينة"))
    }

    @Test
    fun migrationIsRegisteredInBothStartupPaths() {
        val db = source("app/src/main/java/com/fush/erp/data/FushDatabase.kt").readText()
        val container = source("app/src/main/java/com/fush/erp/data/AppContainer.kt").readText()
        val bootstrap = source("app/src/main/java/com/fush/erp/data/AccountingWaveBRoomBootstrap.kt").readText()
        assertTrue((Regex("FUSH_DB_SCHEMA_VERSION\\s*=\\s*(\\d+)").find(db)?.groupValues?.get(1)?.toInt() ?: 0) >= 52)
        assertTrue(container.contains("MIGRATION_51_52_MULTI_CURRENCY_TREASURY"))
        assertTrue(bootstrap.contains("MIGRATION_51_52_MULTI_CURRENCY_TREASURY"))
    }
}
