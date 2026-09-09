package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class BusinessTimeZoneTest {
    @Test
    fun businessTimeZoneIsStableAndIndependentFromHostDefault() {
        assertEquals("Asia/Aden", BusinessTimeZone.ID)
        assertEquals("Asia/Aden", BusinessTimeZone.zoneId.id)
        val lateUtc = Instant.parse("2026-08-22T22:30:00Z").toEpochMilli()
        assertEquals(java.time.LocalDate.of(2026, 8, 23), BusinessDatePolicy.businessDay(lateUtc))
    }
}
