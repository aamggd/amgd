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
            .values
            .forEach { permissions ->
                assertFalse(SecurityPermissions.USERS_MANAGE in permissions)
                assertFalse(SecurityPermissions.ROLES_MANAGE in permissions)
                assertFalse(SecurityPermissions.BACKUP_RESTORE in permissions)
            }
    }
}
