package com.fush.erp.domain

/** Central guard for date-only operational documents. */
object FutureDocumentDatePolicy {
    fun requireNotFuture(value: Long, label: String, trustedNow: Long = TrustedTimeService.requireTrustedNow()) {
        require(value > 0L) { "$label غير صالح" }
        val documentDay = BusinessDatePolicy.businessDay(value)
        val trustedDay = BusinessDatePolicy.businessDay(trustedNow)
        require(!documentDay.isAfter(trustedDay)) { "$label لا يمكن أن يكون في المستقبل" }
    }
}
