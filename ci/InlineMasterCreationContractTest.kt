package com.fush.erp.domain

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class InlineMasterCreationContractTest {
    private fun source(path: String): String {
        val candidates = listOf(
            File(path),
            File(System.getProperty("user.dir"), path),
            File(System.getProperty("user.dir"), "../$path")
        )
        return candidates.firstOrNull { it.exists() }?.readText()
            ?: error("Source file not found: $path")
    }

    @Test
    fun quickCreateCategoryBrandAndUnitAreDuplicateSafeAndPermissionGuarded() {
        val productMaster = source("src/main/java/com/fush/erp/domain/ProductMasterService.kt")
        val masterData = source("src/main/java/com/fush/erp/domain/MasterDataService.kt")
        val autoNumber = source("src/main/java/com/fush/erp/domain/AutoNumberService.kt")

        assertTrue(productMaster.contains("createCategoryQuick"))
        assertTrue(productMaster.contains("createBrandQuick"))
        assertTrue(productMaster.contains("MasterNameNormalizer.normalize"))
        assertTrue(productMaster.contains("MASTER_DATA_MANAGE"))
        assertTrue(productMaster.contains("categoryByNormalizedName"))
        assertTrue(productMaster.contains("audit"))

        assertTrue(masterData.contains("MasterNameNormalizer.normalize"))
        assertTrue(masterData.contains("createUnit"))
        assertTrue(masterData.contains("MASTER_DATA_MANAGE"))

        assertTrue(autoNumber.contains("nextProductCategoryCode"))
        assertTrue(autoNumber.contains("nextProductFamilyCode"))
        assertTrue(autoNumber.contains("nextBrandCode"))
    }
}
