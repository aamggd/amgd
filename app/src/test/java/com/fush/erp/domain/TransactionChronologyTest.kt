package com.fush.erp.domain

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionChronologyTest {
    private val zone = BusinessTimeZone.zoneId
    private fun at(day: Int, hour: Int = 0): Long =
        LocalDate.of(2026, 8, day).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun eventCannotPrecedeSourceBusinessDay() {
        val error = runCatching {
            TransactionChronology.requireOnOrAfter(
                eventDate = at(21, 23),
                sourceDate = at(22, 1),
                eventLabel = "تاريخ التحصيل",
                sourceLabel = "تاريخ الفاتورة",
            )
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun sameBusinessDayAndLaterDatesAreAllowedRegardlessOfClockTime() {
        TransactionChronology.requireOnOrAfter(at(22, 0), at(22, 23), "حدث", "مستند")
        TransactionChronology.requireOnOrAfter(at(23, 0), at(22, 23), "حدث", "مستند")
    }
}
