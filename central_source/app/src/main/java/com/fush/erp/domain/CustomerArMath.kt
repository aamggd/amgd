package com.fush.erp.domain

import kotlin.math.max

/** Customer receivable settlement math using the invoice historical rate. */
object CustomerArMath {
    data class ReceiptSplit(
        val allocatedBase: Double,
        val cashBase: Double,
        val fxDifferenceBase: Double
    )

    fun outstandingBase(invoiceBase: Double, customerCreditReturnsBase: Double, receivedBase: Double): Double =
        max(0.0, invoiceBase - customerCreditReturnsBase - receivedBase)

    fun receiptSplit(amountOriginal: Double, invoiceExchangeRate: Double, receiptExchangeRate: Double): ReceiptSplit {
        require(amountOriginal > 0.0 && amountOriginal.isFinite()) { "amountOriginal" }
        require(invoiceExchangeRate > 0.0 && invoiceExchangeRate.isFinite()) { "invoiceExchangeRate" }
        require(receiptExchangeRate > 0.0 && receiptExchangeRate.isFinite()) { "receiptExchangeRate" }
        val allocated = amountOriginal * invoiceExchangeRate
        val cash = amountOriginal * receiptExchangeRate
        return ReceiptSplit(allocated, cash, cash - allocated)
    }
}
