package com.fush.erp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fush.erp.data.AppContainer
import com.fush.erp.data.entity.*
import com.fush.erp.domain.*
import com.fush.erp.ui.FushMetricCard
import com.fush.erp.ui.FushSectionHeader
import com.fush.erp.ui.FushStatusPill
import com.fush.erp.ui.FushStatusTone
import com.fush.erp.ui.export.ReportExportActions
import com.fush.erp.ui.export.ReportExportDocument
import com.fush.erp.ui.export.ReportExportTable
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@Composable
fun ReportsScreen(container: AppContainer, user: UserEntity, modifier: Modifier = Modifier, initialTab: String = "ملخص") {
    val now = remember { System.currentTimeMillis() }
    val rolePermissions by container.db.securityDao().observePermissionCodesForRole(user.role).collectAsState(initial = emptyList())
    val canExport = user.role == "ADMIN" || SecurityPermissions.REPORTS_EXPORT in rolePermissions
    var period by remember { mutableStateOf("هذا الشهر") }
    var tab by remember(initialTab) { mutableStateOf(initialTab) }
    val (from, to) = remember(period, now) { periodRange(period, now) }

    var executive by remember { mutableStateOf<ExecutiveReportRow?>(null) }
    var customers by remember { mutableStateOf<List<CustomerSalesReportRow>>(emptyList()) }
    var salesProductQuantities by remember { mutableStateOf<List<ProductSalesQuantityReportRow>>(emptyList()) }
    var provinces by remember { mutableStateOf<List<ProvinceProfitabilityRow>>(emptyList()) }
    var commissions by remember { mutableStateOf<List<CommissionReportRow>>(emptyList()) }
    var suppliers by remember { mutableStateOf<List<SupplierPurchaseReportRow>>(emptyList()) }
    var inventory by remember { mutableStateOf<List<InventoryValuationReportRow>>(emptyList()) }
    var inventoryActivity by remember { mutableStateOf<List<InventoryActivityReportRow>>(emptyList()) }
    var inventoryExpiryLots by remember { mutableStateOf<List<InventoryExpiryLotReportRow>>(emptyList()) }
    var inventoryMovements by remember { mutableStateOf<List<InventoryMovementDetailReportRow>>(emptyList()) }
    var production by remember { mutableStateOf<List<ProductionPerformanceReportRow>>(emptyList()) }
    var productionMaterials by remember { mutableStateOf<List<ProductionMaterialUsageReportRow>>(emptyList()) }
    var quality by remember { mutableStateOf<List<QualityReportRow>>(emptyList()) }
    var maintenance by remember { mutableStateOf<MaintenanceReportRow?>(null) }
    var pnl by remember { mutableStateOf<ProfitLossReport?>(null) }
    var trial by remember { mutableStateOf<TrialBalanceReport?>(null) }
    var cash by remember { mutableStateOf<CashFlowReport?>(null) }
    var balanceSheet by remember { mutableStateOf<BalanceSheetReport?>(null) }

    val accounts by container.db.accountDao().observeAll().collectAsState(initial = emptyList())
    val statementCustomers by container.db.customerDao().observeAll().collectAsState(initial = emptyList())
    val statementSuppliers by container.db.supplierDao().observeAll().collectAsState(initial = emptyList())
    var ledgerAccountId by remember { mutableStateOf<Long?>(null) }
    var ledger by remember { mutableStateOf<LedgerReport?>(null) }
    var statementPartyType by remember { mutableStateOf("CUSTOMER") }
    var statementCustomerId by remember { mutableStateOf<Long?>(null) }
    var statementSupplierId by remember { mutableStateOf<Long?>(null) }
    var partyStatement by remember { mutableStateOf<PartyStatementPeriod?>(null) }
    var agingPartyType by remember { mutableStateOf("CUSTOMER") }
    var agingRows by remember { mutableStateOf<List<PartyAgingReportRow>>(emptyList()) }
    var expenses by remember { mutableStateOf<List<ExpenseReportRow>>(emptyList()) }
    var treasuryReport by remember { mutableStateOf<TreasuryPeriodReport?>(null) }
    var periodComparison by remember { mutableStateOf<PeriodComparisonReport?>(null) }

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(accounts) {
        if (ledgerAccountId == null) ledgerAccountId = accounts.firstOrNull { it.isPosting }?.id
    }
    LaunchedEffect(statementCustomers) {
        if (statementCustomerId == null) statementCustomerId = statementCustomers.firstOrNull()?.id
    }
    LaunchedEffect(statementSuppliers) {
        if (statementSupplierId == null) statementSupplierId = statementSuppliers.firstOrNull()?.id
    }

    LaunchedEffect(tab, from, to, ledgerAccountId, statementPartyType, statementCustomerId, statementSupplierId, agingPartyType) {
        loading = true
        error = null
        try {
            when (tab) {
                "ملخص" -> executive = container.db.reportDao().executive(from, to)
                "المبيعات" -> {
                    customers = container.db.reportDao().customerSales(from, to)
                    salesProductQuantities = container.db.reportDao().salesProductQuantities(from, to)
                    provinces = container.db.geographyDao().provinceProfitability(from, to)
                    commissions = container.db.reportDao().commissions(from, to)
                }
                "المشتريات" -> suppliers = container.db.reportDao().supplierPurchases(from, to)
                "المخزون" -> {
                    val reportDao = container.db.reportDao()
                    inventory = reportDao.inventoryValuation(to)
                    inventoryActivity = reportDao.inventoryActivity(to)
                    inventoryExpiryLots = reportDao.inventoryExpiryLots(to)
                    inventoryMovements = reportDao.inventoryMovementDetails(from, to)
                }
                "الإنتاج" -> {
                    production = container.db.reportDao().productionPerformance(from, to)
                    productionMaterials = container.db.reportDao().productionMaterialUsage(from, to)
                    maintenance = container.db.reportDao().maintenance(from, to)
                }
                "الجودة" -> quality = container.db.reportDao().quality(from, to)
                "المالية" -> {
                    pnl = container.accountingService.profitLoss(from, to)
                    trial = container.accountingService.trialBalance(to)
                    cash = container.accountingService.cashFlow(from, to)
                    balanceSheet = container.accountingService.balanceSheet(to)
                }
                "المصروفات" -> expenses = container.db.expenseDao().reportRows(from, to)
                "الخزائن والبنوك" -> treasuryReport = TreasuryReportMath.build(
                    treasuries = container.db.accountingDao().allTreasury(),
                    movementsThroughEnd = container.db.reportDao().treasuryMovementsThrough(to),
                    fromDate = from,
                    toDate = to
                )
                "مقارنة الفترات" -> {
                    val previousRange = PeriodComparisonMath.previousRange(period, from, to)
                    periodComparison = if (previousRange == null) {
                        PeriodComparisonMath.unavailable(from, to)
                    } else {
                        val (previousFrom, previousTo) = previousRange
                        PeriodComparisonMath.build(
                            currentExecutive = container.db.reportDao().executive(from, to),
                            previousExecutive = container.db.reportDao().executive(previousFrom, previousTo),
                            currentProfitLoss = container.accountingService.profitLoss(from, to),
                            previousProfitLoss = container.accountingService.profitLoss(previousFrom, previousTo),
                            currentFrom = from,
                            currentTo = to,
                            previousFrom = previousFrom,
                            previousTo = previousTo
                        )
                    }
                }
                "الأستاذ العام" -> {
                    ledger = ledgerAccountId?.let { container.accountingService.ledger(it, from, to) }
                }
                "أعمار الديون" -> {
                    val reportDao = container.db.reportDao()
                    val invoices = if (agingPartyType == "SUPPLIER") {
                        reportDao.supplierAgingInvoices(to)
                    } else {
                        reportDao.customerAgingInvoices(to)
                    }
                    val adjustments = if (agingPartyType == "SUPPLIER") {
                        reportDao.supplierAgingAdjustments(to)
                    } else {
                        reportDao.customerAgingAdjustments(to)
                    }
                    agingRows = AgingReportMath.build(invoices, adjustments, to)
                }
                "كشف الأطراف" -> {
                    partyStatement = when (statementPartyType) {
                        "SUPPLIER" -> statementSupplierId?.let { supplierId ->
                            PartyStatementMath.build(
                                events = container.db.purchaseDao().supplierLedgerEvents(supplierId, to).map { event ->
                                    PartyStatementEvent(
                                        eventDate = event.eventDate,
                                        eventOrder = event.eventOrder,
                                        eventType = event.eventType,
                                        referenceNo = event.referenceNo,
                                        description = event.notes,
                                        debitBase = event.debitBase,
                                        creditBase = event.creditBase
                                    )
                                },
                                fromDate = from,
                                toDate = to,
                                customerBalance = false
                            )
                        }
                        else -> statementCustomerId?.let { customerId ->
                            PartyStatementMath.build(
                                events = container.db.salesDao().customerLedgerEvents(customerId).map { event ->
                                    PartyStatementEvent(
                                        eventDate = event.eventDate,
                                        eventOrder = event.eventOrder,
                                        eventType = event.eventType,
                                        referenceNo = event.referenceNo,
                                        description = listOf(event.invoiceNo, event.notes).filter { it.isNotBlank() }.joinToString(" • "),
                                        debitBase = event.debitBase,
                                        creditBase = event.creditBase
                                    )
                                },
                                fromDate = from,
                                toDate = to,
                                customerBalance = true
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            error = e.message ?: "تعذر إعداد التقرير"
        } finally {
            loading = false
        }
    }

    val exportDocument = remember(
        tab, period, from, to, executive, customers, salesProductQuantities, provinces, commissions, suppliers,
        inventory, inventoryActivity, inventoryExpiryLots, inventoryMovements, production, productionMaterials, quality, maintenance, pnl, trial, cash, balanceSheet, accounts,
        ledgerAccountId, ledger, statementCustomers, statementSuppliers, statementPartyType, statementCustomerId,
        statementSupplierId, partyStatement, agingPartyType, agingRows, expenses, treasuryReport, periodComparison
    ) {
        buildCurrentReportExportDocument(
            tab = tab,
            periodLabel = period,
            from = from,
            to = to,
            executive = executive,
            customers = customers,
            salesProductQuantities = salesProductQuantities,
            provinces = provinces,
            commissions = commissions,
            suppliers = suppliers,
            inventory = inventory,
            inventoryActivity = inventoryActivity,
            inventoryExpiryLots = inventoryExpiryLots,
            inventoryMovements = inventoryMovements,
            production = production,
            productionMaterials = productionMaterials,
            quality = quality,
            maintenance = maintenance,
            pnl = pnl,
            trial = trial,
            cash = cash,
            balanceSheet = balanceSheet,
            ledgerAccount = accounts.firstOrNull { it.id == ledgerAccountId },
            ledger = ledger,
            statementPartyType = statementPartyType,
            statementCustomer = statementCustomers.firstOrNull { it.id == statementCustomerId },
            statementSupplier = statementSuppliers.firstOrNull { it.id == statementSupplierId },
            partyStatement = partyStatement,
            agingPartyType = agingPartyType,
            agingRows = agingRows,
            expenses = expenses,
            treasuryReport = treasuryReport,
            periodComparison = periodComparison
        )
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        if (wide) {
            Row(
                Modifier.fillMaxSize().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ReportNavigationPanel(
                    period = period,
                    onPeriodChange = { period = it },
                    tab = tab,
                    onTabChange = { tab = it },
                    modifier = Modifier.width(220.dp).fillMaxHeight()
                )
                VerticalDivider(Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    ReportWorkspaceHeader(tab, period, from, to)
                    ReportLoadingAndExport(loading, error, exportDocument, tab, canExport)
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        ReportTabContent(
                        tab, executive, customers, salesProductQuantities, provinces, commissions, suppliers, inventory,
                        inventoryActivity, inventoryExpiryLots, inventoryMovements, production, productionMaterials, maintenance, quality, pnl, trial, cash, balanceSheet, accounts,
                        ledgerAccountId, { ledgerAccountId = it }, ledger, statementCustomers, statementSuppliers,
                        statementPartyType, { statementPartyType = it }, statementCustomerId, { statementCustomerId = it },
                        statementSupplierId, { statementSupplierId = it }, partyStatement, agingPartyType,
                        { agingPartyType = it }, agingRows, expenses, treasuryReport, periodComparison, to
                    )
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                ReportWorkspaceHeader(tab, period, from, to)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(reportPeriods) { p ->
                        FilterChip(selected = period == p, onClick = { period = p }, label = { Text(p) })
                    }
                }
                Spacer(Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(reportTabs) { t ->
                        FilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(t) })
                    }
                }
                ReportLoadingAndExport(loading, error, exportDocument, tab, canExport)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    ReportTabContent(
                        tab, executive, customers, salesProductQuantities, provinces, commissions, suppliers, inventory,
                        inventoryActivity, inventoryExpiryLots, inventoryMovements, production, productionMaterials, maintenance, quality, pnl, trial, cash, balanceSheet, accounts,
                        ledgerAccountId, { ledgerAccountId = it }, ledger, statementCustomers, statementSuppliers,
                        statementPartyType, { statementPartyType = it }, statementCustomerId, { statementCustomerId = it },
                        statementSupplierId, { statementSupplierId = it }, partyStatement, agingPartyType,
                        { agingPartyType = it }, agingRows, expenses, treasuryReport, periodComparison, to
                    )
                }
            }
        }
    }
}

private val reportPeriods = listOf("اليوم", "هذا الشهر", "30 يوم", "هذه السنة", "كل الفترة")
private val reportTabs = listOf(
    "ملخص", "المبيعات", "المشتريات", "المخزون", "الإنتاج", "الجودة", "المالية", "الخزائن والبنوك", "مقارنة الفترات", "المصروفات", "الأستاذ العام", "أعمار الديون", "كشف الأطراف"
)

@Composable
private fun ReportWorkspaceHeader(tab: String, period: String, from: Long, to: Long) {
    FushSectionHeader(
        title = reportTitle(tab),
        subtitle = "$period • ${fmtDate(from)} — ${fmtDate(to)}"
    )
}

@Composable
private fun ReportNavigationPanel(
    period: String,
    onPeriodChange: (String) -> Unit,
    tab: String,
    onTabChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(modifier) {
        Column(
            Modifier.fillMaxSize().padding(14.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("مركز التقارير", style = MaterialTheme.typography.titleLarge)
            Text("اختر الفترة ونوع التحليل. يبقى التقرير في مساحة عمل مستقلة على الشاشات الواسعة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text("الفترة", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            reportPeriods.forEach { p ->
                FilterChip(
                    selected = period == p,
                    onClick = { onPeriodChange(p) },
                    label = { Text(p) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text("التقرير", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            reportTabs.forEach { t ->
                FilterChip(
                    selected = tab == t,
                    onClick = { onTabChange(t) },
                    label = { Text(t) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ReportLoadingAndExport(
    loading: Boolean,
    error: String?,
    exportDocument: ReportExportDocument,
    tab: String,
    canExport: Boolean
) {
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
    error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 6.dp))
    }
    ReportExportActions(
        document = exportDocument,
        baseName = reportBaseName(tab),
        printJobName = "${reportTitle(tab)} — Fush ERP",
        enabled = canExport && !loading && error == null,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun ReportTabContent(
    tab: String,
    executive: ExecutiveReportRow?,
    customers: List<CustomerSalesReportRow>,
    salesProductQuantities: List<ProductSalesQuantityReportRow>,
    provinces: List<ProvinceProfitabilityRow>,
    commissions: List<CommissionReportRow>,
    suppliers: List<SupplierPurchaseReportRow>,
    inventory: List<InventoryValuationReportRow>,
    inventoryActivity: List<InventoryActivityReportRow>,
    inventoryExpiryLots: List<InventoryExpiryLotReportRow>,
    inventoryMovements: List<InventoryMovementDetailReportRow>,
    production: List<ProductionPerformanceReportRow>,
    productionMaterials: List<ProductionMaterialUsageReportRow>,
    maintenance: MaintenanceReportRow?,
    quality: List<QualityReportRow>,
    pnl: ProfitLossReport?,
    trial: TrialBalanceReport?,
    cash: CashFlowReport?,
    balanceSheet: BalanceSheetReport?,
    accounts: List<AccountEntity>,
    ledgerAccountId: Long?,
    onLedgerAccountChange: (Long) -> Unit,
    ledger: LedgerReport?,
    statementCustomers: List<CustomerEntity>,
    statementSuppliers: List<SupplierEntity>,
    statementPartyType: String,
    onStatementPartyTypeChange: (String) -> Unit,
    statementCustomerId: Long?,
    onStatementCustomerChange: (Long) -> Unit,
    statementSupplierId: Long?,
    onStatementSupplierChange: (Long) -> Unit,
    partyStatement: PartyStatementPeriod?,
    agingPartyType: String,
    onAgingPartyTypeChange: (String) -> Unit,
    agingRows: List<PartyAgingReportRow>,
    expenses: List<ExpenseReportRow>,
    treasuryReport: TreasuryPeriodReport?,
    periodComparison: PeriodComparisonReport?,
    reportTo: Long
) {
    when (tab) {
        "ملخص" -> ExecutiveTab(executive)
        "المبيعات" -> SalesTab(customers, salesProductQuantities, provinces, commissions)
        "المشتريات" -> PurchasesTab(suppliers)
        "المخزون" -> InventoryTab(inventory, inventoryActivity, inventoryExpiryLots, inventoryMovements, reportTo)
        "الإنتاج" -> ProductionTab(production, productionMaterials, maintenance)
        "الجودة" -> QualityTab(quality)
        "المالية" -> FinanceTab(pnl, trial, cash, balanceSheet)
        "المصروفات" -> ExpenseAnalysisTab(expenses)
        "الخزائن والبنوك" -> TreasuryReportTab(treasuryReport)
        "مقارنة الفترات" -> PeriodComparisonTab(periodComparison)
        "الأستاذ العام" -> LedgerReportTab(accounts, ledgerAccountId, onLedgerAccountChange, ledger)
        "أعمار الديون" -> AgingReportTab(agingPartyType, onAgingPartyTypeChange, agingRows)
        "كشف الأطراف" -> PartyStatementTab(
            statementCustomers, statementSuppliers, statementPartyType, onStatementPartyTypeChange,
            statementCustomerId, onStatementCustomerChange, statementSupplierId, onStatementSupplierChange, partyStatement
        )
    }
}

@Composable private fun ExecutiveTab(r: ExecutiveReportRow?) {
    if (r == null) return
    val netSales = ReportMath.net(r.grossSalesBase, r.salesReturnsBase)
    val netPurchases = ReportMath.net(r.grossPurchasesBase, r.purchaseReturnsBase)
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { MetricGrid(listOf(
            "صافي المبيعات" to money(netSales), "التحصيل" to money(r.collectionsBase),
            "صافي المشتريات" to money(netPurchases), "قيمة المخزون" to money(r.inventoryValueBase),
            "الذمم المدينة" to money(r.receivablesBase), "المتأخر" to money(r.overdueBase),
            "أوامر الإنتاج" to r.productionOrders.toString(), "مقبول إجمالي" to qty(r.acceptedQtyBase),
            "مقبول 60 مل" to qty(r.accepted60QtyBase), "مقبول 200 مل" to qty(r.accepted200QtyBase),
            "هالك" to qty(r.scrapQtyBase), "CAPA/NC مفتوحة" to r.openNonConformances.toString(),
            "تكلفة الصيانة" to money(r.maintenanceCostBase)
        )) }
    }
}

@Composable
private fun PeriodComparisonTab(report: PeriodComparisonReport?) {
    if (report == null) return
    if (!report.hasComparablePeriod) {
        ElevatedCard(Modifier.fillMaxWidth()) {
            Text(
                "لا توجد فترة سابقة مكافئة عند اختيار «كل الفترة». اختر اليوم أو هذا الشهر أو 30 يوم أو هذه السنة لإجراء مقارنة زمنية.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("الفترة الحالية: ${fmtDate(report.currentFrom)} — ${fmtDate(report.currentTo)}", style = MaterialTheme.typography.labelLarge)
                    Text("الفترة السابقة: ${fmtDate(report.previousFrom!!)} — ${fmtDate(report.previousTo!!)}", style = MaterialTheme.typography.bodySmall)
                    Text("التغير محايد وصفيًا؛ ارتفاع المؤشر أو انخفاضه لا يعني تلقائيًا تحسنًا أو تراجعًا.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(report.metrics) { metric ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(metric.label, style = MaterialTheme.typography.titleSmall)
                    MetricGrid(listOf(
                        "الحالي" to money(metric.currentBase),
                        "السابق" to money(metric.previousBase),
                        "الفرق" to money(metric.differenceBase),
                        "التغير %" to (metric.percentChange?.let { "%.1f%%".format(Locale.US, it) } ?: "—")
                    ))
                }
            }
        }
    }
}

@Composable private fun SalesTab(
    customers: List<CustomerSalesReportRow>,
    productQuantities: List<ProductSalesQuantityReportRow>,
    provinces: List<ProvinceProfitabilityRow>,
    commissions: List<CommissionReportRow>
) {
    val small60 = productQuantities.filter { ReportMath.productVolumeMl(it.code, it.productName) == 60 }
    val large200 = productQuantities.filter { ReportMath.productVolumeMl(it.code, it.productName) == 200 }
    val gross60 = small60.sumOf { it.grossQtyBase }
    val returned60 = small60.sumOf { it.returnedQtyBase }
    val net60 = small60.sumOf { it.netQtyBase }
    val gross200 = large200.sumOf { it.grossQtyBase }
    val returned200 = large200.sumOf { it.returnedQtyBase }
    val net200 = large200.sumOf { it.netQtyBase }
    val grossSales = customers.sumOf { it.grossSalesBase }
    val returns = customers.sumOf { it.returnsBase }
    val collections = customers.sumOf { it.collectionsBase }
    val outstanding = customers.sumOf { it.outstandingBase }
    val provinceProfit = provinces.sumOf { it.profitBase }
    val commissionNet = commissions.sumOf { it.netCommissionBase }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            FushSectionHeader("تحليل المبيعات", "المبيعات والتحصيل والذمم والربحية الجغرافية في لوحة واحدة.")
            Spacer(Modifier.height(8.dp))
            MetricGrid(listOf(
                "إجمالي المبيعات" to money(grossSales),
                "المرتجعات" to money(returns),
                "التحصيل" to money(collections),
                "الرصيد المستحق" to money(outstanding),
                "ربح المحافظات" to money(provinceProfit),
                "صافي العمولات" to money(commissionNet)
            ))
        }
        item {
            SectionTitle("كميات المنتجات المباعة")
            MetricGrid(listOf(
                "صافي علب 60 مل" to qty(net60),
                "صافي علب 200 مل" to qty(net200)
            ))
            ReportCard("Fush 60 مل", "الكمية خلال الفترة", listOf(
                "مباع" to qty(gross60), "مرتجع" to qty(returned60), "الصافي" to qty(net60)
            ))
            Spacer(Modifier.height(6.dp))
            ReportCard("Fush 200 مل", "الكمية خلال الفترة", listOf(
                "مباع" to qty(gross200), "مرتجع" to qty(returned200), "الصافي" to qty(net200)
            ))
        }
        item { SectionTitle("ربحية المحافظات") }
        items(provinces) { p -> ReportCard(p.province, "${p.invoiceCount} فاتورة", listOf(
            "الإيراد" to money(p.netRevenueBase), "التكلفة" to money(p.netCogsBase),
            "العمولات" to money(p.commissionBase), "تكاليف جغرافية" to money(p.geographicCostBase),
            "الربح" to money(p.profitBase), "الهامش" to "%.1f%%".format(Locale.US, ReportMath.margin(p.profitBase, p.netRevenueBase))
        )) }
        item { SectionTitle("العملاء") }
        items(customers) { c -> ReportCard(c.customerName, c.province, listOf(
            "الفواتير" to c.invoiceCount.toString(), "المبيعات" to money(c.grossSalesBase),
            "المرتجعات" to money(c.returnsBase), "التحصيل" to money(c.collectionsBase),
            "الرصيد" to money(c.outstandingBase)
        )) }
        item { SectionTitle("العمولات") }
        items(commissions) { c -> ReportCard(c.beneficiary.ifBlank { "غير محدد" }, "عمولات بعد التحصيل والمرتجع", listOf(
            "مستحقة" to money(c.earnedBase), "معكوسة" to money(c.reversedBase), "الصافي" to money(c.netCommissionBase)
        )) }
    }
}

@Composable private fun PurchasesTab(rows: List<SupplierPurchaseReportRow>) {
    val gross = rows.sumOf { it.grossPurchasesBase }
    val returns = rows.sumOf { it.returnsBase }
    val net = rows.sumOf { it.netPurchasesBase }
    val invoices = rows.sumOf { it.invoiceCount }
    val topSupplier = rows.maxByOrNull { it.netPurchasesBase }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            FushSectionHeader("تحليل المشتريات", "حجم الشراء والمرتجعات وتركيز الإنفاق على الموردين خلال الفترة.")
            Spacer(Modifier.height(8.dp))
            MetricGrid(listOf(
                "إجمالي المشتريات" to money(gross),
                "المرتجعات" to money(returns),
                "صافي المشتريات" to money(net),
                "عدد الفواتير" to invoices.toString(),
                "عدد الموردين" to rows.size.toString(),
                "أكبر مورد" to (topSupplier?.supplierName ?: "—")
            ))
        }
        item { SectionTitle("المشتريات حسب المورد") }
        if (rows.isEmpty()) item { Text("لا توجد مشتريات في الفترة المحددة.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(rows) { r -> ReportCard(r.supplierName, "${r.invoiceCount} فاتورة", listOf(
            "الإجمالي" to money(r.grossPurchasesBase), "المرتجع" to money(r.returnsBase), "الصافي" to money(r.netPurchasesBase)
        )) }
    }
}

@Composable private fun InventoryTab(
    rows: List<InventoryValuationReportRow>,
    activityRows: List<InventoryActivityReportRow>,
    expiryRows: List<InventoryExpiryLotReportRow>,
    movements: List<InventoryMovementDetailReportRow>,
    asOf: Long
) {
    val total = rows.sumOf { it.inventoryValueBase }
    val reorder = rows.count { it.reorderLevel > 0 && it.quantityBase <= it.reorderLevel }
    val zeroStock = rows.count { it.quantityBase <= 0.0 }
    val topValue = rows.maxByOrNull { it.inventoryValueBase }
    val movementSummary = InventoryReportMath.movementSummary(movements)
    val activityInsights = activityRows.map { InventoryReportMath.activity(it, asOf) }
    val slowRows = activityInsights.filter { it.status != "نشط" && it.status != "لم يُصرف بعد" }
    val expiryInsights = expiryRows.map { InventoryReportMath.expiry(it, asOf) }
    val expiredCount = expiryInsights.count { it.status == "منتهي" }
    val nearExpiryCount = expiryInsights.count { it.daysToExpiry in 0L..30L }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            FushSectionHeader("تحليل المخزون", "التقييم الحالي، حركة الفترة، بطء الحركة، والتشغيلات ذات الصلاحية من بيانات المخزون الفعلية.")
            Spacer(Modifier.height(8.dp))
            MetricGrid(listOf(
                "إجمالي قيمة المخزون" to money(total),
                "عدد الأصناف" to rows.size.toString(),
                "إعادة طلب" to reorder.toString(),
                "رصيد صفري/سالب" to zeroStock.toString(),
                "وارد الفترة" to qty(movementSummary.inboundQtyBase),
                "صادر الفترة" to qty(movementSummary.outboundQtyBase),
                "حركات الفترة" to movementSummary.movementCount.toString(),
                "بطيء/راكد/بدون صرف ≥90" to slowRows.size.toString(),
                "تشغيلات منتهية" to expiredCount.toString(),
                "تنتهي ≤30 يوم" to nearExpiryCount.toString(),
                "أعلى قيمة مخزون" to (topValue?.itemName ?: "—"),
                "قيمته" to money(topValue?.inventoryValueBase ?: 0.0)
            ))
        }

        item { SectionTitle("التقييم الحالي") }
        if (rows.isEmpty()) item { Text("لا توجد أرصدة مخزون متاحة للتقرير.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(rows) { r -> ReportCard("${r.itemName} — ${r.code}", r.baseUnitName, listOf(
            "الرصيد" to qty(r.quantityBase), "القيمة" to money(r.inventoryValueBase),
            "إعادة الطلب" to qty(r.reorderLevel), "الحالة" to if (r.reorderLevel > 0 && r.quantityBase <= r.reorderLevel) "إعادة طلب" else "طبيعي"
        )) }

        item { SectionTitle("الراكد وبطيء الحركة") }
        item {
            Text("التصنيف مبني على آخر صرف فعلي: بطيء من 90 يومًا، وراكد من 180 يومًا. الصنف الذي لم يُصرف منه قط يُعرض كحالة مستقلة ولا يُمنح تاريخ صرف افتراضيًا.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (slowRows.isEmpty()) item { Text("لا توجد أرصدة بطيئة/راكدة وفق حدود التقرير.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(slowRows.sortedByDescending { it.daysSinceLastOutbound ?: it.daysSinceFirstInbound ?: 0L }) { insight ->
            val r = insight.source
            ReportCard("${r.itemName} — ${r.code}", insight.status, listOf(
                "الرصيد" to "${qty(r.quantityBase)} ${r.baseUnitName}",
                "القيمة" to money(r.inventoryValueBase),
                "آخر صرف" to (r.lastOutboundDate?.let { fmtDate(it) } ?: "لم يُصرف"),
                "أيام منذ آخر صرف" to (insight.daysSinceLastOutbound?.toString() ?: "—"),
                "أول توريد" to (r.firstInboundDate?.let { fmtDate(it) } ?: "—")
            ))
        }

        item { SectionTitle("الصلاحية والتشغيلات") }
        val urgentExpiry = expiryInsights.filter { it.daysToExpiry <= 90L }
        if (urgentExpiry.isEmpty()) item { Text("لا توجد تشغيلات منتهية أو ستنتهي خلال 90 يومًا برصيد موجب.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(urgentExpiry) { insight ->
            val r = insight.source
            ReportCard("${r.itemName} — ${r.code}", "${r.warehouseName} • ${r.lotNo ?: "بدون رقم تشغيلة"}", listOf(
                "الانتهاء" to fmtDate(r.expiryDate),
                "الأيام المتبقية" to insight.daysToExpiry.toString(),
                "الحالة" to insight.status,
                "الرصيد" to "${qty(r.quantityBase)} ${r.baseUnitName}",
                "القيمة" to money(r.inventoryValueBase)
            ))
        }

        item { SectionTitle("آخر حركات الفترة") }
        if (movements.isEmpty()) item { Text("لا توجد حركات مخزون في الفترة المختارة.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(movements.take(100)) { r ->
            ReportCard("${r.itemName} — ${r.code}", "${fmtDate(r.movementDate)} • ${r.warehouseName}", listOf(
                "النوع" to r.movementType,
                "وارد" to if (r.quantityBase > 0.0) qty(r.quantityBase) else "—",
                "صادر" to if (r.quantityBase < 0.0) qty(kotlin.math.abs(r.quantityBase)) else "—",
                "تكلفة الوحدة" to money(r.unitCostBase),
                "المرجع" to "${r.referenceType} #${r.referenceId}",
                "التشغيلة" to (r.lotNo ?: "—")
            ))
        }
        if (movements.size > 100) item { Text("تعرض الشاشة آخر 100 حركة لتخفيف الحمل؛ ملف PDF/Excel يتضمن جميع حركات الفترة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable private fun ProductionTab(
    rows: List<ProductionPerformanceReportRow>,
    materialUsage: List<ProductionMaterialUsageReportRow>,
    maintenance: MaintenanceReportRow?
) {
    val planned = rows.sumOf { it.plannedQtyBase }
    val actual = rows.sumOf { it.actualQtyBase }
    val accepted = rows.sumOf { it.acceptedQtyBase }
    val accepted60 = rows.filter { ReportMath.productVolumeMl(it.productCode, it.productName) == 60 }.sumOf { it.acceptedQtyBase }
    val accepted200 = rows.filter { ReportMath.productVolumeMl(it.productCode, it.productName) == 200 }.sumOf { it.acceptedQtyBase }
    val rejected = rows.sumOf { it.rejectedQtyBase }
    val scrap = rows.sumOf { it.scrapQtyBase }
    val materialCost = rows.sumOf { it.materialCostBase }
    val laborCost = rows.sumOf { it.laborCostBase }
    val totalCost = rows.sumOf { it.actualCostBase }
    val planAchievement = ReportMath.percent(actual, planned)
    val acceptanceRate = ReportMath.percent(accepted, actual)
    val scrapRate = ReportMath.percent(scrap, actual)

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            FushSectionHeader("أداء الإنتاج", "قراءة تنفيذ الخطة، جودة الناتج والتكلفة الفعلية خلال الفترة المحددة.")
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FushMetricCard("تحقيق الخطة", "%.1f%%".format(Locale.US, planAchievement), Modifier.weight(1f), "${qty(actual)} من ${qty(planned)}", if (planAchievement >= 95.0) FushStatusTone.Success else FushStatusTone.Info)
                FushMetricCard("نسبة القبول", "%.1f%%".format(Locale.US, acceptanceRate), Modifier.weight(1f), "${qty(accepted)} وحدة", if (acceptanceRate >= 95.0) FushStatusTone.Success else FushStatusTone.Warning)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FushMetricCard("نسبة الهالك", "%.1f%%".format(Locale.US, scrapRate), Modifier.weight(1f), "${qty(scrap)} وحدة", if (scrapRate > 5.0) FushStatusTone.Danger else FushStatusTone.Neutral)
                FushMetricCard("أوامر الإنتاج", rows.size.toString(), Modifier.weight(1f), "خلال الفترة", FushStatusTone.Neutral)
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("الكميات والجودة", style = MaterialTheme.typography.titleMedium)
                    MetricGrid(listOf(
                        "المخطط" to qty(planned),
                        "الناتج الفعلي" to qty(actual),
                        "المقبول الإجمالي" to qty(accepted),
                        "المرفوض" to qty(rejected),
                        "مقبول 60 مل" to qty(accepted60),
                        "مقبول 200 مل" to qty(accepted200)
                    ))
                }
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("التكلفة الفعلية", style = MaterialTheme.typography.titleMedium)
                    MetricGrid(listOf(
                        "تكلفة المواد" to money(materialCost),
                        "تكلفة العمالة" to money(laborCost),
                        "إجمالي تكلفة الإنتاج" to money(totalCost),
                        "تكلفة المقبول/وحدة" to money(ReportMath.unitCost(totalCost, accepted))
                    ))
                }
            }
        }
        item {
            maintenance?.let {
                FushSectionHeader("الصيانة وتأثيرها", "مؤشرات توقف وصيانة المعدات المرتبطة بفترة الإنتاج.")
                Spacer(Modifier.height(6.dp))
                MetricGrid(listOf(
                    "أوامر الصيانة" to it.workOrderCount.toString(), "مفتوحة" to it.openCount.toString(),
                    "توقف/دقيقة" to it.downtimeMinutes.toString(), "تكلفة الصيانة" to money(it.costBase)
                ))
            }
        }
        item { FushSectionHeader("استهلاك المواد", "الكميات والتكلفة التاريخية الفعلية للمواد المصروفة إلى أوامر الإنتاج.") }
        if (materialUsage.isEmpty()) {
            item { Text("لا توجد مواد مصروفة للإنتاج في هذه الفترة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(materialUsage, key = { "report-material-${it.itemId}" }) { m ->
            ReportCard("${m.itemName} — ${m.code}", "استخدمت في ${m.orderCount} أمر إنتاج", listOf(
                "الكمية المصروفة" to "${qty(m.issuedQtyBase)} ${m.unitName}",
                "متوسط تكلفة الوحدة" to money(m.averageUnitCostBase),
                "إجمالي تكلفة المادة" to money(m.totalCostBase)
            ))
        }
        item { FushSectionHeader("أوامر الإنتاج", "تفاصيل الخطة والناتج والتكلفة لكل أمر.") }
        if (rows.isEmpty()) {
            item { Text("لا توجد أوامر إنتاج في الفترة المحددة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(rows, key = { "report-production-order-${it.orderId}" }) { r ->
            val yield = ReportMath.percent(r.acceptedQtyBase, r.plannedQtyBase)
            val activityDate = r.manufactureDate ?: r.plannedDate
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(r.orderNo, style = MaterialTheme.typography.titleMedium)
                            Text("${r.productName} • ${r.batchNo ?: "بدون دفعة"} • ${fmtDate(activityDate)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FushStatusPill(reportProductionStatusAr(r.status), reportProductionStatusTone(r.status))
                    }
                    MetricGrid(listOf(
                        "مخطط" to qty(r.plannedQtyBase), "فعلي" to qty(r.actualQtyBase),
                        "مقبول" to qty(r.acceptedQtyBase), "مرفوض" to qty(r.rejectedQtyBase),
                        "هالك" to qty(r.scrapQtyBase), "العائد" to "%.1f%%".format(Locale.US, yield),
                        "مواد" to money(r.materialCostBase), "عمالة" to money(r.laborCostBase),
                        "تكلفة فعلية" to money(r.actualCostBase), "تكلفة المقبول/وحدة" to money(ReportMath.unitCost(r.actualCostBase, r.acceptedQtyBase))
                    ))
                }
            }
        }
    }
}


private fun buildCurrentReportExportDocument(
    tab: String,
    periodLabel: String,
    from: Long,
    to: Long,
    executive: ExecutiveReportRow?,
    customers: List<CustomerSalesReportRow>,
    salesProductQuantities: List<ProductSalesQuantityReportRow>,
    provinces: List<ProvinceProfitabilityRow>,
    commissions: List<CommissionReportRow>,
    suppliers: List<SupplierPurchaseReportRow>,
    inventory: List<InventoryValuationReportRow>,
    inventoryActivity: List<InventoryActivityReportRow>,
    inventoryExpiryLots: List<InventoryExpiryLotReportRow>,
    inventoryMovements: List<InventoryMovementDetailReportRow>,
    production: List<ProductionPerformanceReportRow>,
    productionMaterials: List<ProductionMaterialUsageReportRow>,
    quality: List<QualityReportRow>,
    maintenance: MaintenanceReportRow?,
    pnl: ProfitLossReport?,
    trial: TrialBalanceReport?,
    cash: CashFlowReport?,
    balanceSheet: BalanceSheetReport?,
    ledgerAccount: AccountEntity?,
    ledger: LedgerReport?,
    statementPartyType: String,
    statementCustomer: CustomerEntity?,
    statementSupplier: SupplierEntity?,
    partyStatement: PartyStatementPeriod?,
    agingPartyType: String,
    agingRows: List<PartyAgingReportRow>,
    expenses: List<ExpenseReportRow>,
    treasuryReport: TreasuryPeriodReport?,
    periodComparison: PeriodComparisonReport?
): ReportExportDocument = when (tab) {
    "ملخص" -> buildExecutiveReportExportDocument(executive, periodLabel, from, to)
    "المبيعات" -> buildSalesReportExportDocument(customers, salesProductQuantities, provinces, commissions, periodLabel, from, to)
    "المشتريات" -> buildPurchasesReportExportDocument(suppliers, periodLabel, from, to)
    "المخزون" -> buildInventoryReportExportDocument(inventory, inventoryActivity, inventoryExpiryLots, inventoryMovements, periodLabel, from, to)
    "الإنتاج" -> buildProductionReportExportDocument(production, productionMaterials, maintenance, periodLabel, from, to)
    "الجودة" -> buildQualityReportExportDocument(quality, periodLabel, from, to)
    "المالية" -> buildFinanceReportExportDocument(pnl, trial, cash, balanceSheet, periodLabel, from, to)
    "المصروفات" -> buildExpenseAnalysisExportDocument(expenses, periodLabel, from, to)
    "الخزائن والبنوك" -> buildTreasuryReportExportDocument(treasuryReport, periodLabel, from, to)
    "مقارنة الفترات" -> buildPeriodComparisonExportDocument(periodComparison, periodLabel, from, to)
    "الأستاذ العام" -> buildLedgerReportExportDocument(ledgerAccount, ledger, periodLabel, from, to)
    "أعمار الديون" -> buildAgingReportExportDocument(agingPartyType, agingRows, to)
    "كشف الأطراف" -> buildPartyStatementExportDocument(
        statementPartyType, statementCustomer, statementSupplier, partyStatement, periodLabel, from, to
    )
    else -> ReportExportDocument(
        title = "تقرير Fush ERP",
        subtitle = reportPeriodSubtitle(periodLabel, from, to),
        notes = listOf("لا توجد بيانات تقرير متاحة لهذا القسم.")
    )
}

private fun buildExecutiveReportExportDocument(
    r: ExecutiveReportRow?,
    periodLabel: String,
    from: Long,
    to: Long
): ReportExportDocument {
    val summary = mutableListOf("الفترة" to "$periodLabel • ${fmtDate(from)} — ${fmtDate(to)}")
    if (r != null) {
        summary += listOf(
            "إجمالي المبيعات" to money(r.grossSalesBase),
            "مرتجعات المبيعات" to money(r.salesReturnsBase),
            "صافي المبيعات" to money(ReportMath.net(r.grossSalesBase, r.salesReturnsBase)),
            "صافي التحصيل" to money(r.collectionsBase),
            "إجمالي المشتريات" to money(r.grossPurchasesBase),
            "مرتجعات المشتريات" to money(r.purchaseReturnsBase),
            "صافي المشتريات" to money(ReportMath.net(r.grossPurchasesBase, r.purchaseReturnsBase)),
            "قيمة المخزون" to money(r.inventoryValueBase),
            "الذمم المدينة" to money(r.receivablesBase),
            "المتأخر" to money(r.overdueBase),
            "أوامر الإنتاج" to r.productionOrders.toString(),
            "كمية مقبولة إجمالي" to qty(r.acceptedQtyBase),
            "مقبول 60 مل" to qty(r.accepted60QtyBase),
            "مقبول 200 مل" to qty(r.accepted200QtyBase),
            "هالك" to qty(r.scrapQtyBase),
            "CAPA/NC مفتوحة" to r.openNonConformances.toString(),
            "تكلفة الصيانة" to money(r.maintenanceCostBase)
        )
    }
    return ReportExportDocument(
        title = "الملخص التنفيذي — Fush ERP",
        subtitle = reportPeriodSubtitle(periodLabel, from, to),
        summary = summary,
        notes = if (r == null) listOf("لا توجد بيانات ملخص للفترة المحددة.") else emptyList()
    )
}

private fun buildSalesReportExportDocument(
    customers: List<CustomerSalesReportRow>,
    productQuantities: List<ProductSalesQuantityReportRow>,
    provinces: List<ProvinceProfitabilityRow>,
    commissions: List<CommissionReportRow>,
    periodLabel: String,
    from: Long,
    to: Long
): ReportExportDocument {
    val net60 = productQuantities.filter { ReportMath.productVolumeMl(it.code, it.productName) == 60 }.sumOf { it.netQtyBase }
    val net200 = productQuantities.filter { ReportMath.productVolumeMl(it.code, it.productName) == 200 }.sumOf { it.netQtyBase }
    val summary = listOf(
        "الفترة" to "$periodLabel • ${fmtDate(from)} — ${fmtDate(to)}",
        "صافي علب 60 مل المباعة" to qty(net60),
        "صافي علب 200 مل المباعة" to qty(net200),
        "عدد العملاء" to customers.size.toString(),
        "عدد الفواتير" to customers.sumOf { it.invoiceCount }.toString(),
        "إجمالي المبيعات" to money(customers.sumOf { it.grossSalesBase }),
        "المرتجعات" to money(customers.sumOf { it.returnsBase }),
        "التحصيل" to money(customers.sumOf { it.collectionsBase }),
        "الرصيد المستحق" to money(customers.sumOf { it.outstandingBase }),
        "ربح المحافظات" to money(provinces.sumOf { it.profitBase }),
        "صافي العمولات" to money(commissions.sumOf { it.netCommissionBase })
    )
    val productQuantityTable = ReportExportTable(
        title = "كميات المنتجات المباعة",
        headers = listOf("المنتج", "الكود", "مباع", "مرتجع", "الصافي"),
        rows = productQuantities.map { q ->
            listOf(q.productName, q.code, qty(q.grossQtyBase), qty(q.returnedQtyBase), qty(q.netQtyBase))
        }
    )
    val provinceTable = ReportExportTable(
        title = "ربحية المحافظات",
        headers = listOf("المحافظة", "الفواتير", "الإيراد", "التكلفة", "العمولات", "التكاليف الجغرافية", "الربح", "الهامش"),
        rows = provinces.map { p ->
            listOf(
                p.province,
                p.invoiceCount.toString(),
                money(p.netRevenueBase),
                money(p.netCogsBase),
                money(p.commissionBase),
                money(p.geographicCostBase),
                money(p.profitBase),
                "%.1f%%".format(Locale.US, ReportMath.margin(p.profitBase, p.netRevenueBase))
            )
        }
    )
    val customerTable = ReportExportTable(
        title = "المبيعات حسب العميل",
        headers = listOf("العميل", "المحافظة", "الفواتير", "المبيعات", "المرتجعات", "التحصيل", "الرصيد"),
        rows = customers.map { c ->
            listOf(
                c.customerName,
                c.province,
                c.invoiceCount.toString(),
                money(c.grossSalesBase),
                money(c.returnsBase),
                money(c.collectionsBase),
                money(c.outstandingBase)
            )
        }
    )
    val commissionTable = ReportExportTable(
        title = "العمولات",
        headers = listOf("المستفيد", "مستحقة", "معكوسة", "الصافي"),
        rows = commissions.map { c ->
            listOf(c.beneficiary.ifBlank { "غير محدد" }, money(c.earnedBase), money(c.reversedBase), money(c.netCommissionBase))
        }
    )
    return ReportExportDocument(
        title = "تقرير المبيعات — Fush ERP",
        subtitle = reportPeriodSubtitle(periodLabel, from, to),
        summary = summary,
        tables = listOf(productQuantityTable, provinceTable, customerTable, commissionTable)
    )
}

private fun buildPurchasesReportExportDocument(
    rows: List<SupplierPurchaseReportRow>,
    periodLabel: String,
    from: Long,
    to: Long
): ReportExportDocument = ReportExportDocument(
    title = "تقرير المشتريات — Fush ERP",
    subtitle = reportPeriodSubtitle(periodLabel, from, to),
    summary = listOf(
        "الفترة" to "$periodLabel • ${fmtDate(from)} — ${fmtDate(to)}",
        "الموردون" to rows.size.toString(),
        "الفواتير" to rows.sumOf { it.invoiceCount }.toString(),
        "إجمالي المشتريات" to money(rows.sumOf { it.grossPurchasesBase }),
        "المرتجعات" to money(rows.sumOf { it.returnsBase }),
        "صافي المشتريات" to money(rows.sumOf { it.netPurchasesBase })
    ),
    tables = listOf(
        ReportExportTable(
            title = "المشتريات حسب المورد",
            headers = listOf("المورد", "الفواتير", "الإجمالي", "المرتجع", "الصافي"),
            rows = rows.map { r -> listOf(r.supplierName, r.invoiceCount.toString(), money(r.grossPurchasesBase), money(r.returnsBase), money(r.netPurchasesBase)) }
        )
    )
)

private fun buildInventoryReportExportDocument(
    rows: List<InventoryValuationReportRow>,
    activityRows: List<InventoryActivityReportRow>,
    expiryRows: List<InventoryExpiryLotReportRow>,
    movements: List<InventoryMovementDetailReportRow>,
    periodLabel: String,
    from: Long,
    to: Long
): ReportExportDocument {
    val movementSummary = InventoryReportMath.movementSummary(movements)
    val activity = activityRows.map { InventoryReportMath.activity(it, to) }
    val expiry = expiryRows.map { InventoryReportMath.expiry(it, to) }
    val slowCount = activity.count { it.status != "نشط" && it.status != "لم يُصرف بعد" }
    return ReportExportDocument(
        title = "تقرير المخزون الاحترافي — Fush ERP",
        subtitle = "الرصيد حتى ${fmtDate(to)} • حركة الفترة: $periodLabel (${fmtDate(from)} — ${fmtDate(to)})",
        summary = listOf(
            "تاريخ الرصيد" to fmtDate(to),
            "عدد الأصناف" to rows.size.toString(),
            "إجمالي قيمة المخزون" to money(rows.sumOf { it.inventoryValueBase }),
            "أصناف عند/تحت إعادة الطلب" to rows.count { it.reorderLevel > 0.0 && it.quantityBase <= it.reorderLevel }.toString(),
            "وارد الفترة" to qty(movementSummary.inboundQtyBase),
            "صادر الفترة" to qty(movementSummary.outboundQtyBase),
            "عدد حركات الفترة" to movementSummary.movementCount.toString(),
            "بطيء/راكد/بدون صرف ≥90" to slowCount.toString(),
            "تشغيلات منتهية" to expiry.count { it.status == "منتهي" }.toString(),
            "تشغيلات تنتهي ≤30 يوم" to expiry.count { it.daysToExpiry in 0L..30L }.toString()
        ),
        tables = listOf(
            ReportExportTable(
                title = "تقييم المخزون",
                headers = listOf("الكود", "الصنف", "الوحدة", "الرصيد", "القيمة", "حد إعادة الطلب", "الحالة"),
                rows = rows.map { r -> listOf(
                    r.code, r.itemName, r.baseUnitName, qty(r.quantityBase), money(r.inventoryValueBase), qty(r.reorderLevel),
                    if (r.reorderLevel > 0.0 && r.quantityBase <= r.reorderLevel) "إعادة طلب" else "طبيعي"
                ) }
            ),
            ReportExportTable(
                title = "عمر وحركة الرصيد",
                headers = listOf("الكود", "الصنف", "الوحدة", "الرصيد", "القيمة", "أول توريد", "آخر حركة", "آخر صرف", "أيام منذ آخر صرف", "التصنيف"),
                rows = activity.map { a ->
                    val r = a.source
                    listOf(r.code, r.itemName, r.baseUnitName, qty(r.quantityBase), money(r.inventoryValueBase),
                        r.firstInboundDate?.let { fmtDate(it) } ?: "—",
                        r.lastMovementDate?.let { fmtDate(it) } ?: "—",
                        r.lastOutboundDate?.let { fmtDate(it) } ?: "لم يُصرف",
                        a.daysSinceLastOutbound?.toString() ?: "—", a.status)
                }
            ),
            ReportExportTable(
                title = "التشغيلات والصلاحية ذات الرصيد الموجب",
                headers = listOf("المخزن", "الكود", "الصنف", "الوحدة", "التشغيلة", "الانتهاء", "الأيام المتبقية", "الحالة", "الرصيد", "القيمة"),
                rows = expiry.map { e ->
                    val r = e.source
                    listOf(r.warehouseName, r.code, r.itemName, r.baseUnitName, r.lotNo ?: "—", fmtDate(r.expiryDate),
                        e.daysToExpiry.toString(), e.status, qty(r.quantityBase), money(r.inventoryValueBase))
                }
            ),
            ReportExportTable(
                title = "حركات المخزون خلال الفترة",
                headers = listOf("التاريخ", "المخزن", "الكود", "الصنف", "النوع", "وارد", "صادر", "تكلفة الوحدة", "قيمة الحركة", "التشغيلة", "الانتهاء", "المرجع"),
                rows = movements.map { r -> listOf(
                    fmtDate(r.movementDate), r.warehouseName, r.code, r.itemName, r.movementType,
                    if (r.quantityBase > 0.0) qty(r.quantityBase) else "—",
                    if (r.quantityBase < 0.0) qty(kotlin.math.abs(r.quantityBase)) else "—",
                    money(r.unitCostBase), money(kotlin.math.abs(r.movementValueBase)), r.lotNo ?: "—",
                    r.expiryDate?.let { fmtDate(it) } ?: "—", "${r.referenceType} #${r.referenceId}") }
            )
        ),
        notes = listOf(
            "تقييم المخزون وعمر الرصيد محسوبان حتى نهاية الفترة المختارة، بينما جدول الحركة يعرض ما حدث داخل الفترة فقط.",
            "البطيء يبدأ بعد 90 يومًا من آخر صرف فعلي، والراكد بعد 180 يومًا. الأصناف التي لم يُصرف منها قط تظهر بشكل مستقل.",
            "تقارير الصلاحية تشمل فقط التشغيلات التي لديها تاريخ انتهاء مسجل ورصيد موجب حتى تاريخ التقرير."
        )
    )
}

private fun buildQualityReportExportDocument(
    rows: List<QualityReportRow>,
    periodLabel: String,
    from: Long,
    to: Long
): ReportExportDocument = ReportExportDocument(
    title = "تقرير الجودة — Fush ERP",
    subtitle = reportPeriodSubtitle(periodLabel, from, to),
    summary = listOf(
        "الفترة" to "$periodLabel • ${fmtDate(from)} — ${fmtDate(to)}",
        "الدفعات" to rows.size.toString(),
        "فحوص PASS" to rows.sumOf { it.passChecks }.toString(),
        "فحوص FAIL" to rows.sumOf { it.failChecks }.toString(),
        "NC/CAPA مفتوحة" to rows.sumOf { it.openNonConformances }.toString(),
        "مقبول" to qty(rows.sumOf { it.acceptedQtyBase }),
        "مرفوض" to qty(rows.sumOf { it.rejectedQtyBase }),
        "هالك" to qty(rows.sumOf { it.scrapQtyBase })
    ),
    tables = listOf(
        ReportExportTable(
            title = "الدفعات والجودة",
            headers = listOf("الدفعة", "تاريخ الإنتاج", "الحالة", "PASS", "FAIL", "NC مفتوحة", "مقبول", "مرفوض", "هالك"),
            rows = rows.map { r ->
                listOf(
                    r.batchNo,
                    fmtDate(r.manufactureDate),
                    r.batchStatus,
                    r.passChecks.toString(),
                    r.failChecks.toString(),
                    r.openNonConformances.toString(),
                    qty(r.acceptedQtyBase),
                    qty(r.rejectedQtyBase),
                    qty(r.scrapQtyBase)
                )
            }
        )
    )
)

private fun buildFinanceReportExportDocument(
    pnl: ProfitLossReport?,
    trial: TrialBalanceReport?,
    cash: CashFlowReport?,
    balanceSheet: BalanceSheetReport?,
    periodLabel: String,
    from: Long,
    to: Long
): ReportExportDocument {
    val summary = mutableListOf("الفترة" to "$periodLabel • ${fmtDate(from)} — ${fmtDate(to)}")
    pnl?.let {
        summary += "الإيرادات" to money(it.revenue)
        summary += "المصروفات" to money(it.expenses)
        summary += "صافي الربح" to money(it.netProfit)
    }
    cash?.let {
        summary += "رصيد نقدي افتتاحي" to money(it.openingCash)
        summary += "المتحصلات النقدية" to money(it.cashInflows)
        summary += "المدفوعات النقدية" to money(it.cashOutflows)
        summary += "صافي حركة النقد" to money(it.netCashMovement)
        summary += "الرصيد النقدي الختامي" to money(it.closingCash)
    }
    trial?.let {
        summary += "إجمالي حركة المدين" to money(it.totalDebitMovement)
        summary += "إجمالي حركة الدائن" to money(it.totalCreditMovement)
        summary += "إجمالي رصيد المدين" to money(it.totalDebitBalance)
        summary += "إجمالي رصيد الدائن" to money(it.totalCreditBalance)
    }
    balanceSheet?.let {
        summary += "إجمالي الأصول" to money(it.assets)
        summary += "إجمالي الالتزامات" to money(it.liabilities)
        summary += "حقوق الملكية قبل ربح الفترة" to money(it.equityBeforeCurrentProfit)
        summary += "ربح/خسارة حتى التاريخ" to money(it.currentProfit)
        summary += "الالتزامات وحقوق الملكية" to money(it.totalLiabilitiesAndEquity)
        summary += "فرق المركز المالي" to money(it.difference)
    }
    val tables = mutableListOf<ReportExportTable>()
    pnl?.let {
        tables += ReportExportTable(
            title = "الإيرادات حسب الحساب",
            headers = listOf("الحساب", "القيمة"),
            rows = it.revenueByAccount.map { row -> listOf(row.first, money(row.second)) }
        )
        tables += ReportExportTable(
            title = "المصروفات حسب الحساب",
            headers = listOf("الحساب", "القيمة"),
            rows = it.expenseByAccount.map { row -> listOf(row.first, money(row.second)) }
        )
    }
    trial?.let {
        tables += ReportExportTable(
            title = "ميزان المراجعة",
            headers = listOf("الكود", "الحساب", "النوع", "حركة مدين", "حركة دائن", "رصيد مدين", "رصيد دائن"),
            rows = it.lines.map { r ->
                listOf(r.code, r.nameAr, r.type, money(r.debitMovement), money(r.creditMovement), money(r.debitBalance), money(r.creditBalance))
            }
        )
    }
    balanceSheet?.let { sheet ->
        tables += ReportExportTable(
            title = "الأصول",
            headers = listOf("الحساب", "الرصيد"),
            rows = sheet.assetsByAccount.map { row -> listOf(row.first, money(row.second)) }
        )
        tables += ReportExportTable(
            title = "الالتزامات",
            headers = listOf("الحساب", "الرصيد"),
            rows = sheet.liabilitiesByAccount.map { row -> listOf(row.first, money(row.second)) }
        )
        tables += ReportExportTable(
            title = "حقوق الملكية",
            headers = listOf("الحساب", "الرصيد"),
            rows = sheet.equityByAccount.map { row -> listOf(row.first, money(row.second)) }
        )
    }
    return ReportExportDocument(
        title = "التقرير المالي — Fush ERP",
        subtitle = reportPeriodSubtitle(periodLabel, from, to),
        summary = summary,
        tables = tables,
        notes = if (pnl == null && trial == null && cash == null && balanceSheet == null) listOf("لا توجد بيانات مالية متاحة للفترة المحددة.") else emptyList()
    )
}

private fun buildLedgerReportExportDocument(
    account: AccountEntity?,
    ledger: LedgerReport?,
    periodLabel: String,
    from: Long,
    to: Long
): ReportExportDocument {
    val accountLabel = account?.let { "${it.code} — ${it.nameAr}" } ?: "لم يتم اختيار حساب"
    return ReportExportDocument(
        title = "دفتر الأستاذ العام — Fush ERP",
        subtitle = reportPeriodSubtitle(periodLabel, from, to),
        summary = listOf(
            "الحساب" to accountLabel,
            "الرصيد الافتتاحي" to (ledger?.let { reportBalanceLabel(it.openingBalance) } ?: "—"),
            "إجمالي المدين" to (ledger?.let { money(it.lines.sumOf { line -> line.debit }) } ?: "—"),
            "إجمالي الدائن" to (ledger?.let { money(it.lines.sumOf { line -> line.credit }) } ?: "—"),
            "الرصيد الختامي" to (ledger?.let { reportBalanceLabel(it.closingBalance) } ?: "—")
        ),
        tables = ledger?.let { report ->
            listOf(
                ReportExportTable(
                    title = "حركة الحساب",
                    headers = listOf("التاريخ", "رقم القيد", "المصدر", "البيان", "مدين", "دائن", "الرصيد"),
                    rows = report.lines.map { line ->
                        listOf(
                            fmtDate(line.entryDate),
                            line.entryNo,
                            line.sourceType,
                            line.description,
                            money(line.debit),
                            money(line.credit),
                            reportBalanceLabel(line.runningBalance)
                        )
                    }
                )
            )
        } ?: emptyList(),
        notes = if (ledger == null) listOf("اختر حساب ترحيل لإعداد دفتر الأستاذ.") else emptyList()
    )
}

private fun buildExpenseAnalysisExportDocument(
    rows: List<ExpenseReportRow>,
    periodLabel: String,
    from: Long,
    to: Long
): ReportExportDocument {
    val analytics = ExpenseReportAnalyticsMath.build(rows)
    fun breakdownTable(title: String, data: List<ExpenseBreakdownRow>) = ReportExportTable(
        title = title,
        headers = listOf("البعد", "عدد السندات", "الإجمالي", "النسبة"),
        rows = data.map { listOf(it.label, it.voucherCount.toString(), money(it.amountBase), "%.1f%%".format(Locale.US, it.sharePercent)) }
    )
    return ReportExportDocument(
        title = "تحليل المصروفات والأبعاد — Fush ERP",
        subtitle = reportPeriodSubtitle(periodLabel, from, to),
        summary = listOf(
            "إجمالي المصروفات" to money(analytics.totalAmountBase),
            "عدد سندات المصروف" to analytics.voucherCount.toString(),
            "متوسط السند" to money(analytics.averageVoucherBase),
            "عدد المرفقات" to analytics.attachmentCount.toString(),
            "حسابات المصروف" to analytics.byAccount.size.toString(),
            "مراكز التكلفة" to analytics.byCostCenter.count { it.label != "غير محدد" } .toString()
        ),
        tables = listOf(
            breakdownTable("حسب حساب المصروف", analytics.byAccount),
            breakdownTable("حسب مركز التكلفة", analytics.byCostCenter),
            breakdownTable("حسب الوحدة التنظيمية", analytics.byOrganizationUnit),
            breakdownTable("حسب الموظف / مندوب المبيعات", analytics.byEmployeeOrRep),
            breakdownTable("حسب طريقة الدفع", analytics.byPaymentMethod),
            ReportExportTable(
                title = "التفصيل الكامل للمصروفات",
                headers = listOf(
                    "التاريخ", "رقم السند", "حساب المصروف", "المبلغ", "العملة الأصلية", "طريقة الدفع",
                    "مركز التكلفة", "الوحدة التنظيمية", "الموظف", "المندوب", "العميل", "المورد",
                    "المرجع", "الصنف", "المرفقات", "البيان"
                ),
                rows = rows.map { row ->
                    listOf(
                        fmtDate(row.voucherDate),
                        row.voucherNo,
                        listOf(row.expenseAccountCode, row.expenseAccountName).filter { it.isNotBlank() }.joinToString(" — "),
                        money(row.amountBase),
                        "${qty(row.amountOriginal)} ${row.currencyCode}",
                        row.paymentMethod,
                        listOf(row.costCenterCode, row.costCenterName).filter { it.isNotBlank() }.joinToString(" — "),
                        row.organizationUnit,
                        row.employeeName,
                        row.salesRepName,
                        row.customerName,
                        row.supplierName,
                        listOf(row.referenceNo, row.referenceLabel).filter { it.isNotBlank() }.joinToString(" — "),
                        row.itemName,
                        row.attachmentCount.toString(),
                        row.description
                    )
                }
            )
        ),
        notes = if (rows.isEmpty()) listOf("لا توجد مصروفات مرحلة خلال الفترة المحددة.") else listOf(
            "يعتمد التقرير على سندات المصروف المرحلة فقط ويعرض الأبعاد المسجلة وقت إنشاء السند.",
            "القيم المعروضة في الإجماليات هي بالعملة الأساسية للنظام."
        )
    )
}

private fun buildAgingReportExportDocument(
    partyType: String,
    rows: List<PartyAgingReportRow>,
    asOf: Long
): ReportExportDocument {
    val typeLabel = if (partyType == "SUPPLIER") "الموردين" else "العملاء"
    val current = rows.sumOf { it.currentBase }
    val overdue = rows.sumOf { it.overdueBase }
    val unapplied = rows.sumOf { it.unappliedBase }
    val total = rows.sumOf { it.totalBalanceBase }
    return ReportExportDocument(
        title = "أعمار ديون $typeLabel — Fush ERP",
        subtitle = "حتى ${fmtDate(asOf)} • يعتمد التصنيف على تاريخ استحقاق كل فاتورة.",
        summary = listOf(
            "نوع الأطراف" to typeLabel,
            "عدد الأطراف" to rows.size.toString(),
            "غير مستحق/حالي" to money(current),
            "إجمالي المتأخر" to money(overdue),
            "سندات غير مخصصة" to money(unapplied),
            "صافي الرصيد" to money(total)
        ),
        tables = listOf(
            ReportExportTable(
                title = "تفصيل أعمار الديون",
                headers = listOf("الطرف", "حالي", "1–30", "31–60", "61–90", ">90", "سندات غير مخصصة", "صافي الرصيد"),
                rows = rows.map { row ->
                    listOf(
                        row.partyName,
                        money(row.currentBase),
                        money(row.days1To30Base),
                        money(row.days31To60Base),
                        money(row.days61To90Base),
                        money(row.over90Base),
                        money(row.unappliedBase),
                        money(row.totalBalanceBase)
                    )
                }
            )
        ),
        notes = listOf(
            "السندات المباشرة غير المرتبطة بتخصيص فاتورة تظهر في عمود مستقل ولا يوزعها النظام افتراضياً على فواتير محددة.",
            "السند المعكوس يحتسب تاريخياً حتى تاريخ العكس ثم يلغى أثره بعد تاريخ العكس."
        )
    )
}

private fun buildPartyStatementExportDocument(
    partyType: String,
    customer: CustomerEntity?,
    supplier: SupplierEntity?,
    statement: PartyStatementPeriod?,
    periodLabel: String,
    from: Long,
    to: Long
): ReportExportDocument {
    val partyLabel = if (partyType == "SUPPLIER") {
        supplier?.let { "${it.code} — ${it.nameAr}" }
    } else {
        customer?.let { "${it.code} — ${it.nameAr}" }
    } ?: "لم يتم اختيار الطرف"
    val typeLabel = if (partyType == "SUPPLIER") "مورد" else "عميل"
    return ReportExportDocument(
        title = "كشف حساب $typeLabel — Fush ERP",
        subtitle = reportPeriodSubtitle(periodLabel, from, to),
        summary = listOf(
            "نوع الطرف" to typeLabel,
            "الطرف" to partyLabel,
            "الرصيد الافتتاحي" to (statement?.let { partyBalanceLabel(it.openingBalance, partyType) } ?: "—"),
            "إجمالي المدين" to (statement?.let { money(it.totalDebit) } ?: "—"),
            "إجمالي الدائن" to (statement?.let { money(it.totalCredit) } ?: "—"),
            "الرصيد الختامي" to (statement?.let { partyBalanceLabel(it.closingBalance, partyType) } ?: "—")
        ),
        tables = statement?.let { report ->
            listOf(
                ReportExportTable(
                    title = "الحركة التفصيلية",
                    headers = listOf("التاريخ", "نوع الحركة", "المرجع", "البيان", "مدين", "دائن", "الرصيد"),
                    rows = report.lines.map { line ->
                        listOf(
                            fmtDate(line.eventDate),
                            partyEventTypeAr(line.eventType),
                            line.referenceNo,
                            line.description.ifBlank { "—" },
                            money(line.debitBase),
                            money(line.creditBase),
                            partyBalanceLabel(line.runningBalance, partyType)
                        )
                    }
                )
            )
        } ?: emptyList(),
        notes = if (statement == null) listOf("اختر العميل أو المورد لإعداد كشف الحساب.") else emptyList()
    )
}

private fun reportTitle(tab: String): String = when (tab) {
    "ملخص" -> "الملخص التنفيذي"
    "المبيعات" -> "تقرير المبيعات"
    "المشتريات" -> "تقرير المشتريات"
    "المخزون" -> "تقرير المخزون"
    "الإنتاج" -> "تقرير الإنتاج"
    "الجودة" -> "تقرير الجودة"
    "المالية" -> "التقرير المالي"
    "الخزائن والبنوك" -> "حركة الخزائن والبنوك"
    "مقارنة الفترات" -> "مقارنة الفترات والانحرافات"
    "المصروفات" -> "تحليل المصروفات والأبعاد"
    "الأستاذ العام" -> "دفتر الأستاذ العام"
    "أعمار الديون" -> "أعمار الديون"
    "كشف الأطراف" -> "كشف حساب الأطراف"
    else -> "تقرير"
}

private fun reportBaseName(tab: String): String = when (tab) {
    "ملخص" -> "FushERP-Executive-Report"
    "المبيعات" -> "FushERP-Sales-Report"
    "المشتريات" -> "FushERP-Purchases-Report"
    "المخزون" -> "FushERP-Inventory-Report"
    "الإنتاج" -> "FushERP-Production-Report"
    "الجودة" -> "FushERP-Quality-Report"
    "المالية" -> "FushERP-Finance-Report"
    "الخزائن والبنوك" -> "FushERP-Treasury-Bank-Movement"
    "مقارنة الفترات" -> "FushERP-Period-Comparison"
    "المصروفات" -> "FushERP-Expense-Dimensions"
    "الأستاذ العام" -> "FushERP-General-Ledger"
    "أعمار الديون" -> "FushERP-AR-AP-Aging"
    "كشف الأطراف" -> "FushERP-Party-Statement"
    else -> "FushERP-Report"
}

private fun reportPeriodSubtitle(periodLabel: String, from: Long, to: Long): String =
    "الفترة: $periodLabel • ${fmtDate(from)} — ${fmtDate(to)} • تم إنشاء التقرير من بيانات النظام الحالية."

private fun buildPeriodComparisonExportDocument(
    report: PeriodComparisonReport?,
    periodLabel: String,
    from: Long,
    to: Long
): ReportExportDocument {
    if (report == null || !report.hasComparablePeriod) {
        return ReportExportDocument(
            title = "مقارنة الفترات والانحرافات — Fush ERP",
            subtitle = reportPeriodSubtitle(periodLabel, from, to),
            notes = listOf("لا توجد فترة سابقة مكافئة لهذه الفترة. اختر فترة محددة بدل «كل الفترة» لإجراء المقارنة.")
        )
    }
    val rows = report.metrics.map { metric ->
        listOf(
            metric.label,
            money(metric.currentBase),
            money(metric.previousBase),
            money(metric.differenceBase),
            metric.percentChange?.let { "%.1f%%".format(Locale.US, it) } ?: "—"
        )
    }
    return ReportExportDocument(
        title = "مقارنة الفترات والانحرافات — Fush ERP",
        subtitle = "الحالية: ${fmtDate(report.currentFrom)} — ${fmtDate(report.currentTo)} • السابقة: ${fmtDate(report.previousFrom!!)} — ${fmtDate(report.previousTo!!)}",
        summary = listOf(
            "الفترة المختارة" to periodLabel,
            "عدد المؤشرات" to report.metrics.size.toString()
        ),
        tables = listOf(
            ReportExportTable(
                title = "مقارنة المؤشرات المالية",
                headers = listOf("المؤشر", "الحالي", "السابق", "الفرق", "نسبة التغير"),
                rows = rows
            )
        ),
        notes = listOf(
            "الفرق = الفترة الحالية - الفترة السابقة.",
            "عندما تكون قيمة الفترة السابقة صفراً لا تُعرض نسبة تغير مضللة، ويظهر الرمز — بدلاً منها.",
            "ارتفاع أو انخفاض المؤشر لا يُصنف تلقائياً كتحسن أو تراجع؛ يجب تفسيره حسب طبيعة المؤشر."
        )
    )
}

private fun buildProductionReportExportDocument(
    rows: List<ProductionPerformanceReportRow>,
    materialUsage: List<ProductionMaterialUsageReportRow>,
    maintenance: MaintenanceReportRow?,
    periodLabel: String,
    from: Long,
    to: Long
): ReportExportDocument {
    val planned = rows.sumOf { it.plannedQtyBase }
    val actual = rows.sumOf { it.actualQtyBase }
    val accepted = rows.sumOf { it.acceptedQtyBase }
    val accepted60 = rows.filter { ReportMath.productVolumeMl(it.productCode, it.productName) == 60 }.sumOf { it.acceptedQtyBase }
    val accepted200 = rows.filter { ReportMath.productVolumeMl(it.productCode, it.productName) == 200 }.sumOf { it.acceptedQtyBase }
    val rejected = rows.sumOf { it.rejectedQtyBase }
    val scrap = rows.sumOf { it.scrapQtyBase }
    val materialCost = rows.sumOf { it.materialCostBase }
    val laborCost = rows.sumOf { it.laborCostBase }
    val totalCost = rows.sumOf { it.actualCostBase }
    val summary = mutableListOf(
        "الفترة" to "$periodLabel • ${fmtDate(from)} — ${fmtDate(to)}",
        "أوامر الإنتاج" to rows.size.toString(),
        "المخطط" to qty(planned),
        "الناتج الفعلي" to qty(actual),
        "المقبول الإجمالي" to qty(accepted),
        "مقبول 60 مل" to qty(accepted60),
        "مقبول 200 مل" to qty(accepted200),
        "المرفوض" to qty(rejected),
        "الهالك" to qty(scrap),
        "تحقيق الخطة" to "%.1f%%".format(Locale.US, ReportMath.percent(actual, planned)),
        "نسبة القبول" to "%.1f%%".format(Locale.US, ReportMath.percent(accepted, actual)),
        "نسبة الهالك" to "%.1f%%".format(Locale.US, ReportMath.percent(scrap, actual)),
        "تكلفة المواد" to money(materialCost),
        "تكلفة العمالة" to money(laborCost),
        "إجمالي تكلفة الإنتاج" to money(totalCost),
        "متوسط تكلفة المقبول/وحدة" to money(ReportMath.unitCost(totalCost, accepted))
    )
    maintenance?.let {
        summary += "أوامر الصيانة" to it.workOrderCount.toString()
        summary += "الصيانة المفتوحة" to it.openCount.toString()
        summary += "توقف الصيانة/دقيقة" to it.downtimeMinutes.toString()
        summary += "تكلفة الصيانة" to money(it.costBase)
    }
    val materialsTable = ReportExportTable(
        title = "استهلاك المواد الفعلي",
        headers = listOf("المادة", "الكود", "الوحدة", "الكمية المصروفة", "متوسط تكلفة الوحدة", "إجمالي التكلفة", "أوامر الإنتاج"),
        rows = materialUsage.map { m ->
            listOf(
                m.itemName,
                m.code,
                m.unitName,
                qty(m.issuedQtyBase),
                money(m.averageUnitCostBase),
                money(m.totalCostBase),
                m.orderCount.toString()
            )
        }
    )
    val ordersTable = ReportExportTable(
        title = "أوامر الإنتاج",
        headers = listOf("رقم الأمر", "المنتج", "الدفعة", "التاريخ", "الحالة", "مخطط", "فعلي", "مقبول", "مرفوض", "هالك", "تكلفة المواد", "تكلفة العمالة", "التكلفة الفعلية", "تكلفة المقبول/وحدة"),
        rows = rows.map { r ->
            listOf(
                r.orderNo,
                r.productName,
                r.batchNo ?: "بدون دفعة",
                fmtDate(r.manufactureDate ?: r.plannedDate),
                r.status,
                qty(r.plannedQtyBase),
                qty(r.actualQtyBase),
                qty(r.acceptedQtyBase),
                qty(r.rejectedQtyBase),
                qty(r.scrapQtyBase),
                money(r.materialCostBase),
                money(r.laborCostBase),
                money(r.actualCostBase),
                money(ReportMath.unitCost(r.actualCostBase, r.acceptedQtyBase))
            )
        }
    )
    return ReportExportDocument(
        title = "تقرير الإنتاج والتحليل — Fush ERP",
        subtitle = "تم إنشاء التقرير من البيانات المسجلة فعليًا في النظام.",
        summary = summary,
        tables = listOf(materialsTable, ordersTable),
        notes = listOf("تكلفة المواد مبنية على تكلفة الصرف التاريخية المسجلة لأوامر الإنتاج.")
    )
}

@Composable private fun QualityTab(rows: List<QualityReportRow>) {
    val pass = rows.sumOf { it.passChecks }
    val fail = rows.sumOf { it.failChecks }
    val openNc = rows.sumOf { it.openNonConformances }
    val accepted = rows.sumOf { it.acceptedQtyBase }
    val rejected = rows.sumOf { it.rejectedQtyBase }
    val scrap = rows.sumOf { it.scrapQtyBase }
    val inspected = accepted + rejected
    val acceptanceRate = ReportMath.percent(accepted, inspected)

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            FushSectionHeader("مراقبة الجودة", "نتائج الفحوص وعدم المطابقة وقرارات الدفعات خلال الفترة المحددة.")
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FushMetricCard("PASS", pass.toString(), Modifier.weight(1f), "فحوص ناجحة", FushStatusTone.Success)
                FushMetricCard("FAIL", fail.toString(), Modifier.weight(1f), "فحوص فاشلة", if (fail > 0) FushStatusTone.Danger else FushStatusTone.Neutral)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FushMetricCard("NC مفتوحة", openNc.toString(), Modifier.weight(1f), "تحتاج إجراء", if (openNc > 0) FushStatusTone.Warning else FushStatusTone.Success)
                FushMetricCard("نسبة القبول", "%.1f%%".format(Locale.US, acceptanceRate), Modifier.weight(1f), "${qty(accepted)} مقبول", if (acceptanceRate >= 95.0) FushStatusTone.Success else FushStatusTone.Info)
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ملخص قرارات الجودة", style = MaterialTheme.typography.titleMedium)
                    MetricGrid(listOf(
                        "الدفعات" to rows.size.toString(),
                        "المقبول" to qty(accepted),
                        "المرفوض" to qty(rejected),
                        "الهالك" to qty(scrap)
                    ))
                }
            }
        }
        if (rows.isEmpty()) {
            item { Text("لا توجد دفعات جودة في الفترة المحددة.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(rows, key = { it.batchId }) { r ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(r.batchNo, style = MaterialTheme.typography.titleMedium)
                            Text(fmtDate(r.manufactureDate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FushStatusPill(reportProductionStatusAr(r.batchStatus), reportProductionStatusTone(r.batchStatus))
                    }
                    MetricGrid(listOf(
                        "PASS" to r.passChecks.toString(), "FAIL" to r.failChecks.toString(),
                        "NC مفتوحة" to r.openNonConformances.toString(), "مقبول" to qty(r.acceptedQtyBase),
                        "مرفوض" to qty(r.rejectedQtyBase), "هالك" to qty(r.scrapQtyBase)
                    ))
                }
            }
        }
    }
}


@Composable private fun FinanceTab(
    pnl: ProfitLossReport?,
    trial: TrialBalanceReport?,
    cash: CashFlowReport?,
    balanceSheet: BalanceSheetReport?
) {
    val trialDifference = trial?.let { abs(it.totalDebitBalance - it.totalCreditBalance) } ?: 0.0
    val trialBalanced = trial == null || trialDifference < 0.01
    val sheetBalanced = balanceSheet == null || abs(balanceSheet.difference) < 0.01
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            FushSectionHeader("التحليل المالي", "الربحية والسيولة وميزان المراجعة والمركز المالي ضمن تقرير موحد.")
        }
        item {
            pnl?.let { report ->
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth >= 700.dp) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FushMetricCard("الإيرادات", money(report.revenue), Modifier.weight(1f), "قائمة الدخل", FushStatusTone.Info)
                            FushMetricCard("المصروفات", money(report.expenses), Modifier.weight(1f), "قائمة الدخل", FushStatusTone.Neutral)
                            FushMetricCard("صافي الربح", money(report.netProfit), Modifier.weight(1f), if (report.netProfit >= 0) "ربحية موجبة" else "خسارة", if (report.netProfit >= 0) FushStatusTone.Success else FushStatusTone.Danger)
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FushMetricCard("الإيرادات", money(report.revenue), Modifier.fillMaxWidth(), "قائمة الدخل", FushStatusTone.Info)
                            FushMetricCard("المصروفات", money(report.expenses), Modifier.fillMaxWidth(), "قائمة الدخل", FushStatusTone.Neutral)
                            FushMetricCard("صافي الربح", money(report.netProfit), Modifier.fillMaxWidth(), if (report.netProfit >= 0) "ربحية موجبة" else "خسارة", if (report.netProfit >= 0) FushStatusTone.Success else FushStatusTone.Danger)
                        }
                    }
                }
            }
        }
        item {
            cash?.let {
                FushSectionHeader("السيولة النقدية", "الحركة النقدية من الرصيد الافتتاحي حتى الرصيد الختامي.")
                Spacer(Modifier.height(6.dp))
                MetricGrid(listOf(
                    "رصيد افتتاحي نقدي" to money(it.openingCash),
                    "متحصلات" to money(it.cashInflows),
                    "مدفوعات" to money(it.cashOutflows),
                    "رصيد ختامي" to money(it.closingCash)
                ))
            }
        }
        item {
            trial?.let {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("ميزان المراجعة", style = MaterialTheme.typography.titleMedium)
                            FushStatusPill(if (trialBalanced) "متزن" else "يوجد فرق", if (trialBalanced) FushStatusTone.Success else FushStatusTone.Danger)
                        }
                        MetricGrid(listOf(
                            "إجمالي المدين" to money(it.totalDebitBalance),
                            "إجمالي الدائن" to money(it.totalCreditBalance),
                            "الفرق" to money(trialDifference),
                            "عدد الحسابات" to it.lines.size.toString()
                        ))
                    }
                }
            }
        }
        items(trial?.lines ?: emptyList()) { r ->
            ReportCard("${r.code} — ${r.nameAr}", r.type, listOf("مدين" to money(r.debitBalance), "دائن" to money(r.creditBalance)))
        }
        item {
            balanceSheet?.let { sheet ->
                FushSectionHeader("قائمة المركز المالي", "الأصول والالتزامات وحقوق الملكية حتى نهاية الفترة المختارة.")
                Spacer(Modifier.height(6.dp))
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("حالة التوازن", style = MaterialTheme.typography.titleMedium)
                            FushStatusPill(if (sheetBalanced) "متزن" else "يوجد فرق", if (sheetBalanced) FushStatusTone.Success else FushStatusTone.Danger)
                        }
                        MetricGrid(listOf(
                            "الأصول" to money(sheet.assets),
                            "الالتزامات" to money(sheet.liabilities),
                            "حقوق الملكية قبل الربح" to money(sheet.equityBeforeCurrentProfit),
                            "ربح/خسارة حتى التاريخ" to money(sheet.currentProfit),
                            "الالتزامات + حقوق الملكية" to money(sheet.totalLiabilitiesAndEquity),
                            "فرق التوازن" to money(sheet.difference)
                        ))
                    }
                }
            }
        }
        if (!balanceSheet?.assetsByAccount.isNullOrEmpty()) {
            item { SectionTitle("تفصيل الأصول") }
            items(balanceSheet?.assetsByAccount ?: emptyList()) { row -> ReportCard(row.first, "أصل", listOf("الرصيد" to money(row.second))) }
        }
        if (!balanceSheet?.liabilitiesByAccount.isNullOrEmpty()) {
            item { SectionTitle("تفصيل الالتزامات") }
            items(balanceSheet?.liabilitiesByAccount ?: emptyList()) { row -> ReportCard(row.first, "التزام", listOf("الرصيد" to money(row.second))) }
        }
        if (!balanceSheet?.equityByAccount.isNullOrEmpty()) {
            item { SectionTitle("تفصيل حقوق الملكية") }
            items(balanceSheet?.equityByAccount ?: emptyList()) { row -> ReportCard(row.first, "حقوق ملكية", listOf("الرصيد" to money(row.second))) }
        }
    }
}

