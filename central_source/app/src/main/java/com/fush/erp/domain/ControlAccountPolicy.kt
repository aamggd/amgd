package com.fush.erp.domain

/**
 * Protects subsidiary-ledger control accounts from direct manual posting.
 * Trade receivables/payables must be changed only by customer/supplier workflows;
 * employee and sales-representative payables must be changed by linked vouchers/workflows.
 */
object ControlAccountPolicy {
    val protectedAccountCodes: Set<String> = setOf("1300", "2100", "2200", "2300")
    val tradeControlAccountCodes: Set<String> = setOf("1300", "2100")

    fun requireManualPostingAllowed(accountCode: String) {
        require(accountCode !in protectedAccountCodes) {
            "لا يسمح بالترحيل اليدوي المباشر إلى حساب الرقابة $accountCode؛ استخدم شاشة الطرف/العملية المرتبطة للحفاظ على تطابق الأستاذ مع الرصيد التفصيلي"
        }
    }

    fun requireGenericVoucherAllowed(accountCode: String) {
        require(accountCode !in tradeControlAccountCodes) {
            if (accountCode == "1300")
                "تحصيلات/مدفوعات العملاء يجب تسجيلها من مسار تحصيلات وفواتير العميل حتى يتم تخصيصها على الفواتير"
            else
                "دفعات/مقبوضات الموردين يجب تسجيلها من مسار دفعات وفواتير المورد حتى يتم تخصيصها على الفواتير"
        }
    }
}
