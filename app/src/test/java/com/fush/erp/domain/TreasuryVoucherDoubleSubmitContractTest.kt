package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TreasuryVoucherDoubleSubmitContractTest {
    @Test
    fun accountingServiceUsesStableOperationSourceInsteadOfFreshUuid() {
        val source = sourceFile("app/src/main/java/com/fush/erp/domain/AccountingService.kt")
        val postVoucher = source.substringAfter("suspend fun postVoucher(request: VoucherRequest)")
            .substringBefore("suspend fun reverseEntry")
        assertTrue(postVoucher.contains("TreasuryVoucherOperationIdentity.sourceId(request.operationId)"))
        assertTrue(postVoucher.contains("bySource(sourceType, operationSourceId)"))
        assertTrue(postVoucher.contains("return@withTransaction existing.id"))
        assertFalse(postVoucher.contains("sourceId = UUID.randomUUID().toString()"))
    }

    @Test
    fun allActiveVoucherDialogsCarryOperationIdAndBusyGuard() {
        val expense = sourceFile("app/src/main/java/com/fush/erp/ui/screens/ExpenseScreens.kt")
        val accounting = sourceFile("app/src/main/java/com/fush/erp/ui/screens/AccountingScreens.kt")
        val reps = sourceFile("app/src/main/java/com/fush/erp/ui/screens/SalesRepresentativeScreens.kt")
        listOf(expense, accounting, reps).forEach { source ->
            assertTrue(source.contains("operationId = operationId"))
            assertTrue(source.contains("isPosting"))
            assertTrue(source.contains("TreasuryVoucherOperationIdentity.newOperationId()"))
        }
    }

    private fun sourceFile(relativePath: String): String {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .firstOrNull { File(it, relativePath).isFile }
            ?: error("Project root not found for $relativePath")
        return File(root, relativePath).readText()
    }
}
