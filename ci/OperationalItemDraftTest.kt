package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OperationalItemDraftTest {
    private fun validDraft() = OperationalItemDraft(
        categoryId = 1,
        brandId = 2,
        inventoryUomId = 3,
        nameAr = "  شوكولاتة كندر  ",
        barcode = " 1234567890123 ",
        referencePurchasePrice = 750.0,
        salesPrice = 1000.0,
        salesPriceListId = 7,
        imageUri = " content://faz/product/1 ",
        reorderLevel = 5.0,
    )

    @Test
    fun validatesACompleteOperationalItemDraft() {
        OperationalItemDraftValidator.validate(validDraft())
    }

    @Test
    fun requiresRealMasterDataIdsAndAName() {
        assertThrows(IllegalArgumentException::class.java) {
            OperationalItemDraftValidator.validate(validDraft().copy(categoryId = 0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            OperationalItemDraftValidator.validate(validDraft().copy(brandId = 0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            OperationalItemDraftValidator.validate(validDraft().copy(inventoryUomId = 0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            OperationalItemDraftValidator.validate(validDraft().copy(nameAr = "   ـَ  "))
        }
    }

    @Test
    fun referencePurchasePriceIsNonAccountingReferenceButMustBeValid() {
        assertThrows(IllegalArgumentException::class.java) {
            OperationalItemDraftValidator.validate(validDraft().copy(referencePurchasePrice = -1.0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            OperationalItemDraftValidator.validate(validDraft().copy(referencePurchasePrice = Double.NaN))
        }
    }

    @Test
    fun salesPriceAndPriceListMustBeProvidedTogether() {
        assertThrows(IllegalArgumentException::class.java) {
            OperationalItemDraftValidator.validate(validDraft().copy(salesPrice = null, salesPriceListId = 7))
        }
        assertThrows(IllegalArgumentException::class.java) {
            OperationalItemDraftValidator.validate(validDraft().copy(salesPrice = 1000.0, salesPriceListId = null))
        }
        assertThrows(IllegalArgumentException::class.java) {
            OperationalItemDraftValidator.validate(validDraft().copy(salesPrice = -0.01))
        }
    }

    @Test
    fun normalizesDisplayInputsWithoutInventingValues() {
        val normalized = OperationalItemDraftValidator.normalized(validDraft())
        assertEquals("شوكولاتة كندر", normalized.nameAr)
        assertEquals("1234567890123", normalized.barcode)
        assertEquals("content://faz/product/1", normalized.imageUri)
    }
}
