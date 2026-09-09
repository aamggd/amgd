package com.fush.erp.domain

/** User-configurable warning window; the value is persisted by the UI, not hard-coded into inventory math. */
object NearExpiryPolicy {
    const val DEFAULT_DAYS = 60
    const val MIN_DAYS = 1
    const val MAX_DAYS = 3650

    fun normalize(days: Int): Int = days.coerceIn(MIN_DAYS, MAX_DAYS)

    fun status(daysToExpiry: Long, warningDays: Int): String = when {
        daysToExpiry < 0L -> "منتهي"
        daysToExpiry <= normalize(warningDays).toLong() -> "قريب الانتهاء"
        else -> "ساري الصلاحية"
    }
}
