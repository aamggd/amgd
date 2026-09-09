package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseClassificationPolicyTest {
    @Test
    fun `canonical dictionary contains unique stable codes`() {
        assertEquals(
            ExpenseClassificationPolicy.costCenters.size,
            ExpenseClassificationPolicy.costCenters.map { it.code }.toSet().size
        )
        assertEquals(
            ExpenseClassificationPolicy.referenceTypes.size,
            ExpenseClassificationPolicy.referenceTypes.map { it.code }.toSet().size
        )
        assertTrue(ExpenseClassificationPolicy.costCenters.any { it.code == "PRODUCTION" })
        assertTrue(ExpenseClassificationPolicy.referenceTypes.any { it.code == "CUSTOMER" })
    }

    @Test
    fun `cost center is explicit and normalized`() {
        assertEquals("SALES", ExpenseClassificationPolicy.normalizeCostCenter(" sales ").code)
        assertFailsWithMessage("مركز التكلفة / تصنيف المصروف مطلوب") {
            ExpenseClassificationPolicy.normalizeCostCenter("  ")
        }
        assertFailsWithMessage("مركز التكلفة غير صالح") {
            ExpenseClassificationPolicy.normalizeCostCenter("unknown")
        }
    }

    @Test
    fun `customer supplier and product references require matching dimensions`() {
        assertFailsWithMessage("اختر العميل المرتبط بالمصروف") {
            validate(referenceType = "CUSTOMER")
        }
        assertFailsWithMessage("اختر المورد المرتبط بالمصروف") {
            validate(referenceType = "SUPPLIER")
        }
        assertFailsWithMessage("اختر المنتج/الصنف المرتبط بالمصروف") {
            validate(referenceType = "PRODUCT")
        }

        validate(referenceType = "CUSTOMER", customerId = 10L)
        validate(referenceType = "SUPPLIER", supplierId = 20L)
        validate(referenceType = "PRODUCT", itemId = 30L)
    }

    @Test
    fun `document references require a concrete document id`() {
        listOf("SALES_INVOICE", "PURCHASE_INVOICE", "PRODUCTION_ORDER").forEach { type ->
            assertFailsWithMessage("اختر المستند المرتبط بالمصروف") { validate(referenceType = type) }
            validate(referenceType = type, referenceId = 7L)
        }
    }

    @Test
    fun `branch and facility require organization dimension`() {
        assertFailsWithMessage("الفرع / المنشأة مطلوب لهذا التصنيف") {
            validate(referenceType = "BRANCH", referenceNo = "BR-01")
        }
        validate(referenceType = "BRANCH", organizationUnit = "تعز")
        validate(referenceType = "FACILITY", organizationUnit = "معمل الإنتاج")
    }

    @Test
    fun `free reference classifications require traceable reference input`() {
        listOf("SALES_ORDER", "PURCHASE_ORDER", "DISTRIBUTION", "OTHER").forEach { type ->
            assertFailsWithMessage("أدخل رقم أو وصف مرجع المصروف") { validate(referenceType = type) }
            validate(referenceType = type, referenceNo = "REF-1")
        }
    }

    private fun validate(
        referenceType: String,
        referenceId: Long? = null,
        referenceNo: String = "",
        organizationUnit: String = "",
        customerId: Long? = null,
        supplierId: Long? = null,
        itemId: Long? = null
    ) = ExpenseClassificationPolicy.validateMandatoryDimensions(
        ExpenseMandatoryDimensions(
            costCenterCode = "ADMIN",
            organizationUnit = organizationUnit,
            referenceType = referenceType,
            referenceId = referenceId,
            referenceNo = referenceNo,
            customerId = customerId,
            supplierId = supplierId,
            itemId = itemId
        )
    )

    private fun assertFailsWithMessage(expected: String, block: () -> Unit) {
        val error = runCatching(block).exceptionOrNull()
        requireNotNull(error)
        assertEquals(expected, error.message)
    }
}
