package com.fush.erp.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class V153TreasuryAccountCreationContractTest {
    private fun source(path: String) = File(path).takeIf { it.exists() } ?: File("../$path")

    @Test fun addTreasuryDialogFiltersLinkedAccountsAndDoesNotAutoSelectFirstAccount() {
        val text = source("app/src/main/java/com/fush/erp/ui/screens/AccountingScreens.kt").readText()
        assertTrue(text.contains("TreasuryLedgerAccountPolicy.availableAccounts(accounts, linkedAccountIds)"))
        assertTrue(text.contains("اختر حساباً غير مستخدم"))
        assertFalse(text.contains("if (account == null) account = assetAccounts.firstOrNull()"))
    }

    @Test fun newLedgerAndTreasuryAreCreatedAtomicallyWithBothPermissions() {
        val text = source("app/src/main/java/com/fush/erp/domain/AccountingService.kt").readText()
        val start = text.indexOf("suspend fun addTreasuryAccountWithNewLedger")
        val end = text.indexOf("suspend fun postManualJournal", start)
        val method = text.substring(start, end)
        assertTrue(method.contains("db.withTransaction"))
        assertTrue(method.contains("SecurityPermissions.TREASURY_POST"))
        assertTrue(method.contains("SecurityPermissions.ACCOUNTING_POST"))
        assertTrue(method.contains("db.accountDao().insert"))
        assertTrue(method.contains("db.accountingDao().insertTreasury"))
    }
}
