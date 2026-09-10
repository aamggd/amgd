package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoNumberFormatTest {
    @Test
    fun masterCodesUseRequestedWidth() {
        assertEquals("SUP-000001", AutoNumberFormat.master("SUP", 1, 6))
        assertEquals("UNT-012", AutoNumberFormat.master("UNT", 12, 3))
        assertEquals("RM-000147", AutoNumberFormat.master("RM", 147, 6))
    }

    @Test
    fun documentNumbersContainDateAndDailySequence() {
        assertEquals("PINV-20260810-0001", AutoNumberFormat.document("PINV", "20260810", 1))
        assertEquals("SINV-20260810-0042", AutoNumberFormat.document("SINV", "20260810", 42))
        assertEquals("PROD-20260810-0007", AutoNumberFormat.document("PROD", "20260810", 7))
    }
}
