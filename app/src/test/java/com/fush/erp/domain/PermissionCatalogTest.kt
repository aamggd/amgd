package com.fush.erp.domain

import org.junit.Assert.*
import org.junit.Test

class PermissionCatalogTest {
    @Test
    fun permissionCodesAreUnique() {
        val codes = PermissionCatalog.permissions.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun adminContainsEveryPermission() {
        assertEquals(
            PermissionCatalog.permissions.map { it.code }.toSet(),
            PermissionCatalog.defaultRolePermissions.getValue("ADMIN")
        )
    }

    @Test
    fun nonAdminRolesCannotManageSecurityByDefault() {
        PermissionCatalog.defaultRolePermissions
            .filterKeys { it != "ADMIN" }
            .forEach { (role, permissions) ->
                assertFalse(SecurityPermissions.USERS_MANAGE in permissions)
                assertFalse(SecurityPermissions.ROLES_MANAGE in permissions)
                if (role != "ACCOUNTANT") {
                    assertFalse(SecurityPermissions.BACKUP_RESTORE in permissions)
                }
            }
        assertTrue(
            SecurityPermissions.BACKUP_RESTORE in
                PermissionCatalog.defaultRolePermissions.getValue("ACCOUNTANT")
        )
    }

    @Test
    fun accountingReadOnlyRolesDoNotReceiveSensitivePostingPermissions() {
        val sensitive = setOf(
            SecurityPermissions.ACCOUNTING_POST,
            SecurityPermissions.CASH_COUNT_POST,
            SecurityPermissions.BANK_RECONCILIATION_POST,
            SecurityPermissions.FIXED_ASSET_POST,
            SecurityPermissions.FX_REVALUATION_POST,
            SecurityPermissions.ACCOUNTING_PERIOD_MANAGE,
            SecurityPermissions.ACCOUNTING_YEAR_CLOSE
        )
        listOf("AUDITOR", "VIEWER").forEach { role ->
            assertTrue(PermissionCatalog.defaultRolePermissions.getValue(role).intersect(sensitive).isEmpty())
        }
    }

    @Test
    fun salesRoleCanCreateCustomersByDefault() {
        val permissions = PermissionCatalog.defaultRolePermissions.getValue("SALES")
        assertTrue(SecurityPermissions.CUSTOMERS_CREATE in permissions)
    }

    @Test
    fun accountantReceivesSpecializedAccountingPostingPermissions() {
        val permissions = PermissionCatalog.defaultRolePermissions.getValue("ACCOUNTANT")
        assertTrue(SecurityPermissions.BANK_RECONCILIATION_POST in permissions)
        assertTrue(SecurityPermissions.FIXED_ASSET_POST in permissions)
        assertTrue(SecurityPermissions.FX_REVALUATION_POST in permissions)
        assertTrue(SecurityPermissions.ACCOUNTING_PERIOD_MANAGE in permissions)
        assertTrue(SecurityPermissions.ACCOUNTING_YEAR_CLOSE in permissions)
    }
    @Test
    fun accountantReceivesRequestedSalesPurchasesProductionBackupAndInventoryViewScope() {
        val permissions = PermissionCatalog.defaultRolePermissions.getValue("ACCOUNTANT")
        val required = PermissionCatalog.permissions
            .filter { it.moduleKey in setOf("SALES", "PURCHASES", "PRODUCTION") }
            .map { it.code }
            .toSet() + setOf(
                SecurityPermissions.BACKUP_CREATE,
                SecurityPermissions.BACKUP_RESTORE,
                SecurityPermissions.INVENTORY_VIEW
            )
        assertTrue("ACCOUNTANT missing requested permissions: ${required - permissions}", permissions.containsAll(required))
        assertTrue(SecurityPermissions.INVENTORY_TRANSFER !in permissions)
        assertTrue(SecurityPermissions.INVENTORY_COUNT !in permissions)
        assertTrue(SecurityPermissions.INVENTORY_ADJUST !in permissions)
    }

    @Test
    fun accountantRemainsSeparatedFromUnrelatedSensitiveTreasuryAndSupplierPaymentPermissions() {
        val permissions = PermissionCatalog.defaultRolePermissions.getValue("ACCOUNTANT")
        val forbidden = setOf(
            SecurityPermissions.CASH_COUNT_POST,
            SecurityPermissions.GEOGRAPHY_MANAGE
        )
        assertTrue("ACCOUNTANT received unrelated sensitive permissions: ${permissions.intersect(forbidden)}", permissions.intersect(forbidden).isEmpty())
        assertTrue(SecurityPermissions.TREASURY_POST in permissions)
        assertTrue(SecurityPermissions.SUPPLIER_PAYMENT_POST in permissions)
        assertTrue(SecurityPermissions.ACCOUNTING_VIEW in permissions)
        assertTrue(SecurityPermissions.ACCOUNTING_POST in permissions)
    }

}
