package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V178ProductionBackfillAccountantContractTest {
    private fun source(relative: String): String {
        val candidates = listOf(File("src/main/java/$relative"), File("app/src/main/java/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText() ?: error("Source file not found: $relative")
    }

    @Test
    fun releaseIdentityKeepsRoom46AndUpdateSafety() {
        val gradle = listOf(File("app/build.gradle.kts"), File("build.gradle.kts")).first { it.isFile }.readText()
        val db = source("com/fush/erp/data/FushDatabase.kt")
        assertTrue(gradle.contains("applicationId = \"com.fush.erp.recovery\""))
        val currentVersion = Regex("versionCode\\s*=\\s*(\\d+)").find(gradle)?.groupValues?.get(1)?.toInt() ?: 0
        assertTrue(currentVersion >= 178)
        assertTrue(gradle.contains("versionName ="))
        assertTrue((Regex("FUSH_DB_SCHEMA_VERSION\\s*=\\s*(\\d+)").find(db)?.groupValues?.get(1)?.toInt() ?: 0) >= 46)
        assertFalse(db.contains("fallbackToDestructiveMigration"))
        assertFalse(db.contains("deleteDatabase("))
    }

    @Test
    fun v185SyncTransportNoLongerDependsOnBusinessRole() {
        val engine = source("com/fush/erp/cloud/InventoryProductionCloudSyncEngine.kt")
        assertTrue(engine.contains("canPublishProduction = true"))
        assertTrue(engine.contains("canPublishInventory = true"))
        assertFalse(engine.contains("canPublishProduction = role in setOf"))
        assertFalse(engine.contains("canPublishInventory = role in setOf"))
    }

    @Test
    fun closedProductionBackfillIgnoresOutboundCursorAndPublishesOnlyCloudMissingOrders() {
        val engine = source("com/fush/erp/cloud/InventoryProductionCloudSyncEngine.kt")
        val start = engine.indexOf("private suspend fun publishMissingClosedProductionOrders")
        val end = engine.indexOf("private suspend fun publishProductionOrderRows", start)
        assertTrue(start >= 0 && end > start)
        val block = engine.substring(start, end)
        assertTrue(block.contains("order.status.equals(\"CLOSED\", true)"))
        assertTrue(block.contains("order.orderNo.trim().uppercase() !in remoteKeys"))
        assertFalse(block.contains("outboundCursor"))
        assertFalse(block.contains("closedAt"))
        assertTrue(engine.contains("if (!bootstrapped && canPublishProduction)"))
        assertTrue(engine.contains("publishMissingClosedProductionOrders(session, remote)"))
    }

    @Test
    fun existingCloudProductionIsComparedAndConflictedWithoutSilentOverwritePath() {
        val engine = source("com/fush/erp/cloud/InventoryProductionCloudSyncEngine.kt")
        val applyPos = engine.indexOf("applyProduction(localUser, remote, counters, conflicts)")
        val backfillPos = engine.indexOf("publishMissingClosedProductionOrders(session, remote)")
        assertTrue(applyPos >= 0 && backfillPos > applyPos)
        assertTrue(engine.contains("val differences = productionDifferences(existing, header, remote)"))
        assertTrue(engine.contains("counters.productionConflicts++"))
        assertTrue(engine.contains("InventoryProductionConflict(\"PRODUCTION_ORDER\", orderNo, differences)"))
    }

    @Test
    fun cloudProductionDownloadIsDocumentHydrationOnlyAndDoesNotReexecuteProductionService() {
        val engine = source("com/fush/erp/cloud/InventoryProductionCloudSyncEngine.kt")
        val start = engine.indexOf("private suspend fun applyProduction(")
        val end = engine.indexOf("private suspend fun reconcileInventory", start)
        assertTrue(start >= 0 && end > start)
        val block = engine.substring(start, end)
        assertTrue(block.contains("dao.insertOrder("))
        assertTrue(block.contains("dao.insertMaterials("))
        assertTrue(block.contains("dao.insertBatch("))
        assertTrue(block.contains("dao.insertIssue("))
        assertFalse(block.contains("ProductionService"))
        assertFalse(block.contains("reserveMaterials"))
        assertFalse(block.contains("issueMaterial"))
        assertFalse(block.contains("receiveFinished"))
        assertFalse(block.contains("closeOrder"))
    }

    @Test
    fun v177AccountingAndTreasurySyncRemainPresent() {
        val repository = source("com/fush/erp/cloud/CloudSyncRepository.kt")
        val accounting = source("com/fush/erp/cloud/AccountingCloudSyncEngine.kt")
        assertTrue(repository.contains("syncAccounting"))
        assertTrue(repository.contains("resolveAccountingConflict"))
        assertTrue(accounting.contains("fush_publish_accounting_batch"))
        assertTrue(accounting.contains("fush_resolve_accounting_conflict"))
        assertTrue(accounting.contains("treasuryBalanceCheck"))
    }
}
