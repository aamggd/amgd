package com.fush.erp.domain

import kotlin.math.max

object SupplierApMath {
    data class PaymentSplit(
        val allocatedBase: Double,
        val cashBase: Double,
        val fxDifferenceBase: Double
    )

    fun outstandingBase(invoiceBase: Double, supplierCreditReturnsBase: Double, paidBase: Double): Double =
        max(0.0, invoiceBase - supplierCreditReturnsBase - paidBase)

    fun paymentSplit(amountOriginal: Double, invoiceExchangeRate: Double, paymentExchangeRate: Double): PaymentSplit {
        require(amountOriginal > 0.0 && amountOriginal.isFinite()) { "amountOriginal" }
        require(invoiceExchangeRate > 0.0 && invoiceExchangeRate.isFinite()) { "invoiceExchangeRate" }
        require(paymentExchangeRate > 0.0 && paymentExchangeRate.isFinite()) { "paymentExchangeRate" }
        val allocated = amountOriginal * invoiceExchangeRate
        val cash = amountOriginal * paymentExchangeRate
        return PaymentSplit(allocated, cash, cash - allocated)
    }

    fun agingBucket(dueDate: Long?, asOf: Long): String {
        if (dueDate == null || dueDate >= asOf) return "CURRENT"
        val days = ((asOf - dueDate) / 86_400_000L).coerceAtLeast(0)
        return when {
            days <= 30 -> "1_30"
            days <= 60 -> "31_60"
            days <= 90 -> "61_90"
            else -> "OVER_90"
        }
    }
}
