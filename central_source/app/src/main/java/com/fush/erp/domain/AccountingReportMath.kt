package com.fush.erp.domain

import kotlin.math.abs

data class ReportDetail(
    val accountId: Long,
    val accountCode: String,
    val accountNameAr: String,
    val accountType: String,
    val entryId: Long,
    val entryNo: String,
    val entryDate: Long,
    val description: String,
    val sourceType: String,
    val debit: Double,
    val credit: Double,
    val memo: String = ""
)

data class TrialBalanceLine(
    val accountId: Long,
    val code: String,
    val nameAr: String,
    val type: String,
    val debitMovement: Double,
    val creditMovement: Double,
    val debitBalance: Double,
    val creditBalance: Double
)

data class TrialBalanceReport(
    val lines: List<TrialBalanceLine>,
    val totalDebitMovement: Double,
    val totalCreditMovement: Double,
    val totalDebitBalance: Double,
    val totalCreditBalance: Double
)

data class LedgerReportLine(
    val entryId: Long,
    val entryNo: String,
    val entryDate: Long,
    val description: String,
    val sourceType: String,
    val debit: Double,
    val credit: Double,
    val memo: String,
    val runningBalance: Double
)

data class LedgerReport(
    val openingBalance: Double,
    val lines: List<LedgerReportLine>,
    val closingBalance: Double
)

data class ProfitLossReport(
    val revenue: Double,
    val expenses: Double,
    val netProfit: Double,
    val revenueByAccount: List<Pair<String, Double>>,
    val expenseByAccount: List<Pair<String, Double>>
)

data class BalanceSheetReport(
    val assets: Double,
    val liabilities: Double,
    val equityBeforeCurrentProfit: Double,
    val currentProfit: Double,
    val totalLiabilitiesAndEquity: Double,
    val difference: Double,
    val assetsByAccount: List<Pair<String, Double>>,
    val liabilitiesByAccount: List<Pair<String, Double>>,
    val equityByAccount: List<Pair<String, Double>>
)

data class CashFlowReport(
    val openingCash: Double,
    val cashInflows: Double,
    val cashOutflows: Double,
    val netCashMovement: Double,
    val closingCash: Double
)

object AccountingReportMath {
    fun trialBalance(details: List<ReportDetail>): TrialBalanceReport {
        val rows = details.groupBy { it.accountId }.values.map { group ->
            val first = group.first()
            val debit = group.sumOf { it.debit }
            val credit = group.sumOf { it.credit }
            val raw = debit - credit
            TrialBalanceLine(
                accountId = first.accountId,
                code = first.accountCode,
                nameAr = first.accountNameAr,
                type = first.accountType,
                debitMovement = debit,
                creditMovement = credit,
                debitBalance = if (raw > 0) raw else 0.0,
                creditBalance = if (raw < 0) -raw else 0.0
            )
        }.sortedBy { it.code }
        return TrialBalanceReport(
            lines = rows,
            totalDebitMovement = rows.sumOf { it.debitMovement },
            totalCreditMovement = rows.sumOf { it.creditMovement },
            totalDebitBalance = rows.sumOf { it.debitBalance },
            totalCreditBalance = rows.sumOf { it.creditBalance }
        )
    }

    fun ledger(allDetailsThroughEnd: List<ReportDetail>, accountId: Long, fromDate: Long, toDate: Long): LedgerReport {
        val accountRows = allDetailsThroughEnd.filter { it.accountId == accountId && it.entryDate <= toDate }
        val opening = accountRows.filter { it.entryDate < fromDate }.sumOf { it.debit - it.credit }
        var running = opening
        val lines = accountRows.filter { it.entryDate in fromDate..toDate }
            .sortedWith(compareBy<ReportDetail> { it.entryDate }.thenBy { it.entryId })
            .map { row ->
                running += row.debit - row.credit
                LedgerReportLine(
                    entryId = row.entryId,
                    entryNo = row.entryNo,
                    entryDate = row.entryDate,
                    description = row.description,
                    sourceType = row.sourceType,
                    debit = row.debit,
                    credit = row.credit,
                    memo = row.memo,
                    runningBalance = running
                )
            }
        return LedgerReport(opening, lines, running)
    }

