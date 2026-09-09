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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fush.erp.data.AppContainer
import com.fush.erp.data.entity.*
import com.fush.erp.domain.*
import com.fush.erp.ui.FushMetricCard
import com.fush.erp.ui.FushSearchableSelectionField
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

private data class ProductionAnalyticsBundle(
    val materialVariance: List<ProductionMaterialVarianceReportRow> = emptyList(),
    val orderOverhead: List<ProductionOrderOverheadReportRow> = emptyList(),
    val overheadAccounts: List<ProductionOverheadAccountReportRow> = emptyList(),
    val wip: ProductionWipReportRow? = null,
    val losses: List<ProductionLossReportRow> = emptyList(),
    val labor: List<ProductionLaborProductivityReportRow> = emptyList(),
    val downtime: List<ProductionDowntimeReportRow> = emptyList(),
    val lotTrace: List<ProductionLotTraceReportRow> = emptyList(),
    val reconciliation: ProductionAccountingReconciliationReportRow? = null
)

private data class ProductionProductAnalysis(
    val productCode: String,
    val productName: String,
    val orderCount: Int,
    val plannedQtyBase: Double,
    val actualQtyBase: Double,
    val acceptedQtyBase: Double,
    val rejectedQtyBase: Double,
    val scrapQtyBase: Double,
    val materialCostBase: Double,
    val laborCostBase: Double,
    val linkedOverheadBase: Double,
    val totalCostBase: Double
)

private fun buildProductionProductAnalysis(
    rows: List<ProductionPerformanceReportRow>,
    analytics: ProductionAnalyticsBundle
): List<ProductionProductAnalysis> {
    val overheadByOrder = analytics.orderOverhead.associate { it.orderId to it.overheadBase }
    return rows.groupBy { it.productCode to it.productName }.map { (key, productRows) ->
        val overhead = productRows.sumOf { overheadByOrder[it.orderId] ?: 0.0 }
        val material = productRows.sumOf { it.materialCostBase }
        val labor = productRows.sumOf { it.laborCostBase }
        ProductionProductAnalysis(
            productCode = key.first,
            productName = key.second,
            orderCount = productRows.size,
            plannedQtyBase = productRows.sumOf { it.plannedQtyBase },
            actualQtyBase = productRows.sumOf { it.actualQtyBase },
            acceptedQtyBase = productRows.sumOf { it.acceptedQtyBase },
            rejectedQtyBase = productRows.sumOf { it.rejectedQtyBase },
            scrapQtyBase = productRows.sumOf { it.scrapQtyBase },
            materialCostBase = material,
            laborCostBase = labor,
            linkedOverheadBase = overhead,
            totalCostBase = material + labor + overhead
        )
    }.sortedByDescending { it.totalCostBase }
}

