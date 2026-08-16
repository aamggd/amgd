package com.fush.erp.domain

object ReportMath {
    fun percent(numerator: Double, denominator: Double): Double =
        if (!numerator.isFinite() || !denominator.isFinite() || denominator <= 0.0) 0.0
        else numerator / denominator * 100.0

    fun margin(profit: Double, revenue: Double): Double = percent(profit, revenue)

    fun net(gross: Double, returns: Double): Double = gross - returns

    fun customerOutstandingBase(
        creditInvoices: Double,
        customerCreditReturns: Double,
        allocatedReceipts: Double,
        partyReceipts: Double,
        partyPayments: Double
    ): Double = (creditInvoices - customerCreditReturns - allocatedReceipts - partyReceipts + partyPayments)
        .coerceAtLeast(0.0)

    fun overdueAfterUnallocatedReceipts(
        overdueAfterAllocatedReceipts: Double,
        partyReceipts: Double
    ): Double = (overdueAfterAllocatedReceipts - partyReceipts).coerceAtLeast(0.0)

    fun unitCost(totalCost: Double, acceptedQty: Double): Double =
        if (acceptedQty <= 0.0 || !totalCost.isFinite()) 0.0 else totalCost / acceptedQty

    fun productVolumeMl(code: String, name: String): Int? {
        val c = code.trim().lowercase()
        val n = name.trim().lowercase()
        return when {
            c == "fg-fush-60" || Regex("""(^|\D)60\s*(ml|مل)?(\D|$)""").containsMatchIn(n) -> 60
            Regex("""(^|\D)200\s*(ml|مل)?(\D|$)""").containsMatchIn(n) -> 200
            else -> null
        }
    }
}
