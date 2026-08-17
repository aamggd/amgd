package com.fush.erp.ui.screens

import com.fush.erp.data.entity.ExpenseReportRow
import com.fush.erp.data.entity.JournalDetailRow
import com.fush.erp.data.entity.JournalEntryEntity
import com.fush.erp.data.entity.JournalHeaderRow
import com.fush.erp.data.entity.TreasuryBalanceRow
import com.fush.erp.domain.BalanceSheetReport
import com.fush.erp.domain.CashFlowReport
import com.fush.erp.domain.ExpenseReportAnalyticsMath
import com.fush.erp.domain.LedgerReport
import com.fush.erp.domain.ProfitLossReport
import com.fush.erp.domain.TreasuryPeriodReport
import com.fush.erp.domain.TrialBalanceReport
import com.fush.erp.ui.export.ReportExportDocument
import com.fush.erp.ui.export.ReportExportTable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

internal fun buildJournalSectionExportDocument(
    rows: List<JournalHeaderRow>,
    filterLabel: String
): ReportExportDocument {
    val active = rows.count { !it.isReversed }
    val reversed = rows.count { it.isReversed }
    return ReportExportDocument(
        title = "دفتر اليومية — Fush ERP",
        subtitle = filterLabel,
        summary = listOf(
            "عدد القيود" to rows.size.toString(),
            "قيود نشطة" to active.toString(),
            "قيود معكوسة" to reversed.toString(),
            "إجمالي المدين" to accountingExportMoney(rows.sumOf { it.debitTotal }),
            "إجمالي الدائن" to accountingExportMoney(rows.sumOf { it.creditTotal })
        ),
        tables = listOf(
            ReportExportTable(
                title = "القيود",
                headers = listOf("التاريخ", "رقم القيد", "البيان", "المصدر", "العملة", "مدين", "دائن", "الحالة"),
                rows = rows.map {
                    listOf(
                        accountingExportDate(it.entryDate),
                        it.entryNo,
                        it.description,
                        it.sourceType,
                        it.currencyCode,
                        accountingExportMoney(it.debitTotal),
                        accountingExportMoney(it.creditTotal),
                        if (it.isReversed) "معكوس" else "مرحّل"
                    )
                }
            )
        ),
        notes = listOf("التقرير يطبع نفس القيود المعروضة بعد تطبيق البحث الحالي داخل شاشة اليومية.")
    )
}

internal fun buildJournalEntryExportDocument(
    entryNo: String,
    entryDate: Long,
    description: String,
    currencyCode: String,
    exchangeRate: Double,
    sourceType: String,
    details: List<JournalDetailRow>,
    title: String = "سند قيد محاسبي — Fush ERP"
): ReportExportDocument {
    val debit = details.sumOf { it.debit }
    val credit = details.sumOf { it.credit }
    return ReportExportDocument(
        title = title,
        subtitle = "$entryNo • ${accountingExportDate(entryDate)}",
        summary = listOf(
            "رقم القيد" to entryNo,
            "التاريخ" to accountingExportDate(entryDate),
            "المصدر" to sourceType,
            "العملة" to currencyCode,
            "سعر الصرف" to accountingExportNumber(exchangeRate),
            "إجمالي المدين" to accountingExportMoney(debit),
            "إجمالي الدائن" to accountingExportMoney(credit),
            "فرق التوازن" to accountingExportMoney(debit - credit)
        ),
        tables = listOf(
            ReportExportTable(
                title = "تفاصيل القيد",
                headers = listOf("الكود", "الحساب", "مدين", "دائن", "ملاحظة"),
                rows = details.map { listOf(it.accountCode, it.accountNameAr, accountingExportMoney(it.debit), accountingExportMoney(it.credit), it.memo) }
            )
        ),
        notes = listOf("البيان: $description")
    )
}

internal fun buildManualJournalExportDocument(
    entry: JournalEntryEntity,
    details: List<JournalDetailRow>
): ReportExportDocument = buildJournalEntryExportDocument(
    entryNo = entry.entryNo,
    entryDate = entry.entryDate,
    description = entry.description,
    currencyCode = entry.currencyCode,
    exchangeRate = entry.exchangeRate,
    sourceType = entry.sourceType,
    details = details,
    title = "سند قيد يدوي — Fush ERP"
)

