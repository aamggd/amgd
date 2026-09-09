package com.fush.erp.domain

import com.fush.erp.data.entity.AuditTrailRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AuditTrailPresentation {
    val sections: List<Pair<String, String>> = listOf(
        "" to "الكل",
        "SECURITY" to "الأمان والدخول",
        "GOVERNANCE" to "الحوكمة",
        "MASTER_DATA" to "البيانات الأساسية",
        "SALES" to "المبيعات والعملاء",
        "PURCHASES" to "المشتريات والموردون",
        "ACCOUNTING" to "الحسابات والخزينة",
        "INVENTORY" to "المخزون",
        "PRODUCTION" to "الإنتاج والجودة",
        "HR" to "الموظفون والمناديب",
        "BACKUP" to "النسخ الاحتياطي والاستعادة",
        "SUPPORT" to "الدعم والصيانة",
        "OTHER" to "أخرى",
    )

    val operations: List<Pair<String, String>> = listOf(
        "" to "الكل",
        "CREATE" to "إضافة",
        "UPDATE" to "تعديل",
        "POST" to "ترحيل",
        "REVERSE" to "عكس عملية",
        "DELETE" to "حذف",
        "APPROVE" to "اعتماد",
        "REOPEN" to "إعادة فتح",
        "CLOSE" to "إقفال",
        "LOGIN" to "تسجيل دخول",
        "PERMISSION" to "صلاحيات",
        "SETTINGS" to "إعدادات",
    )

    val eventTypes: List<Pair<String, String>> = listOf(
        "" to "الكل",
        "CUSTOMER" to "عميل",
        "SUPPLIER" to "مورد",
        "ITEM" to "صنف",
        "USER" to "مستخدم",
        "PERMISSION" to "صلاحية",
        "SALES_INVOICE" to "فاتورة مبيعات",
        "CUSTOMER_RECEIPT" to "تحصيل عميل",
        "SALES_RETURN" to "مرتجع مبيعات",
        "PURCHASE_INVOICE" to "فاتورة مشتريات",
        "SUPPLIER_PAYMENT" to "دفعة مورد",
        "PURCHASE_RETURN" to "مرتجع مشتريات",
        "JOURNAL_ENTRY" to "قيد يومية",
        "ACCOUNTING_PERIOD" to "فترة محاسبية",
        "TREASURY_ACCOUNT" to "خزينة/بنك",
        "PRODUCTION_ORDER" to "أمر إنتاج",
        "WAREHOUSE_TRANSFER" to "تحويل مخزني",
        "SALES_REP" to "مندوب مبيعات",
        "EMPLOYEE" to "موظف",
        "DATABASE_BACKUP" to "نسخة احتياطية",
        "SYSTEM" to "النظام",
        "SUPPORT_TICKET" to "بلاغ دعم",
        "SUPPORT_SESSION" to "جلسة دعم",
    )

    val quickFilters: List<Pair<String, String>> = listOf(
        "" to "الكل",
        "CREATE" to "إضافات",
        "UPDATE" to "تعديلات",
        "REVERSE" to "عكس",
        "LOGIN" to "تسجيل دخول",
        "PERMISSION" to "صلاحيات",
        "SETTINGS" to "إعدادات",
    )

    fun actor(row: AuditTrailRow): String {
        if (row.userId <= 0L) return "النظام"
        if (row.actorDisplayName.isNullOrBlank() && row.actorUsername.isNullOrBlank()) return "مستخدم تاريخي غير متاح"
        val display = row.actorDisplayName?.trim().orEmpty()
        val username = row.actorUsername?.trim().orEmpty()
        return when {
            display.isNotBlank() && username.isNotBlank() -> "$display — $username"
            display.isNotBlank() -> display
            username.isNotBlank() -> username
            else -> "النظام"
        }
    }

    fun actionLabel(code: String): String = when (code.uppercase(Locale.ROOT)) {
        "LOGIN_SUCCESS" -> "تسجيل دخول ناجح"
        "LOGIN_FAILED" -> "محاولة دخول فاشلة"
        "LOGOUT" -> "تسجيل خروج"
        "PASSWORD_ONLY" -> "تسجيل الدخول بكلمة المرور"
        "CREATE", "CREATE_BACKUP" -> "إضافة"
        "UPDATE" -> "تعديل"
        "POST" -> "ترحيل"
        "REVERSE" -> "عكس عملية"
        "DELETE" -> "حذف"
        "APPROVE", "APPROVED" -> "اعتماد"
        "REJECT", "REJECTED" -> "رفض"
        "CLOSE" -> "إقفال"
        "REOPEN" -> "إعادة فتح"
        "ACCESS_DENIED" -> "رفض وصول"
        "REAUTH_FAILED" -> "فشل إعادة التحقق من الهوية"
        "SESSION_POLICY_CHANGED" -> "تغيير سياسة الجلسة"
        "TERMINATE" -> "إنهاء جلسة"
        "CUSTOMER_COLLECTION_DISCOUNT" -> "خصم تحصيل عميل"
        "FREE_QTY_OVERRIDE_APPROVED" -> "اعتماد تجاوز حد الكمية المجانية"
        "RESTORE_BACKUP" -> "استعادة نسخة احتياطية"
        "SECURITY_PERMISSION_HARDENING", "SECURITY_PERMISSION_UPGRADE" -> "تحديث إعدادات الصلاحيات"
        "REVALUE" -> "إعادة تقييم"
        "OVERRIDE" -> "اعتماد تجاوز"
        "CANCEL" -> "إلغاء"
        "CORRECT" -> "تصحيح"
        "SUBMIT" -> "إرسال للاعتماد"
        "DECIDE" -> "اتخاذ قرار"
        "START" -> "بدء عملية"
        "SUCCESS" -> "نجاح العملية"
        "FAILED" -> "فشل العملية"
        "IMPORT" -> "استيراد"
        "RECONCILE" -> "تسوية بنكية"
        "RELEASE" -> "إطلاق"
        "DEACTIVATE" -> "إيقاف"
        "REACTIVATE" -> "إعادة تفعيل"
        "DISPOSE" -> "استبعاد"
        "REVIEW" -> "مراجعة"
        "STATUS_CHANGE" -> "تغيير الحالة"
        "MATCH" -> "مطابقة"
        "UNMATCH" -> "إلغاء مطابقة"
        "COUNT" -> "جرد"
        "RESOLVE" -> "تسوية"
        "ATTACH" -> "إرفاق ملف"
        "MIGRATE_ATTACHMENTS" -> "ترحيل المرفقات"
        "SUPPORT_TICKET_CREATED" -> "فتح بلاغ دعم"
        "SUPPORT_TICKET_CLOSED" -> "إغلاق بلاغ دعم"
        "SUPPORT_SESSION_ACTIVATED" -> "تفعيل جلسة صيانة"
        "SUPPORT_SESSION_CLOSED" -> "إنهاء جلسة صيانة"
        "SUPPORT_SESSION_REVOKED" -> "إلغاء وصول الدعم"
        "VENDOR_SUPPORT_PROVISION_CHALLENGE" -> "إنشاء طلب تحقق لهوية دعم FUSH"
        "VENDOR_SUPPORT_PROVISION_SUCCESS" -> "تهيئة هوية دعم FUSH"
        "VENDOR_SUPPORT_REBIND_SUCCESS" -> "إعادة ربط هوية دعم FUSH"
        "VENDOR_SUPPORT_ROTATE_SUCCESS" -> "تدوير بيانات اعتماد دعم FUSH"
        "VENDOR_SUPPORT_LEGACY_CLAIM_SUCCESS" -> "اعتماد حساب دعم FUSH تاريخي"
        "VENDOR_SUPPORT_PROVISION_FAILED" -> "فشل تهيئة/ربط هوية دعم FUSH"
        "VENDOR_SUPPORT_KEY_ROTATED" -> "تدوير مفتاح توقيع دعم FUSH"
        "VENDOR_SUPPORT_KEY_ROTATION_FAILED" -> "فشل تدوير مفتاح توقيع دعم FUSH"
        else -> when {
            code.uppercase(Locale.ROOT).startsWith("TEST_") -> "اختبار رقابي"
            code.uppercase(Locale.ROOT).contains("SETTING") || code.uppercase(Locale.ROOT).contains("POLICY") -> "تغيير إعدادات"
            else -> "حدث إداري"
        }
    }

    fun entityLabel(type: String): String = when (type.uppercase(Locale.ROOT)) {
        "CUSTOMER" -> "عميل"
        "SUPPLIER" -> "مورد"
        "ITEM" -> "صنف"
        "USER" -> "مستخدم"
        "ROLE" -> "دور"
        "PERMISSION" -> "صلاحية"
        "SALES_INVOICE", "SALE", "SALES_DOCUMENT" -> "فاتورة مبيعات"
        "CUSTOMER_RECEIPT" -> "تحصيل عميل"
        "SALES_RETURN" -> "مرتجع مبيعات"
        "PURCHASE_INVOICE", "PURCHASE_DOCUMENT" -> "فاتورة مشتريات"
        "SUPPLIER_PAYMENT" -> "دفعة مورد"
        "PURCHASE_RETURN" -> "مرتجع مشتريات"
        "JOURNAL_ENTRY" -> "قيد يومية"
        "ACCOUNTING_PERIOD" -> "فترة محاسبية"
        "FISCAL_YEAR", "ACCOUNTING_FISCAL_YEAR" -> "سنة مالية"
        "TREASURY_ACCOUNT" -> "خزينة/بنك"
        "TREASURY_CASH_COUNT" -> "جرد خزينة"
        "TREASURY_FX_REVALUATION" -> "إعادة تقييم عملة"
        "PRODUCTION_ORDER" -> "أمر إنتاج"
        "WAREHOUSE_TRANSFER" -> "تحويل مخزني"
        "SALES_REP" -> "مندوب مبيعات"
        "EMPLOYEE" -> "موظف"
        "DATABASE_BACKUP", "BACKUP" -> "نسخة احتياطية"
        "RESTORE" -> "استعادة نسخة"
        "SYSTEM", "SESSION", "SECURITY_POLICY" -> "النظام"
        "SALES_PRICE" -> "سعر بيع"
        "SALES_DISCOUNT" -> "خصم مبيعات"
        "PURCHASE_PRICE" -> "سعر شراء"
        "TREASURY_VOUCHER", "PARTY_VOUCHER" -> "سند خزينة"
        "BANK_STATEMENT", "BANK_RECONCILIATION" -> "تسوية بنكية"
        "FX_REVALUATION" -> "إعادة تقييم عملة"
        "EXPENSE", "EXPENSE_DIMENSION" -> "مصروف"
        "INVENTORY_ADJUSTMENT" -> "تسوية مخزون"
        "INVENTORY_COUNT", "CASH_COUNT" -> "جرد"
        "INVENTORY_LOT" -> "تشغيلة مخزون"
        "INVENTORY_COST" -> "تكلفة مخزون"
        "PRODUCTION_ISSUE" -> "صرف إنتاج"
        "PRODUCTION_RETURN" -> "مرتجع مواد إنتاج"
        "PRODUCTION_RECEIPT", "PRODUCTION_BATCH" -> "استلام إنتاج"
        "PAYROLL" -> "مسير رواتب"
        "FIXED_ASSET" -> "أصل ثابت"
        "DEPRECIATION" -> "إهلاك"
        "PLAN" -> "خطة"
        "RISK" -> "مخاطر"
        "CONTROL" -> "رقابة"
        "CONTROL_EXCEPTION" -> "استثناء رقابي"
        "DOCUMENT" -> "وثيقة حوكمة"
        "CHANGE_REQUEST" -> "طلب تغيير"
        "APPROVAL" -> "موافقة"
        "SUPPORT_TICKET" -> "بلاغ دعم"
        "SUPPORT_SESSION" -> "جلسة دعم"
        else -> "سجل إداري"
    }

    fun sectionLabel(code: String): String = sections.firstOrNull { it.first == code }?.second ?: "أخرى"

    fun title(row: AuditTrailRow): String {
        val action = actionLabel(row.action)
        val entity = entityLabel(row.entityType)
        return if (entity == "النظام") action else "$action — $entity"
    }

    fun dateText(epochMillis: Long): String = formatter("dd/MM/yyyy").format(Date(epochMillis))
    fun timeText(epochMillis: Long): String = formatter("hh:mm:ss a").format(Date(epochMillis))
    fun dateTimeText(epochMillis: Long): String = "${dateText(epochMillis)} — ${timeText(epochMillis)}"

    fun parseDayStart(text: String): Long? = parseDay(text)?.apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }?.timeInMillis

    fun parseDayEnd(text: String): Long? = parseDay(text)?.apply {
        set(java.util.Calendar.HOUR_OF_DAY, 23); set(java.util.Calendar.MINUTE, 59); set(java.util.Calendar.SECOND, 59); set(java.util.Calendar.MILLISECOND, 999)
    }?.timeInMillis

    private fun parseDay(text: String): java.util.Calendar? {
        if (text.isBlank()) return null
        val date = runCatching { formatter("yyyy-MM-dd").apply { isLenient = false }.parse(text.trim()) }.getOrNull() ?: return null
        return BusinessTimeZone.calendarAt(date.time)
    }

    private fun formatter(pattern: String) = SimpleDateFormat(pattern, Locale("ar")).apply {
        timeZone = BusinessTimeZone.timeZone
    }
}
