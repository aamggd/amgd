package com.fush.erp.ui.screens

import com.fush.erp.data.entity.ExpenseReportRow
import com.fush.erp.data.entity.JournalDetailRow
import com.fush.erp.data.entity.JournalEntryEntity
import com.fush.erp.data.entity.JournalHeaderRow
import com.fush.erp.data.entity.TreasuryMovementReportRow
import com.fush.erp.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountingSectionExportTest {
    @Test
    fun financialSectionBuildersProduceProfessionalDocuments() {
        val ledger = buildLedgerSectionExportDocument(
            "1101", "الصندوق", "2026-08-01", "2026-08-31",
            LedgerReport(
                openingBalance = 100.0,
                lines = listOf(LedgerReportLine(1, "JE-1", 1_754_006_400_000, "قبض", "VOUCHER", 50.0, 0.0, "", 150.0)),
                closingBalance = 150.0
            )
        )
        assertEquals("دفتر الأستاذ العام — Fush ERP", ledger.title)
        assertEquals(1, ledger.tables.size)
        assertTrue(ledger.summary.any { it.first == "الرصيد الختامي" })

        val trial = buildTrialBalanceSectionExportDocument(
            "2026-08-31",
            TrialBalanceReport(
                lines = listOf(TrialBalanceLine(1, "1101", "الصندوق", "ASSET", 100.0, 0.0, 100.0, 0.0)),
                totalDebitMovement = 100.0,
                totalCreditMovement = 100.0,
                totalDebitBalance = 100.0,
                totalCreditBalance = 100.0
            )
        )
        assertEquals(7, trial.tables.single().headers.size)

        val pnl = buildProfitLossSectionExportDocument(
            "2026-08-01", "2026-08-31",
            ProfitLossReport(1000.0, 600.0, 400.0, listOf("المبيعات" to 1000.0), listOf("المصروفات" to 600.0))
        )
        assertEquals(2, pnl.tables.size)

        val balance = buildBalanceSheetSectionExportDocument(
            "2026-08-31",
            BalanceSheetReport(
                assets = 1000.0,
                liabilities = 300.0,
                equityBeforeCurrentProfit = 300.0,
                currentProfit = 400.0,
                totalLiabilitiesAndEquity = 1000.0,
                difference = 0.0,
                assetsByAccount = listOf("الصندوق" to 1000.0),
                liabilitiesByAccount = listOf("الموردون" to 300.0),
                equityByAccount = listOf("رأس المال" to 300.0)
            )
        )
        assertEquals(3, balance.tables.size)
        assertTrue(balance.notes.single().contains("متوازنة"))

        val cash = buildCashFlowSectionExportDocument(
            "2026-08-01", "2026-08-31",
            CashFlowReport(100.0, 500.0, 200.0, 300.0, 400.0)
        )
        assertEquals(5, cash.tables.single().rows.size)
    }

    @Test
    fun treasuryAndExpenseDocumentsPreserveOperationalMeaning() {
        val treasuryReport = TreasuryPeriodReport(
            accounts = listOf(
                TreasuryAccountReportRow(1, "CASH-1", "الصندوق", "CASH", "YER_NEW", "", "", 100.0, 300.0, 50.0, 20.0, 0.0, 370.0)
            ),
            movements = listOf(
                TreasuryMovementReportRow(1, "CASH-1", "الصندوق", "CASH", "YER_NEW", "", "", 1, "JE-1", 1_754_006_400_000, "تحويل داخلي", "TRANSFER", 20.0, 0.0, true),
                TreasuryMovementReportRow(1, "CASH-1", "الصندوق", "CASH", "YER_NEW", "", "", 2, "JE-2", 1_754_006_400_000, "قبض عميل", "VOUCHER_RECEIPT", 300.0, 0.0, false)
            ),
            openingBase = 100.0,
            externalInBase = 300.0,
            externalOutBase = 50.0,
            transferInBase = 20.0,
            transferOutBase = 0.0,
            closingBase = 370.0
        )
        val treasuryDoc = buildTreasuryPeriodSectionExportDocument(treasuryReport, "2026-08-01", "2026-08-31")
        assertEquals(2, treasuryDoc.tables.size)
        assertEquals("تحويل داخلي", treasuryDoc.tables[1].rows.first()[3])

        val expense = ExpenseReportRow(
            expenseId = 1,
            voucherId = 1,
            voucherNo = "EXP-1",
            voucherDate = 1_754_006_400_000,
            expenseAccountId = 9,
            expenseAccountCode = "5101",
            expenseAccountName = "مواصلات",
            amountBase = 60_000.0,
            description = "مواصلات توزيع",
            currencyCode = "YER_NEW",
            amountOriginal = 60_000.0,
            paymentMethod = "CASH",
            employeeId = 2,
            employeeName = "أحمد",
            salesRepId = null,
            salesRepName = "",
            costCenterCode = "DISTRIBUTION",
            costCenterName = "التوزيع",
            organizationUnit = "تعز",
            referenceType = "OTHER",
            referenceId = null,
            referenceNo = "REF-1",
            referenceLabel = "رحلة توزيع",
            customerId = null,
            customerName = "",
            supplierId = null,
            supplierName = "",
            itemId = null,
            itemName = "",
            attachmentCount = 1
        )
        val expenseDoc = buildExpenseSectionExportDocument(listOf(expense), listOf("الموظف" to "أحمد"))
        assertTrue(expenseDoc.summary.any { it.first == "الموظف" && it.second == "أحمد" })
        assertEquals(3, expenseDoc.tables.size)
    }

    @Test
    fun journalAndManualJournalDocumentsContainEntryDetails() {
        val header = JournalHeaderRow(
            id = 1,
            entryNo = "JE-100",
            entryDate = 1_754_006_400_000,
            description = "قيد اختبار",
            currencyCode = "YER_NEW",
            exchangeRate = 1.0,
            sourceType = "MANUAL",
            sourceId = null,
            createdBy = 1,
            debitTotal = 100.0,
            creditTotal = 100.0,
            isReversed = false
        )
        val details = listOf(
            JournalDetailRow(1, "JE-100", header.entryDate, "قيد اختبار", "MANUAL", 1, "1101", "الصندوق", "ASSET", 100.0, 0.0, ""),
            JournalDetailRow(1, "JE-100", header.entryDate, "قيد اختبار", "MANUAL", 2, "4101", "إيراد", "REVENUE", 0.0, 100.0, "")
        )
        val journalDoc = buildJournalSectionExportDocument(listOf(header), "كل القيود")
        assertEquals(1, journalDoc.tables.single().rows.size)

        val entry = JournalEntryEntity(
            id = 1,
            entryNo = "JE-100",
            entryDate = header.entryDate,
            description = "قيد اختبار",
            currencyCode = "YER_NEW",
            exchangeRate = 1.0,
            sourceType = "MANUAL",
            sourceId = null,
            status = "POSTED",
            createdBy = 1
        )
        val manualDoc = buildManualJournalExportDocument(entry, details)
        assertEquals("سند قيد يدوي — Fush ERP", manualDoc.title)
        assertEquals(2, manualDoc.tables.single().rows.size)
    }
}
