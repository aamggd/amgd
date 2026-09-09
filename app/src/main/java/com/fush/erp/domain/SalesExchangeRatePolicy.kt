package com.fush.erp.domain

/**
 * v208 sales FX policy.
 *
 * Accounting exchange rates are canonical at 8 decimal places. UI formatting must never
 * turn an approved accounting rate into a different rate merely by rounding it for display.
 */
object SalesExchangeRatePolicy {
    fun canonical(rate: Double): Double = AccountingPrecision.normalizeRate(rate)

    fun matchesApproved(requestedRate: Double, approvedRate: Double): Boolean =
        AccountingPrecision.rateToScaled(requestedRate) == AccountingPrecision.rateToScaled(approvedRate)

    fun requiresOverride(requestedRate: Double, approvedRate: Double): Boolean =
        !matchesApproved(requestedRate, approvedRate)
}