@Composable
private fun TreasuryReportTab(report: TreasuryPeriodReport?) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            FushSectionHeader(
                "حركة الخزائن والبنوك",
                "الرصيد الافتتاحي والتدفقات الخارجية والتحويلات الداخلية والرصيد الختامي لكل صندوق أو بنك."
            )
        }
        report?.let { r ->
            item {
                MetricGrid(listOf(
                    "الرصيد الافتتاحي" to money(r.openingBase),
                    "داخل خارجي" to money(r.externalInBase),
                    "خارج خارجي" to money(r.externalOutBase),
                    "تحويلات واردة" to money(r.transferInBase),
                    "تحويلات صادرة" to money(r.transferOutBase),
                    "الرصيد الختامي" to money(r.closingBase)
                ))
            }
            item { FushSectionHeader("حسب الخزينة", "ملخص مستقل لكل صندوق أو حساب بنكي.") }
            if (r.accounts.isEmpty()) item { Text("لا توجد حركة خزائن خلال الفترة أو قبلها.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(r.accounts, key = { it.treasuryId }) { row ->
                ReportCard(
                    "${row.code} — ${row.nameAr}",
                    listOf(row.kind, row.currencyCode, row.bankName).filter { it.isNotBlank() }.joinToString(" • "),
                    listOf(
                        "افتتاحي" to money(row.openingBase),
                        "داخل خارجي" to money(row.externalInBase),
                        "خارج خارجي" to money(row.externalOutBase),
                        "تحويل وارد" to money(row.transferInBase),
                        "تحويل صادر" to money(row.transferOutBase),
                        "ختامي" to money(row.closingBase)
                    )
                )
            }
            item { FushSectionHeader("تفصيل الحركة", "جميع حركات الخزائن في الفترة مع تمييز التحويل الداخلي وعكسه.") }
            items(r.movements, key = { "${it.treasuryId}-${it.entryId}-${it.debitBase}-${it.creditBase}" }) { movement ->
                val direction = when {
                    movement.isInternalTransfer && movement.debitBase > 0 -> "تحويل داخلي وارد"
                    movement.isInternalTransfer && movement.creditBase > 0 -> "تحويل داخلي صادر"
                    movement.debitBase > 0 -> "داخل خارجي"
                    else -> "خارج خارجي"
                }
                ReportCard(
                    "${movement.entryNo} — ${fmtDate(movement.entryDate)}",
                    "${movement.treasuryCode} — ${movement.treasuryName} • $direction",
                    listOf(
                        "مدين" to money(movement.debitBase),
                        "دائن" to money(movement.creditBase),
                        "المصدر" to movement.sourceType,
                        "البيان" to movement.description.ifBlank { "—" }
                    )
                )
            }
        }
    }
}

