package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class CashRefundActualCashGuardContractTest {
    private fun source(path: String): String {
        val f = listOf(File("src/main/java/$path"), File("app/src/main/java/$path")).firstOrNull { it.isFile }
            ?: error("Missing source: $path")
        return f.readText()
    }

    @Test fun salesReturnAndReceiptReversalUseActualCashTimelineGuard() {
        val s = source("com/fush/erp/domain/SalesService.kt")
        assertTrue(s.contains("requireSalesCashRefundTimelineCovered("))
        assertTrue(s.contains("receivedBaseForInvoiceAsOf"))
        assertTrue(s.contains("cashRefundedBaseForInvoiceAsOf"))
        assertTrue(s.contains("proposedCashDeltaBase = -allocation.amountBase"))
    }

    @Test fun purchaseReturnAndPaymentReversalUseActualCashTimelineGuard() {
        val s = source("com/fush/erp/domain/PurchaseService.kt")
        assertTrue(s.contains("requirePurchaseCashRefundTimelineCovered("))
        assertTrue(s.contains("paidBaseForInvoiceAsOf"))
        assertTrue(s.contains("cashRefundedBaseForInvoiceAsOf"))
        assertTrue(s.contains("proposedCashDeltaBase = -allocation.allocatedBase"))
        assertTrue(s.contains("invoice.paymentType == \"CASH\""))
    }
}
