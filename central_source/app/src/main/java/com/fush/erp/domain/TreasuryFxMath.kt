package com.fush.erp.domain

import kotlin.math.abs

data class FxRevaluationComputation(
    val originalBalance: Double,
    val carryingBalanceBeforeBase: Double,
    val rateToBase: Double,
    val targetBalanceBase: Double,
    val differenceBase: Double,
    val needsJournal: Boolean
)

data class ForeignCashCountComputation(
    val expectedBalanceOriginal: Double,
    val actualBalanceOriginal: Double,
    val differenceOriginal: Double,
    val rateToBase: Double,
    val varianceBase: Double,
    val status: String
)

data class ForeignBankReconciliationComputation(
    val currencyCode: String,
    val statementOpeningBalanceOriginal: Double,
    val statementClosingBalanceOriginal: Double,
    val statementMovementOriginal: Double,
    val bookClosingBalanceOriginal: Double,
    val outstandingBookNetOriginal: Double,
    val adjustedStatementClosingOriginal: Double,
    val differenceOriginal: Double,
    val arithmeticDifferenceOriginal: Double,
    val isBalanced: Boolean
)

object TreasuryFxMath {
    const val ORIGINAL_TOLERANCE = 0.000001

    fun revaluation(
        originalBalance: Double,
        carryingBalanceBeforeBase: Double,
        rateToBase: Double
    ): FxRevaluationComputation {
        require(originalBalance.isFinite()) { "رصيد العملة الأصلية غير صالح" }
        require(carryingBalanceBeforeBase.isFinite()) { "الرصيد الدفتري الأساسي غير صالح" }
        require(rateToBase.isFinite() && rateToBase > 0.0) { "سعر الصرف غير صالح" }
        val target = originalBalance * rateToBase
        val difference = target - carryingBalanceBeforeBase
        return FxRevaluationComputation(
            originalBalance = originalBalance,
            carryingBalanceBeforeBase = carryingBalanceBeforeBase,
            rateToBase = rateToBase,
            targetBalanceBase = target,
            differenceBase = difference,
            needsJournal = abs(difference) > TreasuryReconciliationMath.TOLERANCE
        )
    }

    fun cashCountOriginal(
        expectedBalanceOriginal: Double,
        actualBalanceOriginal: Double,
        rateToBase: Double
    ): ForeignCashCountComputation {
        require(expectedBalanceOriginal.isFinite() && actualBalanceOriginal.isFinite()) { "أرصدة الجرد بالعملة الأصلية غير صالحة" }
        require(actualBalanceOriginal >= 0.0) { "الرصيد الفعلي لا يمكن أن يكون سالباً" }
        require(rateToBase.isFinite() && rateToBase > 0.0) { "سعر الصرف غير صالح" }
        val difference = actualBalanceOriginal - expectedBalanceOriginal
        return ForeignCashCountComputation(
            expectedBalanceOriginal = expectedBalanceOriginal,
            actualBalanceOriginal = actualBalanceOriginal,
            differenceOriginal = difference,
            rateToBase = rateToBase,
            varianceBase = difference * rateToBase,
            status = if (abs(difference) <= ORIGINAL_TOLERANCE) "BALANCED" else "VARIANCE"
        )
    }

    fun bankReconciliationOriginal(
        currencyCode: String,
        openingBalanceOriginal: Double,
        closingBalanceOriginal: Double,
        statementLineAmountsOriginal: List<Double>,
        bookClosingBalanceOriginal: Double,
        outstandingBookNetOriginal: Double
    ): ForeignBankReconciliationComputation {
        require(
            listOf(openingBalanceOriginal, closingBalanceOriginal, bookClosingBalanceOriginal, outstandingBookNetOriginal).all { it.isFinite() } &&
                statementLineAmountsOriginal.all { it.isFinite() }
        ) { "قيم المطابقة البنكية بالعملة الأصلية غير صالحة" }
        val movement = statementLineAmountsOriginal.sum()
        val arithmeticDifference = openingBalanceOriginal + movement - closingBalanceOriginal
        val adjustedClosing = closingBalanceOriginal + outstandingBookNetOriginal
        val difference = bookClosingBalanceOriginal - adjustedClosing
        return ForeignBankReconciliationComputation(
            currencyCode = currencyCode,
            statementOpeningBalanceOriginal = openingBalanceOriginal,
            statementClosingBalanceOriginal = closingBalanceOriginal,
            statementMovementOriginal = movement,
            bookClosingBalanceOriginal = bookClosingBalanceOriginal,
            outstandingBookNetOriginal = outstandingBookNetOriginal,
            adjustedStatementClosingOriginal = adjustedClosing,
            differenceOriginal = difference,
            arithmeticDifferenceOriginal = arithmeticDifference,
            isBalanced = abs(arithmeticDifference) <= ORIGINAL_TOLERANCE && abs(difference) <= ORIGINAL_TOLERANCE
        )
    }
}