private fun buildTreasuryReportExportDocument(
    report: TreasuryPeriodReport?,
    periodLabel: String,
    from: Long,
    to: Long
): ReportExportDocument {
    if (report == null) return ReportExportDocument(
        title = "حركة الخزائن والبنوك — Fush ERP",
        subtitle = reportPeriodSubtitle(periodLabel, from, to),
        notes = listOf("لا توجد بيانات خزائن متاحة للفترة المحددة.")
    )
    return ReportExportDocument(
        title = "حركة الخزائن والبنوك — Fush ERP",
        subtitle = reportPeriodSubtitle(periodLabel, from, to),
        summary = listOf(
            "الرصيد الافتتاحي" to money(report.openingBase),
            "التدفقات الداخلة الخارجية" to money(report.externalInBase),
            "التدفقات الخارجة الخارجية" to money(report.externalOutBase),
            "التحويلات الداخلية الواردة" to money(report.transferInBase),
            "التحويلات الداخلية الصادرة" to money(report.transferOutBase),
            "الرصيد الختامي" to money(report.closingBase)
        ),
        tables = listOf(
            ReportExportTable(
                title = "ملخص الخزائن والبنوك",
                headers = listOf("الكود", "الخزينة", "النوع", "العملة", "افتتاحي", "داخل خارجي", "خارج خارجي", "تحويل وارد", "تحويل صادر", "ختامي"),
                rows = report.accounts.map { row -> listOf(
                    row.code, row.nameAr, row.kind, row.currencyCode,
                    money(row.openingBase), money(row.externalInBase), money(row.externalOutBase),
                    money(row.transferInBase), money(row.transferOutBase), money(row.closingBase)
                ) }
            ),
            ReportExportTable(
                title = "تفصيل الحركة",
                headers = listOf("التاريخ", "المستند", "الخزينة", "التصنيف", "مدين", "دائن", "المصدر", "البيان"),
                rows = report.movements.map { movement ->
                    val classification = when {
                        movement.isInternalTransfer && movement.debitBase > 0 -> "تحويل داخلي وارد"
                        movement.isInternalTransfer && movement.creditBase > 0 -> "تحويل داخلي صادر"
                        movement.debitBase > 0 -> "داخل خارجي"
                        else -> "خارج خارجي"
                    }
                    listOf(
                        fmtDate(movement.entryDate), movement.entryNo,
                        "${movement.treasuryCode} — ${movement.treasuryName}", classification,
                        money(movement.debitBase), money(movement.creditBase), movement.sourceType, movement.description
                    )
                }
            )
        )
    )
}

