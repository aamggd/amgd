package com.fush.erp.domain

import org.junit.Assert.assertThrows
import org.junit.Test

class AccountingValidatorTest {
    @Test
    fun balancedEntryPasses() {
        AccountingValidator.validate(
            listOf(
                DraftJournalLine(1, 100.0, 0.0),
                DraftJournalLine(2, 0.0, 100.0)
            )
        )
    }

    @Test
    fun unbalancedEntryFails() {
        assertThrows(IllegalArgumentException::class.java) {
            AccountingValidator.validate(
                listOf(
                    DraftJournalLine(1, 100.0, 0.0),
                    DraftJournalLine(2, 0.0, 90.0)
                )
            )
        }
    }
}