@Composable
fun ReportsScreen(container: AppContainer, user: UserEntity, modifier: Modifier = Modifier, initialTab: String = "ملخص") {
    val context = LocalContext.current
    val now = remember { com.fush.erp.domain.TrustedTimeService.now() }
    val expiryPrefs = remember { context.getSharedPreferences("inventory_alert_settings", android.content.Context.MODE_PRIVATE) }
    var nearExpiryDays by remember { mutableIntStateOf(NearExpiryPolicy.normalize(expiryPrefs.getInt("near_expiry_days", NearExpiryPolicy.DEFAULT_DAYS))) }
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
    var salesInvoicesAccounting by remember { mutableStateOf<List<SalesInvoiceAccountingReportRow>>(emptyList()) }
    var salesProductProfitability by remember { mutableStateOf<List<ProductProfitabilityReportRow>>(emptyList()) }
    var salesRepPerformance by remember { mutableStateOf<List<SalesRepPerformanceReportRow>>(emptyList()) }
    var salesReturnDetails by remember { mutableStateOf<List<SalesReturnDetailReportRow>>(emptyList()) }
    var salesMonthlyTrend by remember { mutableStateOf<List<SalesMonthlyTrendReportRow>>(emptyList()) }
    var salesAgingRows by remember { mutableStateOf<List<PartyAgingReportRow>>(emptyList()) }
    var salesAdditionalCharges by remember { mutableStateOf<List<SalesAdditionalChargeReportRow>>(emptyList()) }
    var salesShipmentCosts by remember { mutableStateOf<List<SalesShipmentCostAllocationReportRow>>(emptyList()) }
    var salesReconciliation by remember { mutableStateOf<SalesReconciliationReportRow?>(null) }
    var suppliers by remember { mutableStateOf<List<SupplierPurchaseReportRow>>(emptyList()) }
    var purchaseDetails by remember { mutableStateOf<List<PurchaseInvoiceDetailReportRow>>(emptyList()) }
    var purchaseAccounting by remember { mutableStateOf<List<PurchaseInvoiceAccountingReportRow>>(emptyList()) }
    var purchaseReturns by remember { mutableStateOf<List<PurchaseReturnReportRow>>(emptyList()) }
    var purchaseItems by remember { mutableStateOf<List<PurchaseItemAnalysisReportRow>>(emptyList()) }
    var supplierAgingRows by remember { mutableStateOf<List<SupplierAgingRow>>(emptyList()) }
    var purchaseReconciliation by remember { mutableStateOf<PurchaseReconciliationReportRow?>(null) }
    var inventory by remember { mutableStateOf<List<InventoryValuationReportRow>>(emptyList()) }
    var inventoryActivity by remember { mutableStateOf<List<InventoryActivityReportRow>>(emptyList()) }
    var inventoryExpiryLots by remember { mutableStateOf<List<InventoryExpiryLotReportRow>>(emptyList()) }
    var inventoryMovements by remember { mutableStateOf<List<InventoryMovementDetailReportRow>>(emptyList()) }
    var production by remember { mutableStateOf<List<ProductionPerformanceReportRow>>(emptyList()) }
    var productionMaterials by remember { mutableStateOf<List<ProductionMaterialUsageReportRow>>(emptyList()) }
    var productionAnalytics by remember { mutableStateOf(ProductionAnalyticsBundle()) }
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
                    val reportDao = container.db.reportDao()
                    customers = reportDao.customerSales(from, to).map { it.copy(province = normalizeSalesGeographyLabel(it.province)) }
                    salesProductQuantities = reportDao.salesProductQuantities(from, to)
                    provinces = normalizeProvinceProfitabilityRows(container.db.geographyDao().provinceProfitability(from, to))
                    commissions = reportDao.commissions(from, to)
                    salesInvoicesAccounting = reportDao.salesInvoiceAccounting(from, to, to).map { it.copy(province = normalizeSalesGeographyLabel(it.province)) }
                    salesProductProfitability = reportDao.productProfitability(from, to)
                    salesRepPerformance = reportDao.salesRepPerformance(from, to)
                    salesReturnDetails = reportDao.salesReturnDetails(from, to)
                    salesMonthlyTrend = reportDao.salesMonthlyTrend(from, to)
                    salesAgingRows = AgingReportMath.build(reportDao.customerAgingInvoices(to), reportDao.customerAgingAdjustments(to), to)
                    salesAdditionalCharges = reportDao.salesAdditionalCharges(from, to)
                    salesShipmentCosts = reportDao.salesShipmentCostAllocations(from, to)
                    salesReconciliation = reportDao.salesReconciliation(from, to, to)
                }
                "المشتريات" -> {
                    val reportDao = container.db.reportDao()
                    suppliers = reportDao.supplierPurchases(from, to)
                    purchaseDetails = reportDao.purchaseInvoiceDetails(from, to)
                    purchaseAccounting = reportDao.purchaseInvoiceAccounting(from, to, to)
                    purchaseReturns = reportDao.purchaseReturnsForReport(from, to)
                    purchaseItems = reportDao.purchaseItemAnalysis(from, to)
                    supplierAgingRows = container.db.purchaseDao().supplierAging(to)
                    purchaseReconciliation = reportDao.purchaseReconciliation(from, to, to)
                }
                "المخزون" -> {
                    val reportDao = container.db.reportDao()
                    inventory = reportDao.inventoryValuation(to)
                    inventoryActivity = reportDao.inventoryActivity(to)
                    inventoryExpiryLots = reportDao.inventoryExpiryLots(to)
                    inventoryMovements = reportDao.inventoryMovementDetails(from, to)
                }
                "الإنتاج" -> {
                    val reportDao = container.db.reportDao()
                    production = reportDao.productionPerformance(from, to)
                    productionMaterials = reportDao.productionMaterialUsage(from, to)
                    maintenance = reportDao.maintenance(from, to)
                    productionAnalytics = ProductionAnalyticsBundle(
                        materialVariance = reportDao.productionMaterialVariance(from, to),
                        orderOverhead = reportDao.productionOrderOverhead(from, to),
                        overheadAccounts = reportDao.productionOverheadAccounts(from, to),
                        wip = reportDao.productionWip(from, to),
                        losses = reportDao.productionLosses(from, to),
                        labor = reportDao.productionLaborProductivity(from, to),
                        downtime = reportDao.productionDowntime(from, to),
                        lotTrace = reportDao.productionLotTrace(from, to),
                        reconciliation = reportDao.productionAccountingReconciliation(from, to)
                    )
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
        tab, period, from, to, executive, customers, salesProductQuantities, provinces, commissions, salesInvoicesAccounting, salesProductProfitability, salesRepPerformance, salesReturnDetails, salesMonthlyTrend, salesAgingRows, salesAdditionalCharges, salesShipmentCosts, salesReconciliation, suppliers, purchaseDetails,
        purchaseAccounting, purchaseReturns, purchaseItems, supplierAgingRows, purchaseReconciliation,
        inventory, inventoryActivity, inventoryExpiryLots, inventoryMovements, production, productionMaterials, productionAnalytics, quality, maintenance, pnl, trial, cash, balanceSheet, accounts,
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
            salesInvoicesAccounting = salesInvoicesAccounting,
            salesProductProfitability = salesProductProfitability,
            salesRepPerformance = salesRepPerformance,
            salesReturnDetails = salesReturnDetails,
            salesMonthlyTrend = salesMonthlyTrend,
            salesAgingRows = salesAgingRows,
            salesAdditionalCharges = salesAdditionalCharges,
            salesShipmentCosts = salesShipmentCosts,
            salesReconciliation = salesReconciliation,
            suppliers = suppliers,
            purchaseDetails = purchaseDetails,
            purchaseAccounting = purchaseAccounting,
            purchaseReturns = purchaseReturns,
            purchaseItems = purchaseItems,
            supplierAgingRows = supplierAgingRows,
            purchaseReconciliation = purchaseReconciliation,
            inventory = inventory,
            inventoryActivity = inventoryActivity,
            inventoryExpiryLots = inventoryExpiryLots,
            inventoryMovements = inventoryMovements,
            production = production,
            productionMaterials = productionMaterials,
            productionAnalytics = productionAnalytics,
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
                        tab, executive, customers, salesProductQuantities, provinces, commissions, salesInvoicesAccounting, salesProductProfitability, salesRepPerformance, salesReturnDetails, salesMonthlyTrend, salesAgingRows, salesAdditionalCharges, salesShipmentCosts, salesReconciliation, suppliers,
                        purchaseAccounting, supplierAgingRows, purchaseReconciliation, inventory,
                        inventoryActivity, inventoryExpiryLots, inventoryMovements, production, productionMaterials, productionAnalytics, maintenance, quality, pnl, trial, cash, balanceSheet, accounts,
                        ledgerAccountId, { ledgerAccountId = it }, ledger, statementCustomers, statementSuppliers,
                        statementPartyType, { statementPartyType = it }, statementCustomerId, { statementCustomerId = it },
                        statementSupplierId, { statementSupplierId = it }, partyStatement, agingPartyType,
                        { agingPartyType = it }, agingRows, expenses, treasuryReport, periodComparison, to,
                        nearExpiryDays, { days ->
                            val normalized = NearExpiryPolicy.normalize(days)
                            nearExpiryDays = normalized
                            expiryPrefs.edit().putInt("near_expiry_days", normalized).apply()
                        }
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
                        tab, executive, customers, salesProductQuantities, provinces, commissions, salesInvoicesAccounting, salesProductProfitability, salesRepPerformance, salesReturnDetails, salesMonthlyTrend, salesAgingRows, salesAdditionalCharges, salesShipmentCosts, salesReconciliation, suppliers,
                        purchaseAccounting, supplierAgingRows, purchaseReconciliation, inventory,
                        inventoryActivity, inventoryExpiryLots, inventoryMovements, production, productionMaterials, productionAnalytics, maintenance, quality, pnl, trial, cash, balanceSheet, accounts,
                        ledgerAccountId, { ledgerAccountId = it }, ledger, statementCustomers, statementSuppliers,
                        statementPartyType, { statementPartyType = it }, statementCustomerId, { statementCustomerId = it },
                        statementSupplierId, { statementSupplierId = it }, partyStatement, agingPartyType,
                        { agingPartyType = it }, agingRows, expenses, treasuryReport, periodComparison, to,
                        nearExpiryDays, { days ->
                            val normalized = NearExpiryPolicy.normalize(days)
                            nearExpiryDays = normalized
                            expiryPrefs.edit().putInt("near_expiry_days", normalized).apply()
                        }
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
    salesInvoicesAccounting: List<SalesInvoiceAccountingReportRow>,
    salesProductProfitability: List<ProductProfitabilityReportRow>,
    salesRepPerformance: List<SalesRepPerformanceReportRow>,
    salesReturnDetails: List<SalesReturnDetailReportRow>,
    salesMonthlyTrend: List<SalesMonthlyTrendReportRow>,
    salesAgingRows: List<PartyAgingReportRow>,
    salesAdditionalCharges: List<SalesAdditionalChargeReportRow>,
    salesShipmentCosts: List<SalesShipmentCostAllocationReportRow>,
    salesReconciliation: SalesReconciliationReportRow?,
    suppliers: List<SupplierPurchaseReportRow>,
    purchaseAccounting: List<PurchaseInvoiceAccountingReportRow>,
    supplierAgingRows: List<SupplierAgingRow>,
    purchaseReconciliation: PurchaseReconciliationReportRow?,
    inventory: List<InventoryValuationReportRow>,
    inventoryActivity: List<InventoryActivityReportRow>,
    inventoryExpiryLots: List<InventoryExpiryLotReportRow>,
    inventoryMovements: List<InventoryMovementDetailReportRow>,
    production: List<ProductionPerformanceReportRow>,
    productionMaterials: List<ProductionMaterialUsageReportRow>,
    productionAnalytics: ProductionAnalyticsBundle,
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
    reportTo: Long,
    nearExpiryDays: Int,
    onNearExpiryDaysChange: (Int) -> Unit
) {
    when (tab) {
        "ملخص" -> ExecutiveTab(executive)
        "المبيعات" -> SalesTab(customers, salesProductQuantities, provinces, commissions, salesInvoicesAccounting, salesProductProfitability, salesRepPerformance, salesReturnDetails, salesMonthlyTrend, salesAgingRows, salesAdditionalCharges, salesShipmentCosts, salesReconciliation)
        "المشتريات" -> PurchasesTab(suppliers, purchaseAccounting, supplierAgingRows, purchaseReconciliation)
        "المخزون" -> InventoryTab(
            inventory, inventoryActivity, inventoryExpiryLots, inventoryMovements, reportTo, nearExpiryDays, onNearExpiryDaysChange
        )
        "الإنتاج" -> ProductionTab(production, productionMaterials, productionAnalytics, maintenance)
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


private fun normalizeSalesGeographyLabel(raw: String): String {
    var v = raw.trim().replace(Regex("\\s+"), " ")
    v = v.replace(Regex("\\s*/\\s*"), "/")
    v = v.replace("تعز بير باشا", "تعز/بيرباشا", ignoreCase = true)
        .replace("تعز/بير باشا", "تعز/بيرباشا", ignoreCase = true)
        .replace("تعز/ بيرباشا", "تعز/بيرباشا", ignoreCase = true)
    if (v == "الحوبان") v = "تعز/الحوبان"
    return v
}

private fun normalizeProvinceProfitabilityRows(rows: List<ProvinceProfitabilityRow>): List<ProvinceProfitabilityRow> =
    rows.groupBy { normalizeSalesGeographyLabel(it.province) }.map { (province, group) ->
        ProvinceProfitabilityRow(
            province = province,
            invoiceCount = group.sumOf { it.invoiceCount },
            netRevenueBase = group.sumOf { it.netRevenueBase },
            netCogsBase = group.sumOf { it.netCogsBase },
            commissionBase = group.sumOf { it.commissionBase },
            geographicCostBase = group.sumOf { it.geographicCostBase },
            profitBase = group.sumOf { it.profitBase }
        )
    }.sortedByDescending { it.profitBase }

@Composable private fun SalesTab(
    customers: List<CustomerSalesReportRow>,
    productQuantities: List<ProductSalesQuantityReportRow>,
    provinces: List<ProvinceProfitabilityRow>,
    commissions: List<CommissionReportRow>,
    invoiceRows: List<SalesInvoiceAccountingReportRow>,
    productProfitRows: List<ProductProfitabilityReportRow>,
    repRows: List<SalesRepPerformanceReportRow>,
    returnRows: List<SalesReturnDetailReportRow>,
    monthlyRows: List<SalesMonthlyTrendReportRow>,
    receivableAging: List<PartyAgingReportRow>,
    additionalCharges: List<SalesAdditionalChargeReportRow>,
    shipmentCosts: List<SalesShipmentCostAllocationReportRow>,
    reconciliation: SalesReconciliationReportRow?
) {
    val grossSales = customers.sumOf { it.grossSalesBase }
    val returns = customers.sumOf { it.returnsBase }
    val collections = customers.sumOf { it.collectionsBase }
    val outstanding = customers.sumOf { it.outstandingBase }
    val provinceProfit = provinces.sumOf { it.profitBase }
    val commissionNet = commissions.sumOf { it.netCommissionBase }
    val netItemSales = reconciliation?.netSalesBase ?: productProfitRows.sumOf { it.netRevenueBase }
    val cogs = productProfitRows.sumOf { it.netCostBase }
    val companyExpenses = reconciliation?.companyExpensesBase ?: additionalCharges.filter { it.accountingTreatment == "COMPANY_EXPENSE" }.sumOf { it.amountBase }
    val serviceRevenue = reconciliation?.serviceRevenueBase ?: additionalCharges.sumOf { it.serviceRevenueBase }
    val recoverableOutstanding = reconciliation?.recoverableClosingBase ?: additionalCharges.filter { it.accountingTreatment == "RECOVERABLE" }.sumOf { it.remainingBase }
    val allocatedShipmentCost = shipmentCosts.sumOf { it.allocatedBase }

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
                "إجمالي الكمية المباعة" to qty(productQuantities.sumOf { it.grossQtyBase }),
                "إجمالي المرتجع المباع" to qty(productQuantities.sumOf { it.returnedQtyBase }),
                "صافي الكمية المباعة" to qty(productQuantities.sumOf { it.netQtyBase }),
                "إجمالي المجاني" to qty(productQuantities.sumOf { it.netFreeQtyBase }),
                "تكلفة المجاني" to money(productQuantities.sumOf { it.netFreeCostBase }),
                "أثر المجاني على الربح" to money(-productQuantities.sumOf { it.netFreeCostBase })
            ))
        }
        items(productQuantities.take(20)) { product ->
            ReportCard("${product.productName} — ${product.code}", "الكمية خلال الفترة", listOf(
                "مباع" to qty(product.grossQtyBase),
                "مرتجع مباع" to qty(product.returnedQtyBase),
                "صافي المباع" to qty(product.netQtyBase),
                "مجاني" to qty(product.netFreeQtyBase),
                "تكلفة المجاني" to money(product.netFreeCostBase),
                "أثره على الربح" to money(-product.netFreeCostBase)
            ))
        }
        item {
            SectionTitle("فصل الربحية والرسوم")
            MetricGrid(listOf(
                "صافي مبيعات الأصناف" to money(netItemSales),
                "تكلفة البضاعة المباعة" to money(cogs),
                "مجمل ربح الأصناف" to money(netItemSales - cogs),
                "مصاريف إضافية على الشركة" to money(companyExpenses),
                "إيراد خدمات/رسوم" to money(serviceRevenue),
                "رسوم قابلة للاسترداد — متبقي" to money(recoverableOutstanding),
                "تكلفة شحن فعلية مخصصة" to money(allocatedShipmentCost)
            ))
            Text(
                "تكلفة الشحنة الفعلية مستقلة عن أي رسم محمّل للعميل. رسوم RECOVERABLE لا تُعامل كمبيعات أصناف، وتظهر بمتبقيها حتى تتم تسويتها.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item { SectionTitle("الرسوم والتكاليف الإضافية") }
        items(additionalCharges.take(30), key = { "sales-report-charge-${it.chargeId}" }) { row ->
            val treatment = when (row.accountingTreatment) {
                "RECOVERABLE" -> "قابل للاسترداد"
                "COMPANY_EXPENSE" -> "مصروف على الشركة"
                "SERVICE_REVENUE" -> "إيراد خدمة"
                else -> row.accountingTreatment
            }
            ReportCard("${row.chargeNo} — ${row.chargeTypeName}", "${row.customerName} • $treatment", listOf(
                "القيمة" to money(row.amountBase),
                "المدفوع" to money(row.paidBase),
                "المسوّى" to money(row.settledBase),
                "المتبقي" to money(row.remainingBase),
                "Principal/Agent" to row.principalAgentMode,
                "الدفع بواسطة" to row.paidBy
            ))
        }
        item { SectionTitle("تخصيص تكلفة الشحن الفعلية على الفواتير") }
        items(shipmentCosts.take(40), key = { "sales-report-shipment-${it.shipmentId}-${it.invoiceId}-${it.expenseType}-${it.paymentVoucherNo}" }) { row ->
            ReportCard("${row.shipmentNo} → ${row.invoiceNo}", "${row.destinationProvince} • ${row.customerName}", listOf(
                "نوع المصروف" to row.expenseType,
                "سند الصرف" to row.paymentVoucherNo.ifBlank { "—" },
                "حصة الفاتورة" to money(row.allocatedBase),
                "كمية مرتبطة" to qty(row.shipmentAllocatedQuantityBase),
                "إجمالي تكلفة الشحنة" to money(row.shipmentTotalExpenseBase),
                "موزع من الشحنة" to money(row.shipmentAllocatedExpenseBase),
                "متبقي للشحنة" to money(row.shipmentRemainingExpenseBase)
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
        item {
            SectionTitle("مؤشرات ERP المتقدمة")
            val avgInvoice = if (invoiceRows.isEmpty()) 0.0 else invoiceRows.sumOf { it.totalBase } / invoiceRows.size
            MetricGrid(listOf(
                "متوسط قيمة الفاتورة" to money(avgInvoice),
                "مبيعات نقدية" to money(reconciliation?.cashSalesBase ?: 0.0),
                "مبيعات آجلة" to money(reconciliation?.creditSalesBase ?: 0.0),
                "ذمم متأخرة" to money(receivableAging.sumOf { it.overdueBase }),
                "فرق صافي المبيعات/الأستاذ" to money((reconciliation?.glNetSalesBase ?: 0.0) - (reconciliation?.netSalesBase ?: 0.0)),
                "فرق الذمم/الأستاذ" to money((reconciliation?.glReceivablesClosingBase ?: 0.0) - (reconciliation?.operationalReceivablesClosingBase ?: 0.0))
            ))
        }
        item { SectionTitle("ربحية المنتجات") }
        items(productProfitRows.take(20)) { row -> ReportCard("${row.itemName} — ${row.itemCode}", "ربحية المنتج", listOf(
            "الإيراد" to money(row.netRevenueBase), "التكلفة" to money(row.netCostBase),
            "الربح" to money(row.netProfitBase), "الهامش" to "%.1f%%".format(Locale.US, ReportMath.margin(row.netProfitBase, row.netRevenueBase))
        )) }
        item { SectionTitle("أداء المندوبين") }
        items(repRows) { row -> ReportCard(row.salesRepName, "${row.customerCount} عميل • ${row.invoiceCount} فاتورة", listOf(
            "صافي المبيعات" to money(row.netSalesBase), "التحصيل" to money(row.collectionsBase),
            "الرصيد" to money(row.outstandingBase), "المرتجعات" to money(row.returnsBase), "العمولة" to money(row.commissionBase)
        )) }
        item { SectionTitle("الاتجاه الشهري") }
        items(monthlyRows) { row -> ReportCard(row.periodKey, "${row.invoiceCount} فاتورة", listOf(
            "المبيعات" to money(row.grossSalesBase), "المرتجعات" to money(row.returnsBase), "الصافي" to money(row.netSalesBase)
        )) }
        item { SectionTitle("أعمار الذمم المدينة") }
        items(receivableAging.take(20)) { row -> ReportCard(row.partyName, "الرصيد: ${money(row.totalBalanceBase)}", listOf(
            "جاري" to money(row.currentBase), "1–30" to money(row.days1To30Base), "31–60" to money(row.days31To60Base),
            "61–90" to money(row.days61To90Base), ">90" to money(row.over90Base)
        )) }
        item { SectionTitle("تحليل المرتجعات") }
        items(returnRows.take(30)) { row -> ReportCard("${row.returnNo} — ${row.customerName}", "${row.itemName} • ${row.salesRepName}", listOf(
            "القيمة" to money(row.returnValueBase), "التكلفة" to money(row.returnCostBase), "السبب" to row.reason.ifBlank { "غير محدد" }
        )) }
        item { SectionTitle("العمولات") }
        items(commissions) { c -> ReportCard(c.beneficiary.ifBlank { "غير محدد" }, "عمولات بعد التحصيل والمرتجع", listOf(
            "مستحقة" to money(c.earnedBase), "معكوسة" to money(c.reversedBase), "الصافي" to money(c.netCommissionBase)
        )) }
    }
}

@Composable private fun PurchasesTab(
    rows: List<SupplierPurchaseReportRow>,
    invoicesAccounting: List<PurchaseInvoiceAccountingReportRow>,
    agingRows: List<SupplierAgingRow>,
    reconciliation: PurchaseReconciliationReportRow?
) {
    val gross = rows.sumOf { it.grossPurchasesBase }
    val returns = rows.sumOf { it.returnsBase }
    val net = rows.sumOf { it.netPurchasesBase }
    val invoices = rows.sumOf { it.invoiceCount }
    val topSupplier = rows.maxByOrNull { it.netPurchasesBase }
    val cashPurchases = invoicesAccounting.filter { it.paymentType == "CASH" }.sumOf { it.totalBase }
    val creditPurchases = invoicesAccounting.filter { it.paymentType == "CREDIT" }.sumOf { it.totalBase }
    val supplierOutstanding = reconciliation?.operationalPayablesClosingBase ?: agingRows.sumOf { it.totalOutstandingBase }
    val overdue = agingRows.sumOf { it.days1To30Base + it.days31To60Base + it.days61To90Base + it.over90Base }
    val glDifference = reconciliation?.let { it.apGlClosingBase - it.operationalPayablesClosingBase } ?: 0.0
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            FushSectionHeader("تحليل المشتريات", "الهيكل الحالي مع إضافة مؤشرات محاسبية للشراء النقدي والآجل وذمم الموردين ومطابقة الأستاذ العام.")
            Spacer(Modifier.height(8.dp))
            MetricGrid(listOf(
                "إجمالي المشتريات" to money(gross),
                "المرتجعات" to money(returns),
                "صافي المشتريات" to money(net),
                "مشتريات نقدية" to money(cashPurchases),
                "مشتريات آجلة" to money(creditPurchases),
                "ذمم الموردين" to money(supplierOutstanding),
                "متأخر للموردين" to money(overdue),
                "فرق الموردين/الأستاذ" to money(glDifference),
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
    asOf: Long,
    nearExpiryDays: Int,
    onNearExpiryDaysChange: (Int) -> Unit
) {
    val total = rows.sumOf { it.inventoryValueBase }
    val reorder = rows.count { it.reorderLevel > 0 && it.quantityBase <= it.reorderLevel }
    val zeroStock = rows.count { it.quantityBase <= 0.0 }
    val topValue = rows.maxByOrNull { it.inventoryValueBase }
    val movementSummary = InventoryReportMath.movementSummary(movements)
    val activityInsights = activityRows.map { InventoryReportMath.activity(it, asOf) }
    val slowRows = activityInsights.filter { it.status != "نشط" && it.status != "لم يُصرف بعد" }
    val expiryInsights = expiryRows.map { InventoryReportMath.expiry(it, asOf, nearExpiryDays) }
    val expiredCount = expiryInsights.count { it.status == "منتهي" }
    val nearExpiryCount = expiryInsights.count { it.status == "قريب الانتهاء" }
    val validExpiryCount = expiryInsights.count { it.status == "ساري الصلاحية" }

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
                "قريبة الانتهاء ≤ $nearExpiryDays يوم" to nearExpiryCount.toString(),
                "تشغيلات سارية بعد التنبيه" to validExpiryCount.toString(),
                "أعلى قيمة مخزون" to (topValue?.itemName ?: "—"),
                "قيمته" to money(topValue?.inventoryValueBase ?: 0.0)
            ))
        }

        item {
            var thresholdText by remember(nearExpiryDays) { mutableStateOf(nearExpiryDays.toString()) }
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("إعداد تنبيه قرب انتهاء الصلاحية", style = MaterialTheme.typography.titleMedium)
                    Text("القيمة قابلة للتعديل وتُحفظ على الجهاز؛ لا توجد عتبة 30/90 يوم مخفية داخل الحسابات.", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = thresholdText,
                            onValueChange = { thresholdText = it.filter(Char::isDigit) },
                            label = { Text("عدد الأيام") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { thresholdText.toIntOrNull()?.let(onNearExpiryDaysChange) },
                            enabled = thresholdText.toIntOrNull()?.let { it in NearExpiryPolicy.MIN_DAYS..NearExpiryPolicy.MAX_DAYS } == true
                        ) { Text("حفظ") }
                    }
                }
            }
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
        val expiryByDate = expiryInsights.sortedBy { it.source.expiryDate }
        if (expiryByDate.isEmpty()) item { Text("لا توجد تشغيلات ذات تاريخ صلاحية وبرصيد موجب.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(expiryByDate) { insight ->
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
    analytics: ProductionAnalyticsBundle,
    maintenance: MaintenanceReportRow?
) {
    val planned = rows.sumOf { it.plannedQtyBase }
    val actual = rows.sumOf { it.actualQtyBase }
    val accepted = rows.sumOf { it.acceptedQtyBase }
    val rejected = rows.sumOf { it.rejectedQtyBase }
    val scrap = rows.sumOf { it.scrapQtyBase }
    val materialCost = rows.sumOf { it.materialCostBase }
    val laborCost = rows.sumOf { it.laborCostBase }
    val overheadCost = analytics.overheadAccounts.sumOf { it.amountBase }
    val industrialCost = materialCost + laborCost + overheadCost
    val planAchievement = ReportMath.percent(actual, planned)
    val acceptanceRate = ReportMath.percent(accepted, actual)
    val scrapRate = ReportMath.percent(scrap, actual)
    val productAnalysis = buildProductionProductAnalysis(rows, analytics)
    val materialVarianceAbs = analytics.materialVariance.sumOf { abs(it.varianceCostBase) }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            FushSectionHeader("أداء الإنتاج", "نفس التقرير التشغيلي الحالي مع إضافة الانحرافات، WIP، التكاليف الصناعية والتتبع المحاسبي.")
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FushMetricCard("تحقيق الخطة", "%.1f%%".format(Locale.US, planAchievement), Modifier.weight(1f), "${qty(actual)} من ${qty(planned)}", if (planAchievement in 95.0..105.0) FushStatusTone.Success else FushStatusTone.Warning)
                FushMetricCard("نسبة القبول", "%.1f%%".format(Locale.US, acceptanceRate), Modifier.weight(1f), "${qty(accepted)} وحدة", if (acceptanceRate >= 95.0) FushStatusTone.Success else FushStatusTone.Warning)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FushMetricCard("تكلفة صناعية", money(industrialCost), Modifier.weight(1f), "مواد + عمالة + غير مباشر", FushStatusTone.Info)
                FushMetricCard("انحراف مواد", money(materialVarianceAbs), Modifier.weight(1f), "قيمة فروقات الكمية المطلقة", if (materialVarianceAbs > 0.0) FushStatusTone.Warning else FushStatusTone.Success)
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("التكلفة الفعلية الصناعية", style = MaterialTheme.typography.titleMedium)
                    MetricGrid(listOf(
                        "تكلفة المواد" to money(materialCost),
                        "تكلفة العمالة" to money(laborCost),
                        "التكاليف غير المباشرة" to money(overheadCost),
                        "إجمالي التكلفة الصناعية" to money(industrialCost),
                        "متوسط تكلفة المقبول/وحدة" to money(ReportMath.unitCost(industrialCost, accepted)),
                        "الهالك والمرفوض" to qty(rejected + scrap)
                    ))
                    if (overheadCost > analytics.orderOverhead.sumOf { it.overheadBase } + 0.01) {
                        Text("يوجد جزء من التكاليف غير المباشرة مصنف على مركز تكلفة الإنتاج لكنه غير مربوط بأمر إنتاج محدد؛ يظهر في الإجمالي ولا يوزع اعتباطياً على الأوامر.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        analytics.wip?.let { wip ->
            item {
                FushSectionHeader("الإنتاج تحت التشغيل WIP", "مطابقة مباشرة مع حساب 1210 في الأستاذ العام.")
                MetricGrid(listOf(
                    "رصيد أول الفترة" to money(wip.openingWipBase),
                    "مواد مضافة" to money(wip.materialAddedBase),
                    "عمالة مضافة" to money(wip.laborAddedBase),
                    "غير مباشر محمل على WIP" to money(wip.overheadAddedBase),
                    "محول لمنتج نهائي" to money(wip.finishedTransferredBase),
                    "محول لخسائر إنتاج" to money(wip.rejectedTransferredBase),
                    "رصيد آخر الفترة" to money(wip.closingWipBase)
                ))
            }
        }
        item { FushSectionHeader("المعياري مقابل الفعلي للمواد", "يعرض معيار BOM المحفوظ في أمر الإنتاج مقابل الصرف الفعلي وتكلفة فرق الكمية.") }
        if (analytics.materialVariance.isEmpty()) item { Text("لا توجد بيانات مواد معيارية/فعلية للفترة.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(analytics.materialVariance.take(40), key = { "prod-var-${it.orderId}-${it.itemCode}" }) { r ->
            ReportCard("${r.orderNo} • ${r.itemName}", "${r.productName} • ${r.itemCode}", listOf(
                "المعياري" to "${qty(r.standardQtyBase)} ${r.unitName}",
                "الفعلي" to "${qty(r.actualQtyBase)} ${r.unitName}",
                "فرق الكمية" to qty(r.quantityVarianceBase),
                "تكلفة الفرق" to money(r.varianceCostBase),
                "نسبة الانحراف" to "%.1f%%".format(Locale.US, r.variancePct)
            ))
        }
        if (analytics.materialVariance.size > 40) item { Text("تعرض الشاشة أول 40 صفاً؛ PDF/Excel يتضمن كامل التفاصيل.", style = MaterialTheme.typography.bodySmall) }

        item { FushSectionHeader("الإنتاج حسب المنتج", "تجميع عدد الأوامر، الكميات والتكلفة الفعلية لكل منتج.") }
        items(productAnalysis, key = { "prod-product-${it.productCode}" }) { pRow ->
            ReportCard("${pRow.productName} — ${pRow.productCode}", "${pRow.orderCount} أمر إنتاج", listOf(
                "مخطط" to qty(pRow.plannedQtyBase), "فعلي" to qty(pRow.actualQtyBase),
                "مقبول" to qty(pRow.acceptedQtyBase), "مرفوض" to qty(pRow.rejectedQtyBase),
                "هالك" to qty(pRow.scrapQtyBase), "إجمالي تكلفة مرتبطة" to money(pRow.totalCostBase),
                "متوسط تكلفة المقبول" to money(ReportMath.unitCost(pRow.totalCostBase, pRow.acceptedQtyBase))
            ))
        }

        item { FushSectionHeader("العمالة", "التكلفة والإنتاج المرتبطان بالموظف المسند لأمر الإنتاج.") }
        if (analytics.labor.isEmpty()) item { Text("لا توجد إسنادات عمالة لأوامر الفترة.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(analytics.labor, key = { "prod-labor-${it.employeeId}" }) { l ->
            ReportCard(l.employeeName, "${l.orderCount} أمر إنتاج", listOf(
                "المقبول" to qty(l.acceptedQtyBase),
                "تكلفة العمالة" to money(l.laborCostBase),
                "تكلفة العمالة/وحدة" to money(ReportMath.unitCost(l.laborCostBase, l.acceptedQtyBase)),
                "ساعات العمل" to "غير مسجلة على مستوى أمر الإنتاج",
                "وحدات/ساعة" to "—"
            ))
        }

        item { FushSectionHeader("التوقفات وكفاءة التشغيل", "التوقفات الفعلية المسجلة للصيانة/الأعطال المرتبطة بالأصول المستخدمة في الإنتاج.") }
        if (analytics.downtime.isEmpty()) item { Text("لا توجد توقفات مسجلة في الفترة.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(analytics.downtime.take(30), key = { "prod-down-${it.sourceType}-${it.sourceNo}" }) { d ->
            ReportCard("${d.sourceType} ${d.sourceNo}", d.assetName, listOf(
                "التاريخ" to fmtDate(d.eventDate), "مدة التوقف" to "${d.downtimeMinutes} دقيقة",
                "السبب" to d.reason.ifBlank { "غير مسجل" }, "أوامر مرتبطة بالأصل" to d.relatedOrders.ifBlank { "—" },
                "الحالة" to d.status
            ))
        }
        item { Text("وقت التشغيل الفعلي وساعات الطاقة المخططة غير مسجلين حالياً على مستوى أمر الإنتاج؛ لذلك لا يحسب التقرير نسبة استغلال طاقة مصطنعة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

        item { FushSectionHeader("تتبع التشغيلات", "ربط دفعات المواد الخام المصروفة بالتشغيلة النهائية الناتجة.") }
        items(analytics.lotTrace.take(40), key = { "prod-lot-${it.orderId}-${it.rawItemCode}-${it.rawLotNo}" }) { t ->
            ReportCard("${t.orderNo} • ${t.finishedLotNo ?: "بدون تشغيلة نهائية"}", "${t.productName} — ${t.productCode}", listOf(
                "المادة الخام" to "${t.rawItemName} — ${t.rawItemCode}",
                "تشغيلة الخام" to (t.rawLotNo ?: "بدون رقم"),
                "كمية مصروفة" to qty(t.issuedQtyBase),
                "تكلفة" to money(t.issueCostBase),
                "صلاحية الخام" to (t.rawExpiryDate?.let(::fmtDate) ?: "—")
            ))
        }

        item { FushSectionHeader("أوامر الإنتاج", "تفاصيل الخطة والناتج والتكلفة لكل أمر مع تكلفة غير مباشرة مرتبطة مباشرة إن وجدت.") }
        val overheadByOrder = analytics.orderOverhead.associate { it.orderId to it.overheadBase }
        items(rows, key = { "report-production-order-${it.orderId}" }) { r ->
            val overhead = overheadByOrder[r.orderId] ?: 0.0
            val orderCost = r.actualCostBase + overhead
            val variancePct = if (r.plannedQtyBase > 0.0) (r.actualQtyBase-r.plannedQtyBase)*100.0/r.plannedQtyBase else 0.0
            ReportCard(r.orderNo, "${r.productName} • ${r.batchNo ?: "بدون دفعة"} • ${fmtDate(r.manufactureDate ?: r.plannedDate)}", listOf(
                "مخطط" to qty(r.plannedQtyBase), "فعلي" to qty(r.actualQtyBase), "فرق الإنتاج" to qty(r.actualQtyBase-r.plannedQtyBase),
                "انحراف الإنتاج" to "%.1f%%".format(Locale.US, variancePct), "مقبول" to qty(r.acceptedQtyBase),
                "مرفوض" to qty(r.rejectedQtyBase), "هالك" to qty(r.scrapQtyBase), "مواد" to money(r.materialCostBase),
                "عمالة" to money(r.laborCostBase), "غير مباشر مرتبط" to money(overhead), "التكلفة" to money(orderCost),
                "تكلفة المقبول/وحدة" to money(ReportMath.unitCost(orderCost, r.acceptedQtyBase))
            ))
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
    salesInvoicesAccounting: List<SalesInvoiceAccountingReportRow>,
    salesProductProfitability: List<ProductProfitabilityReportRow>,
    salesRepPerformance: List<SalesRepPerformanceReportRow>,
    salesReturnDetails: List<SalesReturnDetailReportRow>,
    salesMonthlyTrend: List<SalesMonthlyTrendReportRow>,
    salesAgingRows: List<PartyAgingReportRow>,
    salesAdditionalCharges: List<SalesAdditionalChargeReportRow>,
    salesShipmentCosts: List<SalesShipmentCostAllocationReportRow>,
    salesReconciliation: SalesReconciliationReportRow?,
    suppliers: List<SupplierPurchaseReportRow>,
    purchaseDetails: List<PurchaseInvoiceDetailReportRow>,
    purchaseAccounting: List<PurchaseInvoiceAccountingReportRow>,
    purchaseReturns: List<PurchaseReturnReportRow>,
    purchaseItems: List<PurchaseItemAnalysisReportRow>,
    supplierAgingRows: List<SupplierAgingRow>,
    purchaseReconciliation: PurchaseReconciliationReportRow?,
    inventory: List<InventoryValuationReportRow>,
    inventoryActivity: List<InventoryActivityReportRow>,
    inventoryExpiryLots: List<InventoryExpiryLotReportRow>,
    inventoryMovements: List<InventoryMovementDetailReportRow>,
    production: List<ProductionPerformanceReportRow>,
    productionMaterials: List<ProductionMaterialUsageReportRow>,
    productionAnalytics: ProductionAnalyticsBundle,
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
    "المبيعات" -> buildSalesReportExportDocument(customers, salesProductQuantities, provinces, commissions, salesInvoicesAccounting, salesProductProfitability, salesRepPerformance, salesReturnDetails, salesMonthlyTrend, salesAgingRows, salesAdditionalCharges, salesShipmentCosts, salesReconciliation, periodLabel, from, to)
    "المشتريات" -> buildPurchasesReportExportDocument(
        suppliers, purchaseDetails, purchaseAccounting, purchaseReturns, purchaseItems,
        supplierAgingRows, purchaseReconciliation, periodLabel, from, to
    )
    "المخزون" -> buildInventoryReportExportDocument(inventory, inventoryActivity, inventoryExpiryLots, inventoryMovements, periodLabel, from, to)
    "الإنتاج" -> buildProductionReportExportDocument(production, productionMaterials, productionAnalytics, maintenance, periodLabel, from, to)
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
    invoiceRows: List<SalesInvoiceAccountingReportRow>,
    productProfitRows: List<ProductProfitabilityReportRow>,
    repRows: List<SalesRepPerformanceReportRow>,
    returnRows: List<SalesReturnDetailReportRow>,
    monthlyRows: List<SalesMonthlyTrendReportRow>,
    agingRows: List<PartyAgingReportRow>,
    additionalCharges: List<SalesAdditionalChargeReportRow>,
    shipmentCosts: List<SalesShipmentCostAllocationReportRow>,
    reconciliation: SalesReconciliationReportRow?,
    periodLabel: String,
    from: Long,
    to: Long
): ReportExportDocument {
    val totalNetQty = productQuantities.sumOf { it.netQtyBase }
    val summary = listOf(
        "الفترة" to "$periodLabel • ${fmtDate(from)} — ${fmtDate(to)}",
        "صافي الكمية المباعة" to qty(totalNetQty),
        "إجمالي الكمية المجانية" to qty(productQuantities.sumOf { it.netFreeQtyBase }),
        "تكلفة المجاني" to money(productQuantities.sumOf { it.netFreeCostBase }),
        "أثر المجاني على الربح" to money(-productQuantities.sumOf { it.netFreeCostBase }),
        "عدد الأصناف المباعة" to productQuantities.count { kotlin.math.abs(it.netQtyBase) > 0.000000001 }.toString(),
        "عدد العملاء" to customers.size.toString(),
        "عدد الفواتير" to customers.sumOf { it.invoiceCount }.toString(),
        "إجمالي المبيعات" to money(customers.sumOf { it.grossSalesBase }),
        "المرتجعات" to money(customers.sumOf { it.returnsBase }),
        "التحصيل" to money(customers.sumOf { it.collectionsBase }),
        "الرصيد المستحق" to money(customers.sumOf { it.outstandingBase }),
        "ربح المحافظات" to money(provinces.sumOf { it.profitBase }),
        "صافي العمولات" to money(commissions.sumOf { it.netCommissionBase }),
        "المبيعات النقدية" to money(reconciliation?.cashSalesBase ?: 0.0),
        "المبيعات الآجلة" to money(reconciliation?.creditSalesBase ?: 0.0),
        "متوسط قيمة الفاتورة" to money(if (invoiceRows.isEmpty()) 0.0 else invoiceRows.sumOf { it.totalBase } / invoiceRows.size),
        "الذمم المتأخرة" to money(agingRows.sumOf { it.overdueBase }),
        "صافي مبيعات الأصناف" to money(reconciliation?.netSalesBase ?: productProfitRows.sumOf { it.netRevenueBase }),
        "تكلفة البضاعة المباعة" to money(productProfitRows.sumOf { it.netCostBase }),
        "مصاريف إضافية على الشركة" to money(reconciliation?.companyExpensesBase ?: additionalCharges.filter { it.accountingTreatment == "COMPANY_EXPENSE" }.sumOf { it.amountBase }),
        "إيراد خدمات/رسوم" to money(reconciliation?.serviceRevenueBase ?: additionalCharges.sumOf { it.serviceRevenueBase }),
        "رسوم قابلة للاسترداد — رصيد آخر الفترة" to money(reconciliation?.recoverableClosingBase ?: additionalCharges.filter { it.accountingTreatment == "RECOVERABLE" }.sumOf { it.remainingBase }),
        "تكلفة شحن فعلية مخصصة للفواتير" to money(shipmentCosts.sumOf { it.allocatedBase })
    )
    val productQuantityTable = ReportExportTable(
        title = "كميات المنتجات المباعة",
        headers = listOf("المنتج", "الكود", "مباع", "مرتجع مباع", "صافي المباع", "مجاني", "مرتجع مجاني", "صافي مجاني", "تكلفة المجاني", "أثره على الربح"),
        rows = productQuantities.map { q ->
            listOf(
                q.productName, q.code, qty(q.grossQtyBase), qty(q.returnedQtyBase), qty(q.netQtyBase),
                qty(q.freeQtyBase), qty(q.returnedFreeQtyBase), qty(q.netFreeQtyBase),
                money(q.netFreeCostBase), money(-q.netFreeCostBase)
            )
        }
    )
    val provinceTable = ReportExportTable(
        title = "ربحية المحافظات",
        headers = listOf("المحافظة", "الفواتير", "الإيراد", "التكلفة", "العمولات", "تكلفة الشحن/التوزيع الفعلية", "الربح", "الهامش"),
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
    fun invoiceStatus(row: SalesInvoiceAccountingReportRow): String = when {
        row.paymentType == "CASH" -> "نقدي / مسدد"
        row.outstandingBase <= 0.000001 -> "مسدد"
        row.dueDate != null && row.dueDate < to -> "متأخر"
        else -> "مفتوح"
    }
    val invoiceTable = ReportExportTable(
        title = "تفاصيل فواتير المبيعات",
        headers = listOf("الفاتورة", "التاريخ", "العميل", "المحافظة/المنطقة", "المندوب", "الدفع", "الصافي", "مرتجعات", "المحصّل", "المتبقي", "الاستحقاق", "الحالة"),
        rows = invoiceRows.map { r -> listOf(r.invoiceNo, fmtDate(r.invoiceDate), r.customerName, r.province, r.salesRepName.ifBlank { "غير محدد" },
            if (r.paymentType == "CASH") "نقدي" else "آجل", money(r.totalBase), money(r.returnsBase), money(r.collectedBase), money(r.outstandingBase), r.dueDate?.let(::fmtDate) ?: "—", invoiceStatus(r)) }
    )
    val productProfitTable = ReportExportTable(
        title = "ربحية المبيعات حسب المنتج",
        headers = listOf("المنتج", "الكود", "الإيراد", "المرتجع", "صافي الإيراد", "التكلفة", "تكلفة المرتجع", "صافي التكلفة", "الربح", "الهامش"),
        rows = productProfitRows.map { r -> listOf(r.itemName, r.itemCode, money(r.grossRevenueBase), money(r.returnedRevenueBase), money(r.netRevenueBase), money(r.grossCostBase), money(r.returnedCostBase), money(r.netCostBase), money(r.netProfitBase), "%.1f%%".format(Locale.US, ReportMath.margin(r.netProfitBase, r.netRevenueBase))) }
    )
    val repTable = ReportExportTable(
        title = "تحليل أداء مندوبي المبيعات",
        headers = listOf("المندوب", "العملاء", "الفواتير", "إجمالي المبيعات", "المرتجعات", "الصافي", "التحصيل", "الرصيد", "العمولة"),
        rows = repRows.map { r -> listOf(r.salesRepName, r.customerCount.toString(), r.invoiceCount.toString(), money(r.grossSalesBase), money(r.returnsBase), money(r.netSalesBase), money(r.collectionsBase), money(r.outstandingBase), money(r.commissionBase)) }
    )
    val agingTable = ReportExportTable(
        title = "أعمار الذمم المدينة",
        headers = listOf("العميل", "جاري", "1–30", "31–60", "61–90", ">90", "تسويات غير مخصصة", "الإجمالي"),
        rows = agingRows.map { r -> listOf(r.partyName, money(r.currentBase), money(r.days1To30Base), money(r.days31To60Base), money(r.days61To90Base), money(r.over90Base), money(r.unappliedBase), money(r.totalBalanceBase)) }
    )
    val returnTable = ReportExportTable(
        title = "تحليل مرتجعات المبيعات حسب العميل والمنتج والمندوب",
        headers = listOf("المرتجع", "التاريخ", "الفاتورة", "العميل", "المندوب", "المنتج", "الكمية", "القيمة", "التكلفة", "سبب المرتجع"),
        rows = returnRows.map { r -> listOf(r.returnNo, fmtDate(r.returnDate), r.invoiceNo, r.customerName, r.salesRepName, "${r.itemName} — ${r.itemCode}", qty(r.quantityBase), money(r.returnValueBase), money(r.returnCostBase), r.reason.ifBlank { "غير محدد" }) }
    )
    val monthlyTable = ReportExportTable(
        title = "الاتجاه الزمني الشهري للمبيعات",
        headers = listOf("الشهر", "الفواتير", "إجمالي المبيعات", "المرتجعات", "صافي المبيعات", "متوسط الفاتورة"),
        rows = monthlyRows.map { r -> listOf(r.periodKey, r.invoiceCount.toString(), money(r.grossSalesBase), money(r.returnsBase), money(r.netSalesBase), money(if (r.invoiceCount == 0) 0.0 else r.grossSalesBase / r.invoiceCount)) }
    )
    val reconciliationTable = ReportExportTable(
        title = "مطابقة المبيعات والذمم مع الأستاذ العام",
        headers = listOf("البند", "التشغيلي", "الأستاذ العام", "الفرق", "الحالة"),
        rows = reconciliation?.let { r -> listOf(
            listOf("إجمالي المبيعات", money(r.grossSalesBase), money(r.glSalesRevenueBase), money(r.glSalesRevenueBase-r.grossSalesBase), if (kotlin.math.abs(r.glSalesRevenueBase-r.grossSalesBase) < 0.01) "متطابق" else "فرق يحتاج مراجعة"),
            listOf("مرتجعات المبيعات", money(r.salesReturnsBase), money(r.glSalesReturnsBase), money(r.glSalesReturnsBase-r.salesReturnsBase), if (kotlin.math.abs(r.glSalesReturnsBase-r.salesReturnsBase) < 0.01) "متطابق" else "فرق يحتاج مراجعة"),
            listOf("صافي المبيعات", money(r.netSalesBase), money(r.glNetSalesBase), money(r.glNetSalesBase-r.netSalesBase), if (kotlin.math.abs(r.glNetSalesBase-r.netSalesBase) < 0.01) "متطابق" else "فرق يحتاج مراجعة"),
            listOf("مصاريف إضافية على الشركة", money(r.companyExpensesBase), money(r.glCompanyExpensesBase), money(r.glCompanyExpensesBase-r.companyExpensesBase), if (kotlin.math.abs(r.glCompanyExpensesBase-r.companyExpensesBase) < 0.01) "متطابق" else "فرق يحتاج مراجعة"),
            listOf("إيراد خدمات/رسوم", money(r.serviceRevenueBase), money(r.glServiceRevenueBase), money(r.glServiceRevenueBase-r.serviceRevenueBase), if (kotlin.math.abs(r.glServiceRevenueBase-r.serviceRevenueBase) < 0.01) "متطابق" else "فرق يحتاج مراجعة"),
            listOf("رسوم قابلة للاسترداد — رصيد آخر الفترة", money(r.recoverableClosingBase), money(r.glRecoverableClosingBase), money(r.glRecoverableClosingBase-r.recoverableClosingBase), if (kotlin.math.abs(r.glRecoverableClosingBase-r.recoverableClosingBase) < 0.01) "متطابق" else "فرق يحتاج مراجعة"),
            listOf("رصيد العملاء حتى نهاية الفترة", money(r.operationalReceivablesClosingBase), money(r.glReceivablesClosingBase), money(r.glReceivablesClosingBase-r.operationalReceivablesClosingBase), if (kotlin.math.abs(r.glReceivablesClosingBase-r.operationalReceivablesClosingBase) < 0.01) "متطابق" else "فرق يحتاج مراجعة")
        ) } ?: emptyList()
    )
    fun chargeTreatmentLabel(value: String): String = when (value) {
        "RECOVERABLE" -> "قابل للاسترداد"
        "COMPANY_EXPENSE" -> "مصروف على الشركة"
        "SERVICE_REVENUE" -> "إيراد خدمة"
        else -> value
    }
    fun chargeBearerLabel(value: String): String = if (value == "CUSTOMER") "العميل" else if (value == "COMPANY") "الشركة" else value
    val additionalChargeTable = ReportExportTable(
        title = "الرسوم والتكاليف الإضافية — منفصلة عن مبيعات الأصناف",
        headers = listOf("رقم الرسم", "التاريخ", "العميل", "النوع", "المتحمل", "المعالجة", "Principal/Agent", "حالة الدفع", "الدفع بواسطة", "العملة", "القيمة", "المدفوع", "المسوّى", "المتبقي", "إيراد الخدمة"),
        rows = additionalCharges.map { r -> listOf(
            r.chargeNo, fmtDate(r.chargeDate), r.customerName, r.chargeTypeName, chargeBearerLabel(r.bearer), chargeTreatmentLabel(r.accountingTreatment),
            r.principalAgentMode, r.paymentStatus, r.paidBy, r.currencyCode, money(r.amountBase), money(r.paidBase), money(r.settledBase), money(r.remainingBase), money(r.serviceRevenueBase)
        ) }
    )
    val shipmentCostTable = ReportExportTable(
        title = "تتبع الشحنة ↔ الفاتورة ↔ سند الصرف ↔ حصة التكلفة",
        headers = listOf("الشحنة", "تاريخ الشحنة", "المحافظة", "مرجع النقل", "الفاتورة", "تاريخ الفاتورة", "العميل", "نوع المصروف", "سند الصرف", "مرجع الدفع", "قيمة المصروف", "حصة الفاتورة", "كمية مرتبطة", "إجمالي الشحنة", "الموزع", "المتبقي"),
        rows = shipmentCosts.map { r -> listOf(
            r.shipmentNo, fmtDate(r.shipmentDate), r.destinationProvince, r.transportReference.ifBlank { "—" }, r.invoiceNo, fmtDate(r.invoiceDate), r.customerName,
            r.expenseType, r.paymentVoucherNo.ifBlank { "—" }, r.paymentReference.ifBlank { "—" }, money(r.expenseAmountBase), money(r.allocatedBase),
            qty(r.shipmentAllocatedQuantityBase), money(r.shipmentTotalExpenseBase), money(r.shipmentAllocatedExpenseBase), money(r.shipmentRemainingExpenseBase)
        ) }
    )
    val cashCreditTable = ReportExportTable(
        title = "المبيعات النقدية مقابل الآجلة",
        headers = listOf("النوع", "القيمة", "النسبة من الإجمالي"),
        rows = reconciliation?.let { r -> val total=r.cashSalesBase+r.creditSalesBase; listOf(
            listOf("نقدي", money(r.cashSalesBase), if(total<=0) "0.0%" else "%.1f%%".format(Locale.US, r.cashSalesBase*100.0/total)),
            listOf("آجل", money(r.creditSalesBase), if(total<=0) "0.0%" else "%.1f%%".format(Locale.US, r.creditSalesBase*100.0/total))
        ) } ?: emptyList()
    )
    return ReportExportDocument(
        title = "تقرير المبيعات — Fush ERP",
        subtitle = reportPeriodSubtitle(periodLabel, from, to),
        summary = summary,
        tables = listOf(productQuantityTable, provinceTable, customerTable, invoiceTable, productProfitTable, repTable, agingTable, returnTable, additionalChargeTable, shipmentCostTable, cashCreditTable, monthlyTable, reconciliationTable, commissionTable),
        notes = listOf("تم توحيد أسماء المحافظات والمناطق داخل التقرير فقط دون تعديل البيانات التاريخية المخزنة.", "ربحية المنتج تعتمد صافي إيراد أسطر الأصناف والتكلفة الفعلية للتخصيصات. تكلفة الشحن الفعلية تأتي أولاً من Shipment Expense Allocation؛ وعند عدم وجود تخصيص شحنة فقط يبقى سجل التكلفة الجغرافية القديم كمسار توافق تاريخي لمنع الازدواجية.", "إذا كان مصروف الشحنة محدداً على العميل، تسترد الفاتورة تلقائياً الحصة الفعلية المخصصة من نفس المصروف دون إنشاء مصروف ثانٍ؛ أما AdditionalCharges فتبقى مستقلة للرسوم أو الزيادات السعرية المنفصلة عن التكلفة الفعلية.")
    )
}

private fun buildPurchasesReportExportDocument(
    rows: List<SupplierPurchaseReportRow>,
    details: List<PurchaseInvoiceDetailReportRow>,
    accountingRows: List<PurchaseInvoiceAccountingReportRow>,
    returnRows: List<PurchaseReturnReportRow>,
    itemRows: List<PurchaseItemAnalysisReportRow>,
    agingRows: List<SupplierAgingRow>,
    reconciliation: PurchaseReconciliationReportRow?,
    periodLabel: String,
    from: Long,
    to: Long
): ReportExportDocument {
    val invoiceRows = details
        .groupBy { it.invoiceId }
        .values
        .mapNotNull { lines -> lines.firstOrNull() }
        .sortedWith(compareBy<PurchaseInvoiceDetailReportRow> { it.invoiceDate }.thenBy { it.invoiceId })

    fun invoiceStatus(row: PurchaseInvoiceAccountingReportRow): String = when {
        row.paymentType == "CASH" -> "نقدي / مسدد"
        row.outstandingBase <= 0.000001 -> "مسدد"
        row.dueDate != null && row.dueDate < to -> "متأخر"
        row.paidBase > 0.000001 -> "مسدد جزئياً"
        else -> "مفتوح"
    }

    fun paymentTypeLabel(value: String): String = when (value) {
        "CASH" -> "نقدي"
        "CREDIT" -> "آجل"
        else -> value
    }

    fun settlementTypeLabel(value: String): String = when (value) {
        "SUPPLIER_CREDIT" -> "تخفيض ذمة المورد"
        "CASH_REFUND" -> "استرداد نقدي"
        else -> value
    }

    val supplierTable = ReportExportTable(
        title = "المشتريات حسب المورد",
        headers = listOf("المورد", "الفواتير", "الإجمالي", "المرتجع", "الصافي"),
        rows = rows.map { r ->
            listOf(r.supplierName, r.invoiceCount.toString(), money(r.grossPurchasesBase), money(r.returnsBase), money(r.netPurchasesBase))
        }
    )

    val invoiceIdentityTable = ReportExportTable(
        title = "تفاصيل الفواتير — البيانات الأساسية | تفاصيل فواتير المشتريات",
        headers = listOf("الفاتورة", "فاتورة المورد", "التاريخ", "المورد", "العملة", "نوع الشراء", "الاستحقاق", "الحالة"),
        rows = accountingRows.map { r ->
            listOf(
                r.invoiceNo,
                r.supplierInvoiceNo.ifBlank { "—" },
                fmtDate(r.invoiceDate),
                r.supplierName,
                r.currencyCode,
                paymentTypeLabel(r.paymentType),
                r.dueDate?.let { fmtDate(it) } ?: "—",
                invoiceStatus(r)
            )
        }
    )

    val invoiceFinancialTable = ReportExportTable(
        title = "تفاصيل الفواتير — التحليل المالي (المشتريات)",
        headers = listOf("الفاتورة", "الإجمالي قبل الخصم", "الخصم", "رسوم ونقل", "الضريبة*", "الصافي", "الصافي أساسي", "المدفوع أساسي", "المتبقي أساسي"),
        rows = accountingRows.map { r ->
            val grossBeforeDiscount = r.subtotalOriginal + r.chargesOriginal + r.taxOriginal
            listOf(
                r.invoiceNo,
                money(grossBeforeDiscount),
                money(r.discountOriginal),
                money(r.chargesOriginal),
                money(r.taxOriginal),
                money(r.totalOriginal),
                money(r.totalBase),
                money(r.paidBase),
                money(r.outstandingBase)
            )
        }
    )

    val lineTable = ReportExportTable(
        title = "أصناف وكميات وأسعار كل فاتورة",
        headers = listOf("الفاتورة", "الصنف", "الوحدة", "الكمية", "سعر الوحدة", "إجمالي السطر", "العملة", "التشغيلة/الصلاحية"),
        rows = details.map { r ->
            val lotExpiry = listOfNotNull(
                r.lotNo?.takeIf { it.isNotBlank() },
                r.expiryDate?.let { fmtDate(it) }
            ).joinToString(" • ").ifBlank { "—" }
            listOf(
                r.invoiceNo,
                "${r.itemCode} — ${r.itemName}",
                r.unitName,
                qty(r.quantity),
                money(r.unitPriceOriginal),
                money(r.lineTotalOriginal),
                r.currencyCode,
                lotExpiry
            )
        }
    )

    val itemAnalysisTable = ReportExportTable(
        title = "تحليل المشتريات حسب الصنف",
        headers = listOf("الصنف", "الوحدة", "وارد", "مرتجع", "صافي الكمية", "قيمة الوارد", "قيمة المرتجع", "صافي القيمة"),
        rows = itemRows.map { r ->
            listOf(
                "${r.itemCode} — ${r.itemName}",
                r.baseUnitName,
                qty(r.grossQtyBase),
                qty(r.returnedQtyBase),
                qty(r.netQtyBase),
                money(r.grossValueBase),
                money(r.returnedValueBase),
                money(r.netValueBase)
            )
        }
    )

    val monthFormat = SimpleDateFormat("yyyy-MM", Locale.US)
    val periodInvoiceGroups = accountingRows.groupBy { monthFormat.format(Date(it.invoiceDate)) }
    val periodReturnGroups = returnRows.groupBy { monthFormat.format(Date(it.returnDate)) }
    val periods = (periodInvoiceGroups.keys + periodReturnGroups.keys).toSortedSet()
    val periodAnalysisTable = ReportExportTable(
        title = "تحليل المشتريات حسب الفترة",
        headers = listOf("الفترة", "الفواتير", "إجمالي المشتريات", "المرتجعات", "صافي المشتريات"),
        rows = periods.map { key ->
            val inv = periodInvoiceGroups[key].orEmpty()
            val ret = periodReturnGroups[key].orEmpty()
            val grossBase = inv.sumOf { it.totalBase }
            val returnsBase = ret.sumOf { it.totalBase }
            listOf(key, inv.size.toString(), money(grossBase), money(returnsBase), money(grossBase - returnsBase))
        }
    )

    val paymentTypeRows = listOf("CASH", "CREDIT").map { type ->
        val inv = accountingRows.filter { it.paymentType == type }
        val linkedReturns = returnRows.filter { it.originalPaymentType == type }
        val grossBase = inv.sumOf { it.totalBase }
        val returnsBase = linkedReturns.sumOf { it.totalBase }
        listOf(paymentTypeLabel(type), inv.size.toString(), money(grossBase), money(returnsBase), money(grossBase - returnsBase))
    }
    val paymentTypeTable = ReportExportTable(
        title = "المشتريات النقدية مقابل الآجلة",
        headers = listOf("نوع الشراء", "الفواتير", "الإجمالي", "المرتجعات المرتبطة", "الصافي"),
        rows = paymentTypeRows
    )

    val agingTable = ReportExportTable(
        title = "أرصدة الموردين وأعمار الديون حتى ${fmtDate(to)}",
        headers = listOf("المورد", "جاري", "1-30", "31-60", "61-90", "أكثر من 90", "الإجمالي"),
        rows = agingRows.map { r ->
            listOf(
                r.supplierName,
                money(r.currentBase),
                money(r.days1To30Base),
                money(r.days31To60Base),
                money(r.days61To90Base),
                money(r.over90Base),
                money(r.totalOutstandingBase)
            )
        }
    )

    val returnsTable = ReportExportTable(
        title = "مرتجعات المشتريات وربطها بالفواتير الأصلية",
        headers = listOf("المرتجع", "الفاتورة الأصلية", "التاريخ", "المورد", "العملة", "نوع التسوية", "قيمة المرتجع", "القيمة الأساسية"),
        rows = returnRows.map { r ->
            listOf(
                r.returnNo,
                r.invoiceNo,
                fmtDate(r.returnDate),
                r.supplierName,
                r.currencyCode,
                settlementTypeLabel(r.settlementType),
                money(r.totalOriginal),
                money(r.totalBase)
            )
        }
    )

    val reconciliationRows = reconciliation?.let { r ->
        listOf(
            listOf(
                "صافي المشتريات ↔ حساب المخزون 1200",
                money(r.netPurchasesBase),
                money(r.inventoryGlNetBase),
                money(r.inventoryGlNetBase - r.netPurchasesBase),
                if (abs(r.inventoryGlNetBase - r.netPurchasesBase) <= 0.01) "متطابق" else "فرق يحتاج مراجعة"
            ),
            listOf(
                "المشتريات الآجلة الصافية ↔ حساب الموردين 2100 (مستندات الشراء)",
                money(r.apDocumentOperationalNetBase),
                money(r.apDocumentGlNetBase),
                money(r.apDocumentGlNetBase - r.apDocumentOperationalNetBase),
                if (abs(r.apDocumentGlNetBase - r.apDocumentOperationalNetBase) <= 0.01) "متطابق" else "فرق يحتاج مراجعة"
            ),
            listOf(
                "رصيد الموردين التشغيلي ↔ رصيد الأستاذ العام 2100",
                money(r.operationalPayablesClosingBase),
                money(r.apGlClosingBase),
                money(r.apGlClosingBase - r.operationalPayablesClosingBase),
                if (abs(r.apGlClosingBase - r.operationalPayablesClosingBase) <= 0.01) "متطابق" else "فرق يحتاج مراجعة"
            )
        )
    }.orEmpty()

    val reconciliationTable = ReportExportTable(
        title = "مطابقة المشتريات والحسابات الدائنة مع الأستاذ العام",
        headers = listOf("المطابقة", "المصدر التشغيلي", "الأستاذ العام", "الفرق", "الحالة"),
        rows = reconciliationRows
    )

    val reconciliationDetailTable = reconciliation?.let { r ->
        ReportExportTable(
            title = "تفاصيل مطابقة الحسابات الدائنة",
            headers = listOf("البند", "القيمة"),
            rows = listOf(
                listOf("إجمالي المشتريات الآجلة خلال الفترة", money(r.creditPurchasesBase)),
                listOf("مرتجعات تخفيض ذمة المورد خلال الفترة", money(r.supplierCreditReturnsBase)),
                listOf("رصيد الفواتير الآجلة المفتوح حتى نهاية التقرير", money(r.openSupplierInvoicesBase)),
                listOf("تعديلات سندات الموردين حتى نهاية التقرير", money(r.supplierVoucherAdjustmentBase)),
                listOf("رصيد الموردين التشغيلي بعد التعديلات", money(r.operationalPayablesClosingBase)),
                listOf("رصيد حساب الموردين 2100 في الأستاذ العام", money(r.apGlClosingBase))
            )
        )
    }

    val cashPurchasesBase = accountingRows.filter { it.paymentType == "CASH" }.sumOf { it.totalBase }
    val creditPurchasesBase = accountingRows.filter { it.paymentType == "CREDIT" }.sumOf { it.totalBase }
    val totalOutstanding = reconciliation?.operationalPayablesClosingBase ?: agingRows.sumOf { it.totalOutstandingBase }
    val totalOverdue = agingRows.sumOf { it.days1To30Base + it.days31To60Base + it.days61To90Base + it.over90Base }

    val tables = mutableListOf(
        supplierTable,
        invoiceIdentityTable,
        invoiceFinancialTable,
        lineTable,
        itemAnalysisTable,
        periodAnalysisTable,
        paymentTypeTable,
        agingTable,
        returnsTable,
        reconciliationTable
    )
    reconciliationDetailTable?.let { tables += it }

    val notes = mutableListOf<String>()
    if (details.isEmpty()) notes += "لا توجد تفاصيل فواتير مشتريات مرحلة ضمن الفترة المحددة."
    notes += "الضريبة*: لا يوجد حقل ضريبة مستقل في مستند فاتورة الشراء الحالية، لذلك يعرض التقرير 0 حتى تتم إضافة ضريبة موثقة كمكوّن مستقل في المستند."
    notes += "مطابقة صافي المشتريات مع حساب المخزون تستخدم فقط قيود المصدر PURCHASE وPURCHASE_RETURN حتى لا تختلط بحركات الإنتاج أو التسويات الأخرى."
    notes += "مطابقة الحسابات الدائنة تفصل أثر مستندات الشراء عن الرصيد الختامي لحساب الموردين 2100؛ المشتريات النقدية لا يفترض أن تزيد الحسابات الدائنة."

    return ReportExportDocument(
        title = "تقرير المشتريات المحاسبي الاحترافي — Fush ERP",
        subtitle = reportPeriodSubtitle(periodLabel, from, to),
        summary = listOf(
            "الفترة" to "$periodLabel • ${fmtDate(from)} — ${fmtDate(to)}",
            "الموردون" to rows.size.toString(),
            "الفواتير" to accountingRows.size.toString(),
            "أسطر الأصناف" to details.size.toString(),
            "إجمالي المشتريات" to money(rows.sumOf { it.grossPurchasesBase }),
            "المرتجعات" to money(rows.sumOf { it.returnsBase }),
            "صافي المشتريات" to money(rows.sumOf { it.netPurchasesBase }),
            "مشتريات نقدية" to money(cashPurchasesBase),
            "مشتريات آجلة" to money(creditPurchasesBase),
            "رصيد الموردين" to money(totalOutstanding),
            "ديون موردين متأخرة" to money(totalOverdue)
        ),
        tables = tables,
        notes = notes
    )
}

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
            "تشغيلات سارية الصلاحية" to expiry.count { it.daysToExpiry >= 0L }.toString()
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
    analytics: ProductionAnalyticsBundle,
    maintenance: MaintenanceReportRow?,
    periodLabel: String,
    from: Long,
    to: Long
): ReportExportDocument {
    val planned = rows.sumOf { it.plannedQtyBase }
    val actual = rows.sumOf { it.actualQtyBase }
    val accepted = rows.sumOf { it.acceptedQtyBase }
    val rejected = rows.sumOf { it.rejectedQtyBase }
    val scrap = rows.sumOf { it.scrapQtyBase }
    val materialCost = rows.sumOf { it.materialCostBase }
    val laborCost = rows.sumOf { it.laborCostBase }
    val overheadCost = analytics.overheadAccounts.sumOf { it.amountBase }
    val industrialCost = materialCost + laborCost + overheadCost
    val linkedOverhead = analytics.orderOverhead.sumOf { it.overheadBase }
    val unlinkedOverhead = overheadCost - linkedOverhead
    val productAnalysis = buildProductionProductAnalysis(rows, analytics)
    val overheadByOrder = analytics.orderOverhead.associate { it.orderId to it.overheadBase }
    val productAverageCost = productAnalysis.associate { p -> p.productCode to ReportMath.unitCost(p.totalCostBase, p.acceptedQtyBase) }

    val summary = mutableListOf(
        "الفترة" to "$periodLabel • ${fmtDate(from)} — ${fmtDate(to)}",
        "أوامر الإنتاج" to rows.size.toString(),
        "المخطط" to qty(planned),
        "الناتج الفعلي" to qty(actual),
        "فرق الإنتاج" to qty(actual - planned),
        "تحقيق الخطة" to "%.1f%%".format(Locale.US, ReportMath.percent(actual, planned)),
        "المقبول الإجمالي" to qty(accepted),
        "المرفوض" to qty(rejected),
        "الهالك" to qty(scrap),
        "نسبة القبول" to "%.1f%%".format(Locale.US, ReportMath.percent(accepted, actual)),
        "نسبة الهالك" to "%.1f%%".format(Locale.US, ReportMath.percent(scrap, actual)),
        "تكلفة المواد" to money(materialCost),
        "تكلفة العمالة" to money(laborCost),
        "التكاليف الصناعية غير المباشرة" to money(overheadCost),
        "إجمالي التكلفة الصناعية" to money(industrialCost),
        "متوسط تكلفة المقبول/وحدة" to money(ReportMath.unitCost(industrialCost, accepted))
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
        rows = materialUsage.map { m -> listOf(m.itemName, m.code, m.unitName, qty(m.issuedQtyBase), money(m.averageUnitCostBase), money(m.totalCostBase), m.orderCount.toString()) }
    )
    val materialVarianceTable = ReportExportTable(
        title = "المعياري مقابل الفعلي للمواد الخام حسب أمر الإنتاج",
        headers = listOf("رقم الأمر", "المنتج", "المادة", "الوحدة", "المعياري BOM", "الفعلي", "فرق الكمية", "متوسط التكلفة", "تكلفة الفرق", "الانحراف %"),
        rows = analytics.materialVariance.map { r -> listOf(
            r.orderNo, r.productName, "${r.itemName} — ${r.itemCode}", r.unitName,
            qty(r.standardQtyBase), qty(r.actualQtyBase), qty(r.quantityVarianceBase), money(r.averageUnitCostBase), money(r.varianceCostBase), "%.1f%%".format(Locale.US, r.variancePct)
        ) }
    )
    val orderQuantityTable = ReportExportTable(
        title = "أوامر الإنتاج — الخطة والناتج والجودة",
        headers = listOf("رقم الأمر", "المنتج", "التشغيلة", "التاريخ", "الحالة", "مخطط", "فعلي", "فرق الإنتاج", "الانحراف %", "مقبول", "مرفوض", "هالك"),
        rows = rows.map { r ->
            val variancePct = if (r.plannedQtyBase > 0.0) (r.actualQtyBase-r.plannedQtyBase)*100.0/r.plannedQtyBase else 0.0
            listOf(r.orderNo, "${r.productName} — ${r.productCode}", r.batchNo ?: "بدون دفعة", fmtDate(r.manufactureDate ?: r.plannedDate), reportProductionStatusAr(r.status),
                qty(r.plannedQtyBase), qty(r.actualQtyBase), qty(r.actualQtyBase-r.plannedQtyBase), "%.1f%%".format(Locale.US, variancePct), qty(r.acceptedQtyBase), qty(r.rejectedQtyBase), qty(r.scrapQtyBase))
        }
    )
    val orderCostTable = ReportExportTable(
        title = "أوامر الإنتاج — التكلفة الفعلية ومقارنة تكلفة الوحدة",
        headers = listOf("رقم الأمر", "المنتج", "مواد", "عمالة", "غير مباشر مرتبط", "إجمالي مرتبط", "مقبول", "تكلفة الوحدة", "متوسط المنتج", "الانحراف %", "التنبيه"),
        rows = rows.map { r ->
            val overhead = overheadByOrder[r.orderId] ?: 0.0
            val orderCost = r.actualCostBase + overhead
            val unitCost = ReportMath.unitCost(orderCost, r.acceptedQtyBase)
            val avg = productAverageCost[r.productCode] ?: 0.0
            val variancePct = if (avg > 0.000000001) (unitCost-avg)*100.0/avg else 0.0
            val alert = when {
                variancePct >= 15.0 -> "مرتفع غير طبيعي"
                variancePct <= -15.0 -> "منخفض غير طبيعي"
                else -> "ضمن النطاق"
            }
            listOf(r.orderNo, r.productName, money(r.materialCostBase), money(r.laborCostBase), money(overhead), money(orderCost), qty(r.acceptedQtyBase), money(unitCost), money(avg), "%.1f%%".format(Locale.US, variancePct), alert)
        }
    )
    val overheadTable = ReportExportTable(
        title = "التكاليف الصناعية غير المباشرة الفعلية — مركز تكلفة الإنتاج",
        headers = listOf("الحساب", "البيان", "القيمة"),
        rows = analytics.overheadAccounts.map { listOf(it.accountCode, it.accountName, money(it.amountBase)) } +
            listOf(listOf("—", "إجمالي غير مباشر", money(overheadCost)), listOf("—", "مرتبط مباشرة بأوامر إنتاج", money(linkedOverhead)), listOf("—", "غير مرتبط بأمر محدد", money(unlinkedOverhead)))
    )
    val wipTable = ReportExportTable(
        title = "الإنتاج تحت التشغيل WIP — حساب 1210",
        headers = listOf("رصيد أول الفترة", "مواد مضافة", "عمالة مضافة", "غير مباشر محمل", "محول لمنتج نهائي", "محول لخسائر", "حركات أخرى صافي", "رصيد آخر الفترة"),
        rows = analytics.wip?.let { w -> listOf(listOf(money(w.openingWipBase), money(w.materialAddedBase), money(w.laborAddedBase), money(w.overheadAddedBase), money(w.finishedTransferredBase), money(w.rejectedTransferredBase), money(w.otherNetMovementBase), money(w.closingWipBase))) } ?: emptyList()
    )
    val productTable = ReportExportTable(
        title = "تحليل الإنتاج حسب المنتج",
        headers = listOf("المنتج", "الكود", "الأوامر", "مخطط", "فعلي", "مقبول", "مرفوض", "هالك", "مواد", "عمالة", "غير مباشر مرتبط", "إجمالي مرتبط", "متوسط الوحدة"),
        rows = productAnalysis.map { p -> listOf(p.productName, p.productCode, p.orderCount.toString(), qty(p.plannedQtyBase), qty(p.actualQtyBase), qty(p.acceptedQtyBase), qty(p.rejectedQtyBase), qty(p.scrapQtyBase), money(p.materialCostBase), money(p.laborCostBase), money(p.linkedOverheadBase), money(p.totalCostBase), money(ReportMath.unitCost(p.totalCostBase,p.acceptedQtyBase))) }
    )
    val lossTable = ReportExportTable(
        title = "الهالك والمرفوض وإعادة التشغيل",
        headers = listOf("رقم الأمر", "المنتج", "التشغيلة", "مرفوض", "هالك", "إعادة تشغيل", "التكلفة التقديرية", "النسبة %", "السبب", "المسؤول"),
        rows = analytics.losses.map { l ->
            val lossQty = l.rejectedQtyBase + l.scrapQtyBase
            val lossCost = if (l.actualQtyBase > 0.000000001) l.baseProductionCost * lossQty / l.actualQtyBase else 0.0
            listOf(l.orderNo, l.productName, l.batchNo ?: "—", qty(l.rejectedQtyBase), qty(l.scrapQtyBase), "غير مسجل", money(lossCost), "%.1f%%".format(Locale.US, ReportMath.percent(lossQty,l.actualQtyBase)), l.reason, l.responsible)
        }
    )
    val laborTable = ReportExportTable(
        title = "تحليل إنتاجية العمالة",
        headers = listOf("العامل/المشغل", "الأوامر", "المقبول", "ساعات العمل", "تكلفة ساعات العمل", "وحدات/ساعة", "تكلفة العمالة/وحدة"),
        rows = analytics.labor.map { l -> listOf(l.employeeName, l.orderCount.toString(), qty(l.acceptedQtyBase), "غير مسجلة", money(l.laborCostBase), "—", money(ReportMath.unitCost(l.laborCostBase,l.acceptedQtyBase))) }
    )
    val downtimeTable = ReportExportTable(
        title = "التوقفات وكفاءة التشغيل",
        headers = listOf("النوع", "المرجع", "الأصل/المعدة", "التاريخ", "مدة التوقف", "السبب", "الحالة", "أوامر إنتاج مرتبطة بالأصل"),
        rows = analytics.downtime.map { d -> listOf(d.sourceType, d.sourceNo, d.assetName, fmtDate(d.eventDate), "${d.downtimeMinutes} دقيقة", d.reason.ifBlank { "غير مسجل" }, d.status, d.relatedOrders.ifBlank { "—" }) }
    )
    val lotTraceTable = ReportExportTable(
        title = "تتبع المواد الخام إلى تشغيلة المنتج النهائي",
        headers = listOf("رقم الأمر", "المنتج النهائي", "تشغيلة النهائي", "المادة الخام", "تشغيلة الخام", "صلاحية الخام", "الكمية", "التكلفة"),
        rows = analytics.lotTrace.map { t -> listOf(t.orderNo, "${t.productName} — ${t.productCode}", t.finishedLotNo ?: "—", "${t.rawItemName} — ${t.rawItemCode}", t.rawLotNo ?: "بدون رقم", t.rawExpiryDate?.let(::fmtDate) ?: "—", qty(t.issuedQtyBase), money(t.issueCostBase)) }
    )
    val recon = analytics.reconciliation
    val reconciliationTable = ReportExportTable(
        title = "المطابقة مع المخزون والأستاذ العام",
        headers = listOf("البند", "التشغيلي", "الأستاذ العام", "الفرق", "الحالة"),
        rows = if (recon == null) emptyList() else listOf(
            listOf("صرف المواد إلى WIP", money(recon.operationalMaterialCostBase), money(recon.glMaterialToWipBase), money(recon.operationalMaterialCostBase-recon.glMaterialToWipBase), if (abs(recon.operationalMaterialCostBase-recon.glMaterialToWipBase)<0.01) "متطابق" else "فرق يحتاج مراجعة"),
            listOf("تحميل العمالة إلى WIP", money(recon.operationalLaborCostBase), money(recon.glLaborToWipBase), money(recon.operationalLaborCostBase-recon.glLaborToWipBase), if (abs(recon.operationalLaborCostBase-recon.glLaborToWipBase)<0.01) "متطابق" else "فرق يحتاج مراجعة"),
            listOf("استلام المنتج النهائي", money(recon.operationalFinishedReceiptBase), money(recon.glFinishedReceiptBase), money(recon.operationalFinishedReceiptBase-recon.glFinishedReceiptBase), if (abs(recon.operationalFinishedReceiptBase-recon.glFinishedReceiptBase)<0.01) "متطابق" else "فرق يحتاج مراجعة"),
            listOf("التكاليف غير المباشرة مقابل المحمل على WIP", money(overheadCost), money(analytics.wip?.overheadAddedBase ?: 0.0), money(overheadCost-(analytics.wip?.overheadAddedBase ?: 0.0)), if (abs(overheadCost-(analytics.wip?.overheadAddedBase ?: 0.0))<0.01) "متطابق" else "غير محمل بالكامل على WIP"),
            listOf("رصيد WIP آخر الفترة", money(recon.closingWipGlBase), money(recon.closingWipGlBase), money(0.0), "رصيد GL"),
            listOf("خسائر الإنتاج المرحلة 6300", money(recon.glProductionLossBase), money(recon.glProductionLossBase), money(0.0), "قيد محاسبي")
        )
    )
    val totalsTable = ReportExportTable(
        title = "الإجماليات النهائية",
        headers = listOf("إجمالي المواد", "إجمالي العمالة", "غير مباشر", "الإنتاج المقبول", "المرفوض", "الهالك", "إجمالي تكلفة الإنتاج", "متوسط تكلفة الوحدة"),
        rows = listOf(listOf(money(materialCost), money(laborCost), money(overheadCost), qty(accepted), qty(rejected), qty(scrap), money(industrialCost), money(ReportMath.unitCost(industrialCost,accepted))))
    )

    return ReportExportDocument(
        title = "تقرير الإنتاج والتحليل — Fush ERP",
        subtitle = "تم إنشاء التقرير من البيانات الفعلية المسجلة في أوامر الإنتاج والمخزون والمحاسبة.",
        summary = summary,
        tables = listOf(materialsTable, materialVarianceTable, orderQuantityTable, orderCostTable, overheadTable, wipTable, productTable, lossTable, laborTable, downtimeTable, lotTraceTable, reconciliationTable, totalsTable),
        notes = listOf(
            "تكلفة المواد مبنية على تكلفة الصرف التاريخية المسجلة لأوامر الإنتاج.",
            "تكلفة فرق الكمية في جدول المعياري/الفعلي = فرق الكمية × متوسط تكلفة الصرف الفعلي للمادة في الأمر؛ ولا يدّعي التقرير وجود انحراف سعر معياري إذا لم يسجل النظام تكلفة معيارية مستقلة.",
            "التكاليف الصناعية غير المباشرة تشمل المصروفات الفعلية المصنفة على مركز تكلفة الإنتاج. المصروف غير المرتبط بأمر إنتاج يظهر في الإجمالي ولا يوزع اعتباطياً على الأوامر.",
            "ساعات العمل ووقت التشغيل الفعلي والطاقة المخططة وإعادة التشغيل ليست حقولاً مسجلة حالياً على مستوى أمر الإنتاج؛ لذلك يعرض التقرير —/غير مسجل بدلاً من أرقام تقديرية.",
            "تنبيه تكلفة الوحدة يعتبر الانحراف عن متوسط المنتج غير طبيعي عند ±15% أو أكثر.",
            "جداول أوامر الإنتاج قُسمت إلى جدول كميات/جودة وجدول تكلفة لتقليل تداخل الأكواد والأسماء وتحسين التفاف النص في PDF."
        )
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
                onSelect = { selectedId -> selectedId?.let(onSelectedAccountChange) }
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
                    onSelect = { selectedId -> selectedId?.let(onSupplierChange) }
                )
            } else {
                ReportSelectionMenu(
                    label = "العميل",
                    selectedText = customer?.let { "${it.code} — ${it.nameAr}" } ?: "اختر العميل",
                    options = customers.map { it.id to "${it.code} — ${it.nameAr}" },
                    onSelect = { selectedId -> selectedId?.let(onCustomerChange) }
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
    onSelect: (Long?) -> Unit
) {
    FushSearchableSelectionField(
        label = label,
        selectedText = selectedText.takeUnless { it.startsWith("اختر") }.orEmpty(),
        options = options,
        optionText = { it.second },
        searchTerms = { listOf(it.first.toString(), it.second) },
        onSelected = { onSelect(it.first) },
        onCleared = { onSelect(null) },
        placeholder = "اكتب للبحث",
    )
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
     val end = now
    val start = when (period) {
        "اليوم" -> BusinessTimeZone.calendarAt(now).apply { set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }.timeInMillis
        "30 يوم" -> now - 30L*86_400_000L
        "هذه السنة" -> BusinessTimeZone.calendarAt(now).apply { set(Calendar.MONTH,0); set(Calendar.DAY_OF_MONTH,1); set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }.timeInMillis
        "كل الفترة" -> 0L
        else -> BusinessTimeZone.calendarAt(now).apply { set(Calendar.DAY_OF_MONTH,1); set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }.timeInMillis
    }
    return start to end
}

private val num = NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = 2 }
private fun money(v: Double) = "${num.format(v)} ر.ي"
private fun qty(v: Double) = num.format(v)
private fun fmtDate(ms: Long): String = if (ms <= 0L) "البداية" else SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))
