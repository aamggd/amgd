package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V133TimeSelectionHardeningTest {
    private fun source(path: String): String {
        val file = listOf(File("src/main/java/$path"), File("app/src/main/java/$path")).firstOrNull { it.isFile }
            ?: error("Missing source: $path")
        return file.readText()
    }

    @Test fun futureDocumentDatePolicyBlocksTomorrowButAllowsToday() {
        val trustedNow = BusinessDatePolicy.parseIsoDateStart("2026-08-23") + 12 * 60 * 60 * 1000L
        val today = BusinessDatePolicy.parseIsoDateStart("2026-08-23")
        val tomorrow = BusinessDatePolicy.parseIsoDateStart("2026-08-24")
        FutureDocumentDatePolicy.requireNotFuture(today, "today", trustedNow)
        val failure = runCatching { FutureDocumentDatePolicy.requireNotFuture(tomorrow, "tomorrow", trustedNow) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    @Test fun salesAndPurchasesGuardDependentDocumentFutureDates() {
        val sales = source("com/fush/erp/domain/SalesService.kt")
        val purchase = source("com/fush/erp/domain/PurchaseService.kt")
        assertTrue(sales.contains("FutureDocumentDatePolicy.requireNotFuture(receiptDate"))
        assertTrue(sales.contains("FutureDocumentDatePolicy.requireNotFuture(returnDate"))
        assertTrue(purchase.contains("FutureDocumentDatePolicy.requireNotFuture(paymentDate"))
        assertTrue(purchase.contains("FutureDocumentDatePolicy.requireNotFuture(request.returnDate"))
    }

    @Test fun searchableSelectorRequiresRealClearCallback() {
        val selector = source("com/fush/erp/ui/FushSearchableSelectionField.kt")
        assertTrue(selector.contains("onCleared: () -> Unit"))
        assertFalse(selector.contains("onCleared: (() -> Unit)?"))
        assertTrue(selector.contains("value != selectedText) onCleared()"))
    }

    @Test fun noDirectNoArgDateOrCalendarClockRemainsInMainSource() {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).firstOrNull { it.isDirectory }
            ?: error("main source not found")
        val offenders = root.walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
            file.readLines().asSequence().mapIndexedNotNull { index, line ->
                val noArgDate = Regex("(?<![A-Za-z0-9_])Date\\(\\)").containsMatchIn(line)
                val calendar = line.contains("Calendar.getInstance()")
                if (noArgDate || calendar) "${file.name}:${index + 1}" else null
            }
        }.toList()
        assertEquals(emptyList<String>(), offenders)
    }

    @Test fun trustedUtcRequiresConsensusInsteadOfFirstResponder() {
        val samples = listOf(
            TrustedUtcSample(1_000_000L, "a", 40L),
            TrustedUtcSample(1_000_700L, "b", 60L),
            TrustedUtcSample(2_000_000L, "bad", 10L)
        )
        val consensus = TrustedTimeMath.consensusUtc(samples, minSources = 2, maxSkewMs = 2_000L)
        assertEquals(1_000_350L, consensus?.utcMillis)
        assertNull(TrustedTimeMath.consensusUtc(samples, minSources = 3, maxSkewMs = 2_000L))
    }
}
