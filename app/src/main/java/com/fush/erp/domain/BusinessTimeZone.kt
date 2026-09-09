package com.fush.erp.domain

import java.time.ZoneId
import java.util.Calendar
import java.util.TimeZone

/** Stable ERP business-time policy. Device time-zone changes must not alter accounting dates. */
object BusinessTimeZone {
    const val ID = "Asia/Aden"
    val zoneId: ZoneId = ZoneId.of(ID)
    val timeZone: TimeZone get() = TimeZone.getTimeZone(ID)

    fun installAsProcessDefault() {
        TimeZone.setDefault(timeZone)
    }

    fun calendarAt(epochMillis: Long): Calendar = Calendar.getInstance(timeZone).apply {
        timeInMillis = epochMillis
    }
}
