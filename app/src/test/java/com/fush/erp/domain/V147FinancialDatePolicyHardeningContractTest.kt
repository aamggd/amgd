package com.fush.erp.domain

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class V147FinancialDatePolicyHardeningContractTest {
    private fun source(relative: String): String {
        val root = File(System.getProperty("user.dir"))
        return File(root, "src/main/java/$relative").readText()
    }

    @Test fun accountingPostingDatesAreGuardedAgainstFutureDays() {
        val body = source("com/fush/erp/domain/AccountingService.kt")
        listOf(
            "FutureDocumentDatePolicy.requireNotFuture(entryDate, \"تاريخ القيد\")",
            "FutureDocumentDatePolicy.requireNotFuture(request.voucherDate, \"تاريخ السند\")",
            "FutureDocumentDatePolicy.requireNotFuture(reversalDate, \"تاريخ عكس القيد\")",
            "FutureDocumentDatePolicy.requireNotFuture(countDate, \"تاريخ جرد الصندوق\")",
            "FutureDocumentDatePolicy.requireNotFuture(endDate, \"تاريخ نهاية كشف البنك\")"
        ).forEach { assertTrue("Missing guard: $it", body.contains(it)) }
    }

    @Test fun fixedAssetPostingDatesAreGuardedAgainstFutureDays() {
        val body = source("com/fush/erp/domain/FixedAssetService.kt")
        listOf(
            "FutureDocumentDatePolicy.requireNotFuture(request.acquisitionDate, \"تاريخ اقتناء الأصل\")",
            "FutureDocumentDatePolicy.requireNotFuture(reversalDate, \"تاريخ عكس الإهلاك\")",
            "FutureDocumentDatePolicy.requireNotFuture(request.disposalDate, \"تاريخ استبعاد الأصل\")",
            "FutureDocumentDatePolicy.requireNotFuture(reversalDate, \"تاريخ عكس استبعاد الأصل\")",
            "FutureDocumentDatePolicy.requireNotFuture(reversalDate, \"تاريخ إلغاء اقتناء الأصل\")"
        ).forEach { assertTrue("Missing guard: $it", body.contains(it)) }
    }

    @Test fun openingStockHasExplicitEffectiveDateInServiceAndBothUis() {
        val service = source("com/fush/erp/domain/InventoryService.kt")
        assertTrue(service.contains("postingDate: Long = TrustedTimeService.now()"))
        assertTrue(service.contains("FutureDocumentDatePolicy.requireNotFuture(postingDate, \"تاريخ الرصيد الافتتاحي\")"))
        val home = source("com/fush/erp/ui/screens/HomeShell.kt")
        val advanced = source("com/fush/erp/ui/screens/AdvancedInventoryScreens.kt")
        assertTrue(home.contains("تاريخ الرصيد الافتتاحي"))
        assertTrue(advanced.contains("تاريخ الرصيد الافتتاحي"))
        assertTrue(home.contains("postOpeningStock(warehouse.id, item.id, qty, cost, user.id, note, postingDate)"))
        assertTrue(advanced.contains("postOpeningStock(warehouse.id, item.id, qty, cost, user.id, note, postingDate)"))
    }
}
