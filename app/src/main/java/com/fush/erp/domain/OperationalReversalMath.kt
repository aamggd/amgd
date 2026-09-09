package com.fush.erp.domain

import kotlin.math.max

object OperationalReversalMath {
    fun negate(amount: Double): Double {
        require(amount.isFinite()) { "المبلغ غير صالح" }
        return -amount
    }

    fun commissionReduction(currentNetCommission: Double, targetNetCommission: Double): Double {
        require(currentNetCommission.isFinite() && targetNetCommission.isFinite()) { "قيمة العمولة غير صالحة" }
        return max(0.0, currentNetCommission - targetNetCommission)
    }

    fun reverseJournalLines(lines: List<DraftJournalLine>): List<DraftJournalLine> {
        require(lines.isNotEmpty()) { "القيد لا يحتوي سطوراً" }
        return lines.map { DraftJournalLine(it.accountId, debit = it.credit, credit = it.debit) }
    }
}
