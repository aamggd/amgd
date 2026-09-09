package com.fush.erp.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class V201AdminUnusedShipmentDeleteContractTest {
    private fun source(path: String): String = File("src/main/java/$path").readText()

    @Test
    fun deleteIsAdminOnlyAndRejectsAnyBusinessUse() {
        val service = source("com/fush/erp/domain/ShipmentService.kt")
        val dao = source("com/fush/erp/data/dao/ShipmentDao.kt")
        assertTrue(service.contains("require(actor.role == \"ADMIN\")"))
        assertTrue(service.contains("shipmentExpenseCountAll"))
        assertTrue(service.contains("shipmentItemAllocationCountAll"))
        assertTrue(service.contains("shipmentExpenseAllocationCountAll"))
        assertTrue(dao.contains("DELETE FROM sales_shipments WHERE id=:shipmentId"))
        assertTrue(dao.contains("JOIN sales_shipment_items i ON i.id=a.shipmentItemId"))
    }

    @Test
    fun cloudTombstoneIsPublishedBeforeLocalHardDelete() {
        val service = source("com/fush/erp/domain/ShipmentService.kt")
        val repo = source("com/fush/erp/cloud/CloudSyncRepository.kt")
        val cloud = source("com/fush/erp/cloud/SalesAuxiliaryCloudSyncEngine.kt")
        val method = service.substringAfter("suspend fun deleteUnusedShipment").substringBefore("suspend fun markDelivered")
        assertTrue(method.contains("publishUnusedShipmentDeletionTombstones"))
        assertTrue(method.indexOf("publishUnusedShipmentDeletionTombstones") < method.indexOf("deleteShipmentById"))
        assertTrue(repo.contains("publishAdminDeletionForUnusedShipment"))
        assertTrue(cloud.contains("ADMIN_UNUSED_SHIPMENT_DELETE"))
        assertTrue(cloud.contains("TYPE_SHIPMENT_ITEM"))
        assertTrue(cloud.contains("TYPE_SHIPMENT"))
        assertTrue(cloud.contains("remoteUse.isEmpty()"))
    }

    @Test
    fun staleDeviceConsumesShipmentAndItemTombstonesBeforeRepublish() {
        val cloud = source("com/fush/erp/cloud/SalesAuxiliaryCloudSyncEngine.kt")
        assertTrue(cloud.contains("val tombstoneDeletes = applyDeletionTombstones(initialRemote, localUser)"))
        assertTrue(cloud.indexOf("val tombstoneDeletes = applyDeletionTombstones(initialRemote, localUser)") < cloud.indexOf("val localBefore = localDocuments()"))
        assertTrue(cloud.contains("CLOUD_UNUSED_SHIPMENT_DELETE_APPLIED"))
        assertTrue(cloud.contains("CLOUD_UNUSED_SHIPMENT_ITEM_DELETE_APPLIED"))
    }

    @Test
    fun shipmentManagerShowsExplicitDeleteWithConfirmation() {
        val screen = source("com/fush/erp/ui/screens/SalesScreens.kt")
        assertTrue(screen.contains("Text(\"حذف الشحنة\")"))
        assertTrue(screen.contains("ShipmentService(container.db, container.cloudSyncRepository)"))
        assertTrue(screen.contains("deleteUnusedShipment(candidate.id, user.id)"))
        assertTrue(screen.contains("تأكيد الحذف"))
    }

    @Test
    fun version201KeepsSchema49AndNoDestructiveMigration() {
        val gradle = File("build.gradle.kts").readText()
        val db = source("com/fush/erp/data/FushDatabase.kt")
        val container = source("com/fush/erp/data/AppContainer.kt")
        assertTrue(Regex("versionCode\\s*=\\s*(\\d+)").find(gradle)?.groupValues?.get(1)?.toInt() ?: 0 >= 201)
        assertTrue(gradle.contains("versionName ="))
        assertTrue(Regex("FUSH_DB_SCHEMA_VERSION\\s*=\\s*(\\d+)").find(db)?.groupValues?.get(1)?.toInt() ?: 0 >= 49)
        assertFalse(container.contains("fallbackToDestructiveMigration"))
    }
}
