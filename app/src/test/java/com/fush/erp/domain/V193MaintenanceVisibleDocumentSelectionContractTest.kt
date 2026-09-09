package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V193MaintenanceVisibleDocumentSelectionContractTest {
    private val root = File(System.getProperty("user.dir"))
    private fun source(path: String) = File(root, "src/main/java/$path").readText()

    @Test
    fun adminCleanupUsesVisibleBusinessReferencesInsteadOfHiddenIds() {
        val screen = source("com/fush/erp/ui/screens/SupportCenterScreen.kt")
        assertTrue(screen.contains("فاتورة البيع — ابحث برقم الفاتورة الظاهر"))
        assertTrue(screen.contains("JE-${'$'}{it.invoiceNo}"))
        assertTrue(screen.contains("مصروف الشحنة — ابحث برقم الشحنة أو سند الصرف"))
        assertTrue(screen.contains("it.expense.paymentVoucherNo"))
        assertTrue(screen.contains("selectedCleanupInvoice"))
        assertTrue(screen.contains("selectedCleanupExpense"))
        assertFalse(screen.contains("label = { Text(\"Invoice ID أو Shipment Expense ID\") }"))
    }

    @Test
    fun cleanupStillExecutesTypedServiceWithResolvedInternalId() {
        val screen = source("com/fush/erp/ui/screens/SupportCenterScreen.kt")
        assertTrue(screen.contains("deleteTestSalesInvoiceBundle(user.id, selectedSession!!.id, invoice.id, reason)"))
        assertTrue(screen.contains("deleteTestShipmentExpense(user.id, selectedSession!!.id, option.expense.id, reason)"))
        val support = source("com/fush/erp/domain/SupportService.kt")
        assertTrue(support.contains("requireTestCleanupContext(userId, sessionId)"))
        assertTrue(support.contains("requireTestDeletionReason(reason)"))
    }

    @Test
    fun v193MetadataPreservesUpgradeIdentity() {
        val gradle = File(root, "build.gradle.kts").readText()
        assertTrue(Regex("versionCode\\s*=\\s*(\\d+)").find(gradle)?.groupValues?.get(1)?.toInt() ?: 0 >= 193)
    }
}