internal fun buildTreasuryBalancesExportDocument(rows: List<TreasuryBalanceRow>): ReportExportDocument =
    ReportExportDocument(
        title = "أرصدة الخزينة والبنوك — Fush ERP",
        subtitle = "الأرصدة الدفترية الحالية",
        summary = listOf(
            "عدد الحسابات" to rows.size.toString(),
            "إجمالي السيولة" to accountingExportMoney(rows.sumOf { it.balanceBase }),
            "إجمالي الصناديق" to accountingExportMoney(rows.filter { it.kind == "CASH" }.sumOf { it.balanceBase }),
            "إجمالي البنوك" to accountingExportMoney(rows.filter { it.kind == "BANK" }.sumOf { it.balanceBase })
        ),
        tables = listOf(
            ReportExportTable(
                title = "الحسابات النقدية",
                headers = listOf("الكود", "الحساب", "النوع", "العملة", "البنك", "رقم الحساب", "الرصيد"),
                rows = rows.map {
                    listOf(
                        it.code,
                        it.nameAr,
                        if (it.kind == "BANK") "بنك" else "صندوق",
                        it.currencyCode,
                        it.bankName,
                        it.accountNumber,
                        accountingExportMoney(it.balanceBase)
                    )
                }
            )
        )
    )

internal fun buildTreasuryPeriodSectionExportDocument(
    report: TreasuryPeriodReport,
    fromLabel: String,
    toLabel: String
): ReportExportDocument = ReportExportDocument(
    title = "حركة الخزينة والبنوك — Fush ERP",
    subtitle = "الفترة من $fromLabel إلى $toLabel",
    summary = listOf(
        "الرصيد الافتتاحي" to accountingExportMoney(report.openingBase),
        "متحصلات خارجية" to accountingExportMoney(report.externalInBase),
        "مدفوعات خارجية" to accountingExportMoney(report.externalOutBase),
        "تحويلات واردة" to accountingExportMoney(report.transferInBase),
        "تحويلات صادرة" to accountingExportMoney(report.transferOutBase),
        "الرصيد الختامي" to accountingExportMoney(report.closingBase)
    ),
    tables = listOf(
        ReportExportTable(
            title = "ملخص حسب الخزينة",
            headers = listOf("الكود", "الخزينة", "النوع", "العملة", "افتتاحي", "داخل خارجي", "خارج خارجي", "تحويل وارد", "تحويل صادر", "ختامي"),
            rows = report.accounts.map {
                listOf(
                    it.code,
                    it.nameAr,
                    if (it.kind == "BANK") "بنك" else "صندوق",
                    it.currencyCode,
                    accountingExportMoney(it.openingBase),
                    accountingExportMoney(it.externalInBase),
                    accountingExportMoney(it.externalOutBase),
                    accountingExportMoney(it.transferInBase),
                    accountingExportMoney(it.transferOutBase),
                    accountingExportMoney(it.closingBase)
                )
            }
        ),
        ReportExportTable(
            title = "تفاصيل الحركة",
            headers = listOf("التاريخ", "المستند", "الخزينة", "التصنيف", "مدين", "دائن", "المصدر", "البيان"),
            rows = report.movements.map {
                listOf(
                    accountingExportDate(it.entryDate),
                    it.entryNo,
                    it.treasuryName,
                    if (it.isInternalTransfer) "تحويل داخلي" else "حركة خارجية",
                    accountingExportMoney(it.debitBase),
                    accountingExportMoney(it.creditBase),
                    it.sourceType,
                    it.description
                )
            }
        )
    ),
    notes = listOf("التحويلات الداخلية بين الصناديق والبنوك تظهر مستقلة ولا تُعامل كمتحصلات أو مدفوعات تشغيلية خارجية.")
)

