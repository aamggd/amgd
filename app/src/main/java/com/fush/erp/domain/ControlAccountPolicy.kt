package com.fush.erp.domain

/**
 * Protects subsidiary-ledger control accounts from direct user posting.
 * Inventory, trade receivables/payables, employee and sales-representative control balances
 * must be changed by their linked operational workflows.
 */
object ControlAccountPolicy {
    const val INVENTORY_CONTROL_ACCOUNT = "1200"

    val protectedAccountCodes: Set<String> = setOf(INVENTORY_CONTROL_ACCOUNT, "1300", "1310", "2100", "2200", "2300", "2410")
    val tradeControlAccountCodes: Set<String> = setOf("1300", "2100")

    fun requireManualPostingAllowed(accountCode: String) {
        require(accountCode !in protectedAccountCodes) {
            "لا يسمح بالترحيل اليدوي المباشر إلى حساب الرقابة $accountCode؛ استخدم شاشة الطرف/العملية المرتبطة للحفاظ على تطابق الأستاذ مع الرصيد التفصيلي"
        }
    }

    fun requireGenericVoucherAllowed(accountCode: String) {
        require(accountCode != INVENTORY_CONTROL_ACCOUNT) {
            "لا يسمح باستخدام حساب المخزون 1200 في سند عام؛ يجب أن تتغير رقابة المخزون من حركة مخزون نظامية حتى يبقى الأستاذ مطابقاً للرصيد التفصيلي"
        }
        require(accountCode !in tradeControlAccountCodes) {
            if (accountCode == "1300")
                "تحصيلات/مدفوعات العملاء يجب تسجيلها من مسار تحصيلات وفواتير العميل حتى يتم تخصيصها على الفواتير"
            else
                "دفعات/مقبوضات الموردين يجب تسجيلها من مسار دفعات وفواتير المورد حتى يتم تخصيصها على الفواتير"
        }
    }
}
