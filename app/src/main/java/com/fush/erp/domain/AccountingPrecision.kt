package com.fush.erp.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Canonical fixed-decimal policy for accounting journal amounts and FX rates.
 *
 * Journal amounts are persisted as 4-decimal scaled integers and exchange rates
 * as 8-decimal scaled integers. HALF_EVEN is used consistently at the boundary
 * before persistence or balance validation.
 */
object AccountingPrecision {
    const val AMOUNT_DECIMALS = 4
    const val RATE_DECIMALS = 8
    const val AMOUNT_SCALE: Long = 10_000L
    const val RATE_SCALE: Long = 100_000_000L

    private val amountScaleDecimal = BigDecimal.valueOf(AMOUNT_SCALE)
    private val rateScaleDecimal = BigDecimal.valueOf(RATE_SCALE)

    fun amountToScaled(value: Double): Long = toScaled(value, AMOUNT_DECIMALS, amountScaleDecimal, "المبلغ")

    fun rateToScaled(value: Double): Long = toScaled(value, RATE_DECIMALS, rateScaleDecimal, "سعر الصرف")

    fun amountToDouble(value: Long): Double = BigDecimal.valueOf(value)
        .divide(amountScaleDecimal)
        .toDouble()

    fun rateToDouble(value: Long): Double = BigDecimal.valueOf(value)
        .divide(rateScaleDecimal)
        .toDouble()

    fun normalizeAmount(value: Double): Double = amountToDouble(amountToScaled(value))

    fun normalizeRate(value: Double): Double = rateToDouble(rateToScaled(value))

    fun sumAmountsToScaled(values: Iterable<Double>): Long = values.fold(0L) { total, value ->
        Math.addExact(total, amountToScaled(value))
    }

    private fun toScaled(value: Double, decimals: Int, scale: BigDecimal, label: String): Long {
        require(value.isFinite()) { "$label يجب أن يكون رقمًا صالحًا" }
        return BigDecimal.valueOf(value)
            .setScale(decimals, RoundingMode.HALF_EVEN)
            .multiply(scale)
            .longValueExact()
    }
}
