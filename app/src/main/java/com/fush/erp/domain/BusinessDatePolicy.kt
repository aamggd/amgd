package com.fush.erp.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Central policy for date-only ERP documents.
 *
 * A user-entered yyyy-MM-dd value represents a business day, not an invisible
 * intra-day timestamp. Historical guards therefore compare day boundaries and
 * closing balances consistently in the selected/system business zone.
 */
object BusinessDatePolicy {
    fun businessDay(value: Long, zoneId: ZoneId = BusinessTimeZone.zoneId): LocalDate {
        require(value > 0L) { "تاريخ يوم الأعمال غير صالح" }
        return Instant.ofEpochMilli(value).atZone(zoneId).toLocalDate()
    }

    fun startOfBusinessDay(value: Long, zoneId: ZoneId = BusinessTimeZone.zoneId): Long =
        businessDay(value, zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli()

    fun endOfBusinessDay(value: Long, zoneId: ZoneId = BusinessTimeZone.zoneId): Long =
        businessDay(value, zoneId)
            .plusDays(1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli() - 1L

    fun parseIsoDateStart(value: String, zoneId: ZoneId = BusinessTimeZone.zoneId): Long =
        LocalDate.parse(value.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
            .atStartOfDay(zoneId).toInstant().toEpochMilli()

    fun parseIsoDateEnd(value: String, zoneId: ZoneId = BusinessTimeZone.zoneId): Long =
        LocalDate.parse(value.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
            .plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1L

    fun formatIsoDate(value: Long, zoneId: ZoneId = BusinessTimeZone.zoneId): String =
        businessDay(value, zoneId).format(DateTimeFormatter.ISO_LOCAL_DATE)

    fun sameBusinessDay(a: Long, b: Long, zoneId: ZoneId = BusinessTimeZone.zoneId): Boolean =
        businessDay(a, zoneId) == businessDay(b, zoneId)

    fun onOrAfter(eventDate: Long, sourceDate: Long, zoneId: ZoneId = BusinessTimeZone.zoneId): Boolean =
        !businessDay(eventDate, zoneId).isBefore(businessDay(sourceDate, zoneId))
}
