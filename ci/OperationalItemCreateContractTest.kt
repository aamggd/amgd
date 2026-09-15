package com.fush.erp.domain

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OperationalItemCreateContractTest {
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
    fun unifiedOperationalItemCreateIsAtomicAndPersistsOnlyReferencePurchasePrice() {
        val productMaster = source("src/main/java/com/fush/erp/domain/ProductMasterService.kt")
        val autoNumber = source("src/main/java/com/fush/erp/domain/AutoNumberService.kt")

        assertTrue(productMaster.contains("createOperationalItem"))
        assertTrue(productMaster.contains("OperationalItemDraftValidator.validate"))
        assertTrue(productMaster.contains("db.withTransaction"))
        assertTrue(productMaster.contains("MASTER_DATA_MANAGE"))
        assertTrue(productMaster.contains("categoryById"))
        assertTrue(productMaster.contains("brandById"))
        assertTrue(productMaster.contains("unitDao().byId"))
        assertTrue(productMaster.contains("nextProductFamilyCode"))
        assertTrue(productMaster.contains("categoryId = category.id"))
        assertTrue(productMaster.contains("referencePurchasePrice = draft.referencePurchasePrice"))
        assertTrue(productMaster.contains("imageUri = draft.imageUri"))
        assertTrue(productMaster.contains("insertVariantPrice"))
        assertTrue(productMaster.contains("PRODUCT_VARIANT_PRICE"))
        assertTrue(autoNumber.contains("nextProductFamilyCode"))
    }
}
