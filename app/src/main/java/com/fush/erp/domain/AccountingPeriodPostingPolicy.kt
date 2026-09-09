package com.fush.erp.domain

/**
 * Fail-closed contract for the shared accounting-period lookup used by posting.
 * A missing fiscal period is never treated as permission to continue posting.
 */
object AccountingPeriodPostingPolicy {
    fun <T> requirePeriod(period: T?, postingDate: Long): T = requireNotNull(period) {
        "لا توجد فترة محاسبية تغطي تاريخ الترحيل $postingDate؛ لا يمكن الترحيل"
    }
}
