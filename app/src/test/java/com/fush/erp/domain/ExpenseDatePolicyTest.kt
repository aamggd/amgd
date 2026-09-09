package com.fush.erp.domain

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class ExpenseDatePolicyTest {
    private val utc = ZoneId.of("UTC")

    @Test
    fun validIsoDateParses() {
        assertNotNull(ExpenseDatePolicy.parseStartOrNull("2026-08-20", utc))
    }

    @Test
    fun leapDayParsesOnlyInLeapYear() {
        assertNotNull(ExpenseDatePolicy.parseStartOrNull("2024-02-29", utc))
        assertNull(ExpenseDatePolicy.parseStartOrNull("2026-02-29", utc))
    }

    @Test
    fun impossibleDateIsRejectedInsteadOfFallingBackToToday() {
        assertNull(ExpenseDatePolicy.parseStartOrNull("2026-99-45", utc))
        assertNull(ExpenseDatePolicy.parseStartOrNull("2026-02-30", utc))
    }

    @Test
    fun malformedShapeIsRejected() {
        assertNull(ExpenseDatePolicy.parseStartOrNull("2026-8-2", utc))
        assertNull(ExpenseDatePolicy.parseStartOrNull("20/08/2026", utc))
        assertNull(ExpenseDatePolicy.parseStartOrNull("", utc))
    }

    @Test
    fun surroundingWhitespaceDoesNotChangeAValidDate() {
        assertNotNull(ExpenseDatePolicy.parseStartOrNull(" 2026-08-20 ", utc))
    }
}
