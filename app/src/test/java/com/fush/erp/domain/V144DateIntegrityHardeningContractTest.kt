package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V144DateIntegrityHardeningContractTest {
    private fun source(path: String): String {
        val file = listOf(File("src/main/java/$path"), File("app/src/main/java/$path")).firstOrNull { it.isFile }
            ?: error("Missing source: $path")
        return file.readText()
    }

    @Test fun purchaseInvoiceUsesCentralFutureDateGuardBeforePostingPeriod() {
        val s = source("com/fush/erp/domain/PurchaseService.kt")
        val body = s.substringAfter("suspend fun postPurchase(request: PostPurchaseRequest)")
            .substringBefore("data class PostPurchaseReturnRequest")
        val future = body.indexOf("FutureDocumentDatePolicy.requireNotFuture(request.invoiceDate")
        val period = body.indexOf("requirePostingPeriodOpen(request.invoiceDate)")
        val insert = body.indexOf("insertInvoice(")
        assertTrue(future >= 0)
        assertTrue(period > future)
        assertTrue(insert > period)
    }

    @Test fun inventoryCountSnapshotIsTakenAsOfSelectedBusinessDay() {
        val dao = source("com/fush/erp/data/dao/AdvancedInventoryDao.kt")
        val service = source("com/fush/erp/domain/AdvancedInventoryService.kt")
        assertTrue(dao.contains("sm.movementDate <= :asOf"))
        assertTrue(dao.contains("suspend fun snapshotAt(warehouseId: Long, asOf: Long)"))
        assertTrue(service.contains("snapshotAt(warehouseId, effectiveDate)"))
    }

    @Test fun inventoryCountAdjustmentAndJournalUseCountEffectiveDateNotPostTimestamp() {
        val service = source("com/fush/erp/domain/AdvancedInventoryService.kt")
        val body = service.substringAfter("suspend fun postCount(").substringBefore("suspend fun assignLegacyLotAndExpiry")
        assertTrue(body.contains("val effectiveDate = count.countDate"))
        assertTrue(body.contains("movementDate = effectiveDate"))
        assertTrue(body.contains("entryDate = effectiveDate"))
        assertTrue(body.contains("postedAt = postedAt"))
        assertFalse(body.contains("movementDate = now"))
        assertFalse(body.contains("entryDate = now"))
    }

    @Test fun backdatedNegativeCountVarianceUsesHistoricalOutflowGuard() {
        val service = source("com/fush/erp/domain/AdvancedInventoryService.kt")
        val body = service.substringAfter("suspend fun postCount(").substringBefore("suspend fun assignLegacyLotAndExpiry")
        assertTrue(body.contains("variance < -InventoryMath.EPS"))
        assertTrue(body.contains("requireHistoricalLotOutflowAvailable("))
        assertTrue(body.contains("requestedQtyBase = -variance"))
    }

    @Test fun inventoryCountUiExposesExplicitDateAndPassesItToService() {
        val ui = source("com/fush/erp/ui/screens/AdvancedInventoryScreens.kt")
        assertTrue(ui.contains("FushDateField(dateText, { dateText = it }, \"تاريخ الجرد\""))
        assertTrue(ui.contains("countDate = date"))
    }

    @Test fun warehouseTransferUiAndServiceUseSameEndOfBusinessDayBalanceSemantics() {
        val ui = source("com/fush/erp/ui/screens/AdvancedInventoryScreens.kt")
        val service = source("com/fush/erp/domain/AdvancedInventoryService.kt")
        assertTrue(ui.contains("lotBalancesAt(sourceWarehouseId, it.id, BusinessDatePolicy.endOfBusinessDay(transferDate))"))
        assertTrue(service.contains("FutureDocumentDatePolicy.requireNotFuture(transferDate, \"تاريخ التحويل\")"))
        assertTrue(service.contains("BusinessDatePolicy.endOfBusinessDay(transfer.transferDate)"))
    }
}
