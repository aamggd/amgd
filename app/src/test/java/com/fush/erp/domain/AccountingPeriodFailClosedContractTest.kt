package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountingPeriodFailClosedContractTest {
    @Test
    fun databaseGuardIsInstalledAtColdOpen() {
        val source = dataSource("AccountingDatabaseGuardInitializer.kt").readText()
        assertTrue(
            "Fail-closed period database guard must be installed before database exposure",
            source.contains("AccountingPeriodDatabaseGuard.install(db)")
        )
    }

    @Test
    fun databaseGuardRejectsPostedJournalWithoutOpenPeriod() {
        val source = dataSource("AccountingPeriodDatabaseGuard.kt").readText()
        assertTrue(source.contains("BEFORE INSERT ON journal_entries"))
        assertTrue(source.contains("BEFORE UPDATE OF status, entryDate, sourceType, sourceId ON journal_entries"))
        assertTrue(source.contains("NEW.status"))
        assertTrue(source.contains("NOT EXISTS("))
        assertTrue(source.contains("NEW.entryDate BETWEEN ap.startDate AND ap.endDate"))
        assertTrue(source.contains("ap.status = 'OPEN'"))
        assertTrue(source.contains("ACCOUNTING_PERIOD_NOT_OPEN"))
    }

    @Test
    fun centralServiceGateCannotSilentlyReturnWhenPeriodIsMissing() {
        val source = domainSource("AccountingService.kt").readText()
        val block = functionBlock(source, "suspend fun requirePostingPeriodOpen(")
        assertTrue(block.contains("periodForDate(entryDate)"))
        assertTrue(block.contains("period.status == \"OPEN\""))
        assertTrue(
            "Missing period must fail closed; the gate must never use ?: return",
            !block.contains("?: return")
        )
    }

    @Test
    fun salesPurchaseInventoryAndProductionGateBeforeBusinessMutation() {
        assertGateBeforeMutation("SalesService.kt", "suspend fun postSale(")
        assertGateBeforeMutation("SalesService.kt", "private suspend fun postReceiptAllocationsInternal(")
        assertGateBeforeMutation("SalesService.kt", "suspend fun postReturn(")

        assertGateBeforeMutation("PurchaseService.kt", "suspend fun postPurchase(")
        assertGateBeforeMutation("PurchaseService.kt", "suspend fun postPurchaseReturn(")
        assertGateBeforeMutation("PurchaseService.kt", "private suspend fun postSupplierPaymentAllocationsInternal(")

        assertGateBeforeMutation("InventoryService.kt", "suspend fun postOpeningStock(")

        assertGateBeforeMutation("ProductionService.kt", "suspend fun issueReservedMaterials(")
        assertGateBeforeMutation("ProductionService.kt", "suspend fun correctMaterialIssue(")
        assertGateBeforeMutation("ProductionService.kt", "suspend fun acceptBatch(")
        assertGateBeforeMutation("ProductionService.kt", "suspend fun rejectBatch(")
    }

    private fun assertGateBeforeMutation(fileName: String, marker: String) {
        val source = domainSource(fileName).readText()
        val block = functionBlock(source, marker)
        val gate = block.indexOf("requirePostingPeriodOpen")
        val insert = Regex("\\.insert[A-Z][A-Za-z0-9_]*\\(").find(block)?.range?.first ?: Int.MAX_VALUE
        val update = Regex("\\.update[A-Z][A-Za-z0-9_]*\\(").find(block)?.range?.first ?: Int.MAX_VALUE
        val firstMutation = minOf(insert, update)
        assertTrue("$fileName::$marker must use the central period gate", gate >= 0)
        assertTrue("$fileName::$marker mutates before period validation", gate < firstMutation)
    }

    private fun functionBlock(source: String, marker: String): String {
        val start = source.indexOf(marker)
        assertTrue("Missing function $marker", start >= 0)
        val candidates = listOf(
            source.indexOf("\n    suspend fun ", start + marker.length),
            source.indexOf("\n    private suspend fun ", start + marker.length),
            source.indexOf("\n    data class ", start + marker.length),
            source.indexOf("\n    private data class ", start + marker.length)
        ).filter { it >= 0 }
        val end = candidates.minOrNull() ?: source.length
        return source.substring(start, end)
    }

    private fun domainSource(fileName: String): File {
        val relative = "src/main/java/com/fush/erp/domain/$fileName"
        return listOf(File(relative), File("app/$relative")).firstOrNull { it.isFile }
            ?: error("Cannot locate $relative")
    }

    private fun dataSource(fileName: String): File {
        val relative = "src/main/java/com/fush/erp/data/$fileName"
        return listOf(File(relative), File("app/$relative")).firstOrNull { it.isFile }
            ?: error("Cannot locate $relative")
    }
}
