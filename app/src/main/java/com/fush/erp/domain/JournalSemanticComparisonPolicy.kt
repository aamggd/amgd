package com.fush.erp.domain

import kotlin.math.abs

/**
 * Pure semantic comparator used by Support Safe Repair.
 *
 * Balance alone is deliberately insufficient: header provenance and the complete multiset of
 * account/debit/credit lines must equal the journal reconstructed from the source document.
 */
object JournalSemanticComparisonPolicy {
    data class Header(
        val entryNo: String,
        val entryDate: Long,
        val currencyCode: String,
        val exchangeRate: Double,
    )

    fun issues(
        expectedHeader: Header,
        actualHeader: Header,
        expectedLines: List<DraftJournalLine>,
        actualLines: List<DraftJournalLine>,
    ): List<String> {
        val issues = mutableListOf<String>()
        if (actualHeader.entryNo != expectedHeader.entryNo) {
            issues += "entryNo expected=${expectedHeader.entryNo} actual=${actualHeader.entryNo}"
        }
        if (actualHeader.entryDate != expectedHeader.entryDate) {
            issues += "entryDate expected=${expectedHeader.entryDate} actual=${actualHeader.entryDate}"
        }
        if (actualHeader.currencyCode != expectedHeader.currencyCode) {
            issues += "currency expected=${expectedHeader.currencyCode} actual=${actualHeader.currencyCode}"
        }
        if (abs(actualHeader.exchangeRate - expectedHeader.exchangeRate) > EPS) {
            issues += "exchangeRate expected=${fmt(expectedHeader.exchangeRate)} actual=${fmt(actualHeader.exchangeRate)}"
        }

        val expected = normalize(expectedLines)
        val actual = normalize(actualLines)
        if (expected.size != actual.size) issues += "lineCount expected=${expected.size} actual=${actual.size}"
        repeat(maxOf(expected.size, actual.size)) { index ->
            val e = expected.getOrNull(index)
            val a = actual.getOrNull(index)
            when {
                e == null -> issues += "unexpectedLine[$index]=${lineText(a)}"
                a == null -> issues += "missingLine[$index]=${lineText(e)}"
                e.accountId != a.accountId || abs(e.debit - a.debit) > EPS || abs(e.credit - a.credit) > EPS ->
                    issues += "lineMismatch[$index] expected=${lineText(e)} actual=${lineText(a)}"
            }
        }

        val expectedDebit = expected.sumOf { it.debit }
        val expectedCredit = expected.sumOf { it.credit }
        if (abs(expectedDebit - expectedCredit) > EPS) issues += "EXPECTED_JOURNAL_NOT_BALANCED"
        val actualDebit = actual.sumOf { it.debit }
        val actualCredit = actual.sumOf { it.credit }
        if (abs(actualDebit - actualCredit) > EPS) issues += "ACTUAL_JOURNAL_NOT_BALANCED"
        return issues
    }

    fun normalized(lines: List<DraftJournalLine>): List<DraftJournalLine> = normalize(lines)

    private fun normalize(lines: List<DraftJournalLine>): List<DraftJournalLine> =
        lines.filterNot { abs(it.debit) <= EPS && abs(it.credit) <= EPS }
            .sortedWith(compareBy<DraftJournalLine>({ it.accountId }, { it.debit }, { it.credit }))

    private fun lineText(line: DraftJournalLine?): String = line?.let {
        "acct=${it.accountId},D=${fmt(it.debit)},C=${fmt(it.credit)}"
    } ?: "null"

    private fun fmt(value: Double): String = "%.6f".format(java.util.Locale.US, value)
    private const val EPS = 0.000001
}
