package com.fush.erp.ui.screens

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerSearchAutocompleteContractTest {
    private fun source(relative: String): String {
        val candidates = listOf(
            File("src/main/java/$relative"),
            File("app/src/main/java/$relative")
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Source file not found: $relative")
    }

    @Test
    fun salesInvoiceCustomerFieldSupportsSearchAndAutocomplete() {
        val ui = source("com/fush/erp/ui/screens/SalesScreens.kt")
        assertTrue(ui.contains("private fun SalesCustomerSearchField"))
        assertTrue(ui.contains("اكتب اسم أو كود العميل..."))
        assertTrue(ui.contains("customer.nameAr"))
        assertTrue(ui.contains("customer.nameEn"))
        assertTrue(ui.contains("customer.code"))
        assertTrue(ui.contains("customer.phone"))
        assertTrue(ui.contains("filterQuery.isBlank()) customers"))
    }

    @Test
    fun newCustomerActionIsPermissionGatedAndAutoSelectsCreatedCustomer() {
        val ui = source("com/fush/erp/ui/screens/SalesScreens.kt")
        assertTrue(ui.contains("SecurityPermissions.CUSTOMERS_CREATE in rolePermissions"))
        assertTrue(ui.contains("if (canCreateCustomer)"))
        assertTrue(ui.contains("+ عميل جديد"))
        assertTrue(ui.contains("val created = container.salesService.createCustomer"))
        assertTrue(ui.contains("customer = created"))
    }

    @Test
    fun customerCreationServiceUsesDedicatedPermission() {
        val service = source("com/fush/erp/domain/SalesService.kt")
        val start = service.indexOf("suspend fun createCustomer(")
        val end = service.indexOf("suspend fun", start + 1).let { if (it < 0) service.length else it }
        val block = service.substring(start, end)
        assertTrue(block.contains("db.requireUserPermission(createdBy, SecurityPermissions.CUSTOMERS_CREATE)"))
    }

    @Test
    fun existingSalesPostRolesKeepCustomerCreationOnceDuringUpgrade() {
        val security = source("com/fush/erp/domain/SecurityService.kt")
        assertTrue(security.contains("CUSTOMER_CREATE_V118"))
        assertTrue(security.contains("SecurityPermissions.SALES_POST in current"))
        assertTrue(security.contains("RolePermissionEntity(role.code, SecurityPermissions.CUSTOMERS_CREATE)"))
    }
}
