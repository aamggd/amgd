package com.fush.erp.domain

import org.junit.Assert.assertTrue
import org.junit.Test

class JournalSemanticComparisonPolicyTest {
    private val header = JournalSemanticComparisonPolicy.Header(
        entryNo = "JE-SINV-001",
        entryDate = 1_777_000_000_000L,
        currencyCode = "YER_NEW",
        exchangeRate = 1.0,
    )

    @Test fun balancedJournalWithWrongAccountIsRejected() {
        val expected = listOf(
            DraftJournalLine(1300, 100_000.0, 0.0),
            DraftJournalLine(4000, 0.0, 100_000.0),
        )
        val wrongButBalanced = listOf(
            DraftJournalLine(1100, 100_000.0, 0.0),
            DraftJournalLine(4000, 0.0, 100_000.0),
        )
        val issues = JournalSemanticComparisonPolicy.issues(header, header, expected, wrongButBalanced)
        assertTrue(issues.any { it.startsWith("lineMismatch") })
    }

    @Test fun balancedJournalWithWrongAmountsIsRejected() {
        val expected = listOf(
            DraftJournalLine(1300, 100_000.0, 0.0),
            DraftJournalLine(4000, 0.0, 100_000.0),
        )
        val wrongButBalanced = listOf(
            DraftJournalLine(1300, 90_000.0, 0.0),
            DraftJournalLine(4000, 0.0, 90_000.0),
        )
        val issues = JournalSemanticComparisonPolicy.issues(header, header, expected, wrongButBalanced)
        assertTrue(issues.any { it.startsWith("lineMismatch") })
    }

    @Test fun exactJournalPassesRegardlessOfLineOrder() {
        val expected = listOf(
            DraftJournalLine(1300, 100_000.0, 0.0),
            DraftJournalLine(4000, 0.0, 100_000.0),
            DraftJournalLine(5000, 35_000.0, 0.0),
            DraftJournalLine(1200, 0.0, 35_000.0),
        )
        val actual = expected.reversed()
        assertTrue(JournalSemanticComparisonPolicy.issues(header, header, expected, actual).isEmpty())
    }

    @Test fun wrongHeaderSemanticsAreRejected() {
        val lines = listOf(
            DraftJournalLine(1300, 100_000.0, 0.0),
            DraftJournalLine(4000, 0.0, 100_000.0),
        )
        val actualHeader = header.copy(entryDate = header.entryDate + 86_400_000L, currencyCode = "USD", exchangeRate = 1500.0)
        val issues = JournalSemanticComparisonPolicy.issues(header, actualHeader, lines, lines)
        assertTrue(issues.any { it.startsWith("entryDate expected=") })
        assertTrue(issues.any { it.startsWith("currency expected=") })
        assertTrue(issues.any { it.startsWith("exchangeRate expected=") })
    }
}