internal fun buildExpenseSectionExportDocument(
    rows: List<ExpenseReportRow>,
    filterSummary: List<Pair<String, String>>
): ReportExportDocument {
    val analytics = ExpenseReportAnalyticsMath.build(rows)
    return ReportExportDocument(
        title = "تقرير المصروفات التشغيلية — Fush ERP",
        subtitle = "حسب الفلاتر المطبقة داخل شاشة المصروفات",
        summary = listOf(
            "إجمالي المصروفات" to accountingExportMoney(analytics.totalAmountBase),
            "عدد السندات" to analytics.voucherCount.toString(),
            "متوسط السند" to accountingExportMoney(analytics.averageVoucherBase),
            "عدد المرفقات" to analytics.attachmentCount.toString()
        ) + filterSummary,
        tables = listOf(
            ReportExportTable(
                title = "المصروفات حسب الحساب",
                headers = listOf("الحساب", "عدد السندات", "الإجمالي", "النسبة"),
                rows = analytics.byAccount.map { listOf(it.label, it.voucherCount.toString(), accountingExportMoney(it.amountBase), accountingExportPercent(it.sharePercent)) }
            ),
            ReportExportTable(
                title = "المصروفات حسب مركز التكلفة",
                headers = listOf("مركز التكلفة", "عدد السندات", "الإجمالي", "النسبة"),
                rows = analytics.byCostCenter.map { listOf(it.label, it.voucherCount.toString(), accountingExportMoney(it.amountBase), accountingExportPercent(it.sharePercent)) }
            ),
            ReportExportTable(
                title = "تفاصيل سندات المصروف",
                headers = listOf("التاريخ", "السند", "حساب المصروف", "المبلغ", "العملة الأصلية", "طريقة الدفع", "مركز التكلفة", "الموظف", "المندوب", "الطرف/المرجع", "البيان"),
                rows = rows.map {
                    val partyRef = listOf(it.customerName, it.supplierName, it.referenceNo, it.referenceLabel, it.itemName).filter { value -> value.isNotBlank() }.joinToString(" • ")
                    listOf(
                        accountingExportDate(it.voucherDate),
                        it.voucherNo,
                        "${it.expenseAccountCode} — ${it.expenseAccountName}",
                        accountingExportMoney(it.amountBase),
                        "${accountingExportNumber(it.amountOriginal)} ${it.currencyCode}",
                        it.paymentMethod,
                        it.costCenterName,
                        it.employeeName,
                        it.salesRepName,
                        partyRef,
                        it.description
                    )
                }
            )
        ),
        notes = listOf("التقرير يطبع نفس الحركات المطابقة للفلاتر الحالية داخل شاشة المصروفات.")
    )
}

internal fun buildLedgerSectionExportDocument(
    accountCode: String,
    accountName: String,
    fromLabel: String,
    toLabel: String,
    report: LedgerReport
): ReportExportDocument = ReportExportDocument(
    title = "دفتر الأستاذ العام — Fush ERP",
    subtitle = "$accountCode — $accountName • من $fromLabel إلى $toLabel",
    summary = listOf(
        "الرصيد الافتتاحي" to accountingExportSignedBalance(report.openingBalance),
        "إجمالي المدين" to accountingExportMoney(report.lines.sumOf { it.debit }),
        "إجمالي الدائن" to accountingExportMoney(report.lines.sumOf { it.credit }),
        "الرصيد الختامي" to accountingExportSignedBalance(report.closingBalance)
    ),
    tables = listOf(
        ReportExportTable(
            title = "حركة الحساب",
            headers = listOf("التاريخ", "رقم القيد", "المصدر", "البيان", "مدين", "دائن", "الرصيد"),
            rows = report.lines.map {
                listOf(accountingExportDate(it.entryDate), it.entryNo, it.sourceType, it.description, accountingExportMoney(it.debit), accountingExportMoney(it.credit), accountingExportSignedBalance(it.runningBalance))
            }
        )
    )
)

internal fun buildTrialBalanceSectionExportDocument(asOfLabel: String, report: TrialBalanceReport): ReportExportDocument =
    ReportExportDocument(
        title = "ميزان المراجعة — Fush ERP",
        subtitle = "حتى تاريخ $asOfLabel",
        summary = listOf(
            "إجمالي حركة المدين" to accountingExportMoney(report.totalDebitMovement),
            "إجمالي حركة الدائن" to accountingExportMoney(report.totalCreditMovement),
            "إجمالي الرصيد المدين" to accountingExportMoney(report.totalDebitBalance),
            "إجمالي الرصيد الدائن" to accountingExportMoney(report.totalCreditBalance),
            "فرق التوازن" to accountingExportMoney(report.totalDebitBalance - report.totalCreditBalance)
        ),
        tables = listOf(
            ReportExportTable(
                title = "الحسابات",
                headers = listOf("الكود", "الحساب", "النوع", "حركة مدين", "حركة دائن", "رصيد مدين", "رصيد دائن"),
                rows = report.lines.map {
                    listOf(it.code, it.nameAr, it.type, accountingExportMoney(it.debitMovement), accountingExportMoney(it.creditMovement), accountingExportMoney(it.debitBalance), accountingExportMoney(it.creditBalance))
                }
            )
        ),
        notes = listOf(if (abs(report.totalDebitBalance - report.totalCreditBalance) < 0.01) "ميزان المراجعة متوازن." else "تنبيه: يوجد فرق في توازن الأرصدة ويجب مراجعته.")
    )

