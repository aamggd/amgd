package com.fush.erp.domain

object TransactionChronology {
    fun requireOnOrAfter(
        eventDate: Long,
        sourceDate: Long,
        eventLabel: String,
        sourceLabel: String,
    ) {
        require(BusinessDatePolicy.onOrAfter(eventDate, sourceDate)) {
            "$eventLabel لا يمكن أن يسبق $sourceLabel"
        }
    }

    fun eligibleForAllocation(documentDate: Long, invoiceDate: Long): Boolean =
        BusinessDatePolicy.onOrAfter(documentDate, invoiceDate)
}
