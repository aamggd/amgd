package com.fush.erp.domain

object SecurityPermissions {
    const val DASHBOARD_VIEW = "DASHBOARD_VIEW"
    const val SALES_VIEW = "SALES_VIEW"
    const val CUSTOMERS_VIEW = "CUSTOMERS_VIEW"
    const val SALES_POST = "SALES_POST"
    const val SALES_RETURN = "SALES_RETURN"
    const val COLLECTION_POST = "COLLECTION_POST"
    const val PURCHASES_VIEW = "PURCHASES_VIEW"
    const val SUPPLIERS_VIEW = "SUPPLIERS_VIEW"
    const val PURCHASE_POST = "PURCHASE_POST"
    const val PURCHASE_RETURN = "PURCHASE_RETURN"
    const val SUPPLIER_PAYMENT_POST = "SUPPLIER_PAYMENT_POST"
    const val INVENTORY_VIEW = "INVENTORY_VIEW"
    const val INVENTORY_TRANSFER = "INVENTORY_TRANSFER"
    const val INVENTORY_COUNT = "INVENTORY_COUNT"
    const val INVENTORY_ADJUST = "INVENTORY_ADJUST"
    const val MASTER_DATA_VIEW = "MASTER_DATA_VIEW"
    const val MASTER_DATA_MANAGE = "MASTER_DATA_MANAGE"
    const val PRODUCTION_VIEW = "PRODUCTION_VIEW"
    const val PRODUCTION_POST = "PRODUCTION_POST"
    const val QUALITY_DECIDE = "QUALITY_DECIDE"
    const val PLANNING_VIEW = "PLANNING_VIEW"
    const val PLANNING_MANAGE = "PLANNING_MANAGE"
    const val ACCOUNTING_VIEW = "ACCOUNTING_VIEW"
    const val ACCOUNTING_POST = "ACCOUNTING_POST"
    const val TREASURY_POST = "TREASURY_POST"
    const val GEOGRAPHY_VIEW = "GEOGRAPHY_VIEW"
    const val GEOGRAPHY_MANAGE = "GEOGRAPHY_MANAGE"
    const val EMPLOYEES_VIEW = "EMPLOYEES_VIEW"
    const val EMPLOYEES_MANAGE = "EMPLOYEES_MANAGE"
    const val SALES_REPS_VIEW = "SALES_REPS_VIEW"
    const val SALES_REPS_MANAGE = "SALES_REPS_MANAGE"
    const val MAINTENANCE_VIEW = "MAINTENANCE_VIEW"
    const val MAINTENANCE_MANAGE = "MAINTENANCE_MANAGE"
    const val GOVERNANCE_VIEW = "GOVERNANCE_VIEW"
    const val GOVERNANCE_MANAGE = "GOVERNANCE_MANAGE"
    const val APPROVAL_DECIDE = "APPROVAL_DECIDE"
    const val RISK_VIEW = "RISK_VIEW"
    const val RISK_MANAGE = "RISK_MANAGE"
    const val REPORTS_VIEW = "REPORTS_VIEW"
    const val REPORTS_EXPORT = "REPORTS_EXPORT"
    const val BACKUP_CREATE = "BACKUP_CREATE"
    const val BACKUP_RESTORE = "BACKUP_RESTORE"
    const val USERS_VIEW = "USERS_VIEW"
    const val USERS_MANAGE = "USERS_MANAGE"
    const val ROLES_MANAGE = "ROLES_MANAGE"
    const val AUDIT_VIEW = "AUDIT_VIEW"
}

object PasswordPolicy {
    const val MIN_LENGTH = 15
    const val HISTORY_COUNT = 10
    const val MAX_AGE_DAYS = 60L

    fun isExpired(passwordChangedAt: Long?, now: Long = System.currentTimeMillis()): Boolean {
        val changedAt = passwordChangedAt ?: return true
        return now - changedAt >= MAX_AGE_DAYS * 24L * 60L * 60_000L
    }

    fun validate(password: CharArray, username: String = ""): String? {
        val value = password.concatToString()
        if (value.length < MIN_LENGTH) return "يجب ألا تقل كلمة المرور عن $MIN_LENGTH حرفًا"
        if (!value.any { it.isUpperCase() }) return "يجب أن تحتوي كلمة المرور على حرف إنجليزي كبير"
        if (!value.any { it.isLowerCase() }) return "يجب أن تحتوي كلمة المرور على حرف إنجليزي صغير"
        if (!value.any { it.isDigit() }) return "يجب أن تحتوي كلمة المرور على رقم"
        if (!value.any { !it.isLetterOrDigit() }) return "يجب أن تحتوي كلمة المرور على رمز خاص"
        if (username.isNotBlank() && value.contains(username, ignoreCase = true)) return "يجب ألا تحتوي كلمة المرور على اسم المستخدم"
        return null
    }
}

