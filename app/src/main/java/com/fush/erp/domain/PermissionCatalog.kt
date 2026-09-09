package com.fush.erp.domain

import com.fush.erp.data.entity.PermissionEntity
import com.fush.erp.data.entity.RoleEntity

object PermissionCatalog {
    private fun p(code: String, module: String, action: String, ar: String, en: String, order: Int) =
        PermissionEntity(code, module, action, ar, en, sortOrder = order)

    val permissions: List<PermissionEntity> = listOf(
        p(SecurityPermissions.DASHBOARD_VIEW, "DASHBOARD", "VIEW", "عرض لوحة الإدارة", "View dashboard", 10),
        p(SecurityPermissions.SALES_VIEW, "SALES", "VIEW", "عرض المبيعات", "View sales", 20),
        p(SecurityPermissions.CUSTOMERS_VIEW, "SALES", "CUSTOMERS_VIEW", "عرض العملاء", "View customers", 21),
        p(SecurityPermissions.CUSTOMERS_CREATE, "SALES", "CUSTOMERS_CREATE", "إنشاء العملاء", "Create customers", 22),
        p(SecurityPermissions.CUSTOMERS_EDIT, "SALES", "CUSTOMERS_EDIT", "تعديل بيانات العملاء", "Edit customers", 221),
        p(SecurityPermissions.SALES_POST, "SALES", "POST", "إنشاء وترحيل المبيعات", "Post sales", 23),
        p(SecurityPermissions.SALES_RETURN, "SALES", "RETURN", "تنفيذ مرتجعات المبيعات", "Post sales returns", 24),
        p(SecurityPermissions.SALES_FREE_QTY_APPROVE, "SALES", "FREE_QTY_APPROVE", "اعتماد تجاوز حد الكمية المجانية", "Approve sales free-quantity override", 25),
        p(SecurityPermissions.COLLECTION_POST, "SALES", "COLLECT", "تسجيل التحصيلات", "Post collections", 26),
        p(SecurityPermissions.COLLECTION_DISCOUNT_POST, "SALES", "COLLECTION_DISCOUNT", "منح خصم أثناء التحصيل", "Post collection settlement discounts", 27),
        p(SecurityPermissions.PURCHASES_VIEW, "PURCHASES", "VIEW", "عرض المشتريات", "View purchases", 30),
        p(SecurityPermissions.SUPPLIERS_VIEW, "PURCHASES", "SUPPLIERS_VIEW", "عرض الموردين", "View suppliers", 31),
        p(SecurityPermissions.PURCHASE_POST, "PURCHASES", "POST", "إنشاء وترحيل المشتريات", "Post purchases", 32),
        p(SecurityPermissions.PURCHASE_RETURN, "PURCHASES", "RETURN", "تنفيذ مرتجعات المشتريات", "Post purchase returns", 33),
        p(SecurityPermissions.SUPPLIER_PAYMENT_POST, "PURCHASES", "PAYMENT", "تسجيل دفعات الموردين", "Post supplier payments", 34),
        p(SecurityPermissions.INVENTORY_VIEW, "INVENTORY", "VIEW", "عرض المخزون والمستودعات", "View inventory", 40),
        p(SecurityPermissions.INVENTORY_TRANSFER, "INVENTORY", "TRANSFER", "تحويل المخزون", "Transfer inventory", 41),
        p(SecurityPermissions.INVENTORY_COUNT, "INVENTORY", "COUNT", "تنفيذ الجرد", "Perform inventory counts", 42),
        p(SecurityPermissions.INVENTORY_ADJUST, "INVENTORY", "ADJUST", "تسويات المخزون", "Adjust inventory", 43),
        p(SecurityPermissions.MASTER_DATA_VIEW, "MASTER_DATA", "VIEW", "عرض البيانات الأساسية", "View master data", 50),
        p(SecurityPermissions.MASTER_DATA_MANAGE, "MASTER_DATA", "MANAGE", "إدارة الأصناف والوحدات", "Manage master data", 51),
        p(SecurityPermissions.PRODUCTION_VIEW, "PRODUCTION", "VIEW", "عرض الإنتاج والجودة", "View production", 60),
        p(SecurityPermissions.PRODUCTION_POST, "PRODUCTION", "POST", "تنفيذ عمليات الإنتاج", "Post production", 61),
        p(SecurityPermissions.QUALITY_DECIDE, "PRODUCTION", "QUALITY", "اعتماد قرارات الجودة", "Approve quality decisions", 62),
        p(SecurityPermissions.PLANNING_VIEW, "PLANNING", "VIEW", "عرض التخطيط والموسمية", "View planning", 70),
        p(SecurityPermissions.PLANNING_MANAGE, "PLANNING", "MANAGE", "إدارة خطط الإنتاج والموازنة", "Manage planning", 71),
        p(SecurityPermissions.ACCOUNTING_VIEW, "ACCOUNTING", "VIEW", "عرض الحسابات والخزينة", "View accounting", 80),
        p(SecurityPermissions.ACCOUNTING_POST, "ACCOUNTING", "POST", "إنشاء وترحيل القيود", "Post accounting entries", 81),
        p(SecurityPermissions.TREASURY_POST, "ACCOUNTING", "TREASURY", "إدارة سندات الخزينة", "Post treasury vouchers", 82),
        p(SecurityPermissions.GEOGRAPHY_VIEW, "ACCOUNTING", "GEOGRAPHY", "عرض العملات والمحافظات", "View currencies and provinces", 83),
        p(SecurityPermissions.GEOGRAPHY_MANAGE, "ACCOUNTING", "GEOGRAPHY_MANAGE", "إدارة أسعار الصرف والتسعير الجغرافي", "Manage FX and geographic pricing", 84),
        p(SecurityPermissions.EXCHANGE_RATE_VIEW, "ACCOUNTING", "FX_VIEW", "عرض محرك أسعار الصرف", "View exchange-rate engine", 840),
        p(SecurityPermissions.EXCHANGE_RATE_REFRESH, "ACCOUNTING", "FX_REFRESH", "تحديث أسعار الصرف من الإنترنت", "Refresh exchange rates from internet", 841),
        p(SecurityPermissions.EXCHANGE_RATE_APPROVE, "ACCOUNTING", "FX_APPROVE", "اعتماد أسعار الصرف المحاسبية", "Approve accounting exchange rates", 842),
        p(SecurityPermissions.EXCHANGE_RATE_OVERRIDE, "ACCOUNTING", "FX_OVERRIDE", "اعتماد سعر صرف رغم التعارض", "Override exchange-rate conflict", 843),
        p(SecurityPermissions.EXCHANGE_RATE_SETTINGS, "ACCOUNTING", "FX_SETTINGS", "إدارة إعدادات محرك أسعار الصرف", "Manage exchange-rate engine settings", 844),
        p(SecurityPermissions.CASH_COUNT_POST, "ACCOUNTING", "CASH_COUNT", "تنفيذ جرد الصندوق", "Perform cash counts", 85),
        p(SecurityPermissions.BANK_RECONCILIATION_POST, "ACCOUNTING", "BANK_RECONCILIATION", "إنشاء واعتماد المطابقات البنكية", "Manage bank reconciliations", 86),
        p(SecurityPermissions.FIXED_ASSET_POST, "ACCOUNTING", "FIXED_ASSET", "إدارة وترحيل الأصول الثابتة", "Post fixed asset transactions", 87),
        p(SecurityPermissions.FX_REVALUATION_POST, "ACCOUNTING", "FX_REVALUATION", "ترحيل إعادة تقييم العملات", "Post FX revaluations", 88),
        p(SecurityPermissions.ACCOUNTING_PERIOD_MANAGE, "ACCOUNTING", "PERIOD_MANAGE", "إنشاء وإقفال وإعادة فتح الفترات", "Manage accounting periods", 89),
        p(SecurityPermissions.ACCOUNTING_YEAR_CLOSE, "ACCOUNTING", "YEAR_CLOSE", "إقفال وإعادة فتح السنة المالية", "Close and reopen fiscal years", 90),
        p(SecurityPermissions.EMPLOYEES_VIEW, "HR", "VIEW", "عرض الموظفين", "View employees", 100),
        p(SecurityPermissions.EMPLOYEES_MANAGE, "HR", "MANAGE", "إدارة الموظفين والتصاريح", "Manage employees", 101),
        p(SecurityPermissions.SALES_REPS_VIEW, "HR", "SALES_REPS_VIEW", "عرض مناديب المبيعات", "View sales representatives", 102),
        p(SecurityPermissions.SALES_REPS_MANAGE, "HR", "SALES_REPS_MANAGE", "إدارة مناديب المبيعات", "Manage sales representatives", 103),
        p(SecurityPermissions.MAINTENANCE_VIEW, "MAINTENANCE", "VIEW", "عرض الصيانة والسلامة", "View maintenance", 110),
        p(SecurityPermissions.MAINTENANCE_MANAGE, "MAINTENANCE", "MANAGE", "إدارة الصيانة والسلامة", "Manage maintenance", 111),
        p(SecurityPermissions.GOVERNANCE_VIEW, "GOVERNANCE", "VIEW", "عرض الحوكمة والتدقيق", "View governance", 120),
        p(SecurityPermissions.GOVERNANCE_MANAGE, "GOVERNANCE", "MANAGE", "إدارة الوثائق وطلبات التغيير", "Manage governance documents and changes", 121),
        p(SecurityPermissions.APPROVAL_DECIDE, "GOVERNANCE", "APPROVE", "اعتماد الطلبات", "Decide approvals", 122),
        p(SecurityPermissions.RISK_VIEW, "GOVERNANCE", "RISK_VIEW", "عرض المخاطر والرقابة", "View risks and controls", 123),
        p(SecurityPermissions.RISK_MANAGE, "GOVERNANCE", "RISK_MANAGE", "إدارة المخاطر والرقابة", "Manage risks and controls", 124),
        p(SecurityPermissions.REPORTS_VIEW, "REPORTS", "VIEW", "عرض التقارير", "View reports", 130),
        p(SecurityPermissions.REPORTS_EXPORT, "REPORTS", "EXPORT", "تصدير وطباعة التقارير", "Export reports", 131),
        p(SecurityPermissions.BACKUP_CREATE, "SYSTEM", "BACKUP", "إنشاء ومشاركة النسخ الاحتياطية", "Create backups", 140),
        p(SecurityPermissions.BACKUP_RESTORE, "SYSTEM", "RESTORE", "استعادة النسخ الاحتياطية", "Restore backups", 141),
        p(SecurityPermissions.USERS_VIEW, "SECURITY", "USERS_VIEW", "عرض المستخدمين والأدوار", "View users and roles", 150),
        p(SecurityPermissions.USERS_MANAGE, "SECURITY", "USERS_MANAGE", "إدارة المستخدمين", "Manage users", 151),
        p(SecurityPermissions.ROLES_MANAGE, "SECURITY", "ROLES_MANAGE", "إدارة الأدوار والصلاحيات", "Manage roles and permissions", 152),
        p(SecurityPermissions.AUDIT_VIEW, "SECURITY", "AUDIT", "عرض سجل تدقيق الأمان", "View security audit", 153),
        p(SecurityPermissions.SUPPORT_VIEW, "SUPPORT", "VIEW", "عرض مركز دعم FUSH", "View FUSH Support Center", 160),
        p(SecurityPermissions.SUPPORT_DIAGNOSE, "SUPPORT", "DIAGNOSE", "تشخيص بيانات الشركة أثناء جلسة دعم", "Diagnose during support session", 161),
        p(SecurityPermissions.SUPPORT_REPAIR, "SUPPORT", "REPAIR", "تنفيذ Repair Commands المعتمدة", "Execute approved repair commands", 162),
        p(SecurityPermissions.SUPPORT_RECALCULATE, "SUPPORT", "RECALCULATE", "إعادة الحساب والتحقق أثناء الدعم", "Recalculate and validate", 163),
        p(SecurityPermissions.SUPPORT_CORRECT_DATA, "SUPPORT", "CORRECT_DATA", "تصحيح بيانات استثنائي عبر Command مخصص فقط", "Exceptional typed data correction", 164),
        p(SecurityPermissions.SUPPORT_TEST_DATA_DELETE, "SUPPORT", "TEST_DATA_DELETE", "حذف بيانات اختبار محددة أثناء جلسة دعم مؤقتة فقط", "Delete explicitly identified test data during a temporary support session", 165)
    )

