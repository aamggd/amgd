package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V162PurchaseConflictInspectorContractTest {
    private fun source(relative: String): String {
        val candidates = listOf(File("src/main/java/$relative"), File("app/src/main/java/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText() ?: error("Source file not found: $relative")
    }

    @Test
    fun v162KeepsRoom46AndDoesNotAddDestructiveMigration() {
        val db = source("com/fush/erp/data/FushDatabase.kt")
        val container = source("com/fush/erp/data/AppContainer.kt")
        assertTrue((Regex("FUSH_DB_SCHEMA_VERSION\\s*=\\s*(\\d+)").find(db)?.groupValues?.get(1)?.toInt() ?: 0) >= 46)
        assertFalse(container.contains("fallbackToDestructiveMigration"))
    }

    @Test
    fun purchaseSyncCapturesNonDestructiveConflictDetails() {
        val engine = source("com/fush/erp/cloud/PurchaseDocumentsCloudSyncEngine.kt")
        val models = source("com/fush/erp/cloud/PurchaseDocumentsSyncModels.kt")
        assertTrue(models.contains("PurchaseDocumentsConflict"))
        assertTrue(models.contains("PurchaseConflictDifference"))
        assertTrue(engine.contains("invoiceDifferences(existing, header, remote)"))
        assertTrue(engine.contains("returnDifferences(existing, header, remote)"))
        assertTrue(engine.contains("store.saveConflicts"))
        assertTrue(engine.contains("line_count"))
        assertTrue(engine.contains("unit_cost"))
        assertTrue(engine.contains("lot"))
        assertTrue(engine.contains("expiry"))
    }

    @Test
    fun purchaseConflictInspectorIsVisibleAndPersistent() {
        val repository = source("com/fush/erp/cloud/CloudSyncRepository.kt")
        val store = source("com/fush/erp/cloud/PurchaseDocumentsSyncStore.kt")
        val screen = source("com/fush/erp/ui/screens/CloudSyncScreen.kt")
        assertTrue(repository.contains("lastPurchaseDocumentsConflicts"))
        assertTrue(store.contains("saveConflicts"))
        assertTrue(store.contains("fun conflicts"))
        assertTrue(screen.contains("purchaseConflicts"))
        assertTrue(screen.contains("PurchaseConflictCard"))
        assertTrue(screen.contains("cloud_purchase_conflict_inspector_title"))
    }

    @Test
    fun v162DoesNotIntroducePurchaseConflictAutoOverwrite() {
        val engine = source("com/fush/erp/cloud/PurchaseDocumentsCloudSyncEngine.kt")
        assertTrue(engine.contains("conflictDetails += PurchaseDocumentsConflict"))
        assertFalse(engine.contains("resolvePurchaseConflict"))
        assertFalse(engine.contains("deleteConflictDocument"))
    }
}
