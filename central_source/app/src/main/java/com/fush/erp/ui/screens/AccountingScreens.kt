package com.fush.erp.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fush.erp.R
import com.fush.erp.data.AppContainer
import com.fush.erp.data.entity.*
import com.fush.erp.ui.*
import com.fush.erp.ui.export.ReportExportActions
import com.fush.erp.domain.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

private const val DAY_MS = 24L * 60L * 60L * 1000L

@Composable
fun AccountingScreen(container: AppContainer, user: UserEntity, modifier: Modifier = Modifier) {
    val tabs = listOf(
        R.string.accounting_tab_journal, R.string.accounting_tab_treasury, R.string.accounting_tab_expenses,
        R.string.accounting_tab_manual, R.string.accounting_tab_ledger, R.string.accounting_tab_trial_balance,
        R.string.accounting_tab_income_statement, R.string.accounting_tab_balance_sheet, R.string.accounting_tab_cash_flow,
        R.string.accounting_tab_fixed_assets, R.string.accounting_tab_periods_reconciliation,
    )
    var selected by remember { mutableIntStateOf(0) }
    Column(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FushSectionHeader(
                title = stringResource(R.string.accounting_title),
                subtitle = stringResource(R.string.accounting_subtitle),
            )
        }
        ScrollableTabRow(
            selectedTabIndex = selected,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            edgePadding = 10.dp,
        ) {
            tabs.forEachIndexed { index, titleRes ->
                Tab(
                    selected = selected == index,
                    onClick = { selected = index },
                    text = { Text(stringResource(titleRes), maxLines = 1) },
                )
            }
        }
        when (selected) {
            0 -> JournalTab(container, user, Modifier.weight(1f))
            1 -> TreasuryTab(container, user, Modifier.weight(1f))
            2 -> ExpenseManagementTab(container, user, Modifier.weight(1f))
            3 -> ManualJournalTab(container, user, Modifier.weight(1f))
            4 -> LedgerTab(container, Modifier.weight(1f))
            5 -> TrialBalanceTab(container, Modifier.weight(1f))
            6 -> ProfitLossTab(container, Modifier.weight(1f))
            7 -> BalanceSheetTab(container, Modifier.weight(1f))
            8 -> CashFlowTab(container, Modifier.weight(1f))
            9 -> FixedAssetsTab(container, user, Modifier.weight(1f))
            else -> AccountingPeriodsTab(container, user, Modifier.weight(1f))
        }
    }
}