    val roles: List<RoleEntity> = listOf(
        RoleEntity("ADMIN", "مدير النظام", "System Administrator", "صلاحيات كاملة وإدارة المستخدمين والأدوار.", isSystem = true),
        RoleEntity("ACCOUNTANT", "محاسب", "Accountant", "الحسابات والتقارير مع جميع صلاحيات المشتريات والمبيعات والإنتاج والنسخ الاحتياطي، وعرض المخزون فقط."),
        RoleEntity("CASHIER", "أمين صندوق", "Cashier", "الخزينة والتحصيلات والدفعات ضمن نطاق العمل."),
        RoleEntity("SALES", "مبيعات", "Sales", "المبيعات والعملاء والتحصيلات ومناديب المبيعات."),
        RoleEntity("PURCHASING", "مشتريات", "Purchasing", "المشتريات والموردون."),
        RoleEntity("INVENTORY", "مخزون", "Inventory", "المستودعات والجرد والتحويلات."),
        RoleEntity("PRODUCTION", "إنتاج", "Production", "الإنتاج والجودة والمخزون للعرض."),
        RoleEntity("HR", "موارد بشرية", "Human Resources", "الموظفون والتدريب والتصاريح."),
        RoleEntity("AUDITOR", "مراجع", "Auditor", "عرض التقارير والحوكمة والرقابة دون ترحيل."),
        RoleEntity("VIEWER", "مشاهد", "Viewer", "صلاحيات قراءة محدودة." ),
        RoleEntity("FUSH_SUPPORT", "دعم FUSH", "FUSH Support", "حساب صيانة مقيد بجلسة Support وتذكرة فعالة فقط.", isSystem = true)
    )

