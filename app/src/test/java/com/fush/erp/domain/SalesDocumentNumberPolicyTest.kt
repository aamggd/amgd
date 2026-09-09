package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SalesDocumentNumberPolicyTest {
    @Test(expected = IllegalArgumentException::class)
    fun blankDocumentNumberIsRejectedWhenRequired() {
        SalesDocumentNumberPolicy.requireManual("   ", "رقم الفاتورة")
    }

    @Test
    fun manualNumberIsTrimmedButOtherwisePreserved() {
        assertEquals("INV-2026/001", SalesDocumentNumberPolicy.manualOrNull("  INV-2026/001  "))
    }

    @Test(expected = IllegalArgumentException::class)
    fun multilineManualNumberIsRejected() {
        SalesDocumentNumberPolicy.manualOrNull("INV-1\nINV-2")
    }

    @Test(expected = IllegalArgumentException::class)
    fun overlongManualNumberIsRejected() {
        SalesDocumentNumberPolicy.manualOrNull("X".repeat(81))
    }
}
