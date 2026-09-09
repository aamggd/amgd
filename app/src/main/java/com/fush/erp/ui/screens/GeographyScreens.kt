package com.fush.erp.ui.screens

import com.fush.erp.domain.BusinessTimeZone
import com.fush.erp.domain.TrustedTimeService
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fush.erp.data.AppContainer
import com.fush.erp.data.entity.*
import com.fush.erp.domain.GeographicQuoteResult
import com.fush.erp.domain.FxQuoteStatus
import com.fush.erp.domain.SalesMath
import com.fush.erp.domain.SecurityPermissions
import com.fush.erp.ui.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.Alignment

private const val GEO_DAY_MS = 24L * 60L * 60L * 1000L

@Composable
fun CurrencyGeographyScreen(container: AppContainer, user: UserEntity, modifier: Modifier = Modifier) {
    val tabs = listOf("أسعار الصرف", "المحافظات والأسعار", "حاسبة التسعير", "الربحية")
    var selected by remember { mutableIntStateOf(0) }
    Column(modifier.fillMaxSize()) {
        FushSectionHeader(
            title = "العملات والجغرافيا",
            subtitle = "أسعار الصرف التاريخية، سياسات المحافظات، قوائم الأسعار والتسعير والربحية الجغرافية.",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
        ScrollableTabRow(selectedTabIndex = selected) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = selected == index, onClick = { selected = index }, text = { Text(title) })
            }
        }
        when (selected) {
            0 -> FxHistoryTab(container, user, Modifier.weight(1f))
            1 -> ProvincePricingTab(container, user, Modifier.weight(1f))
            2 -> GeographicQuoteTab(container, Modifier.weight(1f))
            else -> GeographicProfitabilityTab(container, user, Modifier.weight(1f))
        }
    }
}

