package com.fush.erp.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class V200TestCleanupCloudTombstoneContractTest {
    private fun mainSource(path: String): String = File("src/main/java/$path").readText()

    @Test
    fun shipmentExpenseCleanupPublishesCloudTombstonesBeforeLocalDelete() {
        val support = mainSource("com/fush/erp/domain/SupportService.kt")
        val repo = mainSource("com/fush/erp/cloud/CloudSyncRepository.kt")
        assertTrue(support.contains("publishTestShipmentExpenseDeletionTombstones"))
        assertTrue(support.indexOf("publishTestShipmentExpenseDeletionTombstones") < support.indexOf("DELETE FROM sales_shipment_expenses"))
        assertTrue(repo.contains("salesAuxiliarySync.publishSupportDeletionForShipmentExpense"))
        assertTrue(repo.contains("accountingSync.publishSupportDeletionForShipmentExpenseAccounting"))
        assertTrue(repo.indexOf("salesAuxiliarySync.publishSupportDeletionForShipmentExpense") < repo.indexOf("accountingSync.publishSupportDeletionForShipmentExpenseAccounting"))
    }

    @Test
    fun stalePhonesConsumeTombstonesBeforePublishingTheirLocalCopies() {
        val salesAux = mainSource("com/fush/erp/cloud/SalesAuxiliaryCloudSyncEngine.kt")
        val accounting = mainSource("com/fush/erp/cloud/AccountingCloudSyncEngine.kt")
        assertTrue(salesAux.contains("val tombstoneDeletes = applyDeletionTombstones(initialRemote, localUser)"))
        assertTrue(salesAux.indexOf("val tombstoneDeletes = applyDeletionTombstones(initialRemote, localUser)") < salesAux.indexOf("val localBefore = localDocuments()"))
        assertTrue(accounting.contains("val tombstoneDeletes = applyDeletionTombstones(remote, localUser)"))
        assertTrue(accounting.indexOf("val tombstoneDeletes = applyDeletionTombstones(remote, localUser)") < accounting.indexOf("val localJournals = localJournalPayloads"))
    }

    @Test
    fun unifiedSyncDeletesShipmentExpenseBeforeAccountingVoucherJournal() {
        val repo = mainSource("com/fush/erp/cloud/CloudSyncRepository.kt")
        assertTrue(repo.contains("syncSalesAuxiliaryDeletionTombstones(localUser)"))
        assertTrue(repo.indexOf("syncSalesAuxiliaryDeletionTombstones(localUser)") < repo.indexOf("syncAccounting(localUser)"))
    }

    @Test
    fun tombstoneDoesNotRequireSupabaseSchemaChangeAndKeepsDatabaseSchema49() {
        val salesAux = mainSource("com/fush/erp/cloud/SalesAuxiliaryCloudSyncEngine.kt")
        val accounting = mainSource("com/fush/erp/cloud/AccountingCloudSyncEngine.kt")
        val db = mainSource("com/fush/erp/data/FushDatabase.kt")
        val gradle = File("build.gradle.kts").readText()
        assertTrue(salesAux.contains("TEST_DATA_SUPPORT"))
        assertTrue(accounting.contains("TEST_DATA_SUPPORT"))
        assertTrue(accounting.contains("resolveCloud(\"JOURNAL\""))
        assertTrue(accounting.contains("resolveCloud(\"TREASURY_VOUCHER\""))
        assertTrue(Regex("FUSH_DB_SCHEMA_VERSION\\s*=\\s*(\\d+)").find(db)?.groupValues?.get(1)?.toInt() ?: 0 >= 49)
        assertFalse(mainSource("com/fush/erp/data/AppContainer.kt").contains("fallbackToDestructiveMigration"))
        assertTrue(Regex("versionCode = (200|20[1-9]|2[1-9][0-9]|[3-9][0-9]{2,})").containsMatchIn(gradle))
        assertTrue(gradle.contains("0.15.4."))
    }
}
