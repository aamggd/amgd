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
        p(SecurityPermissions.SALES_POST, "SALES", "POST", "إنشاء وترحيل المبيعات", "Post sales", 22),
        p(SecurityPermissions.SALES_RETURN, "SALES", "RETURN", "تنفيذ مرتجعات المبيعات", "Post sales returns", 23),
        p(SecurityPermissions.COLLECTION_POST, "SALES", "COLLECT", "تسجيل التحصيلات", "Post collections", 24),
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
        p(SecurityPermissions.EMPLOYEES_VIEW, "HR", "VIEW", "عرض الموظفين", "View employees", 90),
        p(SecurityPermissions.EMPLOYEES_MANAGE, "HR", "MANAGE", "إدارة الموظفين والتصاريح", "Manage employees", 91),
        p(SecurityPermissions.SALES_REPS_VIEW, "HR", "SALES_REPS_VIEW", "عرض مناديب المبيعات", "View sales representatives", 92),
        p(SecurityPermissions.SALES_REPS_MANAGE, "HR", "SALES_REPS_MANAGE", "إدارة مناديب المبيعات", "Manage sales representatives", 93),
        p(SecurityPermissions.MAINTENANCE_VIEW, "MAINTENANCE", "VIEW", "عرض الصيانة والسلامة", "View maintenance", 100),
        p(SecurityPermissions.MAINTENANCE_MANAGE, "MAINTENANCE", "MANAGE", "إدارة الصيانة والسلامة", "Manage maintenance", 101),
        p(SecurityPermissions.GOVERNANCE_VIEW, "GOVERNANCE", "VIEW", "عرض الحوكمة والتدقيق", "View governance", 110),
        p(SecurityPermissions.GOVERNANCE_MANAGE, "GOVERNANCE", "MANAGE", "إدارة الوثائق وطلبات التغيير", "Manage governance documents and changes", 111),
        p(SecurityPermissions.APPROVAL_DECIDE, "GOVERNANCE", "APPROVE", "اعتماد الطلبات", "Decide approvals", 112),
        p(SecurityPermissions.RISK_VIEW, "GOVERNANCE", "RISK_VIEW", "عرض المخاطر والرقابة", "View risks and controls", 112),
        p(SecurityPermissions.RISK_MANAGE, "GOVERNANCE", "RISK_MANAGE", "إدارة المخاطر والرقابة", "Manage risks and controls", 113),
        p(SecurityPermissions.REPORTS_VIEW, "REPORTS", "VIEW", "عرض التقارير", "View reports", 120),
        p(SecurityPermissions.REPORTS_EXPORT, "REPORTS", "EXPORT", "تصدير وطباعة التقارير", "Export reports", 121),
        p(SecurityPermissions.BACKUP_CREATE, "SYSTEM", "BACKUP", "إنشاء ومشاركة النسخ الاحتياطية", "Create backups", 130),
        p(SecurityPermissions.BACKUP_RESTORE, "SYSTEM", "RESTORE", "استعادة النسخ الاحتياطية", "Restore backups", 131),
        p(SecurityPermissions.USERS_VIEW, "SECURITY", "USERS_VIEW", "عرض المستخدمين والأدوار", "View users and roles", 140),
        p(SecurityPermissions.USERS_MANAGE, "SECURITY", "USERS_MANAGE", "إدارة المستخدمين", "Manage users", 141),
        p(SecurityPermissions.ROLES_MANAGE, "SECURITY", "ROLES_MANAGE", "إدارة الأدوار والصلاحيات", "Manage roles and permissions", 142),
        p(SecurityPermissions.AUDIT_VIEW, "SECURITY", "AUDIT", "عرض سجل تدقيق الأمان", "View security audit", 143)
    )

    val roles: List<RoleEntity> = listOf(
        RoleEntity("ADMIN", "مدير النظام", "System Administrator", "صلاحيات كاملة وإدارة المستخدمين والأدوار.", isSystem = true),
        RoleEntity("ACCOUNTANT", "محاسب", "Accountant", "الحسابات والخزينة والتقارير والذمم."),
        RoleEntity("CASHIER", "أمين صندوق", "Cashier", "الخزينة والتحصيلات والدفعات ضمن نطاق العمل."),
        RoleEntity("SALES", "مبيعات", "Sales", "المبيعات والعملاء والتحصيلات ومناديب المبيعات."),
        RoleEntity("PURCHASING", "مشتريات", "Purchasing", "المشتريات والموردون."),
        RoleEntity("INVENTORY", "مخزون", "Inventory", "المستودعات والجرد والتحويلات."),
        RoleEntity("PRODUCTION", "إنتاج", "Production", "الإنتاج والجودة والمخزون للعرض."),
        RoleEntity("HR", "موارد بشرية", "Human Resources", "الموظفون والتدريب والتصاريح."),
        RoleEntity("AUDITOR", "مراجع", "Auditor", "عرض التقارير والحوكمة والرقابة دون ترحيل."),
        RoleEntity("VIEWER", "مشاهد", "Viewer", "صلاحيات قراءة محدودة." )
    )

    private val commonView = setOf(SecurityPermissions.DASHBOARD_VIEW, SecurityPermissions.REPORTS_VIEW)

    val defaultRolePermissions: Map<String, Set<String>> = mapOf(
        "ADMIN" to permissions.map { it.code }.toSet(),
        "ACCOUNTANT" to commonView + setOf(
            SecurityPermissions.ACCOUNTING_VIEW, SecurityPermissions.ACCOUNTING_POST, SecurityPermissions.TREASURY_POST,
            SecurityPermissions.SALES_VIEW, SecurityPermissions.CUSTOMERS_VIEW, SecurityPermissions.COLLECTION_POST,
            SecurityPermissions.PURCHASES_VIEW, SecurityPermissions.SUPPLIERS_VIEW, SecurityPermissions.SUPPLIER_PAYMENT_POST,
            SecurityPermissions.GEOGRAPHY_VIEW, SecurityPermissions.GEOGRAPHY_MANAGE, SecurityPermissions.REPORTS_EXPORT, SecurityPermissions.AUDIT_VIEW
        ),
        "CASHIER" to setOf(
            SecurityPermissions.DASHBOARD_VIEW, SecurityPermissions.ACCOUNTING_VIEW, SecurityPermissions.TREASURY_POST,
            SecurityPermissions.SALES_VIEW, SecurityPermissions.CUSTOMERS_VIEW, SecurityPermissions.COLLECTION_POST,
            SecurityPermissions.PURCHASES_VIEW, SecurityPermissions.SUPPLIERS_VIEW, SecurityPermissions.SUPPLIER_PAYMENT_POST
        ),
        "SALES" to commonView + setOf(
            SecurityPermissions.SALES_VIEW, SecurityPermissions.CUSTOMERS_VIEW, SecurityPermissions.SALES_POST,
            SecurityPermissions.SALES_RETURN, SecurityPermissions.COLLECTION_POST, SecurityPermissions.SALES_REPS_VIEW
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
            SecurityPermissions.REPORTS_VIEW, SecurityPermissions.AUDIT_VIEW
        ),
        "VIEWER" to setOf(SecurityPermissions.DASHBOARD_VIEW, SecurityPermissions.REPORTS_VIEW)
    )
}
