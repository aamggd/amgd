package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V170OldYerReceiptFxContractTest {
    private fun source(relative: String): String {
        val candidates = listOf(File("src/main/java/$relative"), File("app/src/main/java/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Cannot locate $relative from ${File(".").absolutePath}")
    }

    @Test
    fun collectionUsesReceiptDateRateInsteadOfInvoiceHistoricalRate() {
        val screen = source("com/fush/erp/ui/screens/SalesScreens.kt")
        assertTrue(screen.contains("LaunchedEffect(receiptCurrency?.code, receiptDate)") || screen.contains("LaunchedEffect(invoice?.currencyCode, receiptDate)"))
        assertTrue(screen.contains("container.salesService.exchangeRateAt(code, date)"))
        assertTrue(screen.contains("LaunchedEffect(currency?.code, receiptDate)"))
        assertTrue(screen.contains("1 ريال قديم = كم ريال جديد"))
        assertFalse(screen.contains("if (rate.toDoubleOrNull() == null || rate == \"1\") rate = historicalRate.toString()"))
    }

    @Test
    fun exchangeRateLookupIsGenericForSaleAndReceiptBusinessDates() {
        val service = source("com/fush/erp/domain/SalesService.kt")
        assertTrue(service.contains("suspend fun exchangeRateAt(currencyCode: String, invoiceDate: Long)"))
        assertTrue(service.contains("BusinessDatePolicy.endOfBusinessDay(invoiceDate)"))
        assertTrue(service.contains("لا يوجد سعر صرف للعملة"))
    }
}
