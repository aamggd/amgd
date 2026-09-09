package com.fush.erp.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V189RolePermissionGrantSessionTest {
    @Test
    fun additiveGrantDoesNotInvalidateExistingRoleSessions() {
        val before = setOf(SecurityPermissions.ACCOUNTING_VIEW, SecurityPermissions.ACCOUNTING_POST)
        val after = before + SecurityPermissions.TREASURY_POST

        assertFalse(rolePermissionChangeRequiresSessionInvalidation(before, after))
    }

    @Test
    fun revocationStillInvalidatesExistingRoleSessions() {
        val before = setOf(
            SecurityPermissions.ACCOUNTING_VIEW,
            SecurityPermissions.ACCOUNTING_POST,
            SecurityPermissions.TREASURY_POST,
        )
        val after = before - SecurityPermissions.TREASURY_POST

        assertTrue(rolePermissionChangeRequiresSessionInvalidation(before, after))
    }

    @Test
    fun mixedChangeThatRemovesAnyPermissionStillInvalidatesSessions() {
        val before = setOf(SecurityPermissions.ACCOUNTING_VIEW, SecurityPermissions.TREASURY_POST)
        val after = setOf(SecurityPermissions.ACCOUNTING_VIEW, SecurityPermissions.CASH_COUNT_POST)

        assertTrue(rolePermissionChangeRequiresSessionInvalidation(before, after))
    }
}