    fun profitLoss(details: List<ReportDetail>): ProfitLossReport {
        fun netFor(type: String, group: List<ReportDetail>): Double = when (type) {
            "REVENUE" -> group.sumOf { it.credit - it.debit }
            "EXPENSE" -> group.sumOf { it.debit - it.credit }
            else -> 0.0
        }
        val revenueGroups = details.filter { it.accountType == "REVENUE" }.groupBy { it.accountId }
        val expenseGroups = details.filter { it.accountType == "EXPENSE" }.groupBy { it.accountId }
        val revenues = revenueGroups.values.map { g -> g.first().let { it.accountNameAr to netFor("REVENUE", g) } }
            .filter { abs(it.second) > 0.000001 }.sortedBy { it.first }
        val expenses = expenseGroups.values.map { g -> g.first().let { it.accountNameAr to netFor("EXPENSE", g) } }
            .filter { abs(it.second) > 0.000001 }.sortedBy { it.first }
        val revenue = revenues.sumOf { it.second }
        val expense = expenses.sumOf { it.second }
        return ProfitLossReport(revenue, expense, revenue - expense, revenues, expenses)
    }

    fun balanceSheet(detailsThroughDate: List<ReportDetail>): BalanceSheetReport {
        fun accountBalance(type: String, group: List<ReportDetail>): Double = when (type) {
            "ASSET", "EXPENSE" -> group.sumOf { it.debit - it.credit }
            "LIABILITY", "EQUITY", "REVENUE" -> group.sumOf { it.credit - it.debit }
            else -> 0.0
        }
        fun breakdown(type: String): List<Pair<String, Double>> = detailsThroughDate
            .filter { it.accountType == type }
            .groupBy { it.accountId }
            .values
            .map { g -> g.first().accountNameAr to accountBalance(type, g) }
            .filter { abs(it.second) > 0.000001 }
            .sortedBy { it.first }

        val assetsRows = breakdown("ASSET")
        val liabilityRows = breakdown("LIABILITY")
        val equityRows = breakdown("EQUITY")
        val pnl = profitLoss(detailsThroughDate)
        val assets = assetsRows.sumOf { it.second }
        val liabilities = liabilityRows.sumOf { it.second }
        val equity = equityRows.sumOf { it.second }
        val totalRight = liabilities + equity + pnl.netProfit
        return BalanceSheetReport(
            assets = assets,
            liabilities = liabilities,
            equityBeforeCurrentProfit = equity,
            currentProfit = pnl.netProfit,
            totalLiabilitiesAndEquity = totalRight,
            difference = assets - totalRight,
            assetsByAccount = assetsRows,
            liabilitiesByAccount = liabilityRows,
            equityByAccount = equityRows
        )
    }

    fun cashFlow(
        detailsThroughEnd: List<ReportDetail>,
        treasuryAccountIds: Set<Long>,
        fromDate: Long,
        toDate: Long
    ): CashFlowReport {
        val treasuryRows = detailsThroughEnd.filter { it.accountId in treasuryAccountIds && it.entryDate <= toDate }
        val opening = treasuryRows.filter { it.entryDate < fromDate }.sumOf { it.debit - it.credit }
        val period = treasuryRows.filter { it.entryDate in fromDate..toDate }
        // Internal transfers between treasury accounts do not represent business cash inflow/outflow.
        val external = period.filter { it.sourceType != "TREASURY_TRANSFER" }
        val inflows = external.sumOf { it.debit }
        val outflows = external.sumOf { it.credit }
        val closing = opening + period.sumOf { it.debit - it.credit }
        return CashFlowReport(opening, inflows, outflows, inflows - outflows, closing)
    }
}
