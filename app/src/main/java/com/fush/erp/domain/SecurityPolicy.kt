package com.fush.erp.domain

object SecurityPermissions {
    const val DASHBOARD_VIEW = "DASHBOARD_VIEW"
    const val SALES_VIEW = "SALES_VIEW"
    const val CUSTOMERS_VIEW = "CUSTOMERS_VIEW"
    const val CUSTOMERS_CREATE = "CUSTOMERS_CREATE"
    const val CUSTOMERS_EDIT = "CUSTOMERS_EDIT"
    const val SALES_POST = "SALES_POST"
    const val SALES_RETURN = "SALES_RETURN"
    const val SALES_FREE_QTY_APPROVE = "SALES_FREE_QTY_APPROVE"
    const val COLLECTION_POST = "COLLECTION_POST"
    const val COLLECTION_DISCOUNT_POST = "COLLECTION_DISCOUNT_POST"
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
    const val CASH_COUNT_POST = "CASH_COUNT_POST"
    const val BANK_RECONCILIATION_POST = "BANK_RECONCILIATION_POST"
    const val FIXED_ASSET_POST = "FIXED_ASSET_POST"
    const val FX_REVALUATION_POST = "FX_REVALUATION_POST"
    const val ACCOUNTING_PERIOD_MANAGE = "ACCOUNTING_PERIOD_MANAGE"
    const val ACCOUNTING_YEAR_CLOSE = "ACCOUNTING_YEAR_CLOSE"
    const val GEOGRAPHY_VIEW = "GEOGRAPHY_VIEW"
    const val GEOGRAPHY_MANAGE = "GEOGRAPHY_MANAGE"
    const val EXCHANGE_RATE_VIEW = "EXCHANGE_RATE_VIEW"
    const val EXCHANGE_RATE_REFRESH = "EXCHANGE_RATE_REFRESH"
    const val EXCHANGE_RATE_APPROVE = "EXCHANGE_RATE_APPROVE"
    const val EXCHANGE_RATE_OVERRIDE = "EXCHANGE_RATE_OVERRIDE"
    const val EXCHANGE_RATE_SETTINGS = "EXCHANGE_RATE_SETTINGS"
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
    const val SUPPORT_VIEW = "SUPPORT_VIEW"
    const val SUPPORT_DIAGNOSE = "SUPPORT_DIAGNOSE"
    const val SUPPORT_REPAIR = "SUPPORT_REPAIR"
    const val SUPPORT_RECALCULATE = "SUPPORT_RECALCULATE"
    const val SUPPORT_CORRECT_DATA = "SUPPORT_CORRECT_DATA"
    const val SUPPORT_TEST_DATA_DELETE = "SUPPORT_TEST_DATA_DELETE"
}

object PasswordPolicy {
    const val MIN_LENGTH = 15
    const val HISTORY_COUNT = 10
    const val MAX_AGE_DAYS = 60L

    fun isExpired(passwordChangedAt: Long?, now: Long = com.fush.erp.domain.TrustedTimeService.now()): Boolean {
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

    fun isFresh(verifiedAt: Long?, now: Long = TrustedTimeService.elapsedRealtime()): Boolean {
        if (verifiedAt == null || verifiedAt > now) return false
        return now - verifiedAt <= windowMs()
    }
}

data class SessionTimeoutSettings(
    val automaticLogoutEnabled: Boolean = true,
    val idleTimeoutMinutes: Long = SessionPolicy.DEFAULT_IDLE_MINUTES,
    val maxSessionMinutes: Long = SessionPolicy.DEFAULT_ABSOLUTE_MINUTES
)

object SessionPolicy {
    const val DEFAULT_SESSION_MINUTES = 60L
    const val DEFAULT_IDLE_MINUTES = DEFAULT_SESSION_MINUTES
    const val DEFAULT_ABSOLUTE_MINUTES = DEFAULT_SESSION_MINUTES
    const val MIN_TIMEOUT_MINUTES = 1L
    const val MAX_TIMEOUT_MINUTES = 43_200L // 30 days; configurable by an authorized administrator.

    val QUICK_SESSION_MINUTES = listOf(15L, 30L, 60L, 120L, 240L, 480L)

    fun normalize(settings: SessionTimeoutSettings): SessionTimeoutSettings = settings.copy(
        automaticLogoutEnabled = true,
        idleTimeoutMinutes = settings.idleTimeoutMinutes.coerceIn(MIN_TIMEOUT_MINUTES, MAX_TIMEOUT_MINUTES),
        maxSessionMinutes = settings.maxSessionMinutes.coerceIn(MIN_TIMEOUT_MINUTES, MAX_TIMEOUT_MINUTES)
    )

    /**
     * Session duration is controlled by the saved administrator setting.
     * The old hard-coded 3/5 minute role caps intentionally no longer override it.
     */
    fun effective(settings: SessionTimeoutSettings, role: String): SessionTimeoutSettings = normalize(settings)

    fun shouldExpire(
        settings: SessionTimeoutSettings,
        role: String,
        sessionStartedAt: Long,
        lastActivityAt: Long,
        now: Long = TrustedTimeService.elapsedRealtime()
    ): Boolean {
        val safe = effective(settings, role)
        val idleExpired = now - lastActivityAt >= safe.idleTimeoutMinutes * 60_000L
        val absoluteExpired = now - sessionStartedAt >= safe.maxSessionMinutes * 60_000L
        return idleExpired || absoluteExpired
    }

    /**
     * FUSH_SUPPORT access duration is governed by the company-authorized Support Session.
     * A generic app idle/absolute timeout must never truncate an active 1h/6h/24h maintenance grant.
     * When no Support Session is active, only the normal idle timeout applies to the locked Support shell.
     */
    fun shouldExpireSupportShell(
        settings: SessionTimeoutSettings,
        hasActiveSupportSession: Boolean,
        lastActivityAt: Long,
        now: Long = TrustedTimeService.elapsedRealtime(),
    ): Boolean {
        if (hasActiveSupportSession) return false
        val safe = normalize(settings)
        return now - lastActivityAt >= safe.idleTimeoutMinutes * 60_000L
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
