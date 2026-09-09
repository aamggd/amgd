package com.fush.erp.domain

data class DraftJournalLine(val accountId: Long, val debit: Double, val credit: Double)

object AccountingValidator {
    fun validate(lines: List<DraftJournalLine>) {
        require(lines.size >= 2) { "يجب أن يحتوي القيد على سطرين على الأقل" }
        require(lines.all { it.debit >= 0 && it.credit >= 0 }) { "لا يسمح بمبالغ سالبة" }
        require(lines.all { (it.debit > 0.0) xor (it.credit > 0.0) }) {
            "كل سطر قيد يجب أن يكون مديناً أو دائناً بقيمة موجبة واحدة فقط"
        }
        val debitScaled = AccountingPrecision.sumAmountsToScaled(lines.map { it.debit })
        val creditScaled = AccountingPrecision.sumAmountsToScaled(lines.map { it.credit })
        require(debitScaled > 0L && creditScaled > 0L) { "القيد يجب أن يحتوي مديناً ودائناً" }
        require(debitScaled == creditScaled) { "القيد غير متوازن" }
    }
}