internal fun buildProfitLossSectionExportDocument(
    fromLabel: String,
    toLabel: String,
    report: ProfitLossReport
): ReportExportDocument = ReportExportDocument(
    title = "قائمة الدخل — Fush ERP",
    subtitle = "الفترة من $fromLabel إلى $toLabel",
    summary = listOf(
        "الإيرادات" to accountingExportMoney(report.revenue),
        "المصروفات" to accountingExportMoney(report.expenses),
        "صافي الربح/الخسارة" to accountingExportMoney(report.netProfit)
    ),
    tables = listOf(
        ReportExportTable("تفصيل الإيرادات", listOf("الحساب", "المبلغ"), report.revenueByAccount.map { listOf(it.first, accountingExportMoney(it.second)) }),
        ReportExportTable("تفصيل المصروفات", listOf("الحساب", "المبلغ"), report.expenseByAccount.map { listOf(it.first, accountingExportMoney(it.second)) })
    )
)

internal fun buildBalanceSheetSectionExportDocument(asOfLabel: String, report: BalanceSheetReport): ReportExportDocument =
    ReportExportDocument(
        title = "قائمة المركز المالي — Fush ERP",
        subtitle = "حتى تاريخ $asOfLabel",
        summary = listOf(
            "إجمالي الأصول" to accountingExportMoney(report.assets),
            "إجمالي الالتزامات" to accountingExportMoney(report.liabilities),
            "حقوق الملكية قبل ربح الفترة" to accountingExportMoney(report.equityBeforeCurrentProfit),
            "ربح/خسارة الفترة" to accountingExportMoney(report.currentProfit),
            "الالتزامات وحقوق الملكية" to accountingExportMoney(report.totalLiabilitiesAndEquity),
            "فرق التوازن" to accountingExportMoney(report.difference)
        ),
        tables = listOf(
            ReportExportTable("الأصول", listOf("الحساب", "الرصيد"), report.assetsByAccount.map { listOf(it.first, accountingExportMoney(it.second)) }),
            ReportExportTable("الالتزامات", listOf("الحساب", "الرصيد"), report.liabilitiesByAccount.map { listOf(it.first, accountingExportMoney(it.second)) }),
            ReportExportTable("حقوق الملكية", listOf("الحساب", "الرصيد"), report.equityByAccount.map { listOf(it.first, accountingExportMoney(it.second)) })
        ),
        notes = listOf(if (abs(report.difference) < 0.01) "القائمة متوازنة." else "تنبيه: يوجد فرق توازن ويجب مراجعته.")
    )

internal fun buildCashFlowSectionExportDocument(
    fromLabel: String,
    toLabel: String,
    report: CashFlowReport
): ReportExportDocument = ReportExportDocument(
    title = "التدفق النقدي المباشر — Fush ERP",
    subtitle = "الفترة من $fromLabel إلى $toLabel",
    summary = listOf(
        "رصيد أول الفترة" to accountingExportMoney(report.openingCash),
        "المتحصلات النقدية" to accountingExportMoney(report.cashInflows),
        "المدفوعات النقدية" to accountingExportMoney(report.cashOutflows),
        "صافي الحركة" to accountingExportMoney(report.netCashMovement),
        "رصيد آخر الفترة" to accountingExportMoney(report.closingCash)
    ),
    tables = listOf(
        ReportExportTable(
            title = "ملخص التدفق",
            headers = listOf("البند", "المبلغ"),
            rows = listOf(
                listOf("رصيد النقد أول الفترة", accountingExportMoney(report.openingCash)),
                listOf("المتحصلات النقدية", accountingExportMoney(report.cashInflows)),
                listOf("المدفوعات النقدية", accountingExportMoney(report.cashOutflows)),
                listOf("صافي الحركة النقدية", accountingExportMoney(report.netCashMovement)),
                listOf("رصيد النقد آخر الفترة", accountingExportMoney(report.closingCash))
            )
        )
    ),
    notes = listOf("التحويلات الداخلية بين الصناديق والبنوك لا تُحتسب كمتحصلات أو مدفوعات خارجية.")
)

private fun accountingExportDate(ms: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))
private fun accountingExportMoney(value: Double): String = String.format(Locale.US, "%,.2f ر.ي", value)
private fun accountingExportNumber(value: Double): String = String.format(Locale.US, "%,.4f", value).trimEnd('0').trimEnd('.')
private fun accountingExportPercent(value: Double): String = String.format(Locale.US, "%.1f%%", value)
private fun accountingExportSignedBalance(value: Double): String = when {
    value > 0.0000001 -> "${accountingExportMoney(value)} مدين"
    value < -0.0000001 -> "${accountingExportMoney(-value)} دائن"
    else -> accountingExportMoney(0.0)
}