@Composable
private fun FxHistoryTab(container: AppContainer, user: UserEntity, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val rolePermissions by container.db.securityDao().observePermissionCodesForRole(user.role).collectAsState(initial = emptyList())
    val permissionSet = remember(rolePermissions) { rolePermissions.toSet() }
    val canRefresh = user.role == "ADMIN" || SecurityPermissions.EXCHANGE_RATE_REFRESH in permissionSet
    val canApprove = user.role == "ADMIN" || SecurityPermissions.EXCHANGE_RATE_APPROVE in permissionSet
    val canManual = user.role == "ADMIN" || SecurityPermissions.GEOGRAPHY_MANAGE in permissionSet
    val canSettings = user.role == "ADMIN" || SecurityPermissions.EXCHANGE_RATE_SETTINGS in permissionSet
    val snapshots by container.db.geographyDao().observeFxSnapshots().collectAsState(initial = emptyList())
    val rates by container.db.currencyDao().observeRateHistory(120).collectAsState(initial = emptyList())
    val marketRates by container.db.geographyDao().observeLatestFxMarketRates().collectAsState(initial = emptyList())
    val observedSettings by container.db.geographyDao().observeFxRateSettings().collectAsState(initial = null)
    val settings = observedSettings ?: FxRateSettingsEntity()
    var selectedRateType by remember { mutableStateOf("SELL") }
    var showManual by remember { mutableStateOf(false) }
    var showApprove by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { container.fxRateService.ensureSettings() }
    LaunchedEffect(observedSettings?.defaultRateType) {
        observedSettings?.defaultRateType?.let { selectedRateType = it }
    }

    val selectedRows = marketRates.filter { it.rateType == selectedRateType }
    val newestFetchedAt = marketRates.maxOfOrNull { it.fetchedAt }

    LazyColumn(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FushSectionHeader(
                "محرك أسعار الصرف المحلي",
                "YECES مصدر أساسي لـ USD/SAR في عدن وصنعاء. تتم المقارنة عبر YETI للدولار وفحص ربط SAR/USD، ولا يصبح السعر محاسبياً إلا بعد الاعتماد."
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !refreshing && canRefresh,
                    onClick = {
                        scope.launch {
                            refreshing = true
                            try {
                                val result = container.fxRateService.refreshFromInternet(user.id)
                                message = "تم تحديث ${result.rowCount} أسعار • ${result.fallbackMode}"
                            } catch (e: Exception) {
                                message = e.message ?: "تعذر تحديث أسعار الصرف"
                            } finally { refreshing = false }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(if (refreshing) "جارٍ التحديث..." else "تحديث من الإنترنت") }
                OutlinedButton(onClick = { showApprove = true }, enabled = canApprove && marketRates.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("اعتماد السعر") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showManual = true }, enabled = canManual, modifier = Modifier.weight(1f)) { Text("إدخال يدوي") }
                OutlinedButton(onClick = { showSettings = true }, enabled = canSettings, modifier = Modifier.weight(1f)) { Text("إعدادات المحرك") }
            }
            FushOperationMessage(message, onConsumed = { message = null })
            newestFetchedAt?.let { Text("آخر جلب: ${geoDateTime(it)}", style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("عرض:")
                FilterChip(selected = selectedRateType == "BUY", onClick = { selectedRateType = "BUY" }, label = { Text("شراء") })
                FilterChip(selected = selectedRateType == "SELL", onClick = { selectedRateType = "SELL" }, label = { Text("بيع") })
            }
        }

        if (marketRates.isEmpty()) {
            item { FushEmptyState("لا توجد أسعار إنترنت مخزنة", "اضغط تحديث من الإنترنت. إذا كانت الخدمة غير متاحة يبقى السجل التاريخي والعمل اليدوي متاحين.") }
        } else {
            listOf("ADEN" to "عدن — الريال الجديد", "SANAA" to "صنعاء — الريال القديم").forEach { (region, label) ->
                item { Text(label, style = MaterialTheme.typography.titleMedium) }
                items(selectedRows.filter { it.marketRegion == region }, key = { "market-${it.id}" }) { row ->
                    val status = container.fxRateService.localStatus(row, settings)
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("${row.currencyCode} • ${if (row.rateType == "BUY") "شراء" else "بيع"}: ${geoMoney(row.rateYer)}", style = MaterialTheme.typography.titleMedium)
                            Text("المصدر: ${row.primarySource} • نشر ${geoDateTime(row.sourcePublishedAt)}", style = MaterialTheme.typography.bodySmall)
                            row.comparisonRateYer?.let { comparison ->
                                Text("المقارنة: ${row.comparisonSource} = ${geoMoney(comparison)} • فرق ${row.variancePercent?.let { geoPct(it) } ?: "—"}", style = MaterialTheme.typography.bodySmall)
                            }
                            Text("الحالة: ${fxStatusLabel(status)}", color = fxStatusColor(status), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        item { HorizontalDivider(); Text("الأسعار المحاسبية المعتمدة", style = MaterialTheme.typography.titleMedium) }
        if (snapshots.isEmpty()) {
            item { FushInlineState("لا توجد لقطات صرف معتمدة حتى الآن. السعر الموجود على الإنترنت لا يستخدم محاسبياً قبل الاعتماد.") }
        } else {
            items(snapshots.take(40), key = { "snapshot-${it.id}" }) { row ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("${geoDate(row.effectiveAt)} • ${if (row.approvedRateType == "BUY") "شراء" else "بيع"}", style = MaterialTheme.typography.titleSmall)
                        Text("USD عدن: ${geoMoney(row.usdNewYer)} • صنعاء: ${geoMoney(row.usdOldYer)}")
                        if (row.sarNewYer != null && row.sarOldYer != null) Text("SAR عدن: ${geoMoney(row.sarNewYer)} • صنعاء: ${geoMoney(row.sarOldYer)}")
                        Text("1 ريال قديم = ${geoRate(row.oldYerToNewYer)} ريال جديد", style = MaterialTheme.typography.bodySmall)
                        if (row.primarySource.isNotBlank()) Text("المصدر: ${row.primarySource}${row.maxVariancePercent?.let { " • أعلى فرق ${geoPct(it)}" } ?: ""}", style = MaterialTheme.typography.bodySmall)
                        if (row.approvalOverrideReason.isNotBlank()) Text("اعتماد استثنائي: ${row.approvalOverrideReason}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        if (row.sourceNote.isNotBlank()) Text(row.sourceNote, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        item { Text("سجل أسعار التحويل إلى العملة الأساسية", style = MaterialTheme.typography.titleMedium) }
        if (rates.isEmpty()) item { FushInlineState("لا توجد أسعار تحويل تاريخية للعملات حتى الآن.") }
        items(rates) { row ->
            ListItem(
                headlineContent = { Text(row.currencyCode) },
                supportingContent = { Text("${geoDate(row.effectiveAt)} • 1 ${row.currencyCode} = ${geoRate(row.rateToBase)} YER_NEW${if (row.sourceNote.isBlank()) "" else " • ${row.sourceNote}"}") }
            )
            HorizontalDivider()
        }
    }

    if (showApprove) {
        ApproveFxRateDialog(
            initialRateType = settings.defaultRateType,
            onDismiss = { showApprove = false }
        ) { date, type, overrideReason ->
            scope.launch {
                try {
                    val result = container.fxRateService.approveLatest(geoParseStart(date), type, user.id, overrideReason)
                    message = "تم اعتماد ${result.rateType} للتاريخ $date${if (result.usedOverride) " باعتماد استثنائي" else ""}"
                    showApprove = false
                } catch (e: Exception) { message = e.message ?: "تعذر اعتماد سعر الصرف" }
            }
        }
    }

    if (showSettings) {
        FxRateSettingsDialog(settings, onDismiss = { showSettings = false }) { region, type, staleHours, warning, high ->
            scope.launch {
                try {
                    container.fxRateService.saveSettings(region, type, staleHours, warning, high, user.id)
                    message = "تم حفظ إعدادات محرك أسعار الصرف"
                    showSettings = false
                } catch (e: Exception) { message = e.message ?: "تعذر حفظ الإعدادات" }
            }
        }
    }

    if (showManual) {
        AddFxSnapshotDialog(onDismiss = { showManual = false }) { date, newUsd, oldUsd, newSar, oldSar, rateType, source ->
            scope.launch {
                try {
                    container.geographyService.recordFxSnapshot(
                        effectiveAt = geoParseStart(date),
                        usdNewYer = newUsd,
                        usdOldYer = oldUsd,
                        sourceNote = source,
                        createdBy = user.id,
                        sarNewYer = newSar,
                        sarOldYer = oldSar,
                        approvedRateType = rateType
                    )
                    message = "تم تسجيل سعر الصرف اليدوي وتثبيته تاريخياً"
                    showManual = false
                } catch (e: Exception) { message = e.message ?: "تعذر تسجيل سعر الصرف" }
            }
        }
    }
}

@Composable
private fun ApproveFxRateDialog(
    initialRateType: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var date by remember { mutableStateOf(geoTodayText()) }
    var type by remember { mutableStateOf(initialRateType.takeIf { it in setOf("BUY", "SELL") } ?: "SELL") }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اعتماد سعر الصرف المحاسبي") },
        text = {
            com.fush.erp.ui.FushDialogForm {
                Text("بعد الاعتماد تُنسخ الأسعار إلى السجل التاريخي، وكل معاملة تحفظ سعرها الخاص ولا تتغير مع تحديثات الإنترنت اللاحقة.", style = MaterialTheme.typography.bodySmall)
                FushDateField(date, { date = it }, "تاريخ السريان", modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == "BUY", onClick = { type = "BUY" }, label = { Text("شراء") })
                    FilterChip(selected = type == "SELL", onClick = { type = "SELL" }, label = { Text("بيع") })
                }
                OutlinedTextField(reason, { reason = it }, label = { Text("سبب اعتماد استثنائي — اتركه فارغاً إذا لا يوجد تعارض") }, modifier = Modifier.fillMaxWidth())
                Text("إذا كان السعر قديماً أو الفرق بين المصادر مرتفعاً، سيطلب النظام صلاحية EXCHANGE_RATE_OVERRIDE وسبباً واضحاً.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                try { geoParseStart(date); onSave(date, type, reason) }
                catch (e: Exception) { error = e.message }
            }) { Text("اعتماد") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun FxRateSettingsDialog(
    initial: FxRateSettingsEntity,
    onDismiss: () -> Unit,
    onSave: (String, String, Int, Double, Double) -> Unit
) {
    var region by remember { mutableStateOf(initial.defaultMarketRegion) }
    var type by remember { mutableStateOf(initial.defaultRateType) }
    var stale by remember { mutableStateOf(initial.staleAfterHours.toString()) }
    var warning by remember { mutableStateOf(initial.warningVariancePct.toString()) }
    var high by remember { mutableStateOf(initial.highVariancePct.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إعدادات محرك أسعار الصرف") },
        text = {
            com.fush.erp.ui.FushDialogForm {
                Text("المنطقة الافتراضية")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = region == "ADEN", onClick = { region = "ADEN" }, label = { Text("عدن") })
                    FilterChip(selected = region == "SANAA", onClick = { region = "SANAA" }, label = { Text("صنعاء") })
                }
                Text("نوع السعر الافتراضي")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == "BUY", onClick = { type = "BUY" }, label = { Text("شراء") })
                    FilterChip(selected = type == "SELL", onClick = { type = "SELL" }, label = { Text("بيع") })
                }
                OutlinedTextField(stale, { stale = it.filter(Char::isDigit) }, label = { Text("اعتبار السعر قديماً بعد — ساعات") }, singleLine = true)
                OutlinedTextField(warning, { warning = it }, label = { Text("حد التحذير %") }, singleLine = true)
                OutlinedTextField(high, { high = it }, label = { Text("حد التعارض العالي %") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                try {
                    onSave(
                        region, type,
                        requireNotNull(stale.toIntOrNull()) { "عدد الساعات غير صالح" },
                        requireNotNull(warning.toDoubleOrNull()) { "حد التحذير غير صالح" },
                        requireNotNull(high.toDoubleOrNull()) { "حد التعارض غير صالح" }
                    )
                } catch (e: Exception) { error = e.message }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun AddFxSnapshotDialog(
    onDismiss: () -> Unit,
    onSave: (String, Double, Double, Double?, Double?, String, String) -> Unit
) {
    var date by remember { mutableStateOf(geoTodayText()) }
    var usdNew by remember { mutableStateOf("") }
    var usdOld by remember { mutableStateOf("") }
    var sarNew by remember { mutableStateOf("") }
    var sarOld by remember { mutableStateOf("") }
    var rateType by remember { mutableStateOf("SELL") }
    var source by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("سعر صرف يدوي") },
        text = {
            com.fush.erp.ui.FushDialogForm {
                FushDateField(date, { date = it }, "التاريخ", modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = rateType == "BUY", onClick = { rateType = "BUY" }, label = { Text("شراء") })
                    FilterChip(selected = rateType == "SELL", onClick = { rateType = "SELL" }, label = { Text("بيع") })
                }
                OutlinedTextField(usdNew, { usdNew = it }, label = { Text("USD عدن / الريال الجديد") }, singleLine = true)
                OutlinedTextField(usdOld, { usdOld = it }, label = { Text("USD صنعاء / الريال القديم") }, singleLine = true)
                OutlinedTextField(sarNew, { sarNew = it }, label = { Text("SAR عدن — اختياري") }, singleLine = true)
                OutlinedTextField(sarOld, { sarOld = it }, label = { Text("SAR صنعاء — اختياري") }, singleLine = true)
                OutlinedTextField(source, { source = it }, label = { Text("المصدر/سبب الإدخال اليدوي") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                try {
                    val n = requireNotNull(usdNew.toDoubleOrNull()) { "سعر USD عدن غير صالح" }
                    val o = requireNotNull(usdOld.toDoubleOrNull()) { "سعر USD صنعاء غير صالح" }
                    val sn = sarNew.takeIf { it.isNotBlank() }?.toDoubleOrNull()
                    val so = sarOld.takeIf { it.isNotBlank() }?.toDoubleOrNull()
                    require((sn == null) == (so == null)) { "أدخل سعري SAR معاً أو اتركهما معاً" }
                    geoParseStart(date)
                    onSave(date, n, o, sn, so, rateType, source)
                } catch (e: Exception) { error = e.message }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun ProvincePricingTab(container: AppContainer, user: UserEntity, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val policies by container.db.geographyDao().observeProvincePolicies().collectAsState(initial = emptyList())
    val currencies by container.db.currencyDao().observeAll().collectAsState(initial = emptyList())
    val itemsList by container.db.itemDao().observeAll().collectAsState(initial = emptyList())
    val finishedProducts = itemsList.filter { it.category == "FINISHED_GOOD" && it.isActive }
    var product by remember { mutableStateOf<ItemEntity?>(null) }
    var showPrice by remember { mutableStateOf(false) }
    var showPolicy by remember { mutableStateOf(false) }
    var editingPolicy by remember { mutableStateOf<ProvincePolicyEntity?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(finishedProducts) {
        if (product?.id !in finishedProducts.map { it.id }) product = finishedProducts.firstOrNull()
    }

    LazyColumn(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { FormalGeographyHierarchyManager(container, user) }
        item {
            HorizontalDivider()
            FushSectionHeader("سياسات المحافظات وقوائم الأسعار", "يمكن لكل محافظة تحديد العملة والنقل الافتراضي ومتطلبات سعر الصرف والرسوم.")
            if (finishedProducts.isNotEmpty()) {
                GeoSelect("المنتج النهائي", product, finishedProducts, { "${it.code} — ${it.nameAr}" }, onCleared = { product = null }) { product = it }
            } else {
                FushInlineState("لا توجد منتجات نهائية نشطة. أضف المنتج من البيانات الأساسية أولاً.")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { editingPolicy = null; showPolicy = true }) { Text("إضافة سياسة محافظة") }
                Button(enabled = product != null && policies.isNotEmpty(), onClick = { showPrice = true }) { Text("إضافة سعر محافظة") }
            }
            FushOperationMessage(message, onConsumed = { message = null })
        }
        if (policies.isEmpty()) item { FushEmptyState("لا توجد سياسات محافظات", "أضف سياسات المحافظات قبل إنشاء قوائم أسعار جغرافية.") }
        items(policies, key = { it.code }) { p ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(p.nameAr, style = MaterialTheme.typography.titleMedium)
                    Text("العملة: ${p.currencyCode} • نقل افتراضي/كرتون: ${geoMoney(p.defaultTransportPerCartonBase)} ريال جديد")
                    if (p.requiresDailyFx) Text("يتطلب سعر صرف يومي", color = MaterialTheme.colorScheme.tertiary)
                    if (p.requiresFeesAndCustoms) Text("تُدخل الرسوم/الجمارك الفعلية عند التسعير", style = MaterialTheme.typography.bodySmall)
                    Text(p.notes, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { editingPolicy = p; showPolicy = true }) { Text("تعديل السياسة") }
                }
            }
        }
        product?.let { item { ProductPriceList(container, it, user) } }
    }

    if (showPolicy) {
        AddProvincePolicyDialog(
            currencies = currencies,
            initial = editingPolicy,
            onDismiss = { showPolicy = false; editingPolicy = null }
        ) { code, name, currency, transport, dailyFx, actualTransport, feesCustoms, notes ->
            scope.launch {
                try {
                    container.geographyService.upsertProvincePolicy(
                        code = code,
                        nameAr = name,
                        currencyCode = currency.code,
                        defaultTransportPerCartonBase = transport,
                        requiresDailyFx = dailyFx,
                        requiresActualTransport = actualTransport,
                        requiresFeesAndCustoms = feesCustoms,
                        notes = notes,
                        userId = user.id
                    )
                    message = "تم حفظ سياسة المحافظة"
                    showPolicy = false
                    editingPolicy = null
                } catch (e: Exception) { message = e.message ?: "تعذر حفظ سياسة المحافظة" }
            }
        }
    }

    if (showPrice) {
        product?.let { selectedProduct ->
        AddProvincePriceDialog(policies, currencies, onDismiss = { showPrice = false }) { policy, channel, currency, price, effective, effectiveTo, active, note ->
            scope.launch {
                try {
                    container.geographyService.setProvincePrice(
                        itemId = selectedProduct.id,
                        channel = channel,
                        province = policy.nameAr,
                        currencyCode = currency.code,
                        baseUnitPriceOriginal = price,
                        effectiveFrom = geoParseStart(effective),
                        effectiveTo = effectiveTo.takeIf { it.isNotBlank() }?.let(::geoParseEnd),
                        isActive = active,
                        note = note,
                        userId = user.id
                    )
                    message = "تمت إضافة قائمة السعر للمحافظة"
                    showPrice = false
                } catch (e: Exception) { message = e.message ?: "تعذر حفظ السعر" }
            }
        }
        }
    }
}

@Composable
private fun FormalGeographyHierarchyManager(container: AppContainer, user: UserEntity) {
    val scope = rememberCoroutineScope()
    val governorates by container.db.geographyDao().observeGovernorates().collectAsState(initial = emptyList())
    val permissions by container.db.securityDao().observePermissionCodesForRole(user.role).collectAsState(initial = emptyList())
    val canManage = user.role == "ADMIN" || SecurityPermissions.GEOGRAPHY_MANAGE in permissions
    var governorate by remember { mutableStateOf<GovernorateEntity?>(null) }
    var district by remember { mutableStateOf<DistrictEntity?>(null) }
    var area by remember { mutableStateOf<AreaEntity?>(null) }
    val districts by produceState(initialValue = emptyList<DistrictEntity>(), key1 = governorate?.id) {
        value = governorate?.let { container.db.geographyDao().districtsForGovernorate(it.id) }.orEmpty()
    }
    val areas by produceState(initialValue = emptyList<AreaEntity>(), key1 = district?.id) {
        value = district?.let { container.db.geographyDao().areasForDistrict(it.id) }.orEmpty()
    }
    var editLevel by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(governorate?.id) {
        if (district?.governorateId != governorate?.id) district = null
        area = null
    }
    LaunchedEffect(district?.id) { if (area?.districtId != district?.id) area = null }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FushSectionHeader(
                "الهيكل الجغرافي الرسمي",
                "محافظة ← مديرية ← منطقة/عزلة. المعرفات ثابتة وتستخدمها العملاء والفواتير والشحنات بدل مقارنة النصوص."
            )
            Text("بيانات البداية: ${governorates.size} محافظة • 335 مديرية • 2,234 منطقة/عزلة مرجعية", style = MaterialTheme.typography.bodySmall)
            GeoSelect("المحافظة", governorate, governorates, { "${it.nameAr} • ${it.code}" }, onCleared = { governorate = null }) { governorate = it }
            if (governorate != null) {
                GeoSelect("المديرية", district, districts, { "${it.nameAr} • ${it.code}" }, onCleared = { district = null }) { district = it }
            }
            if (district != null) {
                GeoSelect("المنطقة / العزلة", area, areas, { it.nameAr }, onCleared = { area = null }) { area = it }
            }
            if (canManage) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { editLevel = if (governorate == null) "GOV_NEW" else "GOV_EDIT" }, modifier = Modifier.weight(1f)) {
                        Text(if (governorate == null) "إضافة محافظة" else "تعديل المحافظة")
                    }
                    OutlinedButton(enabled = governorate != null, onClick = { editLevel = if (district == null) "DIST_NEW" else "DIST_EDIT" }, modifier = Modifier.weight(1f)) {
                        Text(if (district == null) "إضافة مديرية" else "تعديل المديرية")
                    }
                    OutlinedButton(enabled = district != null, onClick = { editLevel = if (area == null) "AREA_NEW" else "AREA_EDIT" }, modifier = Modifier.weight(1f)) {
                        Text(if (area == null) "إضافة منطقة" else "تعديل المنطقة")
                    }
                }
            }
            FushOperationMessage(message, onConsumed = { message = null })
        }
    }

    editLevel?.let { level ->
        val initialAr = when (level) {
            "GOV_EDIT" -> governorate?.nameAr.orEmpty()
            "DIST_EDIT" -> district?.nameAr.orEmpty()
            "AREA_EDIT" -> area?.nameAr.orEmpty()
            else -> ""
        }
        val initialEn = when (level) {
            "GOV_EDIT" -> governorate?.nameEn.orEmpty()
            "DIST_EDIT" -> district?.nameEn.orEmpty()
            "AREA_EDIT" -> area?.nameEn.orEmpty()
            else -> ""
        }
        val initialActive = when (level) {
            "GOV_EDIT" -> governorate?.isActive ?: true
            "DIST_EDIT" -> district?.isActive ?: true
            "AREA_EDIT" -> area?.isActive ?: true
            else -> true
        }
        GeographyMasterEditDialog(
            title = when (level) {
                "GOV_NEW" -> "إضافة محافظة"
                "GOV_EDIT" -> "تعديل المحافظة"
                "DIST_NEW" -> "إضافة مديرية"
                "DIST_EDIT" -> "تعديل المديرية"
                "AREA_NEW" -> "إضافة منطقة"
                else -> "تعديل المنطقة"
            },
            initialAr = initialAr,
            initialEn = initialEn,
            initialActive = initialActive,
            onDismiss = { editLevel = null }
        ) { ar, en, active ->
            scope.launch {
                try {
                    when (level) {
                        "GOV_NEW", "GOV_EDIT" -> {
                            val saved = container.geographyService.saveGovernorate(
                                id = if (level == "GOV_EDIT") governorate?.id else null,
                                nameAr = ar, nameEn = en, active = active, userId = user.id
                            )
                            governorate = saved; district = null; area = null
                        }
                        "DIST_NEW", "DIST_EDIT" -> {
                            val parent = requireNotNull(governorate) { "اختر المحافظة" }
                            val saved = container.geographyService.saveDistrict(
                                id = if (level == "DIST_EDIT") district?.id else null,
                                governorateId = parent.id, nameAr = ar, nameEn = en, active = active, userId = user.id
                            )
                            district = saved; area = null
                        }
                        else -> {
                            val parent = requireNotNull(district) { "اختر المديرية" }
                            val saved = container.geographyService.saveArea(
                                id = if (level == "AREA_EDIT") area?.id else null,
                                districtId = parent.id, nameAr = ar, nameEn = en, active = active, userId = user.id
                            )
                            area = saved
                        }
                    }
                    message = "تم حفظ الهيكل الجغرافي"
                    editLevel = null
                } catch (e: Exception) { message = e.message ?: "تعذر حفظ الموقع الجغرافي" }
            }
        }
    }
}

@Composable
private fun GeographyMasterEditDialog(
    title: String,
    initialAr: String,
    initialEn: String,
    initialActive: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean) -> Unit,
) {
    var ar by remember(title, initialAr) { mutableStateOf(initialAr) }
    var en by remember(title, initialEn) { mutableStateOf(initialEn) }
    var active by remember(title, initialActive) { mutableStateOf(initialActive) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            com.fush.erp.ui.FushDialogForm {
                OutlinedTextField(ar, { ar = it }, label = { Text("الاسم العربي") }, singleLine = true)
                OutlinedTextField(en, { en = it }, label = { Text("الاسم الإنجليزي — اختياري") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(active, { active = it }); Text("نشط") }
                Text("المعرف الثابت لا يتغير عند تعديل الاسم، حتى لا تنكسر الروابط التاريخية.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(enabled = ar.isNotBlank(), onClick = { onSave(ar, en, active) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun ProductPriceList(container: AppContainer, product: ItemEntity, user: UserEntity) {
    val scope = rememberCoroutineScope()
    val prices by container.db.salesDao().observePrices(product.id).collectAsState(initial = emptyList())
    var message by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("قائمة أسعار ${product.nameAr}", style = MaterialTheme.typography.titleMedium)
        Text("البيع يستخدم فقط القائمة الفعالة والسارية في تاريخ الفاتورة لنفس المحافظة والقناة والعملة.", style = MaterialTheme.typography.bodySmall)
        FushOperationMessage(message, onConsumed = { message = null })
        if (prices.isEmpty()) {
            FushInlineState("لا توجد قوائم أسعار لهذا المنتج حتى الآن.")
        }
        prices.take(40).forEach { row ->
            ListItem(
                headlineContent = {
                    Text("${row.province} — ${geoChannel(row.channel)} — ${if (row.isActive) "فعالة" else "موقوفة"}")
                },
                supportingContent = {
                    val until = row.effectiveTo?.let { " إلى ${geoDate(it)}" } ?: " بدون تاريخ انتهاء"
                    Text("${geoMoney(row.baseUnitPriceOriginal)} ${row.currencyCode} / وحدة أساسية • من ${geoDate(row.effectiveFrom)}$until${if (row.note.isBlank()) "" else " • ${row.note}"}")
                },
                trailingContent = {
                    TextButton(onClick = {
                        scope.launch {
                            try {
                                container.geographyService.setProvincePriceActive(row.id, !row.isActive, user.id)
                                message = if (row.isActive) "تم إيقاف قائمة السعر" else "تم تفعيل قائمة السعر"
                            } catch (e: Exception) {
                                message = e.message ?: "تعذر تغيير حالة قائمة السعر"
                            }
                        }
                    }) { Text(if (row.isActive) "إيقاف" else "تفعيل") }
                }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun AddProvincePolicyDialog(
    currencies: List<CurrencyEntity>,
    initial: ProvincePolicyEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, CurrencyEntity, Double, Boolean, Boolean, Boolean, String) -> Unit
) {
    var code by remember(initial?.code) { mutableStateOf(initial?.code ?: "") }
    var name by remember(initial?.code) { mutableStateOf(initial?.nameAr ?: "") }
    var currency by remember(initial?.code, currencies) { mutableStateOf(currencies.firstOrNull { it.code == initial?.currencyCode } ?: currencies.firstOrNull { it.isBase } ?: currencies.firstOrNull()) }
    var transport by remember(initial?.code) { mutableStateOf(initial?.defaultTransportPerCartonBase?.toString() ?: "") }
    var dailyFx by remember(initial?.code) { mutableStateOf(initial?.requiresDailyFx ?: false) }
    var actualTransport by remember(initial?.code) { mutableStateOf(initial?.requiresActualTransport ?: false) }
    var feesCustoms by remember(initial?.code) { mutableStateOf(initial?.requiresFeesAndCustoms ?: false) }
    var notes by remember(initial?.code) { mutableStateOf(initial?.notes ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "إضافة سياسة محافظة" else "تعديل سياسة محافظة") },
        text = {
            com.fush.erp.ui.FushDialogForm {
                OutlinedTextField(code, { code = it.uppercase().filter { c -> c.isLetterOrDigit() || c == '_' || c == '-' } }, label = { Text("رمز المحافظة") }, enabled = initial == null, singleLine = true)
                OutlinedTextField(name, { name = it }, label = { Text("اسم المحافظة") }, singleLine = true)
                GeoSelect("العملة", currency, currencies, { "${it.code} — ${it.nameAr}" }, onCleared = { currency = null }) { currency = it }
                OutlinedTextField(transport, { transport = it }, label = { Text("النقل الافتراضي لكل كرتون — اختياري") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(dailyFx, { dailyFx = it }); Text("يتطلب سعر صرف يومي") }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(actualTransport, { actualTransport = it }); Text("يتطلب إدخال النقل الفعلي") }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(feesCustoms, { feesCustoms = it }); Text("يتطلب رسوم/جمارك فعلية") }
                OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات") })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                try {
                    val c = requireNotNull(currency) { "اختر العملة" }
                    val tr = if (transport.isBlank()) 0.0 else requireNotNull(transport.toDoubleOrNull()) { "النقل الافتراضي غير صالح" }
                    require(code.isNotBlank()) { "رمز المحافظة مطلوب" }
                    require(name.isNotBlank()) { "اسم المحافظة مطلوب" }
                    onSave(code, name, c, tr, dailyFx, actualTransport, feesCustoms, notes)
                } catch (e: Exception) { error = e.message }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun AddProvincePriceDialog(
    policies: List<ProvincePolicyEntity>,
    currencies: List<CurrencyEntity>,
    onDismiss: () -> Unit,
    onSave: (ProvincePolicyEntity, String, CurrencyEntity, Double, String, String, Boolean, String) -> Unit
) {
    var policy by remember { mutableStateOf<ProvincePolicyEntity?>(policies.firstOrNull()) }
    var channel by remember { mutableStateOf("DIRECT") }
    var currency by remember { mutableStateOf<CurrencyEntity?>(currencies.firstOrNull { it.code == policy?.currencyCode } ?: currencies.firstOrNull()) }
    var price by remember { mutableStateOf("") }
    var effective by remember { mutableStateOf(geoTodayText()) }
    var effectiveTo by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(true) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(policy?.code) { currency = currencies.firstOrNull { it.code == policy?.currencyCode } ?: currency }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("سعر حسب المحافظة") },
        text = {
            com.fush.erp.ui.FushDialogForm {
                GeoSelect("المحافظة", policy, policies, { it.nameAr }, onCleared = { policy = null }) { policy = it }
                GeoSelect("القناة", channel.takeIf { it.isNotBlank() }, listOf("DIRECT", "RETAIL", "DISTRIBUTOR_CASH", "DISTRIBUTOR_CREDIT"), { geoChannel(it) }, onCleared = { channel = "" }) { channel = it }
                GeoSelect("العملة", currency, currencies, { "${it.code} — ${it.nameAr}" }, onCleared = { currency = null }) { currency = it }
                OutlinedTextField(price, { price = it }, label = { Text("سعر العبوة بالعملة المختارة") }, singleLine = true)
                FushDateField(effective, { effective = it }, "ساري من", modifier = Modifier.fillMaxWidth())
                FushDateField(effectiveTo, { effectiveTo = it }, "ساري حتى", modifier = Modifier.fillMaxWidth(), optional = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = active, onCheckedChange = { active = it })
                    Text("قائمة السعر فعالة")
                }
                Text("إذا أضفت سعراً فعالاً أحدث، سيغلق النظام تلقائياً السعر السابق المفتوح لنفس المحافظة والقناة والعملة.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(note, { note = it }, label = { Text("ملاحظة") })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                try {
                    val p = requireNotNull(policy) { "اختر المحافظة" }
                    val c = requireNotNull(currency) { "اختر العملة" }
                    val v = requireNotNull(price.toDoubleOrNull()) { "السعر غير صالح" }
                    val from = geoParseStart(effective)
                    val to = effectiveTo.takeIf { it.isNotBlank() }?.let(::geoParseEnd)
                    SalesMath.validatePricePeriod(from, to)
                    require(channel.isNotBlank()) { "اختر القناة" }
                    onSave(p, channel, c, v, effective, effectiveTo, active, note)
                } catch (e: Exception) { error = e.message }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun GeographicQuoteTab(container: AppContainer, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val policies by container.db.geographyDao().observeProvincePolicies().collectAsState(initial = emptyList())
    var policy by remember { mutableStateOf<ProvincePolicyEntity?>(null) }
    var cartons by remember { mutableStateOf("1") }
    var pricePerCartonNew by remember { mutableStateOf("") }
    var transport by remember { mutableStateOf("") }
    var fees by remember { mutableStateOf("0") }
    var risk by remember { mutableStateOf("0") }
    var date by remember { mutableStateOf(geoTodayText()) }
    var result by remember { mutableStateOf<GeographicQuoteResult?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(policies) { if (policy == null) policy = policies.firstOrNull() }
    LazyColumn(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FushSectionHeader("حاسبة التسعير الجغرافي", "النقل والرسوم هنا إضافات سعرية؛ أما التكاليف الفعلية فتسجل في الربحية حتى لا تختلط الإيرادات بالتكلفة.")
            GeoSelect("المحافظة", policy, policies, { it.nameAr }, onCleared = { policy = null; result = null }) { policy = it; result = null }
            OutlinedTextField(cartons, { cartons = it }, label = { Text("عدد الكراتين") }, singleLine = true)
            OutlinedTextField(pricePerCartonNew, { pricePerCartonNew = it }, label = { Text("سعر المنتج للكرتون بالريال الجديد قبل النقل") }, singleLine = true)
            FushDateField(date, { date = it }, "تاريخ التسعير", modifier = Modifier.fillMaxWidth())
            OutlinedTextField(transport, { transport = it }, label = { Text("النقل بالعملة النهائية — اتركه فارغاً لاستخدام سياسة المحافظة") }, singleLine = true)
            OutlinedTextField(fees, { fees = it }, label = { Text("الرسوم/الجمارك بالعملة النهائية") }, singleLine = true)
            OutlinedTextField(risk, { risk = it }, label = { Text("هامش المخاطر بالعملة النهائية") }, singleLine = true)
            Button(onClick = {
                val p = policy ?: return@Button
                scope.launch {
                    try {
                        val c = requireNotNull(cartons.toDoubleOrNull()) { "عدد الكراتين غير صالح" }
                        val pc = requireNotNull(pricePerCartonNew.toDoubleOrNull()) { "سعر الكرتون غير صالح" }
                        val tr = transport.takeIf { it.isNotBlank() }?.toDoubleOrNull() ?: if (transport.isBlank()) null else error("النقل غير صالح")
                        val f = requireNotNull(fees.toDoubleOrNull()) { "الرسوم غير صالحة" }
                        val r = requireNotNull(risk.toDoubleOrNull()) { "هامش المخاطر غير صالح" }
                        result = container.geographyService.calculateQuote(p.code, c, c * pc, tr, f, r, geoParseEnd(date))
                        message = null
                    } catch (e: Exception) { result = null; message = e.message ?: "تعذر الحساب" }
                }
            }) { Text("احسب السعر") }
            message?.let { FushNotice(it, tone = FushStatusTone.Danger) }
        }
        result?.let { q ->
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("النتيجة", style = MaterialTheme.typography.titleLarge)
                        Text("العملة: ${q.currencyCode} • سعر التحويل للأساس: ${geoRate(q.exchangeRateToBase)}")
                        Text("المنتج: ${geoMoney(q.productOriginal)}")
                        Text("النقل: ${geoMoney(q.transportOriginal)} • الرسوم: ${geoMoney(q.feesOriginal)}")
                        Text("هامش المخاطر: ${geoMoney(q.riskMarginOriginal)}")
                        Text("الإجمالي: ${geoMoney(q.totalOriginal)} ${q.currencyCode}", style = MaterialTheme.typography.headlineSmall)
                        Text("المكافئ: ${geoMoney(q.totalBaseEquivalent)} ريال جديد", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun GeographicProfitabilityTab(container: AppContainer, user: UserEntity, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    var from by remember { mutableStateOf(geoMonthStartText()) }
    var to by remember { mutableStateOf(geoTodayText()) }
    var provinces by remember { mutableStateOf<List<ProvinceProfitabilityRow>>(emptyList()) }
    var invoices by remember { mutableStateOf<List<InvoiceProfitabilityRow>>(emptyList()) }
    var costInvoice by remember { mutableStateOf<InvoiceProfitabilityRow?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            try {
                val f = geoParseStart(from); val t = geoParseEnd(to)
                provinces = container.geographyService.provinceProfitability(f, t)
                invoices = container.geographyService.invoiceProfitability(f, t)
                message = null
            } catch (e: Exception) { message = e.message ?: "تعذر إعداد تقرير الربحية" }
        }
    }
    LaunchedEffect(Unit) { reload() }

    LazyColumn(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FushSectionHeader("الربحية الجغرافية", "تحليل صافي المبيعات والتكلفة والعمولة والتكاليف الجغرافية الفعلية حسب المحافظة والفاتورة.")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(from, { from = it }, label = { Text("من") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(to, { to = it }, label = { Text("إلى") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            Button(onClick = { reload() }) { Text("تحديث التقرير") }
            Text("الربح = صافي المبيعات − تكلفة البضاعة − العمولة المستحقة − النقل/الرسوم الفعلية المسجلة.", style = MaterialTheme.typography.bodySmall)
            FushOperationMessage(message, onConsumed = { message = null })
        }
        item { Text("حسب المحافظة", style = MaterialTheme.typography.titleMedium) }
        if (provinces.isEmpty()) item { FushInlineState("لا توجد نتائج ربحية حسب المحافظة ضمن الفترة المحددة.") }
        items(provinces, key = { "geo-province-${it.province}" }) { p ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(p.province, style = MaterialTheme.typography.titleMedium)
                    Text("فواتير ${p.invoiceCount} • صافي مبيعات ${geoMoney(p.netRevenueBase)}")
                    Text("تكلفة بضاعة ${geoMoney(p.netCogsBase)} • عمولات ${geoMoney(p.commissionBase)} • جغرافي ${geoMoney(p.geographicCostBase)}")
                    Text("الربح: ${geoMoney(p.profitBase)} ريال جديد", style = MaterialTheme.typography.titleSmall, color = if (p.profitBase >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }
            }
        }
        item { Text("حسب الفاتورة والعميل", style = MaterialTheme.typography.titleMedium) }
        if (invoices.isEmpty()) item { FushInlineState("لا توجد فواتير ربحية ضمن الفترة المحددة.") }
        items(invoices, key = { "geo-invoice-${it.invoiceId}" }) { r ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("${r.invoiceNo} — ${r.customerName}", style = MaterialTheme.typography.titleMedium)
                    Text("${r.province} • ${geoDate(r.invoiceDate)} • ${r.currencyCode}")
                    Text("مبيعات ${geoMoney(r.netRevenueBase)} • COGS ${geoMoney(r.netCogsBase)} • عمولة ${geoMoney(r.commissionBase)}")
                    Text("تكلفة جغرافية ${geoMoney(r.geographicCostBase)} • ربح ${geoMoney(r.profitBase)}", color = if (r.profitBase >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { costInvoice = r }) { Text("تسجيل/تعديل التكلفة الفعلية") }
                }
            }
        }
    }

    costInvoice?.let { row ->
        GeographicCostDialog(container, row, onDismiss = { costInvoice = null }) { cartons, transport, fees, other, notes ->
            scope.launch {
                try {
                    container.geographyService.recordInvoiceGeographicCost(row.invoiceId, cartons, transport, fees, other, notes, user.id)
                    message = "تم تحديث التكلفة الفعلية للفاتورة ${row.invoiceNo}"
                    costInvoice = null
                    reload()
                } catch (e: Exception) { message = e.message ?: "تعذر تسجيل التكلفة" }
            }
        }
    }
}

@Composable
private fun GeographicCostDialog(
    container: AppContainer,
    row: InvoiceProfitabilityRow,
    onDismiss: () -> Unit,
    onSave: (Double, Double, Double, Double, String) -> Unit
) {
    var cartons by remember { mutableStateOf("") }
    var transport by remember { mutableStateOf("0") }
    var fees by remember { mutableStateOf("0") }
    var other by remember { mutableStateOf("0") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(row.invoiceId) {
        val existing = container.db.geographyDao().invoiceGeographicCost(row.invoiceId)
        if (existing != null) {
            cartons = existing.cartonsEquivalent.toString(); transport = existing.transportCostBase.toString(); fees = existing.feesCustomsCostBase.toString(); other = existing.otherDirectCostBase.toString(); notes = existing.notes
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تكلفة ${row.invoiceNo} — ${row.province}") },
        text = {
            com.fush.erp.ui.FushDialogForm {
                Text("أدخل التكاليف الفعلية بالريال الجديد، وليس المبالغ التي حُمّلت على العميل.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(cartons, { cartons = it }, label = { Text("مكافئ عدد الكراتين") }, singleLine = true)
                OutlinedTextField(transport, { transport = it }, label = { Text("تكلفة النقل الفعلية") }, singleLine = true)
                OutlinedTextField(fees, { fees = it }, label = { Text("الرسوم والجمارك الفعلية") }, singleLine = true)
                OutlinedTextField(other, { other = it }, label = { Text("تكاليف مباشرة أخرى") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات") })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                try {
                    onSave(
                        requireNotNull(cartons.toDoubleOrNull()) { "عدد الكراتين غير صالح" },
                        requireNotNull(transport.toDoubleOrNull()) { "النقل غير صالح" },
                        requireNotNull(fees.toDoubleOrNull()) { "الرسوم غير صالحة" },
                        requireNotNull(other.toDoubleOrNull()) { "التكلفة الأخرى غير صالحة" },
                        notes
                    )
                } catch (e: Exception) { error = e.message }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun <T> GeoSelect(
    label: String,
    selected: T?,
    options: List<T>,
    text: (T) -> String,
    onCleared: () -> Unit,
    onSelected: (T) -> Unit,
) {
    FushSearchableSelectionField(
        label = label,
        selectedText = selected?.let(text).orEmpty(),
        options = options,
        optionText = text,
        searchTerms = { listOf(text(it)) },
        onCleared = onCleared,
        onSelected = onSelected,
        placeholder = "اختر أو اكتب للبحث",
    )
}

private fun geoChannel(code: String): String = when (code) {
    "DIRECT" -> "مباشر"
    "RETAIL" -> "تجزئة"
    "DISTRIBUTOR_CASH" -> "موزع نقدي"
    "DISTRIBUTOR_CREDIT" -> "موزع آجل"
    else -> code
}

private fun geoMoney(v: Double): String = String.format(Locale.US, "%,.2f", v)
private fun geoRate(v: Double): String = String.format(Locale.US, "%,.6f", v)
private fun geoPct(v: Double): String = String.format(Locale.US, "%.2f%%", v)
private fun geoDate(ms: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))
private fun geoDateTime(ms: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ms))
private fun fxStatusLabel(status: FxQuoteStatus): String = when (status) {
    FxQuoteStatus.FRESH -> "حديث ومتوافق"
    FxQuoteStatus.PRIMARY_ONLY -> "مصدر أساسي فقط"
    FxQuoteStatus.WARNING -> "تحذير اختلاف"
    FxQuoteStatus.CONFLICT -> "تعارض مرتفع"
    FxQuoteStatus.STALE -> "سعر قديم"
}
@Composable
private fun fxStatusColor(status: FxQuoteStatus) = when (status) {
    FxQuoteStatus.FRESH -> MaterialTheme.colorScheme.primary
    FxQuoteStatus.PRIMARY_ONLY -> MaterialTheme.colorScheme.onSurfaceVariant
    FxQuoteStatus.WARNING -> MaterialTheme.colorScheme.tertiary
    FxQuoteStatus.CONFLICT, FxQuoteStatus.STALE -> MaterialTheme.colorScheme.error
}
private fun geoTodayText(): String = geoDate(com.fush.erp.domain.TrustedTimeService.now())
private fun geoMonthStartText(): String {
    val c = BusinessTimeZone.calendarAt(TrustedTimeService.now()); c.set(Calendar.DAY_OF_MONTH, 1)
    return geoDate(c.timeInMillis)
}
private fun geoParseStart(text: String): Long {
    val f = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    return requireNotNull(f.parse(text)) { "التاريخ غير صالح" }.time
}
private fun geoParseEnd(text: String): Long = geoParseStart(text) + GEO_DAY_MS - 1
