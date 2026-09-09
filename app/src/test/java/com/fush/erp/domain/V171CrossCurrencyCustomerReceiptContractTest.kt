package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V171CrossCurrencyCustomerReceiptContractTest {
    private fun source(relative: String): String {
        val candidates = listOf(File("src/main/java/$relative"), File("app/src/main/java/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Cannot locate $relative from ${File(".").absolutePath}")
    }

    @Test
    fun invoiceReceiptCurrencyIsIndependentFromInvoiceCurrency() {
        val screen = source("com/fush/erp/ui/screens/SalesScreens.kt")
        assertTrue(screen.contains("currencies.filter { it.isActive }"))
        assertTrue(screen.contains("عملة الفاتورة") && screen.contains("وعملة التحصيل"))
        assertTrue(screen.contains("لا توجد خزينة أو حساب بنكي نشط بعملة التحصيل"))
        assertFalse(screen.contains("currencies.filter { it.code == invoice.currencyCode }"))
    }

    @Test
    fun serviceAllowsCrossCurrencyCashAndBlocksAmbiguousCrossCurrencyDiscount() {
        val service = source("com/fush/erp/domain/SalesService.kt")
        assertTrue(service.contains("val sameCurrency = invoice.currencyCode == currencyCode"))
        assertTrue(service.contains("sameCurrency = sameCurrency"))
        assertTrue(service.contains("لا يمكن جمع خصم تحصيل مع دفع بعملة مختلفة عن عملة الفاتورة"))
        assertFalse(service.contains("require(invoice.currencyCode == currencyCode)"))
    }
}