    private val commonView = setOf(SecurityPermissions.DASHBOARD_VIEW, SecurityPermissions.REPORTS_VIEW)

    val defaultRolePermissions: Map<String, Set<String>> = mapOf(
        "ADMIN" to permissions.map { it.code }.toSet(),
        "ACCOUNTANT" to commonView + setOf(
            SecurityPermissions.ACCOUNTING_VIEW, SecurityPermissions.ACCOUNTING_POST, SecurityPermissions.TREASURY_POST,
            SecurityPermissions.BANK_RECONCILIATION_POST, SecurityPermissions.FIXED_ASSET_POST,
            SecurityPermissions.FX_REVALUATION_POST, SecurityPermissions.ACCOUNTING_PERIOD_MANAGE, SecurityPermissions.ACCOUNTING_YEAR_CLOSE,
            // Sales: all permissions.
            SecurityPermissions.SALES_VIEW, SecurityPermissions.CUSTOMERS_VIEW, SecurityPermissions.CUSTOMERS_CREATE, SecurityPermissions.CUSTOMERS_EDIT,
            SecurityPermissions.SALES_POST, SecurityPermissions.SALES_RETURN, SecurityPermissions.SALES_FREE_QTY_APPROVE,
            SecurityPermissions.COLLECTION_POST, SecurityPermissions.COLLECTION_DISCOUNT_POST,
            // Purchases: all permissions, including supplier payments.
            SecurityPermissions.PURCHASES_VIEW, SecurityPermissions.SUPPLIERS_VIEW, SecurityPermissions.PURCHASE_POST,
            SecurityPermissions.PURCHASE_RETURN, SecurityPermissions.SUPPLIER_PAYMENT_POST,
            // Inventory: view only.
            SecurityPermissions.INVENTORY_VIEW,
            // Production: all permissions.
            SecurityPermissions.PRODUCTION_VIEW, SecurityPermissions.PRODUCTION_POST, SecurityPermissions.QUALITY_DECIDE,
            // Backup: all permissions.
            SecurityPermissions.BACKUP_CREATE, SecurityPermissions.BACKUP_RESTORE,
            SecurityPermissions.GEOGRAPHY_VIEW, SecurityPermissions.EXCHANGE_RATE_VIEW, SecurityPermissions.EXCHANGE_RATE_REFRESH, SecurityPermissions.EXCHANGE_RATE_APPROVE, SecurityPermissions.REPORTS_EXPORT, SecurityPermissions.AUDIT_VIEW
        ),
        "CASHIER" to setOf(
            SecurityPermissions.DASHBOARD_VIEW, SecurityPermissions.ACCOUNTING_VIEW, SecurityPermissions.TREASURY_POST, SecurityPermissions.CASH_COUNT_POST,
            SecurityPermissions.SALES_VIEW, SecurityPermissions.CUSTOMERS_VIEW, SecurityPermissions.COLLECTION_POST, SecurityPermissions.EXCHANGE_RATE_VIEW,
            SecurityPermissions.PURCHASES_VIEW, SecurityPermissions.SUPPLIERS_VIEW, SecurityPermissions.SUPPLIER_PAYMENT_POST
        ),
        "SALES" to commonView + setOf(
            SecurityPermissions.SALES_VIEW, SecurityPermissions.CUSTOMERS_VIEW, SecurityPermissions.CUSTOMERS_CREATE, SecurityPermissions.CUSTOMERS_EDIT,
            SecurityPermissions.SALES_POST, SecurityPermissions.SALES_RETURN, SecurityPermissions.COLLECTION_POST, SecurityPermissions.COLLECTION_DISCOUNT_POST, SecurityPermissions.SALES_REPS_VIEW
        ),
        "PURCHASING" to commonView + setOf(
            SecurityPermissions.PURCHASES_VIEW, SecurityPermissions.SUPPLIERS_VIEW, SecurityPermissions.PURCHASE_POST,
            SecurityPermissions.PURCHASE_RETURN, SecurityPermissions.INVENTORY_VIEW, SecurityPermissions.MASTER_DATA_VIEW
        ),
        "INVENTORY" to commonView + setOf(
            SecurityPermissions.INVENTORY_VIEW, SecurityPermissions.INVENTORY_TRANSFER, SecurityPermissions.INVENTORY_COUNT,
            SecurityPermissions.MASTER_DATA_VIEW
        ),
        "PRODUCTION" to commonView + setOf(
            SecurityPermissions.PRODUCTION_VIEW, SecurityPermissions.PRODUCTION_POST, SecurityPermissions.QUALITY_DECIDE,
            SecurityPermissions.INVENTORY_VIEW, SecurityPermissions.MASTER_DATA_VIEW, SecurityPermissions.PLANNING_VIEW
        ),
        "HR" to commonView + setOf(
            SecurityPermissions.EMPLOYEES_VIEW, SecurityPermissions.EMPLOYEES_MANAGE,
            SecurityPermissions.SALES_REPS_VIEW, SecurityPermissions.SALES_REPS_MANAGE
        ),
        "AUDITOR" to setOf(
            SecurityPermissions.DASHBOARD_VIEW, SecurityPermissions.ACCOUNTING_VIEW, SecurityPermissions.SALES_VIEW,
            SecurityPermissions.CUSTOMERS_VIEW, SecurityPermissions.PURCHASES_VIEW, SecurityPermissions.SUPPLIERS_VIEW,
            SecurityPermissions.INVENTORY_VIEW, SecurityPermissions.PRODUCTION_VIEW, SecurityPermissions.EMPLOYEES_VIEW,
            SecurityPermissions.MAINTENANCE_VIEW, SecurityPermissions.GOVERNANCE_VIEW, SecurityPermissions.RISK_VIEW,
            SecurityPermissions.REPORTS_VIEW, SecurityPermissions.AUDIT_VIEW, SecurityPermissions.EXCHANGE_RATE_VIEW
        ),
        "VIEWER" to setOf(SecurityPermissions.DASHBOARD_VIEW, SecurityPermissions.REPORTS_VIEW),
        "FUSH_SUPPORT" to setOf(
            SecurityPermissions.SUPPORT_VIEW, SecurityPermissions.SUPPORT_DIAGNOSE,
            SecurityPermissions.SUPPORT_REPAIR, SecurityPermissions.SUPPORT_RECALCULATE, SecurityPermissions.SUPPORT_CORRECT_DATA,
            SecurityPermissions.SUPPORT_TEST_DATA_DELETE
        )
    )
}
