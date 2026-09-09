package com.fush.erp.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReleaseHardeningV128ContractTest {
    private fun source(relative: String): String {
        val candidates = listOf(File("src/main/java/$relative"), File("app/src/main/java/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Cannot locate $relative")
    }

    @Test
    fun cashRefundIsInformationalInCustomerArAndDoesNotCreditArTwice() {
        val dao = source("com/fush/erp/data/dao/SalesDaos.kt")
        val cashRefund = dao.substringAfter("'CASH_REFUND' AS eventType").substringBefore("UNION ALL")
        assertTrue(cashRefund.contains("0.0 AS debitBase"))
        assertTrue(cashRefund.contains("0.0 AS creditBase"))
        assertTrue(cashRefund.contains("sr.settlementType = 'CASH_REFUND'"))
        val creditReturn = dao.substringAfter("'SALES_RETURN' AS eventType").substringBefore("UNION ALL")
        assertTrue(creditReturn.contains("sr.settlementType = 'CUSTOMER_CREDIT'"))
    }

    @Test
    fun finishedGoodsShelfLifeIsRequiredAtMasterDataBoundary() {
        val source = source("com/fush/erp/domain/MasterDataService.kt")
        assertTrue(source.contains("if (category == \"FINISHED_GOOD\")"))
        assertTrue(source.contains("require(shelfLifeDays != null && shelfLifeDays > 0)"))
        assertFalse(source.contains("shelfLifeDays ?: 730"))
    }

    @Test
    fun acceptedProductionExecutiveKpiUsesReceiptDateNotManufactureDate() {
        val dao = source("com/fush/erp/data/dao/ReportDao.kt")
        val compact = dao.replace(" ", "")
        assertTrue(compact.contains("sm.movementType='PRODUCTION_RECEIPT'"))
        assertTrue(dao.contains("sm.movementDate BETWEEN :from AND :to"))
    }

    @Test
    fun reportLayerNoLongerHardcodesSpecific60Or200ProductVolumes() {
        val math = source("com/fush/erp/domain/ReportMath.kt")
        val entities = source("com/fush/erp/data/entity/ReportEntities.kt")
        assertFalse(math.contains("productVolumeMl"))
        assertFalse(entities.contains("accepted60QtyBase"))
        assertFalse(entities.contains("accepted200QtyBase"))
    }

    @Test
    fun backupFormatCarriesAttachmentsAndNearExpiryIsConfigurable() {
        val backup = source("com/fush/erp/backup/BackupArchiveCodec.kt")
        val reports = source("com/fush/erp/ui/screens/ReportsScreen.kt")
        assertTrue(backup.contains("const val FORMAT_VERSION = 4"))
        assertTrue(backup.contains("attachments/\$relative"))
        assertTrue(reports.contains("near_expiry_days"))
    }
}
