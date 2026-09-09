package com.fush.erp.domain

import org.junit.Assert.assertThrows
import org.junit.Test

class AccountingJournalLineIntegrityPolicyTest {
    @Test
    fun validDebitAndCreditLinesPass() {
        AccountingValidator.validate(
            listOf(
                DraftJournalLine(accountId = 1L, debit = 10.0, credit = 0.0),
                DraftJournalLine(accountId = 2L, debit = 0.0, credit = 10.0)
            )
        )
    }

    @Test
    fun zeroZeroLineIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AccountingValidator.validate(
                listOf(
                    DraftJournalLine(1L, 10.0, 0.0),
                    DraftJournalLine(2L, 0.0, 10.0),
                    DraftJournalLine(3L, 0.0, 0.0)
                )
            )
        }
    }

    @Test
    fun bothSidesPositiveLineIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AccountingValidator.validate(
                listOf(
                    DraftJournalLine(1L, 10.0, 1.0),
                    DraftJournalLine(2L, 0.0, 9.0)
                )
            )
        }
    }

    @Test
    fun negativeLineIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AccountingValidator.validate(
                listOf(
                    DraftJournalLine(1L, -1.0, 0.0),
                    DraftJournalLine(2L, 0.0, 1.0)
                )
            )
        }
    }
}
