package com.fush.erp.domain

import kotlin.math.abs

data class CashCountComputation(
    val expectedBalanceBase: Double,
    val actualBalanceBase: Double,
    val differenceBase: Double,
    val status: String
)

data class BankReconciliationComputation(
    val statementOpeningBalanceBase: Double,
    val statementClosingBalanceBase: Double,
    val statementMovementBase: Double,
    val bookClosingBalanceBase: Double,
    val outstandingBookNetBase: Double,
    val adjustedStatementClosingBase: Double,
    val differenceBase: Double,
    val arithmeticDifferenceBase: Double,
    val isBalanced: Boolean
)

data class TreasuryControlIssue(
    val treasuryAccountId: Long,
    val treasuryName: String,
    val kind: String,
    val message: String
)

data class TreasuryControlReport(
    val fromDate: Long,
    val toDate: Long,
    val issues: List<TreasuryControlIssue>
) {
    val isClear: Boolean get() = issues.isEmpty()
}

object TreasuryReconciliationMath {
    const val TOLERANCE = 0.01

    fun cashCount(expectedBalanceBase: Double, actualBalanceBase: Double): CashCountComputation {
        require(expectedBalanceBase.isFinite() && actualBalanceBase.isFinite()) { "أرصدة الجرد غير صالحة" }
        val difference = actualBalanceBase - expectedBalanceBase
        return CashCountComputation(
            expectedBalanceBase = expectedBalanceBase,
            actualBalanceBase = actualBalanceBase,
            differenceBase = difference,
            status = if (abs(difference) <= TOLERANCE) "BALANCED" else "VARIANCE"
        )
    }

    fun bankReconciliation(
        openingBalanceBase: Double,
        closingBalanceBase: Double,
        statementLineAmounts: List<Double>,
        bookClosingBalanceBase: Double,
        outstandingBookNetBase: Double
    ): BankReconciliationComputation {
        require(
            listOf(openingBalanceBase, closingBalanceBase, bookClosingBalanceBase, outstandingBookNetBase).all { it.isFinite() } &&
                statementLineAmounts.all { it.isFinite() }
        ) { "قيم المطابقة البنكية غير صالحة" }

        val statementMovement = statementLineAmounts.sum()
        val arithmeticDifference = openingBalanceBase + statementMovement - closingBalanceBase
        val adjustedStatementClosing = closingBalanceBase + outstandingBookNetBase
        val difference = bookClosingBalanceBase - adjustedStatementClosing
        return BankReconciliationComputation(
            statementOpeningBalanceBase = openingBalanceBase,
            statementClosingBalanceBase = closingBalanceBase,
            statementMovementBase = statementMovement,
            bookClosingBalanceBase = bookClosingBalanceBase,
            outstandingBookNetBase = outstandingBookNetBase,
            adjustedStatementClosingBase = adjustedStatementClosing,
            differenceBase = difference,
            arithmeticDifferenceBase = arithmeticDifference,
            isBalanced = abs(arithmeticDifference) <= TOLERANCE && abs(difference) <= TOLERANCE
        )
    }
}
