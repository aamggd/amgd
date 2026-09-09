package com.fush.erp.domain

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class V198AccountingReferencePrerequisiteReconciliationContractTest {
    private fun source(path: String): String = File("src/main/java/$path").readText()

    @Test
    fun unifiedSyncHydratesAccountingReferencesBeforeAnyTransactionDomain() {
        val repository = source("com/fush/erp/cloud/CloudSyncRepository.kt")
        val block = repository.substringAfter("suspend fun syncAllCompanyData").substringBefore("suspend fun signIn")
        val master = block.indexOf("syncMasterData(localUser)")
        val refs = block.indexOf("syncAccountingReferencePrerequisites(localUser)")
        val sales = block.indexOf("syncSalesReceivables(localUser)")
        val supplierPayments = block.indexOf("syncSupplierPaymentsTreasury(localUser)")
        val accounting = block.indexOf("syncAccounting(localUser)")
        assertTrue(master >= 0)
        assertTrue(refs > master)
        assertTrue(sales > refs)
        assertTrue(supplierPayments > refs)
        assertTrue(accounting > supplierPayments)
    }

    @Test
    fun referenceSnapshotCarriesAllAccountingForeignKeyPrerequisites() {
        val engine = source("com/fush/erp/cloud/AccountingCloudSyncEngine.kt")
        assertTrue(engine.contains("_fush_accounting_reference_snapshot"))
        assertTrue(engine.contains("db.accountDao().allRows()"))
        assertTrue(engine.contains("db.accountingDao().allTreasury()"))
        assertTrue(engine.contains("allEmployeesForCloudReferenceSync()"))
        assertTrue(engine.contains("allForCloudReferenceSync()"))
        assertTrue(engine.contains("ledger_account_code"))
        assertTrue(engine.contains("employee_code"))
    }

    @Test
    fun snapshotIsMissingOnlyAndDoesNotOverwriteExistingMasterRows() {
        val engine = source("com/fush/erp/cloud/AccountingCloudSyncEngine.kt")
        val apply = engine.substringAfter("private suspend fun applyLatestReferenceSnapshot").substringBefore("private fun referenceSnapshotHash")
        assertTrue(apply.contains("db.accountDao().byCode(code) != null) continue"))
        assertTrue(apply.contains("db.accountingDao().treasuryByCode(code) != null) continue"))
        assertTrue(apply.contains("db.employeeDao().employeeByCode(code) != null) continue"))
        assertTrue(apply.contains("db.salesRepresentativeDao().byCode(code) != null) continue"))
    }

    @Test
    fun carrierJournalPublishesTheExactCloudSnapshotToAvoidFullJsonConflicts() {
        val engine = source("com/fush/erp/cloud/AccountingCloudSyncEngine.kt")
        assertTrue(engine.contains("val referenceCarrier = referenceSnapshotCarrier(remote)"))
        assertTrue(engine.contains("localJournalPayloads(referenceCarrier?.first, referenceCarrier?.second)"))
        assertTrue(engine.contains("payload.put(REFERENCE_SNAPSHOT_FIELD, JSONObject(referenceSnapshot.toString()))"))
    }

    @Test
    fun version198OrLaterKeepsRoomSchema49() {
        val gradle = File("build.gradle.kts").readText()
        val db = source("com/fush/erp/data/FushDatabase.kt")
        assertTrue(Regex("versionCode = (19[8-9]|[2-9][0-9]{2,})").containsMatchIn(gradle))
        assertTrue(Regex("FUSH_DB_SCHEMA_VERSION\\s*=\\s*(\\d+)").find(db)?.groupValues?.get(1)?.toInt() ?: 0 >= 49)
    }
}
