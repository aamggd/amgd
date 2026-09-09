package com.fush.erp.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class V154AccountantOperationsPermissionsContractTest {
    private fun source(path: String) = File(path).takeIf { it.exists() } ?: File("../$path")

    @Test
    fun accountantDefaultsMatchRequestedOperationalScope() {
        val accountant = PermissionCatalog.defaultRolePermissions.getValue("ACCOUNTANT")
        val allSalesPurchasesProduction = PermissionCatalog.permissions
            .filter { it.moduleKey in setOf("SALES", "PURCHASES", "PRODUCTION") }
            .map { it.code }
            .toSet()

        assertTrue(accountant.containsAll(allSalesPurchasesProduction))
        assertTrue(SecurityPermissions.BACKUP_CREATE in accountant)
        assertTrue(SecurityPermissions.BACKUP_RESTORE in accountant)
        assertTrue(SecurityPermissions.INVENTORY_VIEW in accountant)
        assertFalse(SecurityPermissions.INVENTORY_TRANSFER in accountant)
        assertFalse(SecurityPermissions.INVENTORY_COUNT in accountant)
        assertFalse(SecurityPermissions.INVENTORY_ADJUST in accountant)
    }

    @Test
    fun existingDatabasesReceiveExactAccountantScopeUpgrade() {
        val text = source("app/src/main/java/com/fush/erp/domain/SecurityService.kt").readText()
        assertTrue(text.contains("ACCOUNTANT_SCOPE_V154"))
        assertTrue(text.contains("it.moduleKey in setOf(\"SALES\", \"PURCHASES\", \"PRODUCTION\")"))
        assertTrue(text.contains("SecurityPermissions.BACKUP_CREATE"))
        assertTrue(text.contains("SecurityPermissions.BACKUP_RESTORE"))
        assertTrue(text.contains("SecurityPermissions.INVENTORY_VIEW"))
        assertTrue(text.contains("dao.deleteRolePermissions(\"ACCOUNTANT\", revokedInventoryWrites)"))
    }

    @Test
    fun accountantKeepsSpecializedTreasuryBoundaryButGetsRequestedVoucherManagement() {
        val accountant = PermissionCatalog.defaultRolePermissions.getValue("ACCOUNTANT")
        // v204 explicitly adds treasury voucher management while keeping specialized controls separate.
        assertTrue(SecurityPermissions.TREASURY_POST in accountant)
        assertFalse(SecurityPermissions.CASH_COUNT_POST in accountant)
        assertFalse(SecurityPermissions.GEOGRAPHY_MANAGE in accountant)
        assertTrue(SecurityPermissions.SUPPLIER_PAYMENT_POST in accountant)
        assertTrue(SecurityPermissions.COLLECTION_POST in accountant)
        assertTrue(SecurityPermissions.COLLECTION_DISCOUNT_POST in accountant)
    }
}
