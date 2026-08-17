package com.fush.erp.domain

import org.junit.Assert.assertThrows
import org.junit.Test

class InventoryCountMathTest {
    @Test
    fun missingLine_acceptsValidUntrackedItem() {
        InventoryCountMath.validateMissingLine(
            InventoryCountMath.MissingLineInput(
                countedQtyBase = 3.0,
                unitCostBase = 125.0,
                lotTracked = false,
                expiryTracked = false,
                lotNo = null,
                expiryDate = null
            )
        )
    }

    @Test
    fun missingLine_rejectsNonPositiveQuantity() {
        assertThrows(IllegalArgumentException::class.java) {
            InventoryCountMath.validateMissingLine(
                InventoryCountMath.MissingLineInput(0.0, 10.0, false, false, null, null)
            )
        }
    }

    @Test
    fun missingLine_rejectsNonPositiveCost() {
        assertThrows(IllegalArgumentException::class.java) {
            InventoryCountMath.validateMissingLine(
                InventoryCountMath.MissingLineInput(1.0, 0.0, false, false, null, null)
            )
        }
    }

    @Test
    fun missingLine_requiresLotWhenTracked() {
        assertThrows(IllegalArgumentException::class.java) {
            InventoryCountMath.validateMissingLine(
                InventoryCountMath.MissingLineInput(1.0, 10.0, true, false, " ", null)
            )
        }
    }

    @Test
    fun missingLine_requiresExpiryWhenTracked() {
        assertThrows(IllegalArgumentException::class.java) {
            InventoryCountMath.validateMissingLine(
                InventoryCountMath.MissingLineInput(1.0, 10.0, false, true, null, null)
            )
        }
    }
}