@Composable
private fun LedgerReportTab(
    accounts: List<AccountEntity>,
    selectedAccountId: Long?,
    onSelectedAccountChange: (Long) -> Unit,
    report: LedgerReport?
) {
    val postingAccounts = accounts.filter { it.isPosting }
    val selected = postingAccounts.firstOrNull { it.id == selectedAccountId }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            FushSectionHeader("دفتر الأستاذ العام", "حركة الحساب المختار مع الرصيد الافتتاحي والجاري والختامي للفترة.")
            Spacer(Modifier.height(8.dp))
            ReportSelectionMenu(
                label = "الحساب",
                selectedText = selected?.let { "${it.code} — ${it.nameAr}" } ?: "اختر حساب ترحيل",
                options = postingAccounts.map { it.id to "${it.code} — ${it.nameAr}" },
                onSelect = onSelectedAccountChange
            )
        }
        report?.let { ledger ->
            item {
                MetricGrid(listOf(
                    "الرصيد الافتتاحي" to reportBalanceLabel(ledger.openingBalance),
                    "إجمالي المدين" to money(ledger.lines.sumOf { it.debit }),
                    "إجمالي الدائن" to money(ledger.lines.sumOf { it.credit }),
                    "الرصيد الختامي" to reportBalanceLabel(ledger.closingBalance)
                ))
            }
            if (ledger.lines.isEmpty()) {
                item { Text("لا توجد حركات على الحساب خلال الفترة المحددة.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(ledger.lines) { line ->
                ReportCard(
                    "${fmtDate(line.entryDate)} — ${line.entryNo}",
                    line.sourceType,
                    listOf(
                        "البيان" to line.description,
                        "مدين" to money(line.debit),
                        "دائن" to money(line.credit),
                        "الرصيد" to reportBalanceLabel(line.runningBalance)
                    )
                )
            }
        }
    }
}