@Composable
private fun FixedAssetsTab(container: AppContainer, user: UserEntity, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val assets by container.fixedAssetService.observeRegister().collectAsState(initial = emptyList())
    val periods by container.db.accountingDao().observePeriods().collectAsState(initial = emptyList())
    val treasury by container.db.accountingDao().observeTreasuryBalances().collectAsState(initial = emptyList())
    val maintenanceAssets by container.db.maintenanceDao().observeAssets().collectAsState(initial = emptyList())
    var showAdd by remember { mutableStateOf(false) }
    var disposalAsset by remember { mutableStateOf<FixedAssetRegisterRow?>(null) }
    var selectedPeriod by remember { mutableStateOf<AccountingPeriodEntity?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val activeCost = assets.filter { it.status != "CANCELLED" }.sumOf { it.acquisitionCostBase }
    val accumulated = assets.filter { it.status != "CANCELLED" }.sumOf { it.accumulatedDepreciationBase }
    val nbv = assets.filter { it.status != "CANCELLED" }.sumOf { it.netBookValueBase }
    val eligiblePeriods = periods.filter { it.status == "OPEN" && it.endDate < System.currentTimeMillis() }
        .sortedWith(compareByDescending<AccountingPeriodEntity> { it.fiscalYear }.thenByDescending { it.periodNo })

    LazyColumn(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            FushSectionHeader(
                title = "الأصول الثابتة",
                subtitle = "دفتر مالي للأصول والتكلفة والإهلاك والقيمة الدفترية والاستبعاد مع قيود آلية"
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FushMetricCard("التكلفة", money(activeCost), Modifier.weight(1f), "YER_NEW", FushStatusTone.Neutral)
                FushMetricCard("مجمع الإهلاك", money(accumulated), Modifier.weight(1f), "YER_NEW", FushStatusTone.Neutral)
                FushMetricCard("صافي القيمة", money(nbv), Modifier.weight(1f), "YER_NEW", FushStatusTone.Success)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { showAdd = true }) { Text("إضافة أصل مالي") }
                SelectionField(
                    "فترة الإهلاك",
                    selectedPeriod?.let { "${it.fiscalYear}/${it.periodNo}" } ?: "اختر فترة منتهية مفتوحة",
                    eligiblePeriods,
                    { "${it.fiscalYear}/${it.periodNo} — ${it.nameAr}" }
                ) { selectedPeriod = it }
                Button(
                    enabled = selectedPeriod != null,
                    onClick = {
                        val p = selectedPeriod ?: return@Button
                        scope.launch {
                            try {
                                val ids = container.fixedAssetService.postDepreciationForPeriod(p.id, user.id)
                                message = if (ids.isEmpty()) "لا توجد أصول مستحقة للإهلاك في هذه الفترة" else "تم ترحيل إهلاك ${ids.size} أصل/أصول"
                            } catch (e: Exception) { message = e.message ?: "تعذر ترحيل الإهلاك" }
                        }
                    }
                ) { Text("ترحيل الإهلاك") }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
        }

        if (assets.isEmpty()) {
            item { Text("لا توجد أصول مالية مسجلة. سجل الأصل المالي دون التأثير على سجل الصيانة التشغيلي.") }
        } else {
            items(assets, key = { "fixed-${it.id}" }) { asset ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${asset.assetNo} — ${asset.nameAr}", style = MaterialTheme.typography.titleMedium)
                            Text(fixedAssetStatusAr(asset.status), color = if (asset.status == "DISPOSED" || asset.status == "CANCELLED") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        }
                        Text("التكلفة: ${money(asset.acquisitionCostBase)} • مجمع الإهلاك: ${money(asset.accumulatedDepreciationBase)} • صافي القيمة: ${money(asset.netBookValueBase)} YER_NEW")
                        Text("بدء الاستخدام: ${formatDate(asset.inServiceDate)} • العمر: ${asset.usefulLifeMonths} شهر • القيمة المتبقية: ${money(asset.residualValueBase)}")
                        if (asset.status == "DISPOSED") {
                            Text("الاستبعاد: ${asset.disposalDate?.let(::formatDate) ?: "-"} • المتحصلات: ${money(asset.disposalProceedsBase)} • ربح/خسارة: ${money(asset.disposalGainLossBase)}")
                        } else if (asset.status in setOf("ACTIVE", "FULLY_DEPRECIATED")) {
                            TextButton(onClick = { disposalAsset = asset }) { Text("بيع / استبعاد الأصل") }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddFixedAssetDialog(
            treasury = treasury,
            maintenanceAssets = maintenanceAssets,
            onDismiss = { showAdd = false }
        ) { req ->
            scope.launch {
                try {
                    container.fixedAssetService.registerAsset(req.copy(createdBy = user.id))
                    showAdd = false
                    message = "تم تسجيل الأصل وترحيل قيد الاقتناء"
                } catch (e: Exception) { message = e.message ?: "تعذر تسجيل الأصل" }
            }
        }
    }

    disposalAsset?.let { asset ->
        DisposeFixedAssetDialog(asset, treasury, onDismiss = { disposalAsset = null }) { req ->
            scope.launch {
                try {
                    container.fixedAssetService.disposeAsset(req.copy(createdBy = user.id))
                    disposalAsset = null
                    message = "تم استبعاد الأصل وترحيل القيد المحاسبي"
                } catch (e: Exception) { message = e.message ?: "تعذر استبعاد الأصل" }
            }
        }
    }
}

@Composable
private fun AddFixedAssetDialog(
    treasury: List<TreasuryBalanceRow>,
    maintenanceAssets: List<AssetEntity>,
    onDismiss: () -> Unit,
    onSave: (FixedAssetService.RegisterRequest) -> Unit
) {
    var nameAr by remember { mutableStateOf("") }
    var nameEn by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("EQUIPMENT") }
    var acquisitionDate by remember { mutableStateOf(todayText()) }
    var inServiceDate by remember { mutableStateOf(todayText()) }
    var cost by remember { mutableStateOf("") }
    var residual by remember { mutableStateOf("0") }
    var life by remember { mutableStateOf("60") }
    var mode by remember { mutableStateOf("TREASURY") }
    var selectedTreasury by remember { mutableStateOf<TreasuryBalanceRow?>(null) }
    var rate by remember { mutableStateOf("1") }
    var linkedMaintenance by remember { mutableStateOf<AssetEntity?>(null) }
    var notes by remember { mutableStateOf("") }
    val categories = listOf("BUILDING", "MACHINERY", "VEHICLE", "EQUIPMENT", "FURNITURE", "IT", "OTHER")
    val modes = listOf("TREASURY", "OPENING")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تسجيل أصل ثابت مالي") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(nameAr, { nameAr = it }, label = { Text("اسم الأصل بالعربية") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(nameEn, { nameEn = it }, label = { Text("الاسم الإنجليزي - اختياري") }, modifier = Modifier.fillMaxWidth())
                SelectionField("التصنيف", category, categories, { fixedAssetCategoryAr(it) }) { category = it }
                OutlinedTextField(acquisitionDate, { acquisitionDate = it }, label = { Text("تاريخ الاقتناء yyyy-MM-dd") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(inServiceDate, { inServiceDate = it }, label = { Text("تاريخ بدء الاستخدام") }, modifier = Modifier.fillMaxWidth())
                SelectionField("طريقة التسجيل", if (mode == "TREASURY") "شراء من خزينة/بنك" else "رصيد افتتاحي", modes, { if (it == "TREASURY") "شراء من خزينة/بنك" else "رصيد افتتاحي مقابل 3100" }) { mode = it }
                if (mode == "TREASURY") {
                    SelectionField("الخزينة/البنك", selectedTreasury?.let { "${it.nameAr} (${it.currencyCode})" } ?: "اختر", treasury, { "${it.nameAr} (${it.currencyCode})" }) { selectedTreasury = it; if (it.currencyCode == "YER_NEW") rate = "1" }
                }
                OutlinedTextField(cost, { cost = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text(if (mode == "TREASURY") "تكلفة الشراء بالعملة الأصلية" else "التكلفة الافتتاحية YER_NEW") }, modifier = Modifier.fillMaxWidth())
                if (mode == "TREASURY" && selectedTreasury?.currencyCode != "YER_NEW") OutlinedTextField(rate, { rate = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("سعر الصرف إلى YER_NEW") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(residual, { residual = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("القيمة المتبقية YER_NEW") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(life, { life = it.filter(Char::isDigit) }, label = { Text("العمر الإنتاجي بالأشهر") }, modifier = Modifier.fillMaxWidth())
                if (maintenanceAssets.isNotEmpty()) {
                    SelectionField("ربط بسجل الصيانة - اختياري", linkedMaintenance?.let { "${it.code} — ${it.nameAr}" } ?: "بدون ربط", maintenanceAssets, { "${it.code} — ${it.nameAr}" }) { linkedMaintenance = it }
                    if (linkedMaintenance != null) TextButton(onClick = { linkedMaintenance = null }) { Text("إلغاء الربط بسجل الصيانة") }
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات") }, modifier = Modifier.fillMaxWidth())
                Text("سياسة الإهلاك: قسط ثابت شهري كامل ابتداءً من شهر وضع الأصل في الخدمة.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                enabled = nameAr.isNotBlank() && cost.toDoubleOrNull()?.let { it > 0 } == true && life.toIntOrNull()?.let { it > 0 } == true && (mode == "OPENING" || selectedTreasury != null),
                onClick = {
                    val tr = selectedTreasury
                    onSave(
                        FixedAssetService.RegisterRequest(
                            nameAr = nameAr,
                            nameEn = nameEn,
                            category = category,
                            maintenanceAssetId = linkedMaintenance?.id,
                            acquisitionDate = parseStart(acquisitionDate),
                            inServiceDate = parseStart(inServiceDate),
                            usefulLifeMonths = life.toInt(),
                            residualValueBase = residual.toDoubleOrNull() ?: 0.0,
                            acquisitionMode = mode,
                            treasuryAccountId = if (mode == "TREASURY") tr?.id else null,
                            acquisitionCostOriginal = cost.toDouble(),
                            currencyCode = if (mode == "TREASURY") tr?.currencyCode ?: "YER_NEW" else "YER_NEW",
                            exchangeRate = if (mode == "TREASURY") rate.toDoubleOrNull() ?: 1.0 else 1.0,
                            notes = notes,
                            createdBy = 0
                        )
                    )
                }
            ) { Text("تسجيل وترحيل") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun DisposeFixedAssetDialog(
    asset: FixedAssetRegisterRow,
    treasury: List<TreasuryBalanceRow>,
    onDismiss: () -> Unit,
    onSave: (FixedAssetService.DisposalRequest) -> Unit
) {
    var date by remember { mutableStateOf(todayText()) }
    var proceeds by remember { mutableStateOf("0") }
    var selectedTreasury by remember { mutableStateOf<TreasuryBalanceRow?>(null) }
    var rate by remember { mutableStateOf("1") }
    var reason by remember { mutableStateOf("") }
    val hasProceeds = (proceeds.toDoubleOrNull() ?: 0.0) > FixedAssetMath.TOLERANCE
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("بيع / استبعاد ${asset.assetNo}") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("صافي القيمة الحالي: ${money(asset.netBookValueBase)} YER_NEW")
                OutlinedTextField(date, { date = it }, label = { Text("تاريخ الاستبعاد") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(proceeds, { proceeds = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("المتحصلات بالعملة الأصلية (0 للشطب)") }, modifier = Modifier.fillMaxWidth())
                if (hasProceeds) {
                    SelectionField("الخزينة المستلمة", selectedTreasury?.let { "${it.nameAr} (${it.currencyCode})" } ?: "اختر", treasury, { "${it.nameAr} (${it.currencyCode})" }) { selectedTreasury = it; if (it.currencyCode == "YER_NEW") rate = "1" }
                    if (selectedTreasury?.currencyCode != "YER_NEW") OutlinedTextField(rate, { rate = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("سعر الصرف") }, modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(reason, { reason = it }, label = { Text("سبب البيع / الاستبعاد") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(enabled = reason.isNotBlank() && (!hasProceeds || selectedTreasury != null), onClick = {
                val tr = selectedTreasury
                onSave(FixedAssetService.DisposalRequest(
                    assetId = asset.id,
                    disposalDate = parseEnd(date),
                    proceedsOriginal = proceeds.toDoubleOrNull() ?: 0.0,
                    treasuryAccountId = if (hasProceeds) tr?.id else null,
                    currencyCode = if (hasProceeds) tr?.currencyCode ?: "YER_NEW" else "YER_NEW",
                    exchangeRate = if (hasProceeds) rate.toDoubleOrNull() ?: 1.0 else 1.0,
                    reason = reason,
                    createdBy = 0
                ))
            }) { Text("ترحيل الاستبعاد") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

private fun fixedAssetStatusAr(status: String) = when (status) {
    "ACTIVE" -> "نشط"
    "FULLY_DEPRECIATED" -> "مستهلك بالكامل"
    "DISPOSED" -> "مستبعد"
    "CANCELLED" -> "ملغى"
    else -> status
}

private fun fixedAssetCategoryAr(category: String) = when (category) {
    "BUILDING" -> "مباني"
    "MACHINERY" -> "آلات"
    "VEHICLE" -> "مركبات"
    "EQUIPMENT" -> "معدات"
    "FURNITURE" -> "أثاث"
    "IT" -> "تقنية معلومات"
    else -> "أخرى"
}

@Composable
private fun AccountingPeriodsTab(container: AppContainer, user: UserEntity, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val periods by container.db.accountingDao().observePeriods().collectAsState(initial = emptyList())
    val fiscalClosings by container.db.accountingDao().observeFiscalYearClosings().collectAsState(initial = emptyList())
    val fxRevaluations by container.accountingService.observeFxRevaluations().collectAsState(initial = emptyList())
    val fiscalYears = periods.map { it.fiscalYear }.distinct().sortedDescending()
    var fiscalYear by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR).toString()) }
    var asOfText by remember { mutableStateOf(todayText()) }
    var report by remember { mutableStateOf<AccountingReconciliationReport?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var selectedPeriod by remember { mutableStateOf<AccountingPeriodEntity?>(null) }
    var action by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var selectedFiscalYear by remember { mutableStateOf<Int?>(null) }
    var fiscalYearAction by remember { mutableStateOf("") }
    var fiscalYearReason by remember { mutableStateOf("") }

    LazyColumn(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("الفترات المحاسبية والمطابقة", style = MaterialTheme.typography.headlineSmall)
            Text(
                "إنشاء سنة مالية يولّد 12 فترة شهرية. إقفال الفترة يمنع أي قيد جديد بتاريخ يقع داخلها على مستوى قاعدة البيانات.",
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = fiscalYear,
                    onValueChange = { fiscalYear = it.filter(Char::isDigit).take(4) },
                    label = { Text("السنة المالية") },
                    modifier = Modifier.width(170.dp)
                )
                Button(
                    enabled = fiscalYear.toIntOrNull() != null,
                    onClick = {
                        scope.launch {
                            try {
                                container.accountingService.createCalendarFiscalYear(fiscalYear.toInt(), user.id)
                                message = "تم إنشاء 12 فترة للسنة $fiscalYear"
                            } catch (e: Exception) {
                                message = e.message ?: "تعذر إنشاء السنة المالية"
                            }
                        }
                    }
                ) { Text("إنشاء السنة") }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }

        item {
            HorizontalDivider()
            Text("مطابقة الأستاذ مع السجلات المساندة", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = asOfText,
                    onValueChange = { asOfText = it },
                    label = { Text("حتى تاريخ YYYY-MM-DD") },
                    modifier = Modifier.width(210.dp)
                )
                Button(onClick = {
                    scope.launch {
                        try {
                            report = container.accountingService.reconciliation(parseEnd(asOfText))
                            message = if (report?.isMatched == true) "المطابقة سليمة" else "توجد فروقات تحتاج معالجة"
                        } catch (e: Exception) {
                            message = e.message ?: "تعذر تنفيذ المطابقة"
                        }
                    }
                }) { Text("تشغيل المطابقة") }
            }
        }

        report?.let { r ->
            item {
                Text(
                    if (r.isMatched) "✓ جميع حسابات الرقابة متطابقة" else "⚠ توجد فروقات بين الأستاذ والسجلات المساندة",
                    color = if (r.isMatched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Text("فرق ميزان المراجعة: ${money(r.trialBalanceDifferenceBase)}", style = MaterialTheme.typography.bodySmall)
            }
            items(r.rows, key = { it.code }) { row ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("${row.code} — ${row.labelAr}", style = MaterialTheme.typography.titleSmall)
                        Text("الأستاذ: ${money(row.glBalanceBase)}")
                        Text("السجل المساند: ${money(row.subledgerBalanceBase)}")
                        Text(
                            "الفرق: ${money(row.differenceBase)} ${if (row.isMatched) "✓" else "⚠"}",
                            color = if (row.isMatched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        item {
            HorizontalDivider()
            Text("إعادة تقييم العملات الأجنبية", style = MaterialTheme.typography.titleMedium)
            Text(
                "في نهاية كل فترة، يعاد تقييم رصيد كل خزينة/بنك أجنبي بسعر الصرف المعتمد دون تغيير كمية العملة الأصلية. فرق التقييم يرحّل إلى أرباح/خسائر فروق العملة.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (fxRevaluations.isEmpty()) {
            item { Text("لا توجد عمليات إعادة تقييم عملات مسجلة بعد.", style = MaterialTheme.typography.bodySmall) }
        } else {
            items(fxRevaluations.take(12), key = { "fx-${it.id}" }) { row ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("${row.treasuryName} • ${formatDate(row.valuationDate)}", style = MaterialTheme.typography.titleSmall)
                        Text("${money(row.originalBalance)} ${row.currencyCode} × ${money(row.rateToBase)} = ${money(row.targetBalanceBase)} YER_NEW")
                        Text("فرق التقييم: ${money(row.differenceBase)} YER_NEW • ${if (row.status == "POSTED") "مرحّل" else "معكوس"}")
                    }
                }
            }
        }

        item {
            HorizontalDivider()
            Text("الإقفال السنوي", style = MaterialTheme.typography.titleMedium)
            Text(
                "بعد إقفال الفترات 1–11، ينشئ النظام قيد إقفال يصفر الإيرادات والمصروفات ويرحل صافي الربح أو الخسارة إلى الحساب 3300 ثم يقفل الفترة 12.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (fiscalYears.isEmpty()) {
            item { Text("أنشئ سنة مالية أولاً لإدارة الإقفال السنوي.") }
        } else {
            items(fiscalYears, key = { "fy-$it" }) { year ->
                val yearPeriods = periods.filter { it.fiscalYear == year }
                val latest = fiscalClosings.firstOrNull { it.fiscalYear == year }
                val activeClose = fiscalClosings.firstOrNull { it.fiscalYear == year && it.status == "CLOSED" }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("السنة المالية $year", style = MaterialTheme.typography.titleSmall)
                        Text("الفترات المقفلة: ${yearPeriods.count { it.status == "CLOSED" }} / ${yearPeriods.size}")
                        latest?.let { row ->
                            Text(
                                if (row.status == "CLOSED")
                                    "مقفلة سنوياً — صافي النتيجة: ${money(row.netIncomeBase)}"
                                else
                                    "أعيد فتحها — آخر نتيجة مقفلة: ${money(row.netIncomeBase)}",
                                color = if (row.status == "CLOSED") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (activeClose == null) {
                                Button(onClick = {
                                    selectedFiscalYear = year
                                    fiscalYearAction = "CLOSE"
                                    fiscalYearReason = ""
                                }) { Text("إقفال السنة") }
                            } else {
                                OutlinedButton(onClick = {
                                    selectedFiscalYear = year
                                    fiscalYearAction = "REOPEN"
                                    fiscalYearReason = ""
                                }) { Text("إعادة فتح السنة") }
                            }
                        }
                    }
                }
            }
        }

        item {
            HorizontalDivider()
            Text("الفترات", style = MaterialTheme.typography.titleMedium)
        }

        if (periods.isEmpty()) {
            item { Text("لم يتم إنشاء فترات محاسبية بعد.") }
        } else {
            items(periods, key = { it.id }) { period ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(period.nameAr, style = MaterialTheme.typography.titleSmall)
                            Text("${formatDate(period.startDate)} — ${formatDate(period.endDate)}")
                            Text(if (period.status == "OPEN") "مفتوحة" else "مقفلة")
                            if (period.status == "CLOSED" && period.closeReason.isNotBlank()) {
                                Text("سبب الإقفال: ${period.closeReason}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (period.periodNo == 12) {
                            val activeAnnualClose = fiscalClosings.any { it.fiscalYear == period.fiscalYear && it.status == "CLOSED" }
                            Text(
                                when {
                                    activeAnnualClose -> "تدار من الإقفال السنوي"
                                    period.status == "OPEN" -> "تقفل من إقفال السنة"
                                    else -> "أعد فتحها ثم استخدم الإقفال السنوي"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (period.status == "CLOSED" && !activeAnnualClose) {
                                OutlinedButton(onClick = {
                                    selectedPeriod = period
                                    action = "REOPEN"
                                    reason = ""
                                }) { Text("إعادة فتح الفترة 12") }
                            }
                        } else if (period.status == "OPEN") {
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (period.endDate <= System.currentTimeMillis()) {
                                    OutlinedButton(onClick = {
                                        scope.launch {
                                            try {
                                                val rows = container.accountingService.revalueForeignTreasuries(
                                                    period.endDate, user.id, "إعادة تقييم عملات نهاية ${period.nameAr}"
                                                )
                                                message = if (rows.isEmpty()) "لا توجد خزائن أجنبية نشطة لإعادة التقييم" else "تمت إعادة تقييم ${rows.size} خزينة/بنك أجنبي"
                                            } catch (e: Exception) {
                                                message = e.message ?: "تعذر إعادة تقييم العملات"
                                            }
                                        }
                                    }) { Text("إعادة تقييم العملات") }
                                }
                                OutlinedButton(onClick = {
                                    selectedPeriod = period
                                    action = "CLOSE"
                                    reason = ""
                                }) { Text("إقفال") }
                            }
                        } else {
                            OutlinedButton(onClick = {
                                selectedPeriod = period
                                action = "REOPEN"
                                reason = ""
                            }) { Text("إعادة فتح") }
                        }
                    }
                }
            }
        }
    }

    selectedPeriod?.let { period ->
        AlertDialog(
            onDismissRequest = { selectedPeriod = null },
            title = { Text(if (action == "CLOSE") "إقفال ${period.nameAr}" else "إعادة فتح ${period.nameAr}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (action == "CLOSE") {
                        Text("سيتم تشغيل مطابقة العملاء والموردين والمخزون وميزان المراجعة قبل السماح بالإقفال.")
                    } else {
                        Text("إعادة الفتح تعيد السماح بالترحيل داخل هذه الفترة، وتُسجل في سجل التدقيق.")
                    }
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text(if (action == "CLOSE") "سبب الإقفال" else "سبب إعادة الفتح") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = reason.isNotBlank(),
                    onClick = {
                        scope.launch {
                            try {
                                if (action == "CLOSE") {
                                    container.accountingService.closePeriod(period.id, reason, user.id)
                                    message = "تم إقفال ${period.nameAr}"
                                } else {
                                    container.accountingService.reopenPeriod(period.id, reason, user.id)
                                    message = "تمت إعادة فتح ${period.nameAr}"
                                }
                                selectedPeriod = null
                            } catch (e: Exception) {
                                message = e.message ?: "تعذر تنفيذ العملية"
                                selectedPeriod = null
                            }
                        }
                    }
                ) { Text(if (action == "CLOSE") "إقفال الفترة" else "إعادة فتح") }
            },
            dismissButton = { TextButton(onClick = { selectedPeriod = null }) { Text("إلغاء") } }
        )
    }

    selectedFiscalYear?.let { year ->
        AlertDialog(
            onDismissRequest = { selectedFiscalYear = null },
            title = { Text(if (fiscalYearAction == "CLOSE") "إقفال السنة المالية $year" else "إعادة فتح السنة المالية $year") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (fiscalYearAction == "CLOSE") {
                        Text("سيتم التحقق من المطابقات، ثم تصفير حسابات الإيرادات والمصروفات وترحيل صافي النتيجة إلى الأرباح المحتجزة 3300، ثم إقفال الفترة 12.")
                    } else {
                        Text("سيتم فتح الفترة 12 وعكس قيد الإقفال السنوي بالكامل. يبقى القيد الأصلي محفوظاً في سجل التدقيق.")
                    }
                    OutlinedTextField(
                        value = fiscalYearReason,
                        onValueChange = { fiscalYearReason = it },
                        label = { Text(if (fiscalYearAction == "CLOSE") "سبب الإقفال السنوي" else "سبب إعادة فتح السنة") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = fiscalYearReason.isNotBlank(),
                    onClick = {
                        scope.launch {
                            try {
                                val result = if (fiscalYearAction == "CLOSE") {
                                    container.accountingService.closeFiscalYear(year, fiscalYearReason, user.id)
                                } else {
                                    container.accountingService.reopenFiscalYear(year, fiscalYearReason, user.id)
                                }
                                message = if (result.status == "CLOSED") {
                                    "تم إقفال السنة $year وترحيل صافي النتيجة ${money(result.netIncomeBase)}"
                                } else {
                                    "تمت إعادة فتح السنة $year وعكس قيد الإقفال"
                                }
                                selectedFiscalYear = null
                            } catch (e: Exception) {
                                message = e.message ?: "تعذر تنفيذ الإجراء السنوي"
                                selectedFiscalYear = null
                            }
                        }
                    }
                ) { Text(if (fiscalYearAction == "CLOSE") "تأكيد الإقفال" else "تأكيد إعادة الفتح") }
            },
            dismissButton = { TextButton(onClick = { selectedFiscalYear = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun JournalTab(container: AppContainer, user: UserEntity, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val headers by container.db.journalDao().observeHeaders().collectAsState(initial = emptyList())
    val accounts by container.db.accountDao().observeAll().collectAsState(initial = emptyList())
    val tableScroll = rememberScrollState()
    var selected by remember { mutableStateOf<JournalHeaderRow?>(null) }
    var details by remember { mutableStateOf<List<JournalDetailRow>>(emptyList()) }
    var reverseReason by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var showAddAccount by remember { mutableStateOf(false) }
    var journalSearch by remember { mutableStateOf("") }

    LaunchedEffect(selected?.id) {
        details = selected?.let { container.accountingService.journalDetails(it.id) } ?: emptyList()
        reverseReason = ""
    }

    val filteredHeaders = remember(headers, journalSearch) {
        val q = journalSearch.trim().lowercase(Locale.ROOT)
        if (q.isBlank()) headers else headers.filter { row ->
            listOf(row.entryNo, row.description, row.sourceType, row.currencyCode)
                .any { it.lowercase(Locale.ROOT).contains(q) }
        }
    }
    val activeEntries = remember(headers) { headers.count { !it.isReversed } }
    val reversedEntries = remember(headers) { headers.count { it.isReversed } }
    val debitMovement = remember(headers) { headers.filter { !it.isReversed }.sumOf { it.debitTotal } }

    LazyColumn(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FushSectionHeader(
                    title = stringResource(R.string.journal_title),
                    subtitle = stringResource(R.string.journal_subtitle),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = { showAddAccount = true }, shape = MaterialTheme.shapes.medium) { Text(stringResource(R.string.journal_new_account)) }
            }
        }
        if (message != null) {
            item { FushOperationMessage(message, onConsumed = { message = null }) }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FushMetricCard(stringResource(R.string.journal_active_entries), activeEntries.toString(), Modifier.weight(1f), stringResource(R.string.journal_last_entries, headers.size), FushStatusTone.Info)
                FushMetricCard(stringResource(R.string.journal_total_debit), money(debitMovement), Modifier.weight(1f), stringResource(R.string.common_base_currency), FushStatusTone.Neutral)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FushMetricCard(stringResource(R.string.journal_reversed_entries), reversedEntries.toString(), Modifier.weight(1f), stringResource(R.string.journal_documented_corrections), if (reversedEntries > 0) FushStatusTone.Warning else FushStatusTone.Success)
                FushMetricCard(stringResource(R.string.journal_chart_accounts), accounts.size.toString(), Modifier.weight(1f), stringResource(R.string.journal_defined_account), FushStatusTone.Info)
            }
        }
        item {
            OutlinedTextField(
                value = journalSearch,
                onValueChange = { journalSearch = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.journal_search)) },
                placeholder = { Text(stringResource(R.string.journal_search_hint)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                supportingText = { Text(stringResource(R.string.journal_showing, filteredHeaders.size, headers.size)) },
            )
        }
        item {
            ReportExportActions(
                document = buildJournalSectionExportDocument(
                    filteredHeaders,
                    if (journalSearch.isBlank()) "القيود المعروضة في دفتر اليومية" else "نتيجة البحث: ${journalSearch.trim()}"
                ),
                baseName = "FushERP-Journal",
                printJobName = "Fush ERP - Journal",
                enabled = filteredHeaders.isNotEmpty()
            )
        }
        if (filteredHeaders.isNotEmpty()) {
            item {
                AccountingTableHeader(tableScroll, listOf(
                    stringResource(R.string.journal_col_date) to 105, stringResource(R.string.journal_col_number) to 180, stringResource(R.string.journal_col_description) to 320,
                    stringResource(R.string.journal_col_source) to 155, stringResource(R.string.journal_col_debit) to 125, stringResource(R.string.journal_col_credit) to 125
                ))
            }
        } else {
            item {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
                    Text(
                        if (headers.isEmpty()) stringResource(R.string.journal_empty) else stringResource(R.string.journal_no_match),
                        Modifier.fillMaxWidth().padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(filteredHeaders, key = { it.id }) { h ->
            AccountingTableRow(
                scroll = tableScroll,
                values = listOf(
                    formatDate(h.entryDate) to 105,
                    h.entryNo to 180,
                    h.description to 320,
                    (h.sourceType + if (h.isReversed) " • ${stringResource(R.string.journal_reversed)}" else "") to 155,
                    money(h.debitTotal) to 125,
                    money(h.creditTotal) to 125
                ),
                onClick = { selected = h }
            )
        }
    }

    selected?.let { header ->
        val detailScroll = rememberScrollState()
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("${header.entryNo} — تفاصيل القيد") },
            text = {
                Column(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(header.description)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FushStatusPill(header.sourceType, FushStatusTone.Info)
                        if (header.isReversed) FushStatusPill("معكوس", FushStatusTone.Warning)
                    }
                    Text("التاريخ ${formatDate(header.entryDate)} • العملة ${header.currencyCode} • سعر الصرف ${header.exchangeRate}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (details.isNotEmpty()) {
                        AccountingTableHeader(detailScroll, listOf(
                            "الكود" to 100, "الحساب" to 220, "مدين" to 120, "دائن" to 120, "ملاحظة" to 260
                        ))
                        details.forEach { d ->
                            AccountingTableRow(
                                detailScroll,
                                listOf(
                                    d.accountCode to 100,
                                    d.accountNameAr to 220,
                                    money(d.debit) to 120,
                                    money(d.credit) to 120,
                                    d.memo to 260
                                )
                            )
                        }
                    }
                    val canReverse = header.sourceType in AccountingService.REVERSIBLE_SOURCE_TYPES && !header.isReversed
                    if (canReverse) {
                        HorizontalDivider()
                        OutlinedTextField(reverseReason, { reverseReason = it }, label = { Text("سبب عكس القيد") }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
                    } else if (header.sourceType !in AccountingService.REVERSIBLE_SOURCE_TYPES && header.sourceType != "REVERSAL") {
                        Text("هذا القيد مرتبط بعملية تشغيلية ويُعكس من شاشة العملية الأصلية.", color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            },
            confirmButton = {
                val canReverse = header.sourceType in AccountingService.REVERSIBLE_SOURCE_TYPES && !header.isReversed
                if (canReverse) {
                    Button(enabled = reverseReason.isNotBlank(), onClick = {
                        scope.launch {
                            try {
                                val id = container.accountingService.reverseEntry(header.id, reverseReason, user.id)
                                message = "تم إنشاء قيد عكسي رقم $id"
                                selected = null
                            } catch (e: Exception) { message = e.message ?: "تعذر عكس القيد" }
                        }
                    }) { Text("عكس القيد") }
                } else TextButton(onClick = { selected = null }) { Text("إغلاق") }
            },
            dismissButton = { if (header.sourceType in AccountingService.REVERSIBLE_SOURCE_TYPES && !header.isReversed) TextButton(onClick = { selected = null }) { Text("إلغاء") } }
        )
    }

    if (showAddAccount) {
        AddAccountDialog(accounts, onDismiss = { showAddAccount = false }) { code, nameAr, nameEn, type, parent, posting ->
            scope.launch {
                try {
                    container.accountingService.addAccount(code, nameAr, nameEn, type, parent, posting, user.id)
                    message = "تم إنشاء الحساب"
                    showAddAccount = false
                } catch (e: Exception) { message = e.message ?: "تعذر إنشاء الحساب" }
            }
        }
    }
}

@Composable
private fun TreasuryTab(container: AppContainer, user: UserEntity, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val balances by container.db.accountingDao().observeTreasuryBalances().collectAsState(initial = emptyList())
    val cashCounts by container.db.accountingDao().observeCashCounts().collectAsState(initial = emptyList())
    val bankStatements by container.db.accountingDao().observeBankStatements().collectAsState(initial = emptyList())
    val accounts by container.db.accountDao().observeAll().collectAsState(initial = emptyList())
    val currencies by container.db.currencyDao().observeAll().collectAsState(initial = emptyList())
    val customers by container.db.customerDao().observeAll().collectAsState(initial = emptyList())
    val suppliers by container.db.supplierDao().observeAll().collectAsState(initial = emptyList())
    val employees by container.db.employeeDao().observeActiveEmployees().collectAsState(initial = emptyList())
    val salesReps by container.db.salesRepresentativeDao().observeActive().collectAsState(initial = emptyList())
    var voucherType by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showCashCount by remember { mutableStateOf(false) }
    var showBankStatement by remember { mutableStateOf(false) }
    var resolvingCashCount by remember { mutableStateOf<TreasuryCashCountRow?>(null) }
    var selectedBankStatement by remember { mutableStateOf<BankStatementRow?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var reportFrom by remember { mutableStateOf(monthStartText()) }
    var reportTo by remember { mutableStateOf(todayText()) }
    var periodReport by remember { mutableStateOf<TreasuryPeriodReport?>(null) }
    var reportMessage by remember { mutableStateOf<String?>(null) }

    val totalBalance = remember(balances) { balances.sumOf { it.balanceBase } }
    val cashBalance = remember(balances) { balances.filter { it.kind == "CASH" }.sumOf { it.balanceBase } }
    val bankBalance = remember(balances) { balances.filter { it.kind == "BANK" }.sumOf { it.balanceBase } }

    LazyColumn(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FushSectionHeader(
                    title = stringResource(R.string.treasury_title),
                    subtitle = stringResource(R.string.treasury_subtitle),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = { showAdd = true }, shape = MaterialTheme.shapes.medium) { Text(stringResource(R.string.treasury_add_account)) }
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    "كل خزينة تعرض كمية العملة الأصلية منفصلة عن قيمتها الدفترية بـ YER_NEW. جرد الصندوق والمطابقة البنكية يتمان بالعملة الأصلية، وإعادة تقييم نهاية الفترة تعدّل القيمة الأساسية فقط.",
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (message != null) {
            item { FushOperationMessage(message, onConsumed = { message = null }) }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FushMetricCard(stringResource(R.string.treasury_total_liquidity), money(totalBalance), Modifier.weight(1f), stringResource(R.string.treasury_book_balance), FushStatusTone.Info)
                FushMetricCard(stringResource(R.string.treasury_cashboxes), money(cashBalance), Modifier.weight(1f), stringResource(R.string.treasury_cashbox_count, balances.count { it.kind == "CASH" }), FushStatusTone.Success)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FushMetricCard(stringResource(R.string.treasury_banks), money(bankBalance), Modifier.weight(1f), stringResource(R.string.treasury_bank_count, balances.count { it.kind == "BANK" }), FushStatusTone.Info)
                FushMetricCard(stringResource(R.string.treasury_accounts), balances.size.toString(), Modifier.weight(1f), stringResource(R.string.treasury_cash_and_banks), FushStatusTone.Neutral)
            }
        }
        item {
            FushSectionHeader(stringResource(R.string.treasury_quick_actions), stringResource(R.string.treasury_quick_actions_detail))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { voucherType = "RECEIPT" }, modifier = Modifier.weight(1f), enabled = balances.isNotEmpty()) { Text(stringResource(R.string.treasury_receipt_voucher)) }
                Button(onClick = { voucherType = "PAYMENT" }, modifier = Modifier.weight(1f), enabled = balances.isNotEmpty()) { Text(stringResource(R.string.treasury_payment_voucher)) }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { voucherType = "INCOME" }, modifier = Modifier.weight(1f), enabled = balances.isNotEmpty()) { Text(stringResource(R.string.treasury_income)) }
                OutlinedButton(onClick = { voucherType = "TRANSFER" }, modifier = Modifier.weight(1f), enabled = balances.size > 1) { Text(stringResource(R.string.treasury_transfer)) }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showCashCount = true }, modifier = Modifier.weight(1f), enabled = balances.any { it.kind == "CASH" }) { Text("جرد صندوق") }
                OutlinedButton(onClick = { showBankStatement = true }, modifier = Modifier.weight(1f), enabled = balances.any { it.kind == "BANK" }) { Text("مطابقة بنك") }
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    FushSectionHeader("تقرير الخزينة والطباعة", "حركة الصناديق والبنوك خلال فترة محددة مع فصل التحويلات الداخلية")
                    DateRangeFields(reportFrom, { reportFrom = it }, reportTo, { reportTo = it })
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    val fromMs = parseStart(reportFrom)
                                    val toMs = parseEnd(reportTo)
                                    periodReport = TreasuryReportMath.build(
                                        treasuries = container.db.accountingDao().allTreasury(),
                                        movementsThroughEnd = container.db.reportDao().treasuryMovementsThrough(toMs),
                                        fromDate = fromMs,
                                        toDate = toMs
                                    )
                                    reportMessage = null
                                } catch (e: Exception) {
                                    reportMessage = e.message ?: "تعذر إعداد تقرير الخزينة"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("إعداد تقرير الخزينة") }
                    reportMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    periodReport?.let { r ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FushMetricCard("افتتاحي", money(r.openingBase), Modifier.weight(1f), "بداية الفترة", FushStatusTone.Neutral)
                            FushMetricCard("ختامي", money(r.closingBase), Modifier.weight(1f), "نهاية الفترة", FushStatusTone.Info)
                        }
                        ReportExportActions(
                            document = buildTreasuryPeriodSectionExportDocument(r, reportFrom, reportTo),
                            baseName = "FushERP-Treasury-Period",
                            printJobName = "Fush ERP - Treasury Period"
                        )
                    } ?: ReportExportActions(
                        document = buildTreasuryBalancesExportDocument(balances),
                        baseName = "FushERP-Treasury-Balances",
                        printJobName = "Fush ERP - Treasury Balances",
                        enabled = balances.isNotEmpty()
                    )
                }
            }
        }
        item { FushSectionHeader(stringResource(R.string.treasury_cash_accounts), stringResource(R.string.treasury_account_count, balances.size)) }
        if (balances.isEmpty()) {
            item {
                FushSystemState(
                    title = stringResource(R.string.treasury_empty),
                    detail = stringResource(R.string.treasury_empty_detail),
                )
            }
        }
        items(balances, key = { it.id }) { row ->
            ElevatedCard(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
            ) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(row.nameAr, style = MaterialTheme.typography.titleMedium)
                            Text("${row.code} • ${row.currencyCode}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FushStatusPill(if (row.kind == "BANK") stringResource(R.string.treasury_bank) else stringResource(R.string.treasury_cashbox), if (row.kind == "BANK") FushStatusTone.Info else FushStatusTone.Success)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("الرصيد بالعملة الأصلية", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${money(row.balanceOriginal)} ${row.currencyCode}", style = MaterialTheme.typography.headlineSmall)
                            if (row.currencyCode != "YER_NEW") {
                                Text("القيمة الدفترية: ${money(row.balanceBase)} YER_NEW", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (row.kind == "BANK") {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(row.bankName.ifBlank { stringResource(R.string.treasury_bank_account) }, style = MaterialTheme.typography.labelLarge)
                                if (row.accountNumber.isNotBlank()) Text(row.accountNumber, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        item { HorizontalDivider(); Text("آخر جرد للصناديق", style = MaterialTheme.typography.titleMedium) }
        if (cashCounts.isEmpty()) item { Text("لا توجد عمليات جرد مسجلة.", style = MaterialTheme.typography.bodySmall) }
        items(cashCounts.take(20), key = { "cash-${it.id}" }) { row ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${row.treasuryName} • ${formatDate(row.countDate)}", style = MaterialTheme.typography.titleSmall)
                    Text("دفتري ${money(row.expectedBalanceOriginal)} ${row.currencyCode} • فعلي ${money(row.actualBalanceOriginal)} • الفرق ${money(row.differenceOriginal)}")
                    if (row.currencyCode != "YER_NEW") Text("سعر الجرد ${money(row.rateToBase)} • أثر الفرق ${money(row.differenceBase)} YER_NEW", style = MaterialTheme.typography.bodySmall)
                    Text(
                        when (row.status) { "BALANCED" -> "متطابق"; "RESOLVED" -> "تمت تسوية الفرق بقيد ${row.resolutionEntryId ?: 0}"; else -> "فرق غير مسوّى" },
                        color = if (row.status == "VARIANCE") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    if (row.notes.isNotBlank()) Text(row.notes, style = MaterialTheme.typography.bodySmall)
                    if (row.status == "VARIANCE") {
                        OutlinedButton(onClick = { resolvingCashCount = row }) { Text("تسوية فرق الجرد") }
                    }
                }
            }
        }

        item { HorizontalDivider(); Text("المطابقة البنكية", style = MaterialTheme.typography.titleMedium) }
        if (bankStatements.isEmpty()) item { Text("لا توجد كشوف بنكية مسجلة.", style = MaterialTheme.typography.bodySmall) }
        items(bankStatements.take(20), key = { "bank-${it.id}" }) { row ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${row.treasuryName} • ${formatDate(row.startDate)} — ${formatDate(row.endDate)}", style = MaterialTheme.typography.titleSmall)
                    Text("افتتاحي ${money(row.openingBalanceOriginal)} • ختامي ${money(row.closingBalanceOriginal)} ${row.currencyCode}")
                    Text(if (row.status == "RECONCILED") "مطابق ومعتمد" else "مسودة تحتاج مطابقة")
                    if (row.notes.isNotBlank()) Text(row.notes, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { selectedBankStatement = row }) { Text(if (row.status == "RECONCILED") "عرض المطابقة" else "فتح المطابقة") }
                }
            }
        }
    }

    voucherType?.let { type ->
        VoucherDialog(type, balances, accounts, currencies, customers, suppliers, employees, salesReps, onDismiss = { voucherType = null }) { req ->
            scope.launch {
                try {
                    val id = container.accountingService.postVoucher(req.copy(createdBy = user.id))
                    message = "تم ترحيل السند والقيد رقم $id"
                    voucherType = null
                } catch (e: Exception) { message = e.message ?: "تعذر ترحيل السند" }
            }
        }
    }

    if (showAdd) {
        AddTreasuryDialog(accounts, currencies, onDismiss = { showAdd = false }) { code, name, kind, account, currency, bank, number ->
            scope.launch {
                try {
                    container.accountingService.addTreasuryAccount(code, name, kind, account.id, currency.code, bank, number, user.id)
                    message = "تمت إضافة الخزينة/البنك"
                    showAdd = false
                } catch (e: Exception) { message = e.message ?: "تعذر الحفظ" }
            }
        }
    }

    if (showCashCount) {
        CashCountDialog(
            treasury = balances.filter { it.kind == "CASH" },
            onDismiss = { showCashCount = false }
        ) { treasury, date, actual, notes ->
            scope.launch {
                try {
                    container.accountingService.createCashCount(treasury.id, date, actual, notes, user.id)
                    message = "تم تسجيل جرد الصندوق"
                    showCashCount = false
                } catch (e: Exception) { message = e.message ?: "تعذر تسجيل الجرد" }
            }
        }
    }

    resolvingCashCount?.let { count ->
        CashCountResolveDialog(count, onDismiss = { resolvingCashCount = null }) { reason ->
            scope.launch {
                try {
                    val entryId = container.accountingService.resolveCashCountDifference(count.id, reason, user.id)
                    message = "تمت تسوية فرق الجرد بقيد رقم $entryId"
                    resolvingCashCount = null
                } catch (e: Exception) { message = e.message ?: "تعذر تسوية فرق الجرد" }
            }
        }
    }

    if (showBankStatement) {
        BankStatementCreateDialog(
            treasury = balances.filter { it.kind == "BANK" },
            onDismiss = { showBankStatement = false }
        ) { treasury, from, to, opening, closing, notes ->
            scope.launch {
                try {
                    container.accountingService.createBankStatement(treasury.id, from, to, opening, closing, notes, user.id)
                    message = "تم إنشاء كشف البنك؛ أضف الحركات ثم طابقها بالقيود"
                    showBankStatement = false
                } catch (e: Exception) { message = e.message ?: "تعذر إنشاء كشف البنك" }
            }
        }
    }

    selectedBankStatement?.let { statement ->
        BankReconciliationDialog(
            container = container,
            user = user,
            statement = statement,
            onDismiss = { selectedBankStatement = null },
            onMessage = { message = it }
        )
    }
}

@Composable
private fun CashCountDialog(
    treasury: List<TreasuryBalanceRow>,
    onDismiss: () -> Unit,
    onSave: (TreasuryBalanceRow, Long, Double, String) -> Unit
) {
    var selected by remember { mutableStateOf<TreasuryBalanceRow?>(null) }
    var date by remember { mutableStateOf(todayText()) }
    var actual by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    LaunchedEffect(treasury) { if (selected == null) selected = treasury.firstOrNull() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("جرد صندوق") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectionField("الصندوق", selected?.nameAr ?: "اختر", treasury, { it.nameAr }) { selected = it }
                selected?.let {
                    Text("الرصيد الدفتري الحالي: ${money(it.balanceOriginal)} ${it.currencyCode}")
                    if (it.currencyCode != "YER_NEW") Text("القيمة الحالية: ${money(it.balanceBase)} YER_NEW", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(date, { date = it }, label = { Text("تاريخ الجرد YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(actual, { actual = it }, label = { Text("الموجود الفعلي (${selected?.currencyCode ?: "العملة الأصلية"})") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات الجرد") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                enabled = selected != null && actual.toDoubleOrNull()?.let { it >= 0 } == true,
                onClick = { onSave(selected!!, parseEnd(date), actual.toDouble(), notes) }
            ) { Text("تسجيل الجرد") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun CashCountResolveDialog(
    count: TreasuryCashCountRow,
    onDismiss: () -> Unit,
    onResolve: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تسوية فرق جرد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(count.treasuryName)
                Text("الفرق: ${money(count.differenceOriginal)} ${count.currencyCode}")
                Text("أثر التسوية: ${money(count.differenceBase)} YER_NEW بسعر ${money(count.rateToBase)}", style = MaterialTheme.typography.bodySmall)
                Text("سيُنشأ قيد مخصص على الحساب 6950 — فروقات الصندوق، ولن يتم حذف سجل الجرد.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(reason, { reason = it }, label = { Text("سبب الفرق والتسوية") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(enabled = reason.isNotBlank(), onClick = { onResolve(reason) }) { Text("إنشاء قيد التسوية") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun BankStatementCreateDialog(
    treasury: List<TreasuryBalanceRow>,
    onDismiss: () -> Unit,
    onSave: (TreasuryBalanceRow, Long, Long, Double, Double, String) -> Unit
) {
    var selected by remember { mutableStateOf<TreasuryBalanceRow?>(null) }
    var from by remember { mutableStateOf(monthStartText()) }
    var to by remember { mutableStateOf(todayText()) }
    var opening by remember { mutableStateOf("") }
    var closing by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    LaunchedEffect(treasury) { if (selected == null) selected = treasury.firstOrNull() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("كشف بنكي للمطابقة") },
        text = {
            Column(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectionField("الحساب البنكي", selected?.nameAr ?: "اختر", treasury, { it.nameAr }) { selected = it }
                DateRangeFields(from, { from = it }, to, { to = it })
                OutlinedTextField(opening, { opening = it }, label = { Text("رصيد كشف البنك الافتتاحي (${selected?.currencyCode ?: "العملة الأصلية"})") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(closing, { closing = it }, label = { Text("رصيد كشف البنك الختامي (${selected?.currencyCode ?: "العملة الأصلية"})") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات") }, modifier = Modifier.fillMaxWidth())
                Text("جميع قيم الكشف تدخل بعملة الحساب الأصلية. الإيداع موجب والسحب/الخصم سالب، وأول كشف يجب أن يبدأ من رصيد يطابق دفتر العملة الأصلية.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                enabled = selected != null && opening.toDoubleOrNull() != null && closing.toDoubleOrNull() != null,
                onClick = { onSave(selected!!, parseStart(from), parseEnd(to), opening.toDouble(), closing.toDouble(), notes) }
            ) { Text("إنشاء الكشف") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun BankReconciliationDialog(
    container: AppContainer,
    user: UserEntity,
    statement: BankStatementRow,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var lines by remember { mutableStateOf<List<BankStatementLineEntity>>(emptyList()) }
    var movements by remember { mutableStateOf<List<BankBookMovementRow>>(emptyList()) }
    var result by remember { mutableStateOf<ForeignBankReconciliationComputation?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var lineDate by remember { mutableStateOf(formatDate(statement.startDate)) }
    var reference by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    val selections = remember { mutableStateMapOf<Long, BankBookMovementRow?>() }

    LaunchedEffect(statement.id, refresh) {
        try {
            lines = container.accountingService.bankStatementLines(statement.id)
            movements = container.accountingService.bankBookMovements(statement.id)
            result = container.accountingService.bankReconciliation(statement.id)
        } catch (e: Exception) { onMessage(e.message ?: "تعذر تحميل المطابقة") }
    }

    val matchedIds = lines.mapNotNull { it.matchedJournalEntryId }.toSet()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مطابقة ${statement.treasuryName}") },
        text = {
            LazyColumn(Modifier.heightIn(max = 620.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text("${formatDate(statement.startDate)} — ${formatDate(statement.endDate)}")
                    Text("افتتاحي ${money(statement.openingBalanceOriginal)} • ختامي ${money(statement.closingBalanceOriginal)} ${statement.currencyCode}")
                    result?.let {
                        Text("رصيد دفتر ${it.currencyCode}: ${money(it.bookClosingBalanceOriginal)}")
                        Text("حركات دفترية معلقة: ${money(it.outstandingBookNetOriginal)}")
                        Text("الرصيد البنكي المعدل: ${money(it.adjustedStatementClosingOriginal)}")
                        Text("فرق المطابقة: ${money(it.differenceOriginal)} ${it.currencyCode}", color = if (abs(it.differenceOriginal) <= TreasuryFxMath.ORIGINAL_TOLERANCE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        if (abs(it.arithmeticDifferenceOriginal) > TreasuryFxMath.ORIGINAL_TOLERANCE) {
                            Text("كشف البنك نفسه غير متوازن بمقدار ${money(it.arithmeticDifferenceOriginal)} ${it.currencyCode}", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                if (statement.status == "DRAFT") {
                    item {
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("إضافة حركة من كشف البنك", style = MaterialTheme.typography.titleSmall)
                                OutlinedTextField(lineDate, { lineDate = it }, label = { Text("التاريخ") }, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(reference, { reference = it }, label = { Text("المرجع") }, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(description, { description = it }, label = { Text("البيان") }, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(amount, { amount = it }, label = { Text("المبلغ (${statement.currencyCode}): إيداع + / سحب -") }, modifier = Modifier.fillMaxWidth())
                                Button(
                                    enabled = amount.toDoubleOrNull()?.let { abs(it) > TreasuryFxMath.ORIGINAL_TOLERANCE } == true,
                                    onClick = {
                                        scope.launch {
                                            try {
                                                container.accountingService.addBankStatementLine(statement.id, parseStart(lineDate), reference, description, amount.toDouble(), user.id)
                                                reference = ""; description = ""; amount = ""; refresh++
                                            } catch (e: Exception) { onMessage(e.message ?: "تعذر إضافة حركة البنك") }
                                        }
                                    }
                                ) { Text("إضافة الحركة") }
                            }
                        }
                    }
                }
                items(lines, key = { it.id }) { line ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("${formatDate(line.transactionDate)} • ${line.referenceNo.ifBlank { "بدون مرجع" }} • ${money(line.amountOriginal)} ${statement.currencyCode}")
                            if (line.description.isNotBlank()) Text(line.description, style = MaterialTheme.typography.bodySmall)
                            val linked = line.matchedJournalEntryId?.let { id -> movements.firstOrNull { it.entryId == id } }
                            if (linked != null) {
                                Text("مطابق مع ${linked.entryNo} — ${linked.description}", color = MaterialTheme.colorScheme.primary)
                                if (statement.status == "DRAFT") TextButton(onClick = {
                                    scope.launch {
                                        try { container.accountingService.unmatchBankStatementLine(line.id, user.id); refresh++ }
                                        catch (e: Exception) { onMessage(e.message ?: "تعذر فك المطابقة") }
                                    }
                                }) { Text("فك المطابقة") }
                            } else if (statement.status == "DRAFT") {
                                val candidates = movements.filter {
                                    it.entryId !in matchedIds && it.currencyCode == statement.currencyCode && abs(it.amountOriginal - line.amountOriginal) <= TreasuryFxMath.ORIGINAL_TOLERANCE
                                }
                                SelectionField(
                                    "القيد الدفتري المقابل",
                                    selections[line.id]?.let { "${it.entryNo} • ${formatDate(it.entryDate)} • ${money(it.amountOriginal)} ${it.currencyCode}" } ?: "اختر القيد",
                                    candidates,
                                    { "${it.entryNo} • ${formatDate(it.entryDate)} • ${money(it.amountOriginal)} ${it.currencyCode}" }
                                ) { selections[line.id] = it }
                                OutlinedButton(
                                    enabled = selections[line.id] != null,
                                    onClick = {
                                        val movement = selections[line.id] ?: return@OutlinedButton
                                        scope.launch {
                                            try {
                                                container.accountingService.matchBankStatementLine(line.id, movement.entryId, user.id)
                                                selections.remove(line.id); refresh++
                                            } catch (e: Exception) { onMessage(e.message ?: "تعذر ربط حركة البنك") }
                                        }
                                    }
                                ) { Text("مطابقة") }
                            } else {
                                Text("غير مطابق", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                if (statement.status == "DRAFT") {
                    item {
                        Button(onClick = {
                            scope.launch {
                                try {
                                    val r = container.accountingService.finalizeBankReconciliation(statement.id, user.id)
                                    onMessage("تم اعتماد المطابقة البنكية؛ الفرق ${money(r.differenceOriginal)} ${r.currencyCode}")
                                    onDismiss()
                                } catch (e: Exception) { onMessage(e.message ?: "تعذر اعتماد المطابقة البنكية") }
                            }
                        }) { Text("اعتماد المطابقة البنكية") }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }
    )
}


@Composable
private fun ManualJournalTab(container: AppContainer, user: UserEntity, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val accounts by container.db.accountDao().observeAll().collectAsState(initial = emptyList())
    val currencies by container.db.currencyDao().observeAll().collectAsState(initial = emptyList())
    var show by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var lastPostedEntry by remember { mutableStateOf<JournalEntryEntity?>(null) }
    var lastPostedDetails by remember { mutableStateOf<List<JournalDetailRow>>(emptyList()) }
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        FushSectionHeader(
            title = "القيد اليدوي",
            subtitle = "استخدمه للقيود المحاسبية التي لا تنشأ من عملية تشغيلية داخل النظام",
        )
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("ضوابط قبل الترحيل", style = MaterialTheme.typography.titleSmall)
                Text("يجب أن يتوازن مجموع المدين والدائن. المبالغ تُدخل بعملة القيد ثم تُحوّل إلى العملة الأساسية بسعر الصرف المسجل.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FushMetricCard("حسابات الترحيل", accounts.count { it.isPosting }.toString(), Modifier.weight(1f), "متاحة للقيد", FushStatusTone.Info)
            FushMetricCard("العملات", currencies.size.toString(), Modifier.weight(1f), "عملة معرفة", FushStatusTone.Neutral)
        }
        Button(onClick = { show = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = MaterialTheme.shapes.medium) { Text("إنشاء قيد يدوي") }
        FushOperationMessage(message, onConsumed = { message = null })
        lastPostedEntry?.let { entry ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FushSectionHeader("طباعة آخر قيد يدوي", "${entry.entryNo} — ${formatDate(entry.entryDate)}")
                    ReportExportActions(
                        document = buildManualJournalExportDocument(entry, lastPostedDetails),
                        baseName = "FushERP-Manual-Journal-${entry.entryNo}",
                        printJobName = "Fush ERP - Manual Journal ${entry.entryNo}",
                        enabled = lastPostedDetails.isNotEmpty()
                    )
                }
            }
        }
    }
    if (show) {
        ManualJournalDialog(accounts.filter { it.isPosting }, currencies, onDismiss = { show = false }) { description, date, currency, rate, lines ->
            scope.launch {
                try {
                    val id = container.accountingService.postManualJournal(description, date, currency.code, rate, lines, user.id)
                    lastPostedEntry = container.db.journalDao().byId(id)
                    lastPostedDetails = container.accountingService.journalDetails(id)
                    message = "تم ترحيل القيد رقم $id — أصبح جاهزًا للمعاينة والطباعة"
                    show = false
                } catch (e: Exception) { message = e.message ?: "تعذر ترحيل القيد" }
            }
        }
    }
}

@Composable
private fun LedgerTab(container: AppContainer, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val accounts by container.db.accountDao().observeAll().collectAsState(initial = emptyList())
    val tableScroll = rememberScrollState()
    var account by remember { mutableStateOf<AccountEntity?>(null) }
    var from by remember { mutableStateOf(monthStartText()) }
    var to by remember { mutableStateOf(todayText()) }
    var report by remember { mutableStateOf<LedgerReport?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(accounts) { if (account == null) account = accounts.firstOrNull { it.isPosting } }
    LazyColumn(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FushSectionHeader("دفتر الأستاذ العام", "حركة حساب محدد مع الرصيد الافتتاحي والجاري والختامي")
            Spacer(Modifier.height(6.dp))
            SelectionField("الحساب", account?.let { "${it.code} — ${it.nameAr}" } ?: "اختر", accounts.filter { it.isPosting }, { "${it.code} — ${it.nameAr}" }) { account = it }
            DateRangeFields(from, { from = it }, to, { to = it })
            Button(onClick = {
                val a = account ?: return@Button
                scope.launch {
                    try { report = container.accountingService.ledger(a.id, parseStart(from), parseEnd(to)); message = null }
                    catch (e: Exception) { message = e.message }
                }
            }) { Text("عرض الأستاذ") }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            report?.let { Text("الرصيد الافتتاحي: ${signedBalance(it.openingBalance)}") }
        }
        report?.let {
            item { AccountingTableHeader(tableScroll, listOf(
                "التاريخ" to 105, "رقم القيد" to 175, "البيان" to 300,
                "مدين" to 125, "دائن" to 125, "الرصيد" to 155
            )) }
        }
        report?.lines?.let { rows -> items(rows) { r ->
            AccountingTableRow(tableScroll, listOf(
                formatDate(r.entryDate) to 105,
                r.entryNo to 175,
                r.description to 300,
                money(r.debit) to 125,
                money(r.credit) to 125,
                signedBalance(r.runningBalance) to 155
            ))
        } }
        report?.let { r ->
            item {
                Text("الرصيد الختامي: ${signedBalance(r.closingBalance)}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                val a = account
                if (a != null) {
                    ReportExportActions(
                        document = buildLedgerSectionExportDocument(a.code, a.nameAr, from, to, r),
                        baseName = "FushERP-General-Ledger-${a.code}",
                        printJobName = "Fush ERP - General Ledger ${a.code}"
                    )
                }
            }
        }
    }
}

@Composable
private fun TrialBalanceTab(container: AppContainer, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val tableScroll = rememberScrollState()
    var asOf by remember { mutableStateOf(todayText()) }
    var report by remember { mutableStateOf<TrialBalanceReport?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    LazyColumn(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            FushSectionHeader("ميزان المراجعة", "تحقق من توازن الحركات والأرصدة حتى تاريخ محدد")
            Spacer(Modifier.height(6.dp))
            FushDateField(asOf, { asOf = it }, "حتى تاريخ", modifier = Modifier.fillMaxWidth())
            Button(onClick = { scope.launch { try { report = container.accountingService.trialBalance(parseEnd(asOf)); message = null } catch (e: Exception) { message = e.message } } }) { Text("إعداد الميزان") }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        report?.let {
            item { AccountingTableHeader(tableScroll, listOf(
                "الكود" to 105, "الحساب" to 240, "حركة مدين" to 135,
                "حركة دائن" to 135, "رصيد مدين" to 135, "رصيد دائن" to 135
            )) }
        }
        report?.lines?.let { rows -> items(rows) { r ->
            AccountingTableRow(tableScroll, listOf(
                r.code to 105,
                r.nameAr to 240,
                money(r.debitMovement) to 135,
                money(r.creditMovement) to 135,
                money(r.debitBalance) to 135,
                money(r.creditBalance) to 135
            ))
        } }
        report?.let { r ->
            item {
                Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("إجمالي الرصيد المدين", style = MaterialTheme.typography.titleSmall)
                        Text(money(r.totalDebitBalance), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.width(12.dp))
                        Text("إجمالي الرصيد الدائن", style = MaterialTheme.typography.titleSmall)
                        Text(money(r.totalCreditBalance), style = MaterialTheme.typography.titleSmall)
                    }
                }
                Spacer(Modifier.height(8.dp))
                ReportExportActions(
                    document = buildTrialBalanceSectionExportDocument(asOf, r),
                    baseName = "FushERP-Trial-Balance",
                    printJobName = "Fush ERP - Trial Balance"
                )
            }
        }
    }
}

@Composable
private fun ProfitLossTab(container: AppContainer, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val tableScroll = rememberScrollState()
    var from by remember { mutableStateOf(monthStartText()) }
    var to by remember { mutableStateOf(todayText()) }
    var report by remember { mutableStateOf<ProfitLossReport?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    LazyColumn(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FushSectionHeader("قائمة الدخل", "الإيرادات والمصروفات وصافي نتيجة الفترة")
            Spacer(Modifier.height(6.dp))
            DateRangeFields(from, { from = it }, to, { to = it })
            Button(onClick = { scope.launch { try { report = container.accountingService.profitLoss(parseStart(from), parseEnd(to)); message = null } catch (e: Exception) { message = e.message } } }) { Text("إعداد القائمة") }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        report?.let { r ->
            item {
                AccountingSummaryRow(listOf(
                    "الإيرادات" to r.revenue,
                    "المصروفات" to r.expenses,
                    "صافي الربح/الخسارة" to r.netProfit
                ))
                Spacer(Modifier.height(6.dp))
                Text("تفصيل الإيرادات", style = MaterialTheme.typography.titleMedium)
                AccountingTableHeader(tableScroll, listOf("الحساب" to 320, "المبلغ" to 150))
            }
            items(r.revenueByAccount) { (name, value) -> AccountingTableRow(tableScroll, listOf(name to 320, money(value) to 150)) }
            item {
                Spacer(Modifier.height(6.dp))
                Text("تفصيل المصروفات", style = MaterialTheme.typography.titleMedium)
                AccountingTableHeader(tableScroll, listOf("الحساب" to 320, "المبلغ" to 150))
            }
            items(r.expenseByAccount) { (name, value) -> AccountingTableRow(tableScroll, listOf(name to 320, money(value) to 150)) }
            item {
                ReportExportActions(
                    document = buildProfitLossSectionExportDocument(from, to, r),
                    baseName = "FushERP-Profit-Loss",
                    printJobName = "Fush ERP - Profit and Loss"
                )
            }
        }
    }
}

@Composable
private fun BalanceSheetTab(container: AppContainer, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val tableScroll = rememberScrollState()
    var asOf by remember { mutableStateOf(todayText()) }
    var report by remember { mutableStateOf<BalanceSheetReport?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    LazyColumn(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FushSectionHeader("قائمة المركز المالي", "الأصول والالتزامات وحقوق الملكية حتى تاريخ محدد")
            Spacer(Modifier.height(6.dp))
            FushDateField(asOf, { asOf = it }, "حتى تاريخ", modifier = Modifier.fillMaxWidth())
            Button(onClick = { scope.launch { try { report = container.accountingService.balanceSheet(parseEnd(asOf)); message = null } catch (e: Exception) { message = e.message } } }) { Text("إعداد القائمة") }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        report?.let { r ->
            item {
                AccountingSummaryRow(listOf(
                    "الأصول" to r.assets,
                    "الالتزامات" to r.liabilities,
                    "ربح/خسارة الفترة" to r.currentProfit
                ))
                Spacer(Modifier.height(6.dp))
                Text("الأصول", style = MaterialTheme.typography.titleMedium)
                AccountingTableHeader(tableScroll, listOf("الحساب" to 320, "الرصيد" to 150))
            }
            items(r.assetsByAccount) { (name, value) -> AccountingTableRow(tableScroll, listOf(name to 320, money(value) to 150)) }
            item {
                Spacer(Modifier.height(6.dp))
                Text("الالتزامات", style = MaterialTheme.typography.titleMedium)
                AccountingTableHeader(tableScroll, listOf("الحساب" to 320, "الرصيد" to 150))
            }
            items(r.liabilitiesByAccount) { (name, value) -> AccountingTableRow(tableScroll, listOf(name to 320, money(value) to 150)) }
            item {
                Spacer(Modifier.height(6.dp))
                Text("حقوق الملكية", style = MaterialTheme.typography.titleMedium)
                AccountingTableHeader(tableScroll, listOf("الحساب" to 320, "الرصيد" to 150))
            }
            items(r.equityByAccount) { (name, value) -> AccountingTableRow(tableScroll, listOf(name to 320, money(value) to 150)) }
            item {
                Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("حقوق الملكية قبل ربح الفترة: ${money(r.equityBeforeCurrentProfit)}")
                        Text("ربح/خسارة حتى التاريخ: ${money(r.currentProfit)}")
                        Text("إجمالي الالتزامات وحقوق الملكية: ${money(r.totalLiabilitiesAndEquity)}")
                        Text("فرق التوازن: ${money(r.difference)}", color = if (abs(r.difference) < 0.01) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    }
                }
            }
            item {
                ReportExportActions(
                    document = buildBalanceSheetSectionExportDocument(asOf, r),
                    baseName = "FushERP-Balance-Sheet",
                    printJobName = "Fush ERP - Balance Sheet"
                )
            }
        }
    }
}

@Composable
private fun CashFlowTab(container: AppContainer, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val tableScroll = rememberScrollState()
    var from by remember { mutableStateOf(monthStartText()) }
    var to by remember { mutableStateOf(todayText()) }
    var report by remember { mutableStateOf<CashFlowReport?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FushSectionHeader("التدفق النقدي المباشر", "المتحصلات والمدفوعات وصافي حركة السيولة خلال الفترة")
        DateRangeFields(from, { from = it }, to, { to = it })
        Button(onClick = { scope.launch { try { report = container.accountingService.cashFlow(parseStart(from), parseEnd(to)); message = null } catch (e: Exception) { message = e.message } } }) { Text("إعداد التدفق") }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        report?.let { r ->
            AccountingTableHeader(tableScroll, listOf("البند" to 300, "المبلغ" to 165))
            AccountingTableRow(tableScroll, listOf("رصيد النقد أول الفترة" to 300, money(r.openingCash) to 165))
            AccountingTableRow(tableScroll, listOf("المتحصلات النقدية" to 300, money(r.cashInflows) to 165))
            AccountingTableRow(tableScroll, listOf("المدفوعات النقدية" to 300, money(r.cashOutflows) to 165))
            AccountingTableRow(tableScroll, listOf("صافي الحركة النقدية" to 300, money(r.netCashMovement) to 165))
            AccountingTableRow(tableScroll, listOf("رصيد النقد آخر الفترة" to 300, money(r.closingCash) to 165))
            Text("التحويلات الداخلية بين الصناديق والبنوك لا تُحتسب كمتحصلات أو مدفوعات خارجية.", style = MaterialTheme.typography.bodySmall)
            ReportExportActions(
                document = buildCashFlowSectionExportDocument(from, to, r),
                baseName = "FushERP-Cash-Flow",
                printJobName = "Fush ERP - Cash Flow"
            )
        }
    }
}

@Composable
private fun AccountingTableHeader(scroll: ScrollState, columns: List<Pair<String, Int>>) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.horizontalScroll(scroll).padding(horizontal = 8.dp, vertical = 10.dp)) {
            columns.forEach { (title, width) ->
                Text(
                    title,
                    modifier = Modifier.width(width.dp).padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AccountingTableRow(
    scroll: ScrollState,
    values: List<Pair<String, Int>>,
    onClick: (() -> Unit)? = null
) {
    val clickModifier = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
    Surface(
        modifier = Modifier.fillMaxWidth().then(clickModifier),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(Modifier.horizontalScroll(scroll).padding(horizontal = 8.dp, vertical = 10.dp)) {
            values.forEach { (value, width) ->
                Text(
                    value.ifBlank { "—" },
                    modifier = Modifier.width(width.dp).padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3
                )
            }
        }
    }
}

@Composable
private fun AccountingSummaryRow(values: List<Pair<String, Double>>) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        values.forEach { (label, value) ->
            FushMetricCard(
                label = label,
                value = money(value),
                modifier = Modifier.width(190.dp),
                helper = "بالعملة الأساسية",
                tone = FushStatusTone.Info,
            )
        }
    }
}

private data class UiManualLine(val key: String = UUID.randomUUID().toString(), val account: AccountEntity? = null, val debit: String = "", val credit: String = "", val memo: String = "")

@Composable
private fun ManualJournalDialog(
    accounts: List<AccountEntity>, currencies: List<CurrencyEntity>, onDismiss: () -> Unit,
    onSave: (String, Long, CurrencyEntity, Double, List<AccountingService.ManualLine>) -> Unit
) {
    var description by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(todayText()) }
    var currency by remember { mutableStateOf<CurrencyEntity?>(null) }
    var rate by remember { mutableStateOf("1") }
    val lines = remember { mutableStateListOf(UiManualLine(), UiManualLine()) }
    LaunchedEffect(currencies) { if (currency == null) currency = currencies.firstOrNull { it.isBase } ?: currencies.firstOrNull() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("قيد يدوي جديد") },
        text = {
            LazyColumn(Modifier.heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(description, { description = it }, label = { Text("البيان") }, modifier = Modifier.fillMaxWidth())
                    FushDateField(date, { date = it }, "التاريخ", modifier = Modifier.fillMaxWidth())
                    SelectionField("العملة", currency?.nameAr ?: "اختر", currencies, { it.nameAr }) { currency = it; if (it.isBase) rate = "1" }
                    OutlinedTextField(rate, { rate = it }, label = { Text("سعر الصرف إلى الريال الجديد") }, modifier = Modifier.fillMaxWidth())
                }
                items(lines, key = { it.key }) { line ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SelectionField("الحساب", line.account?.let { "${it.code} — ${it.nameAr}" } ?: "اختر", accounts, { "${it.code} — ${it.nameAr}" }) { a ->
                                val i = lines.indexOfFirst { it.key == line.key }; if (i >= 0) lines[i] = line.copy(account = a)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(line.debit, { v -> val i=lines.indexOfFirst{it.key==line.key}; if(i>=0) lines[i]=line.copy(debit=v) }, label={Text("مدين")}, modifier=Modifier.weight(1f))
                                OutlinedTextField(line.credit, { v -> val i=lines.indexOfFirst{it.key==line.key}; if(i>=0) lines[i]=line.copy(credit=v) }, label={Text("دائن")}, modifier=Modifier.weight(1f))
                            }
                            OutlinedTextField(line.memo, { v -> val i=lines.indexOfFirst{it.key==line.key}; if(i>=0) lines[i]=line.copy(memo=v) }, label={Text("ملاحظة السطر")}, modifier=Modifier.fillMaxWidth())
                            if (lines.size > 2) TextButton(onClick = { lines.removeAll { it.key == line.key } }) { Text("حذف السطر") }
                        }
                    }
                }
                item { OutlinedButton(onClick = { lines.add(UiManualLine()) }) { Text("إضافة سطر") } }
            }
        },
        confirmButton = {
            Button(onClick = {
                val c = currency ?: return@Button
                val parsed = lines.map { AccountingService.ManualLine(it.account?.id ?: 0, it.debit.toDoubleOrNull() ?: 0.0, it.credit.toDoubleOrNull() ?: 0.0, it.memo) }
                onSave(description, parseStart(date), c, rate.toDoubleOrNull() ?: 0.0, parsed)
            }, enabled = description.isNotBlank() && currency != null && rate.toDoubleOrNull() != null && lines.all { it.account != null }) { Text("ترحيل") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun VoucherDialog(
    type: String,
    treasury: List<TreasuryBalanceRow>,
    accounts: List<AccountEntity>,
    currencies: List<CurrencyEntity>,
    customers: List<CustomerEntity>,
    suppliers: List<SupplierEntity>,
    employees: List<EmployeeEntity>,
    salesReps: List<SalesRepresentativeEntity>,
    onDismiss: () -> Unit,
    onSave: (AccountingService.VoucherRequest) -> Unit
) {
    var source by remember { mutableStateOf<TreasuryBalanceRow?>(null) }
    var target by remember { mutableStateOf<TreasuryBalanceRow?>(null) }
    var offset by remember { mutableStateOf<AccountEntity?>(null) }
    var customer by remember { mutableStateOf<CustomerEntity?>(null) }
    var supplier by remember { mutableStateOf<SupplierEntity?>(null) }
    var employee by remember { mutableStateOf<EmployeeEntity?>(null) }
    var salesRep by remember { mutableStateOf<SalesRepresentativeEntity?>(null) }
    var currency by remember { mutableStateOf<CurrencyEntity?>(null) }
    var amount by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("1") }
    var description by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(todayText()) }
    LaunchedEffect(treasury) { if (source == null) source = treasury.firstOrNull() }
    LaunchedEffect(source?.currencyCode, currencies) {
        source?.let { s -> currency = currencies.firstOrNull { it.code == s.currencyCode } ?: currencies.firstOrNull(); if (s.currencyCode == "YER_NEW") rate = "1" }
    }
    val allowedAccounts = when (type) {
        "EXPENSE" -> accounts.filter { it.isPosting && it.type == "EXPENSE" }
        "INCOME" -> accounts.filter { it.isPosting && it.type == "REVENUE" }
        else -> accounts.filter { it.isPosting && it.id != source?.accountId }
    }
    LaunchedEffect(offset?.id) {
        customer = null; supplier = null; employee = null; salesRep = null
    }
    val partyReady = when (offset?.code) {
        "1300" -> customer != null
        "2100" -> supplier != null
        "2200" -> employee != null
        "2300" -> salesRep != null
        else -> true
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(voucherTitle(type)) },
        text = {
            Column(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectionField("الخزينة", source?.nameAr ?: "اختر", treasury, { it.nameAr }) { source = it }
                if (type == "TRANSFER") {
                    SelectionField("إلى خزينة", target?.nameAr ?: "اختر", treasury.filter { it.id != source?.id }, { it.nameAr }) { target = it }
                } else {
                    SelectionField("الحساب المقابل", offset?.let { "${it.code} — ${it.nameAr}" } ?: "اختر", allowedAccounts, { "${it.code} — ${it.nameAr}" }) { offset = it }
                    when (offset?.code) {
                        "1300" -> SelectionField("العميل — إلزامي", customer?.let { "${it.code} — ${it.nameAr}" } ?: "اختر العميل", customers, { "${it.code} — ${it.nameAr}" }) { customer = it }
                        "2100" -> SelectionField("المورد — إلزامي", supplier?.let { "${it.code} — ${it.nameAr}" } ?: "اختر المورد", suppliers, { "${it.code} — ${it.nameAr}" }) { supplier = it }
                        "2200" -> SelectionField("الموظف — إلزامي", employee?.let { "${it.code} — ${it.fullNameAr}" } ?: "اختر الموظف", employees, { "${it.code} — ${it.fullNameAr}" }) { employee = it }
                        "2300" -> SelectionField("مندوب المبيعات — إلزامي", salesRep?.let { "${it.code} — ${it.fullNameAr}" } ?: "اختر المندوب", salesReps, { "${it.code} — ${it.fullNameAr}" }) { salesRep = it }
                    }
                    if (!partyReady) Text("لا يمكن ترحيل السند قبل تحديد الطرف المرتبط بالحساب.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                SelectionField("العملة", currency?.nameAr ?: "اختر", currencies, { it.nameAr }) { currency = it; if (it.isBase) rate = "1" }
                OutlinedTextField(amount, { amount = it }, label = { Text("المبلغ بالعملة الأصلية") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(rate, { rate = it }, label = { Text("سعر الصرف") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text("البيان") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(reference, { reference = it }, label = { Text("رقم المرجع/المستند") }, modifier = Modifier.fillMaxWidth())
                FushDateField(date, { date = it }, "التاريخ", modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(enabled = source != null && currency != null && amount.toDoubleOrNull()?.let { it > 0 } == true && rate.toDoubleOrNull()?.let { it > 0 } == true && description.isNotBlank() && partyReady && (if (type == "TRANSFER") target != null else offset != null), onClick = {
                onSave(
                    AccountingService.VoucherRequest(
                        type = type,
                        treasuryAccountId = source!!.id,
                        targetTreasuryAccountId = target?.id,
                        offsetAccountId = offset?.id,
                        amountOriginal = amount.toDouble(),
                        currencyCode = currency!!.code,
                        exchangeRate = rate.toDouble(),
                        description = description,
                        referenceNo = reference,
                        voucherDate = parseStart(date),
                        createdBy = 0L,
                        customerId = customer?.id,
                        supplierId = supplier?.id,
                        employeeId = employee?.id,
                        salesRepId = salesRep?.id
                    )
                )
            }) { Text("ترحيل") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun AddTreasuryDialog(accounts: List<AccountEntity>, currencies: List<CurrencyEntity>, onDismiss: () -> Unit, onSave: (String, String, String, AccountEntity, CurrencyEntity, String, String) -> Unit) {
    var code by remember { mutableStateOf("") }; var name by remember { mutableStateOf("") }; var kind by remember { mutableStateOf("CASH") }
    var account by remember { mutableStateOf<AccountEntity?>(null) }; var currency by remember { mutableStateOf<CurrencyEntity?>(null) }; var bank by remember { mutableStateOf("") }; var number by remember { mutableStateOf("") }
    val assetAccounts = accounts.filter { it.isPosting && it.type == "ASSET" }
    LaunchedEffect(assetAccounts) { if (account == null) account = assetAccounts.firstOrNull() }
    LaunchedEffect(currencies) { if (currency == null) currency = currencies.firstOrNull { it.isBase } ?: currencies.firstOrNull() }
    AlertDialog(onDismissRequest=onDismiss, title={Text("إضافة خزينة أو بنك")}, text={Column(verticalArrangement=Arrangement.spacedBy(7.dp)) {
        OutlinedTextField(code,{code=it},label={Text("الكود")},modifier=Modifier.fillMaxWidth()); OutlinedTextField(name,{name=it},label={Text("الاسم")},modifier=Modifier.fillMaxWidth())
        StringSelectionField("النوع", kind, listOf("CASH","BANK")){kind=it}; SelectionField("حساب الأستاذ", account?.let{"${it.code} — ${it.nameAr}"}?:"اختر", assetAccounts,{"${it.code} — ${it.nameAr}"}){account=it}
        SelectionField("العملة",currency?.nameAr?:"اختر",currencies,{it.nameAr}){currency=it}; if(kind=="BANK"){OutlinedTextField(bank,{bank=it},label={Text("اسم البنك")}); OutlinedTextField(number,{number=it},label={Text("رقم الحساب")})}
    }}, confirmButton={Button(enabled=code.isNotBlank()&&name.isNotBlank()&&account!=null&&currency!=null,onClick={onSave(code,name,kind,account!!,currency!!,bank,number)}){Text("حفظ")}}, dismissButton={TextButton(onClick=onDismiss){Text("إلغاء")}})
}

@Composable
private fun AddAccountDialog(accounts: List<AccountEntity>, onDismiss: () -> Unit, onSave: (String,String,String,String,String?,Boolean)->Unit) {
    var code by remember{mutableStateOf("")}; var ar by remember{mutableStateOf("")}; var en by remember{mutableStateOf("")}; var type by remember{mutableStateOf("ASSET")}; var parent by remember{mutableStateOf<AccountEntity?>(null)}; var posting by remember{mutableStateOf(true)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("حساب جديد")},text={Column(verticalArrangement=Arrangement.spacedBy(7.dp)){
        OutlinedTextField(code,{code=it},label={Text("الكود")}); OutlinedTextField(ar,{ar=it},label={Text("الاسم العربي")}); OutlinedTextField(en,{en=it},label={Text("الاسم الإنجليزي")})
        StringSelectionField("النوع",type,listOf("ASSET","LIABILITY","EQUITY","REVENUE","EXPENSE")){type=it}; SelectionField("الحساب الأب",parent?.let{"${it.code} — ${it.nameAr}"}?:"بدون",listOf<AccountEntity?>(null)+accounts,{it?.let{"${it.code} — ${it.nameAr}"}?:"بدون"}){parent=it}
        Row{Checkbox(posting,{posting=it});Text("حساب ترحيل")}
    }},confirmButton={Button(enabled=code.isNotBlank()&&ar.isNotBlank(),onClick={onSave(code,ar,en,type,parent?.code,posting)}){Text("حفظ")}},dismissButton={TextButton(onClick=onDismiss){Text("إلغاء")}})
}

@Composable
private fun DateRangeFields(from: String, onFrom: (String)->Unit, to: String, onTo: (String)->Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FushDateField(from, onFrom, "من تاريخ", modifier=Modifier.weight(1f))
        FushDateField(to, onTo, "إلى تاريخ", modifier=Modifier.weight(1f))
    }
}

private fun voucherTitle(type: String) = when(type){"RECEIPT"->"سند قبض";"PAYMENT"->"سند صرف";"EXPENSE"->"سند مصروف";"INCOME"->"سند إيراد";else->"تحويل بين الخزائن"}
private fun money(v: Double): String = String.format(Locale.US, "%,.2f", v)
private fun signedBalance(v: Double): String = if (v >= 0) "${money(v)} مدين" else "${money(-v)} دائن"
private fun formatDate(ms: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))
private fun todayText(): String = formatDate(System.currentTimeMillis())
private fun monthStartText(): String { val c=Calendar.getInstance(); c.set(Calendar.DAY_OF_MONTH,1); return formatDate(c.timeInMillis) }
private fun parseStart(text: String): Long = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient=false }.parse(text)?.time ?: throw IllegalArgumentException("التاريخ غير صالح")
private fun parseEnd(text: String): Long = parseStart(text) + DAY_MS - 1
