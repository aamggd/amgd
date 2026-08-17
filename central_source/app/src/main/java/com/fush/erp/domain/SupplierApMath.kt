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

    fun cashRefundableBase(
        paymentType: String,
        invoiceBase: Double,
        netPaidBase: Double,
        priorCashRefundBase: Double
    ): Double {
        require(paymentType in setOf("CASH", "CREDIT")) { "paymentType" }
        require(invoiceBase.isFinite() && invoiceBase >= 0.0) { "invoiceBase" }
        require(netPaidBase.isFinite() && netPaidBase >= 0.0) { "netPaidBase" }
        require(priorCashRefundBase.isFinite() && priorCashRefundBase >= 0.0) { "priorCashRefundBase" }
        val cashBackedBase = if (paymentType == "CASH") invoiceBase else netPaidBase.coerceAtMost(invoiceBase)
        return max(0.0, cashBackedBase - priorCashRefundBase)
    }

    fun canReverseSupplierPayment(
        netPaidBase: Double,
        reversedAllocationBase: Double,
        priorCashRefundBase: Double,
        tolerance: Double = 1e-8
    ): Boolean {
        require(netPaidBase.isFinite() && netPaidBase >= 0.0) { "netPaidBase" }
        require(reversedAllocationBase.isFinite() && reversedAllocationBase >= 0.0) { "reversedAllocationBase" }
        require(priorCashRefundBase.isFinite() && priorCashRefundBase >= 0.0) { "priorCashRefundBase" }
        require(tolerance.isFinite() && tolerance >= 0.0) { "tolerance" }
        return netPaidBase - reversedAllocationBase + tolerance >= priorCashRefundBase
    }

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