@Composable
private fun ExpenseAnalysisTab(rows: List<ExpenseReportRow>) {
    val analytics = remember(rows) { ExpenseReportAnalyticsMath.build(rows) }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            FushSectionHeader(
                "تحليل المصروفات والأبعاد",
                "قراءة إدارية للمصروفات حسب الحساب ومركز التكلفة والوحدة التنظيمية والموظف/المندوب وطريقة الدفع."
            )
        }
        item {
            MetricGrid(listOf(
                "إجمالي المصروفات" to money(analytics.totalAmountBase),
                "عدد السندات" to analytics.voucherCount.toString(),
                "متوسط السند" to money(analytics.averageVoucherBase),
                "المرفقات" to analytics.attachmentCount.toString()
            ))
        }
        item { ExpenseBreakdownBlock("حسب حساب المصروف", analytics.byAccount) }
        item { ExpenseBreakdownBlock("حسب مركز التكلفة", analytics.byCostCenter) }
        item { ExpenseBreakdownBlock("حسب الوحدة التنظيمية", analytics.byOrganizationUnit) }
        item { ExpenseBreakdownBlock("حسب الموظف / المندوب", analytics.byEmployeeOrRep) }
        item { ExpenseBreakdownBlock("حسب طريقة الدفع", analytics.byPaymentMethod) }
        item { FushSectionHeader("التفصيل", "سندات المصروف المرحلة في الفترة المحددة مع أهم الأبعاد.") }
        if (rows.isEmpty()) {
            item { Text("لا توجد مصروفات مرحلة خلال الفترة المحددة.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(rows, key = { it.expenseId }) { row ->
            val owner = when {
                row.salesRepName.isNotBlank() -> "مندوب: ${row.salesRepName}"
                row.employeeName.isNotBlank() -> "موظف: ${row.employeeName}"
                else -> "بدون موظف/مندوب"
            }
            ReportCard(
                "${row.voucherNo} — ${fmtDate(row.voucherDate)}",
                "${row.expenseAccountCode} — ${row.expenseAccountName}",
                listOf(
                    "المبلغ" to money(row.amountBase),
                    "مركز التكلفة" to listOf(row.costCenterCode, row.costCenterName).filter { it.isNotBlank() }.joinToString(" — ").ifBlank { "غير محدد" },
                    "الوحدة" to row.organizationUnit.ifBlank { "غير محدد" },
                    "المسؤول" to owner,
                    "طريقة الدفع" to row.paymentMethod.ifBlank { "غير محدد" },
                    "المرفقات" to row.attachmentCount.toString(),
                    "البيان" to row.description.ifBlank { "—" }
                )
            )
        }
    }
}

