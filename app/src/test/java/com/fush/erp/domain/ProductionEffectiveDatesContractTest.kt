package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionEffectiveDatesContractTest {
    private fun source(relative: String): String {
        val candidates = listOf(File("src/main/java/$relative"), File("app/src/main/java/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Source file not found: $relative")
    }

    @Test
    fun productionUiExposesSeparateEffectiveDatesForEconomicEvents() {
        val ui = source("com/fush/erp/ui/screens/ProductionScreens.kt")
        listOf(
            "تاريخ أمر الإنتاج",
            "تاريخ صرف المواد",
            "تاريخ الإنتاج / التصنيع",
            "تاريخ فحص الجودة",
            "تاريخ تحميل الأجور / عمولة الإنتاج",
            "تاريخ قرار الجودة / إدخال المنتج التام"
        ).forEach { label -> assertTrue("Missing $label", ui.contains(label)) }
        assertTrue(ui.contains("FushDateField("))
    }

    @Test
    fun materialIssueUsesChosenHistoricalDateForStockIssueAndJournal() {
        val service = source("com/fush/erp/domain/ProductionService.kt")
        assertTrue(service.contains("issueDate: Long = com.fush.erp.domain.TrustedTimeService.now()"))
        assertTrue(service.contains("usableLots(order.rawWarehouseId, material.itemId, BusinessDatePolicy.endOfBusinessDay(issueDate))"))
        assertTrue(service.contains("movementDate = issueDate"))
        assertTrue(service.contains("issueDate = issueDate"))
        assertTrue(service.contains("requireHistoricalLotOutflowAvailable"))
        assertTrue(service.contains("postMaterialIssueJournal(order, totalIssueCost, createdBy, issueDate)"))
    }

    @Test
    fun manufactureQualityLaborAndReceiptDatesAreIndependentAndOrdered() {
        val service = source("com/fush/erp/domain/ProductionService.kt")
        assertTrue(service.contains("manufactureDate: Long = com.fush.erp.domain.TrustedTimeService.now()"))
        assertTrue(service.contains("checkedAt: Long = com.fush.erp.domain.TrustedTimeService.now()"))
        assertTrue(service.contains("laborDate: Long = receiptDate"))
        assertTrue(service.contains("receiptDate: Long = com.fush.erp.domain.TrustedTimeService.now()"))
        assertTrue(service.contains("تاريخ الإنتاج لا يمكن أن يسبق تاريخ صرف المواد"))
        assertTrue(service.contains("تاريخ فحص الجودة لا يمكن أن يسبق تاريخ التصنيع"))
        assertTrue(service.contains("تاريخ تحميل الأجور لا يمكن أن يسبق تاريخ التصنيع"))
        assertTrue(service.contains("تاريخ إدخال المنتج التام لا يمكن أن يسبق آخر فحص جودة"))
    }

    @Test
    fun expiryAndBatchNumberUseRealManufactureDate() {
        val service = source("com/fush/erp/domain/ProductionService.kt")
        assertTrue(service.contains("val expiryDate = manufactureDate +"))
        assertTrue(service.contains("nextBatchNo(product, manufactureDate)"))
        assertTrue(service.contains("manufactureDate = manufactureDate"))
    }

    @Test
    fun productionJournalsUseEffectiveDatesInsteadOfHiddenNow() {
        val service = source("com/fush/erp/domain/ProductionService.kt")
        assertTrue(service.contains("journalDate: Long"))
        assertTrue(service.contains("entryDate = journalDate"))
        assertTrue(service.contains("nextDocumentNo(sourceType.take(4), journalDate)"))
        assertFalse(service.contains("val journalDate = System.currentTimeMillis()"))
        assertTrue(service.contains("postLaborToWip(order, laborCost, createdBy, laborDate)"))
        assertTrue(service.contains("postFinishedGoodsJournal(order, batch, totalCost, createdBy, movementId, receiptDate)"))
    }

    @Test
    fun auditCreationTimestampsRemainSeparateFromEffectiveDates() {
        val entities = source("com/fush/erp/data/entity/ProductionEntities.kt")
        assertTrue(entities.contains("val createdAt: Long = com.fush.erp.domain.TrustedTimeService.now()"))
        assertTrue(entities.contains("val issueDate: Long = com.fush.erp.domain.TrustedTimeService.now()"))
        assertTrue(entities.contains("val checkedAt: Long = com.fush.erp.domain.TrustedTimeService.now()"))
    }
}
