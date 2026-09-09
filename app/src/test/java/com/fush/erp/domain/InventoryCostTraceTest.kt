package com.fush.erp.domain

import org.junit.Assert.assertThrows
import org.junit.Test

class InventoryCostTraceTest {
    @Test fun signedMovementLayersReconstructInventoryValuation() {
        InventoryCostTracePolicy.requireValuationReconstructable(125.50, 125.50)
    }
    @Test fun valuationMismatchIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            InventoryCostTracePolicy.requireValuationReconstructable(125.50, 124.00)
        }
    }
    @Test fun mappedInventoryGlSourceMustReconcile() {
        InventoryCostTracePolicy.requireMappedGlGroupsReconcile(
            listOf(InventoryCostTraceGroup("SALE", "17", -40.0, -40.0))
        )
    }
    @Test fun mappedGlMismatchIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            InventoryCostTracePolicy.requireMappedGlGroupsReconcile(
                listOf(InventoryCostTraceGroup("PURCHASE", "9", 100.0, 90.0))
            )
        }
    }
}
