package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class V204AccountantTreasuryVoucherPermissionContractTest {
    @Test
    fun accountantDefaultAndUpgradeIncludeTreasuryVoucherManagementOnly() {
        val catalog = File("src/main/java/com/fush/erp/domain/PermissionCatalog.kt").readText()
        val service = File("src/main/java/com/fush/erp/domain/SecurityService.kt").readText()

        val accountantBlock = catalog.substringAfter("\"ACCOUNTANT\" to commonView").substringBefore("\"CASHIER\" to")
        assertTrue(accountantBlock.contains("SecurityPermissions.TREASURY_POST"))

        assertTrue(service.contains("ACCOUNTANT_TREASURY_VOUCHERS_V204"))
        assertTrue(service.contains("RolePermissionEntity(\"ACCOUNTANT\", SecurityPermissions.TREASURY_POST)"))
    }
}
