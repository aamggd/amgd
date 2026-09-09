package com.fush.erp.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Strict date policy for expense posting.
 *
 * Expense dates are entered/stored as ISO yyyy-MM-dd text. Invalid or malformed
 * values must never be replaced silently with the current date because that would
 * alter the accounting period and financial reporting date of the voucher.
 */
object ExpenseDatePolicy {
    private val exactIsoDate = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun parseStartOrNull(text: String, zoneId: ZoneId = BusinessTimeZone.zoneId): Long? {
        val normalized = text.trim()
        if (!exactIsoDate.matches(normalized)) return null
        return try {
            LocalDate.parse(normalized, formatter)
                .atStartOfDay(zoneId)
                .toInstant()
                .toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }

    fun isValid(text: String): Boolean = parseStartOrNull(text) != null
}
