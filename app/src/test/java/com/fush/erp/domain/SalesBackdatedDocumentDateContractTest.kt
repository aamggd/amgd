package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SalesBackdatedDocumentDateContractTest {
    private fun source(relative: String): String {
        val candidates = listOf(File("src/main/java/$relative"), File("app/src/main/java/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Source file not found: $relative")
    }

    @Test
    fun salesInvoiceUiUsesEditableDocumentDateInsteadOfHiddenOpenTime() {
        val ui = source("com/fush/erp/ui/screens/SalesScreens.kt")
        assertTrue(ui.contains("var saleDateText by remember"))
        assertTrue(ui.contains("label = \"تاريخ الفاتورة / العملية\""))
        assertTrue(ui.contains("FushDateField("))
        assertFalse(ui.contains("val saleDate = remember { System.currentTimeMillis() }"))
    }

    @Test
    fun selectedInvoiceDateDrivesPostingInventoryPriceAndCashReceiptWhileNumbersStayManual() {
        val service = source("com/fush/erp/domain/SalesService.kt")
        assertTrue(service.contains("requirePostingPeriodOpen(request.invoiceDate)"))
        assertTrue(service.contains("requestedRaw = request.invoiceNo"))
        assertTrue(service.contains("label = \"رقم فاتورة البيع\""))
        // v207 may fulfill a sales line from shipment custody instead of the source warehouse,
        // but both inventory paths must still use the selected invoice date.
        assertTrue(service.contains("totalCostBase += if (shipmentPlan.isNotEmpty())"))
        assertTrue(service.contains("allocateShipmentPlanStockForSaleInsideTransaction("))
        assertTrue(service.contains("allocateStockForSale("))
        assertTrue(service.contains("draft.baseQuantity,"))
        assertTrue(service.contains("draft.freeBaseQuantity,"))
        assertTrue(service.contains("movementDate = request.invoiceDate"))
        assertTrue(service.contains("request.invoiceDate,"))
        assertTrue(service.contains("date = request.invoiceDate"))
        assertTrue(service.contains("receiptDate = request.invoiceDate"))
        assertTrue(service.contains("requestedRaw = request.cashReceiptNo"))
        assertTrue(service.contains("label = \"رقم سند التحصيل النقدي\""))

        val ui = source("com/fush/erp/ui/screens/SalesScreens.kt")
        assertTrue(ui.contains("latestPrice(i.id, c.channel, c.province, cur.code, salesEndOfDay(at))"))
    }

    @Test
    fun creditControlIsHistoricalAsOfInvoiceDate() {
        val service = source("com/fush/erp/domain/SalesService.kt")
        assertTrue(service.contains("overdueInvoiceCountAsOf(customer.id, asOf)"))
        assertTrue(service.contains("customerOutstandingBaseAsOf(customer.id, asOf)"))

        val dao = source("com/fush/erp/data/dao/SalesDaos.kt")
        assertTrue(dao.contains("suspend fun customerOutstandingBaseAsOf(customerId: Long, asOf: Long): Double"))
        assertTrue(dao.contains("cr.receiptDate <= :asOf"))
        assertTrue(dao.contains("sr.returnDate <= :asOf"))
        assertTrue(dao.contains("pv.voucherDate <= :asOf"))
        assertTrue(dao.contains("pv.reversedAt > :asOf"))
        assertTrue(dao.contains("suspend fun overdueInvoiceCountAsOf(customerId: Long, asOf: Long): Int"))
    }

    @Test
    fun exchangeRateIsHistoricalAndCannotBeSilentlyOverridden() {
        val service = source("com/fush/erp/domain/SalesService.kt")
        assertTrue(service.contains("suspend fun exchangeRateAt(currencyCode: String, invoiceDate: Long): Double"))
        assertTrue(service.contains("latestRateAt(currencyCode, BusinessDatePolicy.endOfBusinessDay(invoiceDate))"))
        assertTrue(service.contains("SalesExchangeRatePolicy.requiresOverride"))
        assertTrue(service.contains("SecurityPermissions.EXCHANGE_RATE_OVERRIDE"))
        assertTrue(service.contains("exchangeRateOverrideReason.trim().length >= 5"))

        val ui = source("com/fush/erp/ui/screens/SalesScreens.kt")
        assertTrue(ui.contains("السعر التاريخي المعتمد"))
        assertTrue(ui.contains("canOverrideExchangeRate"))
        assertTrue(ui.contains("سبب تغيير سعر الصرف"))
    }

    @Test
    fun auditCreationTimeRemainsIndependentFromDocumentDate() {
        val entity = source("com/fush/erp/data/entity/SalesEntities.kt")
        assertTrue(entity.contains("val invoiceDate: Long"))
        assertTrue(entity.contains("val createdAt: Long = com.fush.erp.domain.TrustedTimeService.now()"))
    }
}
