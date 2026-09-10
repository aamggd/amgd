package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountingReportMathTest {
    private fun row(
        accountId: Long, code: String, name: String, type: String,
        debit: Double, credit: Double, date: Long = 1000L,
        source: String = "MANUAL", entryId: Long = accountId
    ) = ReportDetail(accountId, code, name, type, entryId, "JE-$entryId", date, "test", source, debit, credit)

    @Test fun trial_balance_stays_balanced() {
        val report = AccountingReportMath.trialBalance(
            listOf(
                row(1, "1100", "الصندوق", "ASSET", 1000.0, 0.0),
                row(2, "4000", "المبيعات", "REVENUE", 0.0, 1000.0)
            )
        )
        assertEquals(1000.0, report.totalDebitMovement, 0.001)
        assertEquals(1000.0, report.totalCreditMovement, 0.001)
        assertEquals(report.totalDebitBalance, report.totalCreditBalance, 0.001)
    }

    @Test fun profit_and_loss_handles_sales_returns_and_expenses() {
        val report = AccountingReportMath.profitLoss(
            listOf(
                row(1, "4000", "المبيعات", "REVENUE", 0.0, 4000.0),
                row(2, "4100", "مردودات المبيعات", "REVENUE", 500.0, 0.0),
                row(3, "6100", "الإيجار", "EXPENSE", 1000.0, 0.0)
            )
        )
        assertEquals(3500.0, report.revenue, 0.001)
        assertEquals(1000.0, report.expenses, 0.001)
        assertEquals(2500.0, report.netProfit, 0.001)
    }

    @Test fun balance_sheet_includes_current_profit_in_equity() {
        val report = AccountingReportMath.balanceSheet(
            listOf(
                row(1, "1100", "الصندوق", "ASSET", 1500.0, 0.0),
                row(2, "3001", "رأس المال", "EQUITY", 0.0, 1000.0),
                row(3, "4000", "المبيعات", "REVENUE", 0.0, 700.0),
                row(4, "6100", "مصروف", "EXPENSE", 200.0, 0.0)
            )
        )
        assertEquals(1500.0, report.assets, 0.001)
        assertEquals(500.0, report.currentProfit, 0.001)
        assertEquals(1500.0, report.totalLiabilitiesAndEquity, 0.001)
        assertTrue(kotlin.math.abs(report.difference) < 0.001)
    }

    @Test fun cash_flow_excludes_internal_transfers_from_external_inflow_and_outflow() {
        val report = AccountingReportMath.cashFlow(
            listOf(
                row(1, "1100", "صندوق", "ASSET", 1000.0, 0.0, 500L, "TREASURY_RECEIPT", 1),
                row(2, "1150", "بنك", "ASSET", 300.0, 0.0, 1500L, "TREASURY_TRANSFER", 2),
                row(1, "1100", "صندوق", "ASSET", 0.0, 300.0, 1500L, "TREASURY_TRANSFER", 2),
                row(2, "1150", "بنك", "ASSET", 100.0, 0.0, 1550L, "TRANSFER", 4),
                row(1, "1100", "صندوق", "ASSET", 0.0, 100.0, 1550L, "TRANSFER", 4),
                row(1, "1100", "صندوق", "ASSET", 0.0, 200.0, 1600L, "TREASURY_EXPENSE", 3)
            ),
            treasuryAccountIds = setOf(1, 2),
            fromDate = 1000L,
            toDate = 2000L
        )
        assertEquals(1000.0, report.openingCash, 0.001)
        assertEquals(0.0, report.cashInflows, 0.001)
        assertEquals(200.0, report.cashOutflows, 0.001)
        assertEquals(800.0, report.closingCash, 0.001)
    }

    @Test fun ledger_carries_opening_and_running_balance() {
        val report = AccountingReportMath.ledger(
            listOf(
                row(1, "1100", "الصندوق", "ASSET", 500.0, 0.0, 500L, entryId = 1),
                row(1, "1100", "الصندوق", "ASSET", 250.0, 0.0, 1200L, entryId = 2),
                row(1, "1100", "الصندوق", "ASSET", 0.0, 100.0, 1300L, entryId = 3)
            ),
            1, 1000L, 2000L
        )
        assertEquals(500.0, report.openingBalance, 0.001)
        assertEquals(650.0, report.closingBalance, 0.001)
        assertEquals(2, report.lines.size)
    }
}