object ReauthenticationPolicy {
    const val WINDOW_MINUTES = 5L

    fun windowMs(): Long = WINDOW_MINUTES * 60_000L

    fun isFresh(verifiedAt: Long?, now: Long = System.currentTimeMillis()): Boolean {
        if (verifiedAt == null || verifiedAt > now) return false
        return now - verifiedAt <= windowMs()
    }
}

data class SessionTimeoutSettings(
    val automaticLogoutEnabled: Boolean = SessionPolicy.DEFAULT_AUTOMATIC_LOGOUT_ENABLED,
    val idleTimeoutMinutes: Long = SessionPolicy.DEFAULT_IDLE_MINUTES,
    val maxSessionMinutes: Long = SessionPolicy.DEFAULT_ABSOLUTE_MINUTES
)

object SessionPolicy {
    const val DEFAULT_AUTOMATIC_LOGOUT_ENABLED = false
    const val DEFAULT_IDLE_MINUTES = 5L
    const val DEFAULT_ABSOLUTE_MINUTES = 480L
    const val ADMIN_IDLE_MINUTES = 3L
    const val ADMIN_ABSOLUTE_MINUTES = 240L
    const val MIN_TIMEOUT_MINUTES = 1L
    const val MAX_TIMEOUT_MINUTES = 43_200L // 30 days; effective policy caps are stricter when enabled.

    fun normalize(settings: SessionTimeoutSettings): SessionTimeoutSettings = settings.copy(
        automaticLogoutEnabled = settings.automaticLogoutEnabled,
        idleTimeoutMinutes = settings.idleTimeoutMinutes.coerceIn(MIN_TIMEOUT_MINUTES, MAX_TIMEOUT_MINUTES),
        maxSessionMinutes = settings.maxSessionMinutes.coerceIn(MIN_TIMEOUT_MINUTES, MAX_TIMEOUT_MINUTES)
    )

    fun effective(settings: SessionTimeoutSettings, role: String): SessionTimeoutSettings {
        val safe = normalize(settings)
        if (!safe.automaticLogoutEnabled) return safe

        val idleCap = if (role == "ADMIN") ADMIN_IDLE_MINUTES else DEFAULT_IDLE_MINUTES
        val absoluteCap = if (role == "ADMIN") ADMIN_ABSOLUTE_MINUTES else DEFAULT_ABSOLUTE_MINUTES
        return safe.copy(
            idleTimeoutMinutes = minOf(safe.idleTimeoutMinutes, idleCap),
            maxSessionMinutes = minOf(safe.maxSessionMinutes, absoluteCap)
        )
    }

    fun shouldExpire(
        settings: SessionTimeoutSettings,
        role: String,
        sessionStartedAt: Long,
        lastActivityAt: Long,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        val safe = effective(settings, role)
        if (!safe.automaticLogoutEnabled) return false

        val idleExpired = now - lastActivityAt >= safe.idleTimeoutMinutes * 60_000L
        val absoluteExpired = now - sessionStartedAt >= safe.maxSessionMinutes * 60_000L
        return idleExpired || absoluteExpired
    }
}

data class LockoutDecision(val failedAttempts: Int, val lockoutCount: Int, val lockedUntil: Long?)

object LoginLockoutPolicy {
    const val MAX_ATTEMPTS = 5
    const val FIRST_LOCK_MINUTES = 15L
    const val REPEATED_LOCK_MINUTES = 60L

    fun onFailure(currentAttempts: Int, currentLockoutCount: Int, now: Long): LockoutDecision {
        val attempts = currentAttempts + 1
        if (attempts < MAX_ATTEMPTS) return LockoutDecision(attempts, currentLockoutCount, null)
        val nextLockoutCount = currentLockoutCount + 1
        val duration = if (nextLockoutCount <= 1) FIRST_LOCK_MINUTES else REPEATED_LOCK_MINUTES
        return LockoutDecision(0, nextLockoutCount, now + duration * 60_000L)
    }
}
