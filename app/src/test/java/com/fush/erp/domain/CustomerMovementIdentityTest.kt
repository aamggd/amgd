package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CustomerMovementIdentityTest {
    @Test
    fun positiveCustomerIdIsAccepted() {
        assertEquals(42L, CustomerMovementIdentity.requireId(42L))
    }

    @Test
    fun missingCustomerIdIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            CustomerMovementIdentity.requireId(null)
        }
    }

    @Test
    fun zeroCustomerIdIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            CustomerMovementIdentity.requireId(0L)
        }
    }

    @Test
    fun negativeCustomerIdIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            CustomerMovementIdentity.requireId(-7L)
        }
    }
}
