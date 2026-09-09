package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V163SupplierPaymentsTreasuryCloudMirrorContractTest {
    private fun source(relative: String): String {
        val candidates = listOf(File("src/main/java/$relative"), File("app/src/main/java/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText() ?: error("Source file not found: $relative")
    }

    @Test
    fun v163KeepsRoom46AndNoDestructiveMigration() {
        val db = source("com/fush/erp/data/FushDatabase.kt")
        val container = source("com/fush/erp/data/AppContainer.kt")
        assertTrue((Regex("FUSH_DB_SCHEMA_VERSION\\s*=\\s*(\\d+)").find(db)?.groupValues?.get(1)?.toInt() ?: 0) >= 46)
        assertFalse(container.contains("fallbackToDestructiveMigration"))
    }

    @Test
    fun supplierPaymentMirrorUsesStableBusinessKeys() {
        val engine = source("com/fush/erp/cloud/SupplierPaymentsTreasuryCloudSyncEngine.kt")
        assertTrue(engine.contains("payment_no"))
        assertTrue(engine.contains("supplier_code"))
        assertTrue(engine.contains("treasury_code"))
        assertTrue(engine.contains("invoice_no"))
        assertTrue(engine.contains("reversal_of_payment_no"))
    }

    @Test
    fun supplierPaymentMirrorDoesNotReplayAccountingSideEffects() {
        val engine = source("com/fush/erp/cloud/SupplierPaymentsTreasuryCloudSyncEngine.kt")
        assertFalse(engine.contains("insertJournal"))
        assertFalse(engine.contains("insertVoucher"))
        assertFalse(engine.contains("insertCashCount"))
        assertFalse(engine.contains("insertFxRevaluation"))
        assertTrue(engine.contains("insertSupplierPayment"))
        assertTrue(engine.contains("insertSupplierPaymentAllocation"))
    }

    @Test
    fun activeMembersMayTransportSupplierPaymentsWithoutBusinessRoleGate() {
        val engine = source("com/fush/erp/cloud/SupplierPaymentsTreasuryCloudSyncEngine.kt")
        assertTrue(engine.contains("canPublish = true"))
        assertFalse(engine.contains("cloudRole.trim().uppercase() in setOf(\"OWNER\", \"ADMIN\")"))
        assertTrue(engine.contains("skippedLocal"))
        assertTrue(engine.contains("conflicts += SupplierPaymentsTreasuryConflict"))
    }

    @Test
    fun repositoryAndScreenExposeV163Sync() {
        val repository = source("com/fush/erp/cloud/CloudSyncRepository.kt")
        val screen = source("com/fush/erp/ui/screens/CloudSyncScreen.kt")
        assertTrue(repository.contains("syncSupplierPaymentsTreasury"))
        assertTrue(repository.contains("lastSupplierPaymentsTreasurySyncAt"))
        assertTrue(screen.contains("cloud_supplier_payment_sync_now"))
        assertTrue(screen.contains("SupplierPaymentsTreasuryConflictCard"))
    }
}