@Composable
private fun ExpenseBreakdownBlock(title: String, rows: List<ExpenseBreakdownRow>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FushSectionHeader(title, "أعلى البنود حسب القيمة خلال الفترة.")
        if (rows.isEmpty()) {
            Text("لا توجد بيانات.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            rows.take(8).forEach { row ->
                ReportCard(
                    row.label,
                    "${row.voucherCount} سند",
                    listOf(
                        "الإجمالي" to money(row.amountBase),
                        "النسبة" to "%.1f%%".format(Locale.US, row.sharePercent)
                    )
                )
            }
        }
    }
}

@Composable
private fun AgingReportTab(
    partyType: String,
    onPartyTypeChange: (String) -> Unit,
    rows: List<PartyAgingReportRow>
) {
    val typeLabel = if (partyType == "SUPPLIER") "الموردين" else "العملاء"
    val totalCurrent = rows.sumOf { it.currentBase }
    val totalOverdue = rows.sumOf { it.overdueBase }
    val totalUnapplied = rows.sumOf { it.unappliedBase }
    val totalBalance = rows.sumOf { it.totalBalanceBase }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            FushSectionHeader(
                "أعمار ديون $typeLabel",
                "تصنيف الأرصدة حسب تاريخ الاستحقاق مع فصل سندات القبض/الصرف المباشرة غير المخصصة لفاتورة."
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = partyType == "CUSTOMER", onClick = { onPartyTypeChange("CUSTOMER") }, label = { Text("عملاء AR") })
                FilterChip(selected = partyType == "SUPPLIER", onClick = { onPartyTypeChange("SUPPLIER") }, label = { Text("موردون AP") })
            }
        }
        item {
            MetricGrid(listOf(
                "عدد الأطراف" to rows.size.toString(),
                "حالي" to money(totalCurrent),
                "متأخر" to money(totalOverdue),
                "سندات غير مخصصة" to money(totalUnapplied),
                "صافي الرصيد" to money(totalBalance)
            ))
        }
        item {
            Text(
                "ملاحظة: السند المباشر غير المخصص لفاتورة يظهر منفصلاً؛ لا يقوم التقرير بتوزيعه تلقائياً على أقدم فاتورة حتى لا يفترض تسوية لم يسجلها المستخدم.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (rows.isEmpty()) {
            item { Text("لا توجد أرصدة آجلة أو سندات مباشرة غير مخصصة حتى التاريخ المحدد.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(rows, key = { it.partyId }) { row ->
            ReportCard(
                row.partyName,
                if (row.overdueBase > 0.000001) "متأخر ${money(row.overdueBase)}" else "الرصيد غير متأخر",
                listOf(
                    "حالي" to money(row.currentBase),
                    "1–30 يوم" to money(row.days1To30Base),
                    "31–60 يوم" to money(row.days31To60Base),
                    "61–90 يوم" to money(row.days61To90Base),
                    "أكثر من 90 يوم" to money(row.over90Base),
                    "سندات غير مخصصة" to money(row.unappliedBase),
                    "صافي الرصيد" to money(row.totalBalanceBase)
                )
            )
        }
    }
}

@Composable
private fun PartyStatementTab(
    customers: List<CustomerEntity>,
    suppliers: List<SupplierEntity>,
    partyType: String,
    onPartyTypeChange: (String) -> Unit,
    customerId: Long?,
    onCustomerChange: (Long) -> Unit,
    supplierId: Long?,
    onSupplierChange: (Long) -> Unit,
    report: PartyStatementPeriod?
) {
    val customer = customers.firstOrNull { it.id == customerId }
    val supplier = suppliers.firstOrNull { it.id == supplierId }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            FushSectionHeader("كشف حساب الأطراف", "كشف زمني موحد للعميل أو المورد مع الرصيد الافتتاحي والرصيد بعد كل حركة.")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = partyType == "CUSTOMER", onClick = { onPartyTypeChange("CUSTOMER") }, label = { Text("عميل") })
                FilterChip(selected = partyType == "SUPPLIER", onClick = { onPartyTypeChange("SUPPLIER") }, label = { Text("مورد") })
            }
            Spacer(Modifier.height(6.dp))
            if (partyType == "SUPPLIER") {
                ReportSelectionMenu(
                    label = "المورد",
                    selectedText = supplier?.let { "${it.code} — ${it.nameAr}" } ?: "اختر المورد",
                    options = suppliers.map { it.id to "${it.code} — ${it.nameAr}" },
                    onSelect = onSupplierChange
                )
            } else {
                ReportSelectionMenu(
                    label = "العميل",
                    selectedText = customer?.let { "${it.code} — ${it.nameAr}" } ?: "اختر العميل",
                    options = customers.map { it.id to "${it.code} — ${it.nameAr}" },
                    onSelect = onCustomerChange
                )
            }
        }
        report?.let { statement ->
            item {
                MetricGrid(listOf(
                    "الرصيد الافتتاحي" to partyBalanceLabel(statement.openingBalance, partyType),
                    "إجمالي المدين" to money(statement.totalDebit),
                    "إجمالي الدائن" to money(statement.totalCredit),
                    "الرصيد الختامي" to partyBalanceLabel(statement.closingBalance, partyType)
                ))
            }
            if (statement.lines.isEmpty()) {
                item { Text("لا توجد حركات للطرف خلال الفترة المحددة.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(statement.lines) { line ->
                ReportCard(
                    "${fmtDate(line.eventDate)} — ${line.referenceNo}",
                    partyEventTypeAr(line.eventType),
                    listOf(
                        "البيان" to line.description.ifBlank { "—" },
                        "مدين" to money(line.debitBase),
                        "دائن" to money(line.creditBase),
                        "الرصيد" to partyBalanceLabel(line.runningBalance, partyType)
                    )
                )
            }
        }
    }
}

