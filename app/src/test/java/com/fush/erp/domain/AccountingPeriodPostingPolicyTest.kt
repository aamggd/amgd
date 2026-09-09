package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AccountingPeriodPostingPolicyTest {
    @Test
    fun missingPeriodRejectsPosting() {
        assertThrows(IllegalArgumentException::class.java) {
            AccountingPeriodPostingPolicy.requirePeriod<String>(null, 1_788_220_800_000L)
        }
    }

    @Test
    fun existingOpenPeriodIsReturnedUnchanged() {
        assertEquals("OPEN", AccountingPeriodPostingPolicy.requirePeriod("OPEN", 1_788_220_800_000L))
    }

    @Test
    fun existingClosedPeriodIsReturnedForCentralStatusGateToReject() {
        assertEquals("CLOSED", AccountingPeriodPostingPolicy.requirePeriod("CLOSED", 1_788_220_800_000L))
    }
}
