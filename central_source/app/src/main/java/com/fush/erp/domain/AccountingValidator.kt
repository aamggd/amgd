package com.fush.erp.domain

import kotlin.math.abs

data class DraftJournalLine(val accountId: Long, val debit: Double, val credit: Double)

object AccountingValidator {
    fun validate(lines: List<DraftJournalLine>) {
        require(lines.size >= 2) { "يجب أن يحتوي القيد على سطرين على الأقل" }
        require(lines.all { it.debit >= 0 && it.credit >= 0 }) { "لا يسمح بمبالغ سالبة" }
        require(lines.all { !(it.debit > 0 && it.credit > 0) }) { "لا يجوز أن يكون السطر مديناً ودائناً معاً" }
        val debit = lines.sumOf { it.debit }
        val credit = lines.sumOf { it.credit }
        require(debit > 0 && credit > 0) { "القيد يجب أن يحتوي مديناً ودائناً" }
        require(abs(debit - credit) < 0.005) { "القيد غير متوازن" }
    }
}
