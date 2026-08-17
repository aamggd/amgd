package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountingReconciliationMathTest {
    private fun detail(code: String, debit: Double, credit: Double, type: String) =
        ReportDetail(
            accountId = code.hashCode().toLong(),
            accountCode = code,
            accountNameAr = code,
            accountType = type,
            entryId = 1,
            entryNo = "JE-1",
            entryDate = 1,
            description = "test",
            sourceType = "TEST",
            debit = debit,
            credit = credit
        )

    @Test
    fun natural_balances_use_correct_account_side() {
        val details = listOf(
            detail("1300", 150_000.0, 20_000.0, "ASSET"),
            detail("2100", 10_000.0, 90_000.0, "LIABILITY")
        )
        assertEquals(130_000.0, AccountingReconciliationMath.naturalDebitBalance(details, "1300"), 0.001)
        assertEquals(80_000.0, AccountingReconciliationMath.naturalCreditBalance(details, "2100"), 0.001)
    }

    @Test
    fun row_is_matched_inside_one_cent_tolerance() {
        assertTrue(AccountingReconciliationMath.row("1300", "العملاء", 100.0, 100.005).isMatched)
        assertFalse(AccountingReconciliationMath.row("1300", "العملاء", 100.0, 100.02).isMatched)
    }

    @Test
    fun report_fails_when_any_control_account_or_trial_balance_differs() {
        val ok = AccountingReconciliationMath.row("1300", "العملاء", 500.0, 500.0)
        val bad = AccountingReconciliationMath.row("2100", "الموردون", 700.0, 699.0)

        assertFalse(
            AccountingReconciliationReport(
                asOf = 1,
                rows = listOf(ok, bad),
                trialBalanceDifferenceBase = 0.0
            ).isMatched
        )
        assertFalse(
            AccountingReconciliationReport(
                asOf = 1,
                rows = listOf(ok),
                trialBalanceDifferenceBase = 0.02
            ).isMatched
        )
    }
}
