package com.fush.erp.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UnifiedItemCreationContractTest {
    private fun source(path: String): String {
        val candidates = listOf(File(path), File("../$path"), File("../../$path"))
        return candidates.firstOrNull { it.exists() }?.readText()
            ?: error("Source file not found: $path")
    }

    @Test
    fun unifiedItemCreationIsAtomicAndPreservesOperationalVariantIdentity() {
        val service = source("app/src/main/java/com/fush/erp/domain/ProductMasterService.kt")
        val dao = source("app/src/main/java/com/fush/erp/data/dao/ProductMasterDao.kt")

        assertTrue(service.contains("data class UnifiedItemCreateRequest"))
        assertTrue(service.contains("suspend fun createUnifiedItem"))
        assertTrue(service.contains("db.withTransaction"))
        assertTrue(service.contains("SecurityPermissions.MASTER_DATA_MANAGE"))
        assertTrue(service.contains("categoryById(request.categoryId)"))
        assertTrue(service.contains("brandById(request.brandId)"))
        assertTrue(service.contains("unitDao().byId(request.baseUnitId)"))
        assertTrue(service.contains("nextProductFamilyCode()"))
        assertTrue(service.contains("nextItemCode(request.compatibilityCategoryCode)"))
        assertTrue(service.contains("ProductFamilyEntity("))
        assertTrue(service.contains("categoryId = category.id"))
        assertTrue(service.contains("ProductTemplateEntity("))
        assertTrue(service.contains("val itemId = db.itemDao().insert(item)"))
        assertTrue(service.contains("id = itemId"))
        assertTrue(service.contains("db.productMasterDao().insertVariant(variant)"))
        assertTrue(service.contains("factorToBase = 1.0"))
        assertTrue(service.contains("barcodeConflictCount(normalizedBarcode)"))
        assertTrue(service.contains("itemUnitConversionDao().barcodeConflictCount(normalizedBarcode, 0)"))
        assertTrue(service.contains("referencePurchasePrice = request.referencePurchasePrice"))
        assertTrue(service.contains("imageUri = request.imageUri"))
        assertTrue(service.contains("request.salePrice > 0.0"))
        assertTrue(service.contains("firstActiveRetailPriceList()"))
        assertTrue(service.contains("ProductVariantPriceEntity("))
        assertTrue(service.contains("audit(createdBy, \"CREATE\", \"PRODUCT_VARIANT\""))

        assertTrue(dao.contains("suspend fun firstActiveRetailPriceList(): PriceListEntity?"))
        assertTrue(dao.contains("priceType = 'RETAIL'"))

        // Reference purchase price is deliberately not an accounting-cost write.
        val unifiedBody = service.substringAfter("suspend fun createUnifiedItem").substringBefore("suspend fun setVariantActive")
        assertFalse(unifiedBody.contains("inventory_cost_layers"))
        assertFalse(unifiedBody.contains("insertCostLayer"))
        assertFalse(unifiedBody.contains("journal"))
        assertFalse(unifiedBody.contains("postGl"))
    }

    @Test
    fun requestKeepsUserCategorySeparateFromLegacyCompatibilityClassification() {
        val service = source("app/src/main/java/com/fush/erp/domain/ProductMasterService.kt")
        assertTrue(service.contains("val categoryId: Long"))
        assertTrue(service.contains("val compatibilityCategoryCode: String = \"FINISHED_GOOD\""))
        assertTrue(service.contains("setOf(\"RAW_MATERIAL\", \"PACKAGING\", \"FINISHED_GOOD\")"))
    }
}
