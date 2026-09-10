package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SupplierMovementIdentityTest {
    @Test
    fun positiveSupplierIdIsAccepted() {
        assertEquals(42L, SupplierMovementIdentity.requireId(42L))
    }

    @Test
    fun missingSupplierIdIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SupplierMovementIdentity.requireId(null)
        }
    }

    @Test
    fun zeroSupplierIdIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SupplierMovementIdentity.requireId(0L)
        }
    }

    @Test
    fun negativeSupplierIdIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SupplierMovementIdentity.requireId(-7L)
        }
    }
}
