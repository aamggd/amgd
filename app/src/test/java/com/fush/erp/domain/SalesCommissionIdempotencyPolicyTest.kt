package com.fush.erp.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SalesCommissionIdempotencyPolicyTest {
    private val commissionSources = setOf(
        "SALES_COMMISSION",
        "COMMISSION_REVERSAL",
        "RECEIPT_COMMISSION_REVERSAL"
    )

    @Test
    fun commissionLifecycleSourcesAreReplaySafeAndNotFailClosed() {
        commissionSources.forEach { source ->
            assertTrue(source in AccountingPostingIdempotencyPolicy.replaySafeSourceTypes)
            assertFalse(source in AccountingPostingIdempotencyPolicy.blockedUnstableSourceTypes)
        }
    }
}
