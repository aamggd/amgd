package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionChronologyGuardsContractTest {
    private fun source(relative: String): String {
        val candidates = listOf(File("src/main/java/$relative"), File("app/src/main/java/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Source file not found: $relative")
    }

    @Test
    fun customerReceiptCannotPrecedeAllocatedInvoiceAndUsesAsOfOutstanding() {
        val service = source("com/fush/erp/domain/SalesService.kt")
        assertTrue(service.contains("eventLabel = \"تاريخ التحصيل\""))
        assertTrue(service.contains("sourceDate = invoice.invoiceDate"))
        assertTrue(service.contains("invoiceOutstandingBaseAsOf(invoice.id, BusinessDatePolicy.endOfBusinessDay(receiptDate))"))
        assertTrue(service.contains("val currentOutstanding = invoiceOutstandingBase(invoice.id)"))
        assertTrue(service.contains("مفرطة التخصيص في تاريخ لاحق"))
        assertTrue(service.contains("openInvoiceSummariesAsOf(validatedCustomerId, BusinessDatePolicy.endOfBusinessDay(receiptDate))"))

        val dao = source("com/fush/erp/data/dao/SalesDaos.kt")
        assertTrue(dao.contains("suspend fun openInvoiceSummariesAsOf(customerId: Long, asOf: Long)"))
        assertTrue(dao.contains("cr.receiptDate <= :asOf"))
        assertTrue(dao.contains("sr.returnDate <= :asOf"))
    }

    @Test
    fun supplierPaymentCannotPrecedeAllocatedInvoiceAndUsesAsOfOutstanding() {
        val service = source("com/fush/erp/domain/PurchaseService.kt")
        assertTrue(service.contains("eventLabel = \"تاريخ دفعة المورد\""))
        assertTrue(service.contains("sourceDate = invoice.invoiceDate"))
        assertTrue(service.contains("paidBaseForInvoiceAsOf(invoice.id, paymentDate)"))
        assertTrue(service.contains("supplierCreditReturnedBaseForInvoiceAsOf(invoice.id, paymentDate)"))
        assertTrue(service.contains("val currentOutstandingBase = SupplierApMath.outstandingBase"))
        assertTrue(service.contains("مفرطة التخصيص في تاريخ لاحق"))
        assertTrue(service.contains("openSupplierInvoicesAsOf(supplierId, paymentDate)"))
    }

    @Test
    fun salesAndPurchaseReturnsCannotPrecedeOriginalInvoices() {
        val sales = source("com/fush/erp/domain/SalesService.kt")
        val purchases = source("com/fush/erp/domain/PurchaseService.kt")
        assertTrue(sales.contains("eventLabel = \"تاريخ مرتجع البيع\""))
        assertTrue(purchases.contains("eventLabel = \"تاريخ مرتجع الشراء\""))
    }

    @Test
    fun autoAllocationCannotReachFutureInvoices() {
        val salesDao = source("com/fush/erp/data/dao/SalesDaos.kt")
        val purchaseDao = source("com/fush/erp/data/dao/PurchaseDaos.kt")
        assertTrue(salesDao.contains("AND si.invoiceDate <= :asOf"))
        assertTrue(purchaseDao.contains("AND p.invoiceDate <= :asOf"))
    }
}
