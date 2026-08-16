package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoNumberSequencePolicyTest {
    @Test
    fun sequenceMovesStrictlyForwardWithoutReuse() {
        var last = 0L
        repeat(10_000) {
            val next = AutoNumberSequencePolicy.next(last)
            assertTrue(next > last)
            assertEquals(last + 1L, next)
            last = next
        }
        assertEquals(10_000L, last)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeSequenceValueIsRejectedInsteadOfReset() {
        AutoNumberSequencePolicy.next(-1L)
    }

    @Test(expected = IllegalStateException::class)
    fun sequenceOverflowIsRejectedInsteadOfWrapping() {
        AutoNumberSequencePolicy.next(Long.MAX_VALUE)
    }

    @Test
    fun formattingDoesNotWrapWhenConfiguredWidthIsExceeded() {
        assertEquals("UNT-1000", AutoNumberFormat.master("UNT", 1000L, 3))
        assertEquals("PINV-20260817-10000", AutoNumberFormat.document("PINV", "20260817", 10_000L))
    }
}
