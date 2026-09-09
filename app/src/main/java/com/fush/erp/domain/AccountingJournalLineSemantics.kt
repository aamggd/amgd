package com.fush.erp.domain

enum class DimensionBalanceMode { NON_BALANCING, BALANCING }

data class AccountingSemanticLine(
    val functionalDebitScaled: Long,
    val functionalCreditScaled: Long,
    val transactionCurrencyCode: String,
    val transactionDebitScaled: Long,
    val transactionCreditScaled: Long,
    val transactionExchangeRateScaled: Long,
    val dimensionKey: String = ""
)

object AccountingJournalLineSemantics {
    fun validateFunctionalBalance(lines: List<AccountingSemanticLine>) {
        require(lines.size >= 2) { "ACCOUNTING_LINES_REQUIRED" }
        lines.forEach { line ->
            require(line.transactionCurrencyCode.isNotBlank()) { "TRANSACTION_CURRENCY_REQUIRED" }
            require(line.transactionExchangeRateScaled > 0L) { "TRANSACTION_RATE_MUST_BE_POSITIVE" }
            require((line.functionalDebitScaled > 0L) xor (line.functionalCreditScaled > 0L)) { "FUNCTIONAL_DEBIT_CREDIT_XOR_REQUIRED" }
            require((line.transactionDebitScaled > 0L) xor (line.transactionCreditScaled > 0L)) { "TRANSACTION_DEBIT_CREDIT_XOR_REQUIRED" }
        }
        require(lines.sumOf { it.functionalDebitScaled } == lines.sumOf { it.functionalCreditScaled }) {
            "FUNCTIONAL_JOURNAL_NOT_BALANCED"
        }
    }

    fun validateDimensionBalance(lines: List<AccountingSemanticLine>, mode: DimensionBalanceMode) {
        validateFunctionalBalance(lines)
        if (mode == DimensionBalanceMode.NON_BALANCING) return
        lines.groupBy { it.dimensionKey.trim() }.forEach { (key, bucket) ->
            require(key.isNotEmpty()) { "BALANCING_DIMENSION_KEY_REQUIRED" }
            require(bucket.sumOf { it.functionalDebitScaled } == bucket.sumOf { it.functionalCreditScaled }) {
                "BALANCING_DIMENSION_NOT_BALANCED:$key"
            }
        }
    }
}
