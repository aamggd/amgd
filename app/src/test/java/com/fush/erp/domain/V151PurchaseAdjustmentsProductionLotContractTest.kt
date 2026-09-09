package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class V151PurchaseAdjustmentsProductionLotContractTest {
    private val root = File("src/main/java/com/fush/erp")

    @Test
    fun purchaseAdjustmentMigrationIsNonDestructiveAndRegistered() {
        val migration = File(root, "data/PurchaseAdjustmentsMigration.kt").readText()
        val container = File(root, "data/AppContainer.kt").readText()
        val bootstrap = File(root, "data/AccountingWaveBRoomBootstrap.kt").readText()
        assertTrue(migration.contains("Migration(45, 46)"))
        listOf("discountOriginal", "freightOriginal", "customsOriginal", "otherChargesOriginal").forEach {
            assertTrue(migration.contains("ADD COLUMN $it REAL NOT NULL DEFAULT 0.0"))
        }
        assertTrue(!migration.contains("DROP TABLE", ignoreCase = true))
        assertTrue(!migration.contains("DELETE FROM", ignoreCase = true))
        assertTrue(container.contains("MIGRATION_45_46_PURCHASE_INVOICE_ADJUSTMENTS"))
        assertTrue(bootstrap.contains("MIGRATION_45_46_PURCHASE_INVOICE_ADJUSTMENTS"))
    }

    @Test
    fun productionIssueUsesOnlyTrackingCompleteLotsAndDefaultsToActualDate() {
        val service = File(root, "domain/ProductionService.kt").readText()
        val screen = File(root, "ui/screens/ProductionScreens.kt").readText()
        assertTrue(Regex("isIssueLotTrackingComplete").findAll(service).count() >= 3)
        assertTrue(Regex("candidateLots").findAll(service).count() >= 2)
        assertTrue(Regex("incompleteTrackingQty").findAll(service).count() >= 2)
        assertTrue(screen.contains("initialDate = maxOf(order.plannedDate"))
    }
}
