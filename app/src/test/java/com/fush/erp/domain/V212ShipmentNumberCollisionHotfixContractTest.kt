package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class V212ShipmentNumberCollisionHotfixContractTest {
    private fun source(relative: String): String {
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Source file not found: $relative")
    }

    @Test
    fun shipmentNumberReconcilesAgainstHydratedShipmentRows() {
        val dao = source("src/main/java/com/fush/erp/data/dao/ShipmentDao.kt")
        val numbering = source("src/main/java/com/fush/erp/domain/AutoNumberService.kt")
        assertTrue(dao.contains("maxAutomaticShipmentSequence"))
        assertTrue(dao.contains("MAX(CAST(SUBSTR(shipmentNo"))
        assertTrue(numbering.contains("persistedShipmentValue"))
        assertTrue(numbering.contains("maxOf(sequenceValue, persistedShipmentValue) + 1L"))
        assertTrue(numbering.contains("db.shipmentDao().shipmentByNo(candidate) == null"))
    }

    @Test
    fun connectedShipmentCreationUsesCompanyWideCloudReservation() {
        val service = source("src/main/java/com/fush/erp/domain/ShipmentService.kt")
        val ui = source("src/main/java/com/fush/erp/ui/screens/SalesScreens.kt")
        assertTrue(service.contains("reserveNextShipmentNo"))
        assertTrue(service.contains("guard.reserve(createdBy, \"SALE_SHIPMENT\", candidate)"))
        assertTrue(service.contains("is DocumentNumberReservationResult.InUse -> Unit"))
        assertTrue(ui.contains("ShipmentService(container.db, container.cloudSyncRepository).createShipment"))
    }

    @Test
    fun v212KeepsRoomSchemaAndUpgradeIdentity() {
        val gradle = source("build.gradle.kts")
        val db = source("src/main/java/com/fush/erp/data/FushDatabase.kt")
        val versionCode = Regex("versionCode\\s*=\\s*(\\d+)").find(gradle)?.groupValues?.get(1)?.toInt() ?: 0
        assertTrue(versionCode >= 212)
        assertTrue(gradle.contains("versionName ="))
        assertTrue(gradle.contains("applicationId = \"com.fush.erp.recovery\""))
        assertTrue((Regex("FUSH_DB_SCHEMA_VERSION\\s*=\\s*(\\d+)").find(db)?.groupValues?.get(1)?.toInt() ?: 0) >= 53)
    }
}
