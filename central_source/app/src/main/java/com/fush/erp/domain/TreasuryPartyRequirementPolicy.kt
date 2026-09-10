package com.fush.erp.domain

/**
 * P1 treasury rule: when the selected posting account is a party control/payable account,
 * the matching party identity is mandatory and other party identities must not be supplied.
 *
 * Entity existence/active-state checks stay in [AccountingService]; this policy is deliberately
 * pure so the account-to-party contract can be regression-tested without Room.
 */
enum class TreasuryPartyRequirement {
    NONE,
    CUSTOMER,
    SUPPLIER,
    EMPLOYEE,
    SALES_REP
}

data class TreasuryPartySelection(
    val customerId: Long? = null,
    val supplierId: Long? = null,
    val employeeId: Long? = null,
    val salesRepId: Long? = null
)

object TreasuryPartyRequirementPolicy {
    fun requirementForAccount(accountCode: String): TreasuryPartyRequirement = when (accountCode.trim()) {
        "1300" -> TreasuryPartyRequirement.CUSTOMER
        "2100" -> TreasuryPartyRequirement.SUPPLIER
        "2200" -> TreasuryPartyRequirement.EMPLOYEE
        "2300" -> TreasuryPartyRequirement.SALES_REP
        else -> TreasuryPartyRequirement.NONE
    }

    fun requireValidSelection(
        accountCode: String,
        selection: TreasuryPartySelection
    ): TreasuryPartyRequirement {
        val requirement = requirementForAccount(accountCode)
        val customerOnly = selection.customerId != null &&
            selection.supplierId == null && selection.employeeId == null && selection.salesRepId == null
        val supplierOnly = selection.supplierId != null &&
            selection.customerId == null && selection.employeeId == null && selection.salesRepId == null
        val employeeOnly = selection.employeeId != null &&
            selection.customerId == null && selection.supplierId == null && selection.salesRepId == null
        val salesRepOnly = selection.salesRepId != null &&
            selection.customerId == null && selection.supplierId == null && selection.employeeId == null
        val noParty = selection.customerId == null && selection.supplierId == null &&
            selection.employeeId == null && selection.salesRepId == null

        when (requirement) {
            TreasuryPartyRequirement.CUSTOMER ->
                require(customerOnly) { "حساب العملاء يتطلب تحديد عميل واحد فقط" }
            TreasuryPartyRequirement.SUPPLIER ->
                require(supplierOnly) { "حساب الموردين يتطلب تحديد مورد واحد فقط" }
            TreasuryPartyRequirement.EMPLOYEE ->
                require(employeeOnly) { "حساب مستحقات الموظفين يتطلب تحديد موظف واحد فقط" }
            TreasuryPartyRequirement.SALES_REP ->
                require(salesRepOnly) { "حساب عمولات البيع يتطلب تحديد مندوب مبيعات واحد فقط" }
            TreasuryPartyRequirement.NONE ->
                require(noParty) { "لا يمكن ربط طرف بحساب عام غير مخصص للأطراف" }
        }
        return requirement
    }
}
