package com.fush.erp.domain

import com.fush.erp.data.FushDatabase
import com.fush.erp.data.entity.AuditEventEntity

/**
 * Server-side style authorization guard for local Room operations.
 * UI visibility is convenience only; every protected mutation must call this guard.
 */
suspend fun FushDatabase.requireUserPermission(userId: Long, permission: String) {
    val user = userDao().byId(userId) ?: throw SecurityException("المستخدم غير موجود")
    if (!user.isActive) throw SecurityException("الحساب غير نشط")

    if (user.role == "ADMIN") return
    if (securityDao().hasPermission(user.role, permission) > 0) return

    governanceDao().insertAudit(
        AuditEventEntity(
            userId = userId,
            action = "ACCESS_DENIED",
            entityType = "PERMISSION",
            entityId = permission,
            reason = "محاولة تنفيذ عملية دون صلاحية"
        )
    )
    throw SecurityException("ليس لديك صلاحية تنفيذ هذه العملية")
}
