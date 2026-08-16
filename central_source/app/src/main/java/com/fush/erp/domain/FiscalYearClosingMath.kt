package com.fush.erp.domain

import kotlin.math.abs

data class TemporaryAccountMovement(
    val accountId: Long,
    val debit: Double,
    val credit: Double
)

data class FiscalYearClosingComputation(
    val lines: List<DraftJournalLine>,
    val netIncomeBase: Double
)

object FiscalYearClosingMath {
    private const val EPSILON = 0.000001

    fun compute(
        movements: List<TemporaryAccountMovement>,
        retainedEarningsAccountId: Long
    ): FiscalYearClosingComputation {
        val closingLines = movements.mapNotNull { movement ->
            val balance = movement.debit - movement.credit
            when {
                balance > EPSILON -> DraftJournalLine(movement.accountId, debit = 0.0, credit = balance)
                balance < -EPSILON -> DraftJournalLine(movement.accountId, debit = -balance, credit = 0.0)
                else -> null
            }
        }.toMutableList()

        val temporaryDebits = closingLines.sumOf { it.debit }
        val temporaryCredits = closingLines.sumOf { it.credit }
        val netIncome = temporaryDebits - temporaryCredits

        when {
            netIncome > EPSILON -> closingLines += DraftJournalLine(retainedEarningsAccountId, debit = 0.0, credit = netIncome)
            netIncome < -EPSILON -> closingLines += DraftJournalLine(retainedEarningsAccountId, debit = -netIncome, credit = 0.0)
        }

        if (closingLines.isNotEmpty()) {
            AccountingValidator.validate(closingLines)
        }
        return FiscalYearClosingComputation(closingLines, if (abs(netIncome) <= EPSILON) 0.0 else netIncome)
    }
}