@Composable
private fun ReportSelectionMenu(
    label: String,
    selectedText: String,
    options: List<Pair<Long, String>>,
    onSelect: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selectedText, modifier = Modifier.fillMaxWidth())
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (id, text) ->
                    DropdownMenuItem(
                        text = { Text(text) },
                        onClick = {
                            onSelect(id)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

private fun reportBalanceLabel(value: Double): String = when {
    value > 0.000001 -> "${money(value)} مدين"
    value < -0.000001 -> "${money(-value)} دائن"
    else -> money(0.0)
}

private fun partyBalanceLabel(value: Double, partyType: String): String = when {
    value > 0.000001 && partyType == "SUPPLIER" -> "${money(value)} مستحق للمورد"
    value > 0.000001 -> "${money(value)} على العميل"
    value < -0.000001 && partyType == "SUPPLIER" -> "${money(-value)} لصالح المنشأة"
    value < -0.000001 -> "${money(-value)} لصالح العميل"
    else -> money(0.0)
}

private fun partyEventTypeAr(type: String): String = when (type) {
    "INVOICE" -> "فاتورة"
    "RECEIPT" -> "تحصيل"
    "SALES_RETURN" -> "مرتجع مبيعات"
    "CASH_REFUND" -> "رد نقدي للعميل"
    "RETURN" -> "مرتجع مشتريات"
    "PAYMENT" -> "دفعة مورد"
    "VOUCHER_PAYMENT" -> "سند صرف"
    "VOUCHER_RECEIPT" -> "سند قبض"
    "VOUCHER_REVERSAL" -> "عكس سند"
    else -> type
}

@Composable private fun MetricGrid(metrics: List<Pair<String,String>>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = when {
            maxWidth >= 980.dp -> 4
            maxWidth >= 640.dp -> 3
            else -> 2
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            metrics.chunked(columns).forEach { group ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    group.forEach { (k, v) ->
                        ElevatedCard(Modifier.weight(1f)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(v, style = MaterialTheme.typography.titleMedium)
                                Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    repeat(columns - group.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
}

@Composable private fun ReportCard(title: String, subtitle: String, metrics: List<Pair<String,String>>) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = if (maxWidth >= 700.dp) 2 else 1
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    metrics.chunked(columns).forEach { group ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            group.forEach { (k, v) ->
                                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(v, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            repeat(columns - group.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

private fun reportProductionStatusAr(status: String): String = when (status) {
    "PLANNED" -> "مخطط"
    "MATERIALS_RESERVED" -> "مواد محجوزة"
    "MATERIALS_ISSUED" -> "تم صرف المواد"
    "PREPARATION" -> "تحضير"
    "MIXING" -> "خلط"
    "FILLING" -> "تعبئة"
    "QC_HOLD", "UNDER_QC" -> "تحت الجودة"
    "ACCEPTED", "CLOSED" -> "مقبول"
    "REJECTED" -> "مرفوض"
    "CANCELLED" -> "ملغي"
    else -> status
}

private fun reportProductionStatusTone(status: String): FushStatusTone = when (status) {
    "ACCEPTED", "CLOSED" -> FushStatusTone.Success
    "QC_HOLD", "UNDER_QC" -> FushStatusTone.Warning
    "REJECTED" -> FushStatusTone.Danger
    "CANCELLED", "PLANNED" -> FushStatusTone.Neutral
    else -> FushStatusTone.Info
}

private fun periodRange(period: String, now: Long): Pair<Long,Long> {
    val c = Calendar.getInstance().apply { timeInMillis = now }
    val end = now
    val start = when (period) {
        "اليوم" -> Calendar.getInstance().apply { timeInMillis=now; set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }.timeInMillis
        "30 يوم" -> now - 30L*86_400_000L
        "هذه السنة" -> Calendar.getInstance().apply { timeInMillis=now; set(Calendar.MONTH,0); set(Calendar.DAY_OF_MONTH,1); set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }.timeInMillis
        "كل الفترة" -> 0L
        else -> Calendar.getInstance().apply { timeInMillis=now; set(Calendar.DAY_OF_MONTH,1); set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }.timeInMillis
    }
    return start to end
}

private val num = NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = 2 }
private fun money(v: Double) = "${num.format(v)} ر.ي"
private fun qty(v: Double) = num.format(v)
private fun fmtDate(ms: Long): String = if (ms <= 0L) "البداية" else SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))
