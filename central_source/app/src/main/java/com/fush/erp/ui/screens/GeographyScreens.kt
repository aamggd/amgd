package com.fush.erp.ui.screens

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
import com.fush.erp.domain.SalesMath
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
    val snapshots by container.db.geographyDao().observeFxSnapshots().collectAsState(initial = emptyList())
    val rates by container.db.currencyDao().observeRateHistory(100).collectAsState(initial = emptyList())
    var showAdd by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LazyColumn(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FushSectionHeader("أسعار الصرف التاريخية", "كل سعر يحتفظ بتاريخ سريانه، ويُشتق تحويل الريال القديم إلى الجديد من سعر الدولار في الطبعتين.")
            Button(onClick = { showAdd = true }) { Text("تسجيل سعر يومي") }
            FushOperationMessage(message, onConsumed = { message = null })
        }
        if (snapshots.isEmpty()) {
            item { FushEmptyState("لا توجد لقطات صرف يومية", "سجّل أول سعر يومي للدولار بالطبعتين لتكوين سجل تاريخي قابل للتتبع.") }
        } else {
            item { Text("لقطات الدولار اليومية", style = MaterialTheme.typography.titleMedium) }
            items(snapshots, key = { it.id }) { row ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(geoDate(row.effectiveAt), style = MaterialTheme.typography.titleSmall)
                        Text("USD جديد: ${geoMoney(row.usdNewYer)} • USD قديم: ${geoMoney(row.usdOldYer)}")
                        Text("1 ريال قديم = ${geoRate(row.oldYerToNewYer)} ريال جديد", style = MaterialTheme.typography.bodySmall)
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

    if (showAdd) {
        AddFxSnapshotDialog(onDismiss = { showAdd = false }) { date, newUsd, oldUsd, source ->
            scope.launch {
                try {
                    container.geographyService.recordFxSnapshot(geoParseStart(date), newUsd, oldUsd, source, user.id)
                    message = "تم تسجيل سعر الصرف التاريخي وتحديث USD وYER_OLD"
                    showAdd = false
                } catch (e: Exception) { message = e.message ?: "تعذر تسجيل سعر الصرف" }
            }
        }
    }
}

@Composable
private fun AddFxSnapshotDialog(onDismiss: () -> Unit, onSave: (String, Double, Double, String) -> Unit) {
    var date by remember { mutableStateOf(geoTodayText()) }
    var usdNew by remember { mutableStateOf("1554.62") }
    var usdOld by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("سعر صرف يومي") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FushDateField(date, { date = it }, "التاريخ", modifier = Modifier.fillMaxWidth())
                OutlinedTextField(usdNew, { usdNew = it }, label = { Text("سعر 1 USD بالريال الجديد") }, singleLine = true)
                OutlinedTextField(usdOld, { usdOld = it }, label = { Text("سعر 1 USD بالريال القديم") }, singleLine = true)
                OutlinedTextField(source, { source = it }, label = { Text("المصدر/ملاحظة") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                try {
                    val n = requireNotNull(usdNew.toDoubleOrNull()) { "سعر الدولار الجديد غير صالح" }
                    val o = requireNotNull(usdOld.toDoubleOrNull()) { "سعر الدولار القديم غير صالح" }
                    geoParseStart(date)
                    onSave(date, n, o, source)
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
    val product = itemsList.firstOrNull { it.code == "FG-FUSH-60" }
    var showPrice by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LazyColumn(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FushSectionHeader("سياسات المحافظات وقوائم الأسعار", "تعز هي الأساس، ويمكن لكل محافظة تحديد العملة والنقل الافتراضي ومتطلبات سعر الصرف والرسوم.")
            Button(enabled = product != null && policies.isNotEmpty(), onClick = { showPrice = true }) { Text("إضافة سعر محافظة") }
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
                }
            }
        }
        product?.let { item { ProductPriceList(container, it, user) } }
    }

    if (showPrice && product != null) {
        AddProvincePriceDialog(policies, currencies, onDismiss = { showPrice = false }) { policy, channel, currency, price, effective, effectiveTo, active, note ->
            scope.launch {
                try {
                    container.geographyService.setProvincePrice(
                        itemId = product.id,
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

@Composable
private fun ProductPriceList(container: AppContainer, product: ItemEntity, user: UserEntity) {
    val scope = rememberCoroutineScope()
    val prices by container.db.salesDao().observePrices(product.id).collectAsState(initial = emptyList())
    var message by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("قائمة أسعار Fush 60 مل", style = MaterialTheme.typography.titleMedium)
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
                    Text("${geoMoney(row.baseUnitPriceOriginal)} ${row.currencyCode} / عبوة • من ${geoDate(row.effectiveFrom)}$until${if (row.note.isBlank()) "" else " • ${row.note}"}")
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
            Column(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GeoSelect("المحافظة", policy, policies, { it.nameAr }) { policy = it }
                GeoSelect("القناة", channel, listOf("DIRECT", "RETAIL", "DISTRIBUTOR_CASH", "DISTRIBUTOR_CREDIT"), { geoChannel(it) }) { channel = it }
                GeoSelect("العملة", currency, currencies, { "${it.code} — ${it.nameAr}" }) { currency = it }
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
    var pricePerCartonNew by remember { mutableStateOf("480000") }
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
            GeoSelect("المحافظة", policy, policies, { it.nameAr }) { policy = it; result = null }
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
        } else {
            cartons = container.db.geographyDao().invoiceCartonsEquivalent(row.invoiceId).toString()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تكلفة ${row.invoiceNo} — ${row.province}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun <T> GeoSelect(label: String, selected: T?, options: List<T>, text: (T) -> String, onSelected: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${selected?.let(text) ?: "اختر"}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(text(option)) }, onClick = { onSelected(option); expanded = false })
            }
        }
    }
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
private fun geoDate(ms: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))
private fun geoTodayText(): String = geoDate(System.currentTimeMillis())
private fun geoMonthStartText(): String {
    val c = Calendar.getInstance(); c.set(Calendar.DAY_OF_MONTH, 1)
    return geoDate(c.timeInMillis)
}
private fun geoParseStart(text: String): Long {
    val f = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    return requireNotNull(f.parse(text)) { "التاريخ غير صالح" }.time
}
private fun geoParseEnd(text: String): Long = geoParseStart(text) + GEO_DAY_MS - 1
