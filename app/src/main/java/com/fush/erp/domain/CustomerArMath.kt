package com.fush.erp.domain

import kotlin.math.max

/** Customer receivable settlement math using the invoice historical rate. */
object CustomerArMath {
    data class ReceiptSplit(
        val allocatedBase: Double,
        val cashBase: Double,
        val fxDifferenceBase: Double
    )

    data class SettlementSplit(
        val cashOriginal: Double,
        val discountOriginal: Double,
        val cashReceivableBase: Double,
        val discountBase: Double,
        val settledReceivableBase: Double,
        val treasuryCashBase: Double,
        val fxDifferenceBase: Double,
    )

    fun outstandingBase(invoiceBase: Double, customerCreditReturnsBase: Double, receivedBase: Double): Double =
        max(0.0, invoiceBase - customerCreditReturnsBase - receivedBase)

    /**
     * Splits a collection into actual cash and a non-cash settlement discount.
     * FX is calculated on cash only. The discount clears A/R at the invoice historical rate.
     */
    fun settlementSplit(
        cashOriginal: Double,
        discountOriginal: Double,
        invoiceExchangeRate: Double,
        receiptExchangeRate: Double,
        sameCurrency: Boolean = true,
    ): SettlementSplit {
        require(cashOriginal.isFinite() && cashOriginal >= 0.0) { "cashOriginal" }
        require(discountOriginal.isFinite() && discountOriginal >= 0.0) { "discountOriginal" }
        require(cashOriginal + discountOriginal > 0.0) { "settlementOriginal" }
        require(invoiceExchangeRate > 0.0 && invoiceExchangeRate.isFinite()) { "invoiceExchangeRate" }
        require(receiptExchangeRate > 0.0 && receiptExchangeRate.isFinite()) { "receiptExchangeRate" }

        // Same-currency collection settles the receivable at the invoice historical rate
        // and recognizes the difference against the current receipt-date rate as FX gain/loss.
        // Cross-currency collection has no common original currency with the invoice, so the
        // cash leg settles A/R by the base value of the currency actually received at the
        // receipt-date rate. Discounts remain invoice-currency settlements and are blocked
        // by SalesService for cross-currency receipts to keep receipt document semantics clear.
        val cashReceivable = cashOriginal * if (sameCurrency) invoiceExchangeRate else receiptExchangeRate
        val discount = discountOriginal * invoiceExchangeRate
        val settled = cashReceivable + discount
        val treasuryCash = cashOriginal * receiptExchangeRate
        return SettlementSplit(
            cashOriginal = cashOriginal,
            discountOriginal = discountOriginal,
            cashReceivableBase = cashReceivable,
            discountBase = discount,
            settledReceivableBase = settled,
            treasuryCashBase = treasuryCash,
            fxDifferenceBase = treasuryCash - cashReceivable,
        )
    }

    /** Backward-compatible cash-only helper used by existing tests/callers. */
    fun receiptSplit(amountOriginal: Double, invoiceExchangeRate: Double, receiptExchangeRate: Double): ReceiptSplit {
        require(amountOriginal > 0.0 && amountOriginal.isFinite()) { "amountOriginal" }
        val split = settlementSplit(amountOriginal, 0.0, invoiceExchangeRate, receiptExchangeRate)
        return ReceiptSplit(split.cashReceivableBase, split.treasuryCashBase, split.fxDifferenceBase)
    }
}
