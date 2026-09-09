package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SalesReceiptReversalPermissionGuardTest {
    @Test
    fun reverseReceiptRequiresCollectionPermissionBeforeAnyMutation() {
        val source = sourceFile("com/fush/erp/domain/SalesService.kt").readText()
        val block = functionBlock(source, "suspend fun reverseReceipt(")
        val permissionGate = block.indexOf("db.requireUserPermission(createdBy, SecurityPermissions.COLLECTION_POST)")
        val periodGate = block.indexOf("requirePostingPeriodOpen(reversalDate)")
        val firstInsert = Regex("\\.insert[A-Z][A-Za-z0-9_]*\\(").find(block)?.range?.first ?: Int.MAX_VALUE

        assertTrue("reverseReceipt must require COLLECTION_POST", permissionGate >= 0)
        assertTrue("COLLECTION_POST must be checked before the accounting-period gate", permissionGate < periodGate)
        assertTrue("COLLECTION_POST must be checked before any mutation", permissionGate < firstInsert)
    }

    @Test
    fun salesInvoiceDetailsHideAndBlockReversalWithoutCollectionPermission() {
        val source = sourceFile("com/fush/erp/ui/screens/SalesScreens.kt").readText()
        assertTrue(source.contains("SecurityPermissions.COLLECTION_POST in rolePermissions"))
        assertTrue(source.contains("if (canReverseCollection && !isReversal && !isReversedOriginal)"))
        assertTrue(source.contains("if (canReverseCollection) reverseReceipt = receipt"))
        assertTrue(source.contains("if (canReverseCollection) reverseReceipt?.let { receipt ->"))
    }

    private fun functionBlock(source: String, marker: String): String {
        val start = source.indexOf(marker)
        require(start >= 0) { "Missing function $marker" }
        val nextPublic = source.indexOf("\n    suspend fun ", start + marker.length).takeIf { it >= 0 } ?: Int.MAX_VALUE
        val nextPrivate = source.indexOf("\n    private suspend fun ", start + marker.length).takeIf { it >= 0 } ?: Int.MAX_VALUE
        return source.substring(start, minOf(nextPublic, nextPrivate, source.length))
    }

    private fun sourceFile(relative: String): File {
        val candidates = listOf(File("src/main/java/$relative"), File("app/src/main/java/$relative"))
        return candidates.firstOrNull { it.isFile }
            ?: error("Cannot locate $relative from ${File(".").absolutePath}")
    }
}
