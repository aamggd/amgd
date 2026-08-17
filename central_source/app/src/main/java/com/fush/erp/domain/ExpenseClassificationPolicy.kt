package com.fush.erp.domain

import java.util.Locale

data class ExpenseDictionaryEntry(
    val code: String,
    val nameAr: String
)

data class ExpenseMandatoryDimensions(
    val costCenterCode: String,
    val organizationUnit: String = "",
    val referenceType: String = "NONE",
    val referenceId: Long? = null,
    val referenceNo: String = "",
    val referenceLabel: String = "",
    val customerId: Long? = null,
    val supplierId: Long? = null,
    val itemId: Long? = null
)

/**
 * Canonical Phase-P0 dictionary for expense classification and analytical dimensions.
 *
 * The expense GL posting account remains the authoritative expense type/category. This policy
 * centralizes the cross-cutting analytical codes that were previously duplicated in UI and
 * posting logic, and rejects an expense when a mandatory dimension for its reference type is
 * missing.
 */
object ExpenseClassificationPolicy {
    val costCenters: List<ExpenseDictionaryEntry> = listOf(
        ExpenseDictionaryEntry("SALES", "المبيعات"),
        ExpenseDictionaryEntry("PURCHASES", "المشتريات"),
        ExpenseDictionaryEntry("PRODUCTION", "الإنتاج"),
        ExpenseDictionaryEntry("ADMIN", "الإدارة"),
        ExpenseDictionaryEntry("WAREHOUSE", "المخزن"),
        ExpenseDictionaryEntry("DISTRIBUTION", "التوزيع"),
        ExpenseDictionaryEntry("MAINTENANCE", "الصيانة"),
        ExpenseDictionaryEntry("MARKETING", "التسويق"),
        ExpenseDictionaryEntry("OTHER", "أخرى")
    )

    val referenceTypes: List<ExpenseDictionaryEntry> = listOf(
        ExpenseDictionaryEntry("NONE", "بدون مرجع"),
        ExpenseDictionaryEntry("SALES_INVOICE", "فاتورة مبيعات"),
        ExpenseDictionaryEntry("SALES_ORDER", "أمر بيع"),
        ExpenseDictionaryEntry("PURCHASE_INVOICE", "فاتورة مشتريات"),
        ExpenseDictionaryEntry("PURCHASE_ORDER", "أمر شراء"),
        ExpenseDictionaryEntry("PRODUCTION_ORDER", "أمر إنتاج"),
        ExpenseDictionaryEntry("DISTRIBUTION", "توزيع / توصيل"),
        ExpenseDictionaryEntry("CUSTOMER", "عميل"),
        ExpenseDictionaryEntry("SUPPLIER", "مورد"),
        ExpenseDictionaryEntry("PRODUCT", "منتج / صنف"),
        ExpenseDictionaryEntry("BRANCH", "فرع"),
        ExpenseDictionaryEntry("FACILITY", "منشأة"),
        ExpenseDictionaryEntry("OTHER", "مرجع آخر")
    )

    fun normalizeCostCenter(code: String): ExpenseDictionaryEntry {
        val normalized = code.trim().uppercase(Locale.US)
        require(normalized.isNotBlank()) { "مركز التكلفة / تصنيف المصروف مطلوب" }
        return requireNotNull(costCenters.firstOrNull { it.code == normalized }) {
            "مركز التكلفة غير صالح"
        }
    }

    fun normalizeReferenceType(code: String): ExpenseDictionaryEntry {
        val normalized = code.trim().uppercase(Locale.US).ifBlank { "NONE" }
        return requireNotNull(referenceTypes.firstOrNull { it.code == normalized }) {
            "نوع مرجع المصروف غير صالح"
        }
    }

    fun validateMandatoryDimensions(input: ExpenseMandatoryDimensions) {
        normalizeCostCenter(input.costCenterCode)
        when (normalizeReferenceType(input.referenceType).code) {
            "SALES_INVOICE", "PURCHASE_INVOICE", "PRODUCTION_ORDER" ->
                require(input.referenceId != null && input.referenceId > 0L) { "اختر المستند المرتبط بالمصروف" }
            "CUSTOMER" -> require(input.customerId != null && input.customerId > 0L) { "اختر العميل المرتبط بالمصروف" }
            "SUPPLIER" -> require(input.supplierId != null && input.supplierId > 0L) { "اختر المورد المرتبط بالمصروف" }
            "PRODUCT" -> require(input.itemId != null && input.itemId > 0L) { "اختر المنتج/الصنف المرتبط بالمصروف" }
            "BRANCH", "FACILITY" -> require(input.organizationUnit.trim().isNotBlank()) { "الفرع / المنشأة مطلوب لهذا التصنيف" }
            "SALES_ORDER", "PURCHASE_ORDER", "DISTRIBUTION", "OTHER" ->
                require(
                    input.referenceNo.trim().isNotBlank() ||
                        input.referenceLabel.trim().isNotBlank() ||
                        input.organizationUnit.trim().isNotBlank()
                ) { "أدخل رقم أو وصف مرجع المصروف" }
        }
    }
}
