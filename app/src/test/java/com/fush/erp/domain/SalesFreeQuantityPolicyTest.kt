package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SalesFreeQuantityPolicyTest {
    @Test fun revenueUsesSoldQuantityOnlyWhilePhysicalQuantityIncludesFree() {
        val line = SalesDraftLine(1, 1, 10.0, 1.0, 100.0, freeQuantity = 2.0)
        assertEquals(1000.0, line.grossOriginal, 0.0001)
        assertEquals(10.0, line.baseQuantity, 0.0001)
        assertEquals(2.0, line.freeBaseQuantity, 0.0001)
        assertEquals(12.0, line.totalBaseQuantity, 0.0001)
    }

    @Test fun repLimitRequiresApprovalOnlyAboveAllowedFreeQuantity() {
        assertFalse(SalesFreeQuantityPolicy.requiresApproval(100.0, 5.0, 5.0))
        assertTrue(SalesFreeQuantityPolicy.requiresApproval(100.0, 5.01, 5.0))
        assertEquals(5.0, SalesFreeQuantityPolicy.allowedFreeBase(100.0, 5.0), 0.0001)
        // Per-line enforcement: a different product's sold quantity must not subsidize this line's free quantity.
        assertTrue(SalesFreeQuantityPolicy.requiresApproval(10.0, 1.0, 5.0))
    }
}
