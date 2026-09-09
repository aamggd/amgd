package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AccountingPrecisionTest {
    @Test
    fun decimalAmountsAreCanonicalizedToScaledIntegers() {
        assertEquals(1_000L, AccountingPrecision.amountToScaled(0.1))
        assertEquals(2_000L, AccountingPrecision.amountToScaled(0.2))
        assertEquals(3_000L, AccountingPrecision.amountToScaled(0.1 + 0.2))
    }

    @Test
    fun amountHalfEvenTieBoundariesAreExact() {
        assertEquals(12_344L, AccountingPrecision.amountToScaled(1.23445))
        assertEquals(12_346L, AccountingPrecision.amountToScaled(1.23455))
        assertEquals(12_344L, AccountingPrecision.amountToScaled(1.234449))
        assertEquals(12_345L, AccountingPrecision.amountToScaled(1.234451))
        assertEquals(-12_344L, AccountingPrecision.amountToScaled(-1.23445))
        assertEquals(-12_346L, AccountingPrecision.amountToScaled(-1.23455))
        assertEquals(0L, AccountingPrecision.amountToScaled(0.0))
    }

    @Test
    fun exchangeRateUsesEightFixedDecimalsAndHalfEvenTies() {
        assertEquals(123_456_789L, AccountingPrecision.rateToScaled(1.23456789))
        assertEquals(123_456_788L, AccountingPrecision.rateToScaled(1.234567885))
        assertEquals(123_456_790L, AccountingPrecision.rateToScaled(1.234567895))
        assertEquals(1.23456789, AccountingPrecision.rateToDouble(123_456_789L), 0.0)
    }

    @Test
    fun validatorBalancesUsingScaledIntegersNotBinaryFloatingPoint() {
        AccountingValidator.validate(
            listOf(
                DraftJournalLine(1, 0.1 + 0.2, 0.0),
                DraftJournalLine(2, 0.0, 0.3)
            )
        )
    }

    @Test
    fun validatorRejectsOneCanonicalUnitDifference() {
        assertThrows(IllegalArgumentException::class.java) {
            AccountingValidator.validate(
                listOf(
                    DraftJournalLine(1, 1.0001, 0.0),
                    DraftJournalLine(2, 0.0, 1.0000)
                )
            )
        }
    }
}
