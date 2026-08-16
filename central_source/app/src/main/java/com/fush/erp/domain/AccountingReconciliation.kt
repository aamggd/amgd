package com.fush.erp.domain

import kotlin.math.abs

data class AccountingReconciliationRow(
    val code: String,
    val labelAr: String,
    val glBalanceBase: Double,
    val subledgerBalanceBase: Double,
    val differenceBase: Double,
    val isMatched: Boolean
)

data class AccountingReconciliationReport(
    val asOf: Long,
    val rows: List<AccountingReconciliationRow>,
    val trialBalanceDifferenceBase: Double
) {
    val isMatched: Boolean
        get() = abs(trialBalanceDifferenceBase) <= AccountingReconciliationMath.TOLERANCE &&
            rows.all { it.isMatched }
}

object AccountingReconciliationMath {
    const val TOLERANCE = 0.01

    fun row(code: String, labelAr: String, gl: Double, subledger: Double): AccountingReconciliationRow {
        require(gl.isFinite() && subledger.isFinite()) { "أرصدة المطابقة غير صالحة" }
        val difference = gl - subledger
        return AccountingReconciliationRow(
            code = code,
            labelAr = labelAr,
            glBalanceBase = gl,
            subledgerBalanceBase = subledger,
            differenceBase = difference,
            isMatched = abs(difference) <= TOLERANCE
        )
    }

    fun naturalDebitBalance(details: List<ReportDetail>, accountCode: String): Double =
        details.asSequence()
            .filter { it.accountCode == accountCode }
            .sumOf { it.debit - it.credit }

    fun naturalCreditBalance(details: List<ReportDetail>, accountCode: String): Double =
        details.asSequence()
            .filter { it.accountCode == accountCode }
            .sumOf { it.credit - it.debit }
}
