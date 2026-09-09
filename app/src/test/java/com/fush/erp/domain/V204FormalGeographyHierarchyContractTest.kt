package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V204FormalGeographyHierarchyContractTest {
    private fun source(relative: String): String {
        val candidates = listOf(File("src/main/java/$relative"), File("app/src/main/java/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText() ?: error("Source file not found: $relative")
    }

    @Test
    fun version204UsesRoom51WithoutDestructiveMigration() {
        val gradle = listOf(File("build.gradle.kts"), File("app/build.gradle.kts")).first { it.isFile }.readText()
        val db = source("com/fush/erp/data/FushDatabase.kt")
        val container = source("com/fush/erp/data/AppContainer.kt")
        val migration = source("com/fush/erp/data/V204GeographyHierarchyMigration.kt")
        assertTrue(Regex("versionCode\\s*=\\s*(\\d+)").find(gradle)!!.groupValues[1].toInt() >= 204)
        assertTrue(gradle.contains("versionName ="))
        val schema = Regex("FUSH_DB_SCHEMA_VERSION\\s*=\\s*(\\d+)").find(db)?.groupValues?.get(1)?.toInt() ?: 0
        assertTrue(schema >= 51)
        assertTrue(container.contains("MIGRATION_50_51_GEOGRAPHY_HIERARCHY"))
        assertTrue(migration.contains("Migration(50, 51)"))
        assertFalse(container.contains("fallbackToDestructiveMigration"))
        assertFalse(source("com/fush/erp/data/FushDatabase.kt").contains("fallbackToDestructiveMigration"))
    }

    @Test
    fun shipmentsPersistFormalLocationAndInvoiceLinkingIsGovernorateIdBased() {
        val service = source("com/fush/erp/domain/ShipmentService.kt")
        val dao = source("com/fush/erp/data/dao/ShipmentDao.kt")
        val cloud = source("com/fush/erp/cloud/SalesAuxiliaryCloudSyncEngine.kt")

        assertTrue(service.contains("destinationGovernorateId = location.governorate.id"))
        assertTrue(service.contains("destinationDistrictId = location.district?.id"))
        assertTrue(service.contains("destinationAreaId = location.area?.id"))
        assertTrue(service.contains("require(shipmentGovernorateId == invoiceGovernorateId)"))
        assertTrue(cloud.contains("require(shipmentGovernorateId == invoiceGovernorateId)"))
        assertTrue(dao.contains(":governorateId IS NOT NULL AND s.destinationGovernorateId=:governorateId"))
        assertTrue(dao.contains(":governorateId IS NULL AND s.destinationGovernorateId IS NULL"))
        assertFalse(dao.contains("ELSE 1 END, s.shipmentDate"))
    }

    @Test
    fun customersInvoicesAndShipmentsCarryStableLocationKeys() {
        val salesEntities = source("com/fush/erp/data/entity/SalesEntities.kt")
        val shipmentEntities = source("com/fush/erp/data/entity/ShipmentEntities.kt")
        val salesService = source("com/fush/erp/domain/SalesService.kt")
        assertTrue(salesEntities.contains("val governorateId: String? = null"))
        assertTrue(salesEntities.contains("val districtId: String? = null"))
        assertTrue(salesEntities.contains("val areaId: String? = null"))
        assertTrue(shipmentEntities.contains("val destinationGovernorateId: String? = null"))
        assertTrue(salesService.contains("governorateId = location.governorate.id"))
        assertTrue(salesService.contains("districtId = location.district?.id"))
        assertTrue(salesService.contains("areaId = location.area?.id"))
    }

    @Test
    fun formalGeographyIsPartOfMasterDataCloudSync() {
        val sync = source("com/fush/erp/cloud/MasterDataCloudSyncEngine.kt")
        listOf("fush_md_governorates", "fush_md_districts", "fush_md_areas").forEach {
            assertTrue("Missing cloud master scope $it", sync.contains(it))
        }
        assertTrue(sync.contains("governorate_id"))
        assertTrue(sync.contains("district_id"))
        assertTrue(sync.contains("area_id"))
    }

    @Test
    fun officialSeedAssetIsPackagedAndUserEditsAreIgnoreOnly() {
        val seed = listOf(
            File("src/main/assets/yemen_geography_seed_v204.json"),
            File("app/src/main/assets/yemen_geography_seed_v204.json")
        ).first { it.isFile }
        val text = seed.readText()
        val loader = source("com/fush/erp/data/YemenGeographyHierarchy.kt")
        assertTrue(text.contains("\"governorates\""))
        assertTrue(text.contains("\"districts\""))
        assertTrue(text.contains("\"areas\""))
        assertTrue(text.contains("\"nameAr\":\"تعز\"") || text.contains("\"nameAr\": \"تعز\""))
        assertTrue(loader.contains("insertGovernoratesIgnore"))
        assertTrue(loader.contains("insertDistrictsIgnore"))
        assertTrue(loader.contains("insertAreasIgnore"))
    }
}
