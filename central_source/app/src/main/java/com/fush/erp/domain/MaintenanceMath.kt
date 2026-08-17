package com.fush.erp.domain

object MaintenanceMath {
    private const val DAY_MS = 86_400_000L

    fun nextDue(completedAt: Long, intervalDays: Int?): Long? {
        if (intervalDays == null) return null
        require(intervalDays > 0) { "فترة الصيانة يجب أن تكون أكبر من صفر" }
        return completedAt + intervalDays.toLong() * DAY_MS
    }

    fun isOverdue(dueAt: Long?, now: Long): Boolean = dueAt != null && dueAt < now

    fun preventiveCompliancePct(dueCount: Int, completedOnTimeCount: Int): Double {
        require(dueCount >= 0 && completedOnTimeCount >= 0) { "قيم المؤشر غير صالحة" }
        if (dueCount == 0) return 100.0
        return (completedOnTimeCount.coerceAtMost(dueCount).toDouble() / dueCount.toDouble()) * 100.0
    }

    fun workOrdersClosedOnTimePct(closedCount: Int, closedOnTimeCount: Int): Double {
        require(closedCount >= 0 && closedOnTimeCount >= 0) { "قيم المؤشر غير صالحة" }
        if (closedCount == 0) return 100.0
        return (closedOnTimeCount.coerceAtMost(closedCount).toDouble() / closedCount.toDouble()) * 100.0
    }

    fun assetCanOperate(
        status: String,
        overduePlans: Int,
        inspectionOverdue: Boolean,
        calibrationOverdue: Boolean,
        latestPreStartPassed: Boolean
    ): Boolean {
        return status == "ACTIVE" &&
            overduePlans == 0 &&
            !inspectionOverdue &&
            !calibrationOverdue &&
            latestPreStartPassed
    }

    fun recurringBreakdown(previousCountInWindow: Int): Boolean = previousCountInWindow >= 1
}
