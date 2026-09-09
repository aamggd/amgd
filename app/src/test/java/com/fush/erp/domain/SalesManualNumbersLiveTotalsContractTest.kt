package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SalesManualNumbersLiveTotalsContractTest {
    private fun source(relative: String): String {
        val candidates = listOf(
            File("src/main/java/$relative"),
            File("app/src/main/java/$relative"),
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Source file not found: $relative")
    }

    @Test
    fun salesServiceRequiresManualInvoiceAndReceiptNumbersAndChecksUniqueness() {
        val service = source("com/fush/erp/domain/SalesService.kt")
        val dao = source("com/fush/erp/data/dao/SalesDaos.kt")
        assertTrue(service.contains("val invoiceNo: String = \"\""))
        assertTrue(service.contains("val cashReceiptNo: String = \"\""))
        assertTrue(service.contains("SalesDocumentNumberPolicy.requireManual(requestedRaw, label)"))
        assertTrue(service.contains("label = \"رقم فاتورة البيع\""))
        assertTrue(service.contains("label = \"رقم سند التحصيل\""))
        assertTrue(service.contains("label = \"رقم سند التحصيل النقدي\""))
        assertTrue(dao.contains("suspend fun invoiceNoCount"))
        assertTrue(dao.contains("suspend fun receiptNoCount"))
    }

    @Test
    fun invoiceEditorShowsLineDiscountNetAndFinalInvoiceBreakdownBeforePosting() {
        val screen = source("com/fush/erp/ui/screens/SalesScreens.kt")
        assertTrue(screen.contains("label = { Text(\"رقم الفاتورة\") }"))
        assertTrue(screen.contains("label = { Text(\"رقم سند التحصيل\") }"))
        assertTrue(screen.contains("Text(\"قبل الخصم:"))
        assertTrue(screen.contains("Text(\"صافي السطر:"))
        assertTrue(screen.contains("Text(\"ملخص الفاتورة قبل الترحيل\""))
        assertTrue(screen.contains("Text(\"الرسوم/الجمارك:"))
        assertTrue(screen.contains("Text(\"الإجمالي النهائي:"))
    }
}
