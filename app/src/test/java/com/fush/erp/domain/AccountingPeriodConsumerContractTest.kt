package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountingPeriodConsumerContractTest {
    @Test
    fun accountingTreasurySalesPurchasesAndAssetsGateBeforeMutation() {
        assertGateBeforeFirstInsert("AccountingService.kt", "suspend fun postManualJournal(")
        assertGateBeforeFirstInsert("AccountingService.kt", "suspend fun postVoucher(")
        assertGateBeforeFirstInsert("AccountingService.kt", "suspend fun reverseEntry(")
        assertGateBeforeFirstInsert("SalesService.kt", "suspend fun reverseReceipt(")
        assertGateBeforeFirstInsert("PurchaseService.kt", "suspend fun reverseSupplierPayment(")
        assertGateBeforeFirstInsert("FixedAssetService.kt", "suspend fun registerAsset(")
        assertGateBeforeFirstInsert("FixedAssetService.kt", "suspend fun reverseDepreciation(")
        assertGateBeforeFirstInsert("FixedAssetService.kt", "suspend fun disposeAsset(")
        assertGateBeforeFirstInsert("FixedAssetService.kt", "suspend fun reverseDisposal(")
        assertGateBeforeFirstInsert("FixedAssetService.kt", "suspend fun cancelAcquisition(")
        assertGateBeforeFirstInsert("FixedAssetService.kt", "private suspend fun postDepreciationInternal(")
    }

    private fun assertGateBeforeFirstInsert(fileName: String, functionMarker: String) {
        val source = domainSource(fileName).readText()
        val start = source.indexOf(functionMarker)
        assertTrue("Missing function $functionMarker in $fileName", start >= 0)
        val nextPublic = source.indexOf("\n    suspend fun ", start + functionMarker.length)
            .takeIf { it >= 0 }
            ?: Int.MAX_VALUE
        val nextPrivate = source.indexOf("\n    private suspend fun ", start + functionMarker.length)
            .takeIf { it >= 0 }
            ?: Int.MAX_VALUE
        val end = minOf(nextPublic, nextPrivate, source.length)
        val block = source.substring(start, end)
        val gate = block.indexOf("requirePostingPeriodOpen")
        val firstInsert = Regex("\\.insert[A-Z][A-Za-z0-9_]*\\(").find(block)?.range?.first ?: Int.MAX_VALUE
        assertTrue("$fileName::$functionMarker must use the central period gate", gate >= 0)
        assertTrue("$fileName::$functionMarker mutates before period validation", gate < firstInsert)
    }

    private fun domainSource(fileName: String): File {
        val relative = "src/main/java/com/fush/erp/domain/$fileName"
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.firstOrNull { it.isFile }
            ?: error("Cannot locate $relative from ${File(".").absolutePath}")
    }
}
