package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class RequiredSalesNumbersSupplierReverseGuardContractTest {
    private fun source(relative: String): String {
        val candidates = listOf(File("src/main/java/$relative"), File("app/src/main/java/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Source file not found: $relative")
    }

    @Test
    fun salesInvoiceAndReceiptsRequireNumbersAtServiceAndUiLayers() {
        val service = source("com/fush/erp/domain/SalesService.kt")
        val screen = source("com/fush/erp/ui/screens/SalesScreens.kt")
        assertTrue(service.contains("SalesDocumentNumberPolicy.requireManual(requestedRaw, label)"))
        assertTrue(service.contains("label = \"رقم فاتورة البيع\""))
        assertTrue(service.contains("label = \"رقم سند التحصيل\""))
        assertTrue(service.contains("label = \"رقم سند التحصيل النقدي\""))
        assertTrue(screen.contains("invoiceNoText.isNotBlank()"))
        assertTrue(screen.contains("cashReceiptNoText.isNotBlank()"))
        assertTrue(screen.countOccurrences("receiptNoText.isNotBlank()") >= 2)
        assertTrue(screen.contains("placeholder = { Text(\"رقم إلزامي\") }"))
    }

    @Test
    fun supplierPaymentReversalRequiresPostingPermissionBeforeMutationAndUiIsGated() {
        val service = source("com/fush/erp/domain/PurchaseService.kt")
        val screen = source("com/fush/erp/ui/screens/PartyScreens.kt")
        val marker = "suspend fun reverseSupplierPayment("
        val start = service.indexOf(marker)
        assertTrue(start >= 0)
        val end = service.indexOf("\n    private suspend fun ", start).takeIf { it >= 0 } ?: service.length
        val block = service.substring(start, end)
        val guard = block.indexOf("db.requireUserPermission(createdBy, SecurityPermissions.SUPPLIER_PAYMENT_POST)")
        val firstMutation = Regex("\\.insert[A-Z][A-Za-z0-9_]*\\(").find(block)?.range?.first ?: Int.MAX_VALUE
        assertTrue("Missing SUPPLIER_PAYMENT_POST guard", guard >= 0)
        assertTrue("Permission guard must run before mutation", guard < firstMutation)
        assertTrue(screen.contains("val canReverseSupplierPayment = user.role == \"ADMIN\" || SecurityPermissions.SUPPLIER_PAYMENT_POST in rolePermissions"))
        assertTrue(screen.contains("onReverse = if (canReverseSupplierPayment)"))
        assertTrue(screen.contains("!isReversal && !isReversedOriginal && onReverse != null"))
    }

    private fun String.countOccurrences(needle: String): Int {
        var count = 0
        var index = 0
        while (true) {
            index = indexOf(needle, index)
            if (index < 0) return count
            count++
            index += needle.length
        }
    }
}
