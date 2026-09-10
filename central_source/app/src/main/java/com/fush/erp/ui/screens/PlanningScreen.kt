package com.fush.erp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fush.erp.data.AppContainer
import com.fush.erp.data.entity.*
import com.fush.erp.ui.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.Locale
import java.time.YearMonth

private val planningMonthNames = listOf(
    "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
    "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
)

private data class PlanningPolicyEditorTarget(
    val itemId: Long,
    val label: String,
    val safetyStockDays: Double,
    val leadTimeDays: Double,
    val note: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanningScreen(
    container: AppContainer,
    user: UserEntity,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val allItems by container.db.itemDao().observeAll().collectAsState(initial = emptyList())
    val policies by container.db.geographyDao().observeProvincePolicies().collectAsState(initial = emptyList())
    val products = remember(allItems) { allItems.filter { it.category == "FINISHED_GOOD" && it.isActive } }

    var selectedItemId by remember { mutableStateOf<Long?>(null) }
    var selectedProvinceCode by remember { mutableStateOf<String?>(null) }
    var itemMenuOpen by remember { mutableStateOf(false) }
    var provinceMenuOpen by remember { mutableStateOf(false) }
    var editMonth by remember { mutableStateOf<Int?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var forecast by remember { mutableStateOf<DemandForecastSnapshot?>(null) }
    var forecastError by remember { mutableStateOf<String?>(null) }
    var editDemandPlan by remember { mutableStateOf(false) }
    var confirmApprovePlan by remember { mutableStateOf(false) }
    var reopenDemandPlan by remember { mutableStateOf(false) }
    var editWeeklyBudget by remember { mutableStateOf(false) }
    var confirmRedistributeBudget by remember { mutableStateOf(false) }
    var weeklyActual by remember { mutableStateOf<List<WeeklySalesActualRow>>(emptyList()) }
    var seasonalAnalysis by remember { mutableStateOf<SeasonalDemandAnalysis?>(null) }
    var provinceSeasonalityRows by remember { mutableStateOf<List<ProvinceSeasonalityComparisonRow>>(emptyList()) }
    var seasonalError by remember { mutableStateOf<String?>(null) }
    var productionPlanningError by remember { mutableStateOf<String?>(null) }
    var approvedProductionDemandPlans by remember { mutableStateOf<List<DemandPlanEntity>>(emptyList()) }
    var policyEditorTarget by remember { mutableStateOf<PlanningPolicyEditorTarget?>(null) }
    var confirmApproveProductionPlan by remember { mutableStateOf(false) }
    var reopenProductionPlan by remember { mutableStateOf(false) }

    LaunchedEffect(products) {
        if (selectedItemId == null || products.none { it.id == selectedItemId }) {
            selectedItemId = products.firstOrNull()?.id
        }
    }
    LaunchedEffect(policies) {
        if (selectedProvinceCode == null || policies.none { it.code == selectedProvinceCode }) {
            selectedProvinceCode = policies.firstOrNull()?.code
        }
    }

    val seasonalityFlow = remember(selectedItemId, selectedProvinceCode) {
        val itemId = selectedItemId
        val province = selectedProvinceCode
        if (itemId == null || province == null) flowOf(emptyList())
        else container.planningService.observeSeasonality(itemId, province)
    }
    val seasonality by seasonalityFlow.collectAsState(initial = emptyList())
    val seasonalityByMonth = remember(seasonality) { seasonality.associateBy { it.month } }

    val demandPlanFlow = remember(forecast?.itemId, forecast?.provinceCode, forecast?.forecastYear, forecast?.forecastMonth) {
        val f = forecast
        if (f == null) flowOf<DemandPlanEntity?>(null)
        else container.planningService.observeDemandPlan(f.itemId, f.provinceCode, f.forecastYear, f.forecastMonth)
    }
    val demandPlan by demandPlanFlow.collectAsState(initial = null)

    val weeklyBudgetFlow = remember(demandPlan?.id) {
        val planId = demandPlan?.id
        if (planId == null || planId == 0L) flowOf(emptyList())
        else container.planningService.observeWeeklySalesBudget(planId)
    }
    val weeklyBudget by weeklyBudgetFlow.collectAsState(initial = emptyList())

    val finishedPolicyFlow = remember(selectedItemId) {
        selectedItemId?.let { container.planningService.observeInventoryPlanningPolicy(it) }
            ?: flowOf<InventoryPlanningPolicyEntity?>(null)
    }
    val finishedPolicy by finishedPolicyFlow.collectAsState(initial = null)

    val productionPlanFlow = remember(selectedItemId, forecast?.forecastYear, forecast?.forecastMonth) {
        val itemId = selectedItemId
        val f = forecast
        if (itemId == null || f == null) flowOf<ProductionPlanEntity?>(null)
        else container.planningService.observeProductionPlan(itemId, f.forecastYear, f.forecastMonth)
    }
    val productionPlan by productionPlanFlow.collectAsState(initial = null)

    val productionMaterialFlow = remember(productionPlan?.id) {
        val planId = productionPlan?.id
        if (planId == null || planId == 0L) flowOf(emptyList())
        else container.planningService.observeProductionPlanMaterials(planId)
    }
    val productionPlanMaterials by productionMaterialFlow.collectAsState(initial = emptyList())

    LaunchedEffect(demandPlan?.id, demandPlan?.planYear, demandPlan?.planMonth, refreshKey) {
        val plan = demandPlan
        weeklyActual = if (plan == null || plan.id == 0L) emptyList() else try {
            container.planningService.weeklySalesActual(plan)
        } catch (_: Throwable) {
            emptyList()
        }
    }

    LaunchedEffect(selectedItemId, selectedProvinceCode, refreshKey) {
        val itemId = selectedItemId
        val province = selectedProvinceCode
        if (itemId == null || province == null) {
            forecast = null
            forecastError = null
            return@LaunchedEffect
        }
        try {
            forecast = container.planningService.forecastNextMonth(itemId, province)
            forecastError = null
        } catch (t: Throwable) {
            forecast = null
            forecastError = t.message ?: "تعذر حساب التوقع"
        }
    }

    LaunchedEffect(selectedItemId, selectedProvinceCode, policies, refreshKey) {
        val itemId = selectedItemId
        val province = selectedProvinceCode
        if (itemId == null || province == null) {
            seasonalAnalysis = null
            provinceSeasonalityRows = emptyList()
            seasonalError = null
        } else {
            try {
                seasonalAnalysis = container.planningService.seasonalDemandAnalysis(itemId, province)
                provinceSeasonalityRows = container.planningService.provinceSeasonalityComparison(
                    itemId, policies.map { it.code }
                )
                seasonalError = null
            } catch (t: Throwable) {
                seasonalAnalysis = null
                provinceSeasonalityRows = emptyList()
                seasonalError = t.message ?: "تعذر حساب تحليل الصيف والشتاء"
            }
        }
    }

    LaunchedEffect(selectedItemId, forecast?.forecastYear, forecast?.forecastMonth, refreshKey) {
        val itemId = selectedItemId
        val f = forecast
        if (itemId == null || f == null) {
            approvedProductionDemandPlans = emptyList()
            productionPlanningError = null
        } else {
            try {
                approvedProductionDemandPlans = container.planningService.approvedDemandPlans(itemId, f.forecastYear, f.forecastMonth)
                productionPlanningError = null
            } catch (t: Throwable) {
                approvedProductionDemandPlans = emptyList()
                productionPlanningError = t.message ?: "تعذر تحميل خطط الطلب المعتمدة للإنتاج"
            }
        }
    }

    val selectedItem = products.firstOrNull { it.id == selectedItemId }
    val selectedPolicy = policies.firstOrNull { it.code == selectedProvinceCode }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FushSectionHeader(
                title = "التخطيط والموسمية",
                subtitle = "الموازنة والموسمية وتحويل خطط الطلب المعتمدة إلى خطة إنتاج واحتياجات مواد ومخزون أمان ونقاط إعادة طلب.",
            )
        }

        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("نطاق التخطيط", style = MaterialTheme.typography.titleMedium)

                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { itemMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedItem?.let { "${it.code} — ${it.nameAr}" } ?: "اختر المنتج النهائي")
                        }
                        DropdownMenu(expanded = itemMenuOpen, onDismissRequest = { itemMenuOpen = false }) {
                            products.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text("${item.code} — ${item.nameAr}") },
                                    onClick = {
                                        selectedItemId = item.id
                                        itemMenuOpen = false
                                        message = null
                                    }
                                )
                            }
                        }
                    }

                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { provinceMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedPolicy?.nameAr ?: "اختر المحافظة")
                        }
                        DropdownMenu(expanded = provinceMenuOpen, onDismissRequest = { provinceMenuOpen = false }) {
                            policies.forEach { policy ->
                                DropdownMenuItem(
                                    text = { Text(policy.nameAr) },
                                    onClick = {
                                        selectedProvinceCode = policy.code
                                        provinceMenuOpen = false
                                        message = null
                                    }
                                )
                            }
                        }
                    }
                    FushOperationMessage(message, onConsumed = { message = null })
                    forecastError?.let { FushNotice(it, tone = FushStatusTone.Danger) }
                    seasonalError?.let { FushNotice(it, tone = FushStatusTone.Danger) }
                    productionPlanningError?.let { FushNotice(it, tone = FushStatusTone.Danger) }
                }
            }
        }

        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("توقع الشهر القادم", style = MaterialTheme.typography.titleMedium)
                    val f = forecast
                    if (f == null) {
                        FushInlineState("لا توجد نتيجة بعد. اختر المنتج والمحافظة وانتظر اكتمال حساب التوقع.")
                    } else {
                        Text("${planningMonthNames[f.forecastMonth - 1]} ${f.forecastYear}", style = MaterialTheme.typography.titleLarge)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PlanningMetric("خط الأساس 12 شهر", fmtPlanQty(f.baselineQtyBase), Modifier.weight(1f))
                            PlanningMetric("معامل الموسمية", fmtPlanFactor(f.seasonFactor), Modifier.weight(1f))
                            PlanningMetric("التوقع", fmtPlanQty(f.forecastQtyBase), Modifier.weight(1f))
                        }
                        Text(
                            "المعادلة: متوسط صافي الطلب لآخر 12 شهرًا × معامل الشهر. الأشهر بلا مبيعات تُحسب بصفر. القيم غير المعدلة تستخدم معاملًا محايدًا 1.00.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("تحليل الصيف والشتاء", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "التعريف التشغيلي الافتراضي للتحليل: الصيف من أبريل إلى سبتمبر، والشتاء من أكتوبر إلى مارس. لا يفرض النظام نسبًا جاهزة؛ معامل كل شهر ومحافظة يبقى قابلًا للاعتماد من واقع السوق.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    val a = seasonalAnalysis
                    if (a == null) {
                        FushInlineState("لا توجد نتيجة موسمية بعد لهذا الاختيار.")
                    } else {
                        val actualLift = com.fush.erp.domain.PlanningMath.relativeDifferencePct(a.summerActualAvgQtyBase, a.winterActualAvgQtyBase)
                        val forecastLift = com.fush.erp.domain.PlanningMath.relativeDifferencePct(a.summerForecastMonthlyQtyBase, a.winterForecastMonthlyQtyBase)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PlanningMetric("فعلي صيفي/شهر", fmtPlanQty(a.summerActualAvgQtyBase), Modifier.weight(1f))
                            PlanningMetric("فعلي شتوي/شهر", fmtPlanQty(a.winterActualAvgQtyBase), Modifier.weight(1f))
                            PlanningMetric("فرق الصيف", actualLift?.let { fmtPlanSignedPct(it) } ?: "—", Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PlanningMetric("معامل الصيف", fmtPlanFactor(a.summerFactorAvg), Modifier.weight(1f))
                            PlanningMetric("معامل الشتاء", fmtPlanFactor(a.winterFactorAvg), Modifier.weight(1f))
                            PlanningMetric("فرق التوقع", forecastLift?.let { fmtPlanSignedPct(it) } ?: "—", Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PlanningMetric("توقع صيفي/شهر", fmtPlanQty(a.summerForecastMonthlyQtyBase), Modifier.weight(1f))
                            PlanningMetric("توقع شتوي/شهر", fmtPlanQty(a.winterForecastMonthlyQtyBase), Modifier.weight(1f))
                        }
                        Text(
                            "الفعلي يعتمد على آخر ${a.historyMonths} شهرًا مع إدخال الأشهر بلا مبيعات بصفر، والقيم السالبة لصافي الطلب تعامل بصفر لأغراض التخطيط. التوقع الموسمي = خط أساس آخر 12 شهرًا × متوسط معاملات أشهر الموسم.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("مقارنة المحافظات الموسمية", style = MaterialTheme.typography.titleMedium)
                    if (provinceSeasonalityRows.isEmpty()) {
                        FushInlineState("لا توجد بيانات مقارنة بين المحافظات بعد.")
                    } else {
                        val names = policies.associate { it.code to it.nameAr }
                        Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                            SeasonalComparisonRow(
                                province = "المحافظة", summerFactor = "معامل صيف", winterFactor = "معامل شتاء",
                                summerForecast = "توقع صيف", winterForecast = "توقع شتاء",
                                summerActual = "فعلي صيف", winterActual = "فعلي شتاء", header = true
                            )
                            provinceSeasonalityRows.forEach { row ->
                                SeasonalComparisonRow(
                                    province = names[row.provinceCode] ?: row.provinceCode,
                                    summerFactor = fmtPlanFactor(row.summerFactorAvg),
                                    winterFactor = fmtPlanFactor(row.winterFactorAvg),
                                    summerForecast = fmtPlanQty(row.summerForecastMonthlyQtyBase),
                                    winterForecast = fmtPlanQty(row.winterForecastMonthlyQtyBase),
                                    summerActual = fmtPlanQty(row.summerActualAvgQtyBase),
                                    winterActual = fmtPlanQty(row.winterActualAvgQtyBase)
                                )
                            }
                        }
                        Text("المقارنة تستخدم نفس المنتج المختار وتطبق معاملات كل محافظة بصورة مستقلة.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("خطة الطلب — الاعتماد والتعديل اليدوي", style = MaterialTheme.typography.titleMedium)
                    val f = forecast
                    val plan = demandPlan
                    if (f == null) {
                        Text("اختر منتجًا ومحافظة حتى يتم حساب توقع الشهر القادم.")
                    } else {
                        val statusText = when (plan?.status) {
                            "APPROVED" -> "معتمدة"
                            "DRAFT" -> "مسودة"
                            else -> "غير محفوظة"
                        }
                        Text("${planningMonthNames[f.forecastMonth - 1]} ${f.forecastYear} • الحالة: $statusText", style = MaterialTheme.typography.titleLarge)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PlanningMetric("توقع النظام", fmtPlanQty(plan?.systemForecastQtyBase ?: f.forecastQtyBase), Modifier.weight(1f))
                            PlanningMetric("الخطة", fmtPlanQty(plan?.plannedQtyBase ?: f.forecastQtyBase), Modifier.weight(1f))
                            PlanningMetric("التعديل", fmtPlanSignedQty(plan?.manualAdjustmentQtyBase ?: 0.0), Modifier.weight(1f))
                        }
                        plan?.let { saved ->
                            Text("الإصدار: ${saved.revision} • التوقع المحفوظ يعتمد على خط أساس ${fmtPlanQty(saved.baselineQtyBase)} ومعامل ${fmtPlanFactor(saved.seasonFactor)}.", style = MaterialTheme.typography.bodySmall)
                            saved.note.takeIf { it.isNotBlank() }?.let { Text("ملاحظة الخطة: $it", style = MaterialTheme.typography.bodySmall) }
                            if (kotlin.math.abs(saved.systemForecastQtyBase - f.forecastQtyBase) > 0.001) {
                                Text("تنبيه: التوقع الحالي (${fmtPlanQty(f.forecastQtyBase)}) تغير عن التوقع المحفوظ في هذه الخطة (${fmtPlanQty(saved.systemForecastQtyBase)}). لا تتغير الخطة المعتمدة تلقائيًا.", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (plan?.status == "APPROVED") {
                            Text("الخطة المعتمدة مقفلة ضد التعديل. لإجراء تغيير يجب إعادة فتحها وكتابة السبب.", style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = { reopenDemandPlan = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("إعادة فتح الخطة للتعديل")
                            }
                        } else {
                            Button(onClick = { editDemandPlan = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (plan == null) "إنشاء مسودة خطة الطلب" else "تعديل مسودة خطة الطلب")
                            }
                            Button(
                                onClick = { confirmApprovePlan = true },
                                enabled = plan?.status == "DRAFT",
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("اعتماد خطة الطلب") }
                            if (plan == null) Text("احفظ الخطة كمسودة أولًا ثم اعتمدها.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("موازنة المبيعات الشهرية والأسبوعية", style = MaterialTheme.typography.titleMedium)
                    val plan = demandPlan
                    if (plan == null) {
                        Text("أنشئ خطة الطلب واحفظها أولًا لبدء موازنة المبيعات.")
                    } else if (plan.status != "APPROVED") {
                        Text("اعتمد خطة الطلب أولًا. الموازنة الأسبوعية ترتبط فقط بخطة طلب معتمدة حتى لا تتغير الأهداف دون رقابة.")
                    } else {
                        val actualByWeek = weeklyActual.associateBy { it.weekNo }
                        val monthlyActual = weeklyActual.sumOf { it.netQtyBase }
                        val monthlyNetValue = weeklyActual.sumOf { it.netValueBase }
                        val monthlyPct = com.fush.erp.domain.PlanningMath.achievementPct(monthlyActual, plan.plannedQtyBase)
                        Text("${planningMonthNames[plan.planMonth - 1]} ${plan.planYear} • ${selectedPolicy?.nameAr.orEmpty()}", style = MaterialTheme.typography.titleLarge)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PlanningMetric("الهدف الشهري", fmtPlanQty(plan.plannedQtyBase), Modifier.weight(1f))
                            PlanningMetric("الفعلي", fmtPlanQty(monthlyActual), Modifier.weight(1f))
                            PlanningMetric("الإنجاز", fmtPlanPct(monthlyPct), Modifier.weight(1f))
                        }
                        Text("قيمة صافي خطوط المبيعات الفعلية: ${fmtPlanMoney(monthlyNetValue)} بالعملة الأساسية", style = MaterialTheme.typography.bodySmall)

                        if (weeklyBudget.isEmpty()) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        try {
                                            container.planningService.autoDistributeWeeklySalesBudget(plan, user.id)
                                            message = "تم إنشاء الموازنة الأسبوعية تلقائيًا حسب عدد أيام الشهر."
                                        } catch (t: Throwable) {
                                            message = t.message ?: "تعذر إنشاء الموازنة الأسبوعية"
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("إنشاء الموازنة الأسبوعية تلقائيًا") }
                        } else {
                            val days = YearMonth.of(plan.planYear, plan.planMonth).lengthOfMonth()
                            val activeWeeks = com.fush.erp.domain.PlanningMath.activeBudgetWeeks(days)
                            val budgetByWeek = weeklyBudget.associateBy { it.weekNo }
                            val budgetTotal = weeklyBudget.sumOf { it.plannedQtyBase }
                            if (kotlin.math.abs(budgetTotal - plan.plannedQtyBase) > 0.1) {
                                Text(
                                    "تنبيه: مجموع الأهداف الأسبوعية (${fmtPlanQty(budgetTotal)}) لا يساوي الهدف الشهري الحالي (${fmtPlanQty(plan.plannedQtyBase)}). أعد التوزيع أو عدّل الموازنة.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            (1..activeWeeks).forEach { week ->
                                val target = budgetByWeek[week]?.plannedQtyBase ?: 0.0
                                val actual = actualByWeek[week]
                                val pct = com.fush.erp.domain.PlanningMath.achievementPct(actual?.netQtyBase ?: 0.0, target)
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(weekBudgetLabel(week, days), style = MaterialTheme.typography.titleSmall)
                                        Text("المخطط: ${fmtPlanQty(target)} • الفعلي: ${fmtPlanQty(actual?.netQtyBase ?: 0.0)} • الإنجاز: ${fmtPlanPct(pct)}")
                                        Text("مبيعات: ${fmtPlanQty(actual?.soldQtyBase ?: 0.0)} • مرتجعات: ${fmtPlanQty(actual?.returnedQtyBase ?: 0.0)} • صافي القيمة: ${fmtPlanMoney(actual?.netValueBase ?: 0.0)}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            Button(onClick = { editWeeklyBudget = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("تعديل الموازنة الأسبوعية")
                            }
                            OutlinedButton(onClick = { confirmRedistributeBudget = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("إعادة توزيع تلقائي")
                            }
                            OutlinedButton(onClick = { refreshKey++ }, modifier = Modifier.fillMaxWidth()) {
                                Text("تحديث المبيعات الفعلية")
                            }
                        }
                    }
                }
            }
        }

        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("خطة الإنتاج والمواد والمخزون", style = MaterialTheme.typography.titleMedium)
                    val f = forecast
                    if (selectedItem == null || f == null) {
                        Text("اختر منتجًا نهائيًا حتى يتم إعداد خطة الإنتاج.")
                    } else {
                        val approvedTotal = approvedProductionDemandPlans.sumOf { it.plannedQtyBase }
                        val approvedCodes = approvedProductionDemandPlans.map { it.provinceCode }.toSet()
                        val missing = policies.filter { it.code !in approvedCodes }.map { it.nameAr }
                        Text("${planningMonthNames[f.forecastMonth - 1]} ${f.forecastYear} • تخطيط إجمالي لجميع المحافظات", style = MaterialTheme.typography.titleLarge)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PlanningMetric("خطط معتمدة", approvedProductionDemandPlans.size.toString(), Modifier.weight(1f))
                            PlanningMetric("إجمالي الطلب", fmtPlanQty(approvedTotal), Modifier.weight(1f))
                            PlanningMetric("محافظات غير معتمدة", missing.size.toString(), Modifier.weight(1f))
                        }
                        if (missing.isNotEmpty()) {
                            Text("تنبيه: خطة الإنتاج لا تشمل المحافظات التي لم تعتمد خطة الطلب بعد: ${missing.joinToString("، ")}", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PlanningMetric("أمان المنتج/يوم", fmtPlanQty(finishedPolicy?.safetyStockDays ?: 0.0), Modifier.weight(1f))
                            PlanningMetric("زمن التجهيز/يوم", fmtPlanQty(finishedPolicy?.leadTimeDays ?: 0.0), Modifier.weight(1f))
                        }
                        OutlinedButton(
                            onClick = {
                                policyEditorTarget = PlanningPolicyEditorTarget(
                                    itemId = selectedItem.id,
                                    label = "${selectedItem.code} — ${selectedItem.nameAr}",
                                    safetyStockDays = finishedPolicy?.safetyStockDays ?: 0.0,
                                    leadTimeDays = finishedPolicy?.leadTimeDays ?: 0.0,
                                    note = finishedPolicy?.note.orEmpty()
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("تعديل سياسة مخزون المنتج النهائي") }

                        val pp = productionPlan
                        if (pp == null) {
                            Text("لم يتم توليد خطة إنتاج لهذا الشهر بعد. يعتمد التوليد فقط على خطط الطلب المعتمدة وعلى الرصيد القابل للاستخدام.")
                            Button(
                                onClick = {
                                    scope.launch {
                                        try {
                                            container.planningService.generateProductionPlan(selectedItem.id, f.forecastYear, f.forecastMonth, user.id)
                                            refreshKey++
                                            message = "تم توليد خطة الإنتاج والمواد للشهر القادم."
                                        } catch (t: Throwable) {
                                            productionPlanningError = t.message ?: "تعذر توليد خطة الإنتاج"
                                        }
                                    }
                                },
                                enabled = approvedProductionDemandPlans.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("توليد خطة الإنتاج والمواد") }
                        } else {
                            val statusText = if (pp.status == "APPROVED") "معتمدة" else "مسودة"
                            Text("خطة الإنتاج • الحالة: $statusText • الإصدار ${pp.revision}", style = MaterialTheme.typography.titleMedium)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PlanningMetric("طلب معتمد", fmtPlanQty(pp.approvedDemandQtyBase), Modifier.weight(1f))
                                PlanningMetric("مخزون FG", fmtPlanQty(pp.finishedStockQtyBase), Modifier.weight(1f))
                                PlanningMetric("مخزون أمان FG", fmtPlanQty(pp.finishedSafetyStockQtyBase), Modifier.weight(1f))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PlanningMetric("صافي احتياج الإنتاج", fmtPlanQty(pp.netProductionNeedQtyBase), Modifier.weight(1f))
                                PlanningMetric("عدد الطرحات", pp.plannedBatchCount.toString(), Modifier.weight(1f))
                                PlanningMetric("الإنتاج المخطط", fmtPlanQty(pp.plannedOutputQtyBase), Modifier.weight(1f))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PlanningMetric("نقطة إعادة تشغيل", fmtPlanQty(pp.finishedReorderPointQtyBase), Modifier.weight(1f))
                                PlanningMetric("رصيد نهاية متوقع", fmtPlanQty(pp.projectedEndingFinishedQtyBase), Modifier.weight(1f))
                            }
                            Text("الوصفة: v${pp.recipeVersionNo} • الناتج القياسي للطرحة: ${fmtPlanQty(pp.recipeTargetOutputQtyBase)}. الأرصدة الظاهرة هي لقطة وقت آخر توليد للخطة.", style = MaterialTheme.typography.bodySmall)

                            if (productionPlanMaterials.isNotEmpty()) {
                                Text("احتياجات المواد", style = MaterialTheme.typography.titleMedium)
                                Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                    ProductionMaterialPlanningRow(
                                        item = "المادة", required = "مطلوب", stock = "متاح RM", safety = "أمان",
                                        reorder = "إعادة الطلب", purchase = "شراء مقترح", ending = "نهاية", policy = "السياسة", header = true
                                    )
                                    productionPlanMaterials.forEach { row ->
                                        ProductionMaterialPlanningRow(
                                            item = "${row.code} — ${row.itemName}",
                                            required = fmtPlanQty(row.requiredQtyBase),
                                            stock = fmtPlanQty(row.currentStockQtyBase),
                                            safety = fmtPlanQty(row.safetyStockQtyBase),
                                            reorder = fmtPlanQty(row.reorderPointQtyBase),
                                            purchase = fmtPlanQty(row.suggestedPurchaseQtyBase),
                                            ending = fmtPlanQty(row.projectedEndingQtyBase),
                                            policy = "أمان ${fmtPlanQty(row.safetyStockDays)} / توريد ${fmtPlanQty(row.leadTimeDays)}",
                                            onEditPolicy = {
                                                policyEditorTarget = PlanningPolicyEditorTarget(
                                                    itemId = row.itemId,
                                                    label = "${row.code} — ${row.itemName}",
                                                    safetyStockDays = row.safetyStockDays,
                                                    leadTimeDays = row.leadTimeDays,
                                                    note = row.policyNote
                                                )
                                            }
                                        )
                                    }
                                }
                                Text("المتاح RM يستبعد المنتهي والمحجور ويخصم المحجوز لأوامر إنتاج مفتوحة. الشراء المقترح = احتياج الإنتاج + مخزون الأمان − المتاح. نقطة إعادة الطلب = استهلاك يومي × زمن التوريد + مخزون الأمان.", style = MaterialTheme.typography.bodySmall)
                            }

                            if (pp.status == "APPROVED") {
                                Text("الخطة المعتمدة مقفلة ضد إعادة الحساب حتى لا تتغير الطرحات والمواد دون توثيق.", style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(onClick = { reopenProductionPlan = true }, modifier = Modifier.fillMaxWidth()) { Text("إعادة فتح خطة الإنتاج") }
                            } else {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                container.planningService.generateProductionPlan(selectedItem.id, f.forecastYear, f.forecastMonth, user.id)
                                                refreshKey++
                                                message = "تم تحديث خطة الإنتاج وفق أحدث الطلب والمخزون والسياسات."
                                            } catch (t: Throwable) {
                                                productionPlanningError = t.message ?: "تعذر إعادة حساب الخطة"
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("إعادة حساب الخطة") }
                                Button(onClick = { confirmApproveProductionPlan = true }, modifier = Modifier.fillMaxWidth()) { Text("اعتماد خطة الإنتاج والمواد") }
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("معاملات الموسمية", style = MaterialTheme.typography.titleLarge)
            Text(
                "1.00 = محايد، أكبر من 1 = طلب أعلى، أقل من 1 = طلب أضعف. لم نفترض أشهرًا قوية أو ضعيفة مسبقًا؛ أنت تعتمدها من واقع السوق.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        items((1..12).toList()) { month ->
            val row = seasonalityByMonth[month]
            ElevatedCard(
                Modifier.fillMaxWidth().clickable(enabled = selectedItemId != null && selectedProvinceCode != null) {
                    editMonth = month
                }
            ) {
                ListItem(
                    headlineContent = { Text(planningMonthNames[month - 1]) },
                    supportingContent = {
                        val factor = row?.demandFactor ?: 1.0
                        val season = if (com.fush.erp.domain.PlanningMath.isSummerMonth(month)) "صيف" else "شتاء"
                        val level = when { factor > 1.0001 -> "طلب أعلى"; factor < 0.9999 -> "طلب أضعف"; else -> "محايد" }
                        Text("$season • $level • ${row?.note?.takeIf { it.isNotBlank() } ?: "بدون ملاحظة — اضغط للتعديل"}")
                    },
                    trailingContent = { Text(fmtPlanFactor(row?.demandFactor ?: 1.0)) }
                )
            }
        }

        item { Text("صافي الطلب التاريخي — آخر 12 شهرًا", style = MaterialTheme.typography.titleLarge) }

        val history = forecast?.history.orEmpty().reversed()
        if (history.isEmpty()) {
            item { FushEmptyState("لا توجد بيانات تاريخية", "لا تتوفر حركات طلب سابقة كافية لهذا المنتج والمحافظة ضمن نافذة التحليل الحالية.") }
        } else {
            items(history) { row ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${planningMonthNames[row.month - 1]} ${row.year}", style = MaterialTheme.typography.titleMedium)
                        Text("مبيعات: ${fmtPlanQty(row.soldQtyBase)} • مرتجعات: ${fmtPlanQty(row.returnedQtyBase)} • صافي: ${fmtPlanQty(row.netQtyBase)}")
                    }
                }
            }
        }
    }

    policyEditorTarget?.let { target ->
        InventoryPlanningPolicyDialog(
            target = target,
            onDismiss = { policyEditorTarget = null },
            onSave = { safetyDays, leadDays, note ->
                scope.launch {
                    try {
                        container.planningService.saveInventoryPlanningPolicy(target.itemId, safetyDays, leadDays, note, user.id)
                        policyEditorTarget = null
                        val f = forecast
                        val pp = productionPlan
                        if (selectedItemId != null && f != null && pp?.status != "APPROVED" && approvedProductionDemandPlans.isNotEmpty()) {
                            container.planningService.generateProductionPlan(selectedItemId!!, f.forecastYear, f.forecastMonth, user.id)
                        }
                        refreshKey++
                        message = "تم حفظ سياسة المخزون وتحديث الحسابات المتاحة."
                    } catch (t: Throwable) {
                        productionPlanningError = t.message ?: "تعذر حفظ سياسة المخزون"
                    }
                }
            }
        )
    }

    if (confirmApproveProductionPlan) {
        AlertDialog(
            onDismissRequest = { confirmApproveProductionPlan = false },
            title = { Text("اعتماد خطة الإنتاج والمواد") },
            text = { Text("سيتم تثبيت عدد الطرحات واحتياجات المواد الحالية. أي تغيير لاحق يتطلب إعادة فتح الخطة مع سبب موثق.") },
            confirmButton = {
                Button(onClick = {
                    val itemId = selectedItemId
                    val f = forecast
                    if (itemId != null && f != null) {
                        scope.launch {
                            try {
                                container.planningService.approveProductionPlan(itemId, f.forecastYear, f.forecastMonth, user.id)
                                confirmApproveProductionPlan = false
                                refreshKey++
                                message = "تم اعتماد خطة الإنتاج والمواد."
                            } catch (t: Throwable) {
                                productionPlanningError = t.message ?: "تعذر اعتماد خطة الإنتاج"
                            }
                        }
                    }
                }) { Text("اعتماد") }
            },
            dismissButton = { TextButton(onClick = { confirmApproveProductionPlan = false }) { Text("إلغاء") } }
        )
    }

    if (reopenProductionPlan) {
        ReopenProductionPlanDialog(
            onDismiss = { reopenProductionPlan = false },
            onConfirm = { reason ->
                val itemId = selectedItemId
                val f = forecast
                if (itemId != null && f != null) {
                    scope.launch {
                        try {
                            container.planningService.reopenProductionPlan(itemId, f.forecastYear, f.forecastMonth, reason, user.id)
                            reopenProductionPlan = false
                            refreshKey++
                            message = "تمت إعادة فتح خطة الإنتاج. أعد الحساب بعد أي تعديل."
                        } catch (t: Throwable) {
                            productionPlanningError = t.message ?: "تعذر إعادة فتح خطة الإنتاج"
                        }
                    }
                }
            }
        )
    }

    val editingMonth = editMonth
    if (editingMonth != null && selectedItemId != null && selectedProvinceCode != null) {
        SeasonalityEditDialog(
            month = editingMonth,
            current = seasonalityByMonth[editingMonth],
            onDismiss = { editMonth = null },
            onSave = { factor, note ->
                val itemId = requireNotNull(selectedItemId)
                val province = requireNotNull(selectedProvinceCode)
                scope.launch {
                    try {
                        container.planningService.saveSeasonality(
                            itemId = itemId,
                            provinceCode = province,
                            month = editingMonth,
                            factor = factor,
                            note = note,
                            updatedBy = user.id
                        )
                        editMonth = null
                        refreshKey++
                        message = "تم حفظ معامل ${planningMonthNames[editingMonth - 1]}."
                    } catch (t: Throwable) {
                        message = t.message ?: "تعذر حفظ معامل الموسمية"
                    }
                }
            }
        )
    }

    val planForecast = forecast
    if (editDemandPlan && planForecast != null) {
        DemandPlanEditDialog(
            forecast = planForecast,
            current = demandPlan,
            onDismiss = { editDemandPlan = false },
            onSave = { plannedQty, note ->
                scope.launch {
                    try {
                        container.planningService.saveDemandPlanDraft(planForecast, plannedQty, note, user.id)
                        editDemandPlan = false
                        message = "تم حفظ خطة الطلب كمسودة."
                    } catch (t: Throwable) {
                        message = t.message ?: "تعذر حفظ خطة الطلب"
                    }
                }
            }
        )
    }

    if (confirmApprovePlan && demandPlan?.status == "DRAFT") {
        AlertDialog(
            onDismissRequest = { confirmApprovePlan = false },
            title = { Text("اعتماد خطة الطلب") },
            text = { Text("بعد الاعتماد ستصبح الخطة مقفلة ضد التعديل حتى إعادة فتحها بسبب موثق. هل تريد الاعتماد؟") },
            confirmButton = {
                Button(onClick = {
                    val row = demandPlan ?: return@Button
                    scope.launch {
                        try {
                            container.planningService.approveDemandPlan(row.itemId, row.provinceCode, row.planYear, row.planMonth, user.id)
                            confirmApprovePlan = false
                            message = "تم اعتماد خطة الطلب."
                        } catch (t: Throwable) {
                            message = t.message ?: "تعذر اعتماد الخطة"
                        }
                    }
                }) { Text("اعتماد") }
            },
            dismissButton = { TextButton(onClick = { confirmApprovePlan = false }) { Text("إلغاء") } }
        )
    }

    if (reopenDemandPlan && demandPlan?.status == "APPROVED") {
        ReopenDemandPlanDialog(
            onDismiss = { reopenDemandPlan = false },
            onConfirm = { reason ->
                val row = demandPlan ?: return@ReopenDemandPlanDialog
                scope.launch {
                    try {
                        container.planningService.reopenDemandPlan(row.itemId, row.provinceCode, row.planYear, row.planMonth, reason, user.id)
                        reopenDemandPlan = false
                        message = "تمت إعادة فتح الخطة كمسودة جديدة للإصدار ${row.revision + 1}."
                    } catch (t: Throwable) {
                        message = t.message ?: "تعذر إعادة فتح الخطة"
                    }
                }
            }
        )
    }

    if (editWeeklyBudget && demandPlan?.status == "APPROVED") {
        val plan = requireNotNull(demandPlan)
        WeeklySalesBudgetDialog(
            plan = plan,
            current = weeklyBudget,
            onDismiss = { editWeeklyBudget = false },
            onSave = { targets, note ->
                scope.launch {
                    try {
                        container.planningService.saveWeeklySalesBudget(plan, targets, note, user.id)
                        editWeeklyBudget = false
                        message = "تم حفظ الموازنة الأسبوعية."
                    } catch (t: Throwable) {
                        message = t.message ?: "تعذر حفظ الموازنة الأسبوعية"
                    }
                }
            }
        )
    }

    if (confirmRedistributeBudget && demandPlan?.status == "APPROVED") {
        AlertDialog(
            onDismissRequest = { confirmRedistributeBudget = false },
            title = { Text("إعادة توزيع الموازنة الأسبوعية") },
            text = { Text("سيتم استبدال الأهداف الأسبوعية الحالية بتوزيع تلقائي حسب عدد أيام الشهر، مع بقاء الهدف الشهري المعتمد دون تغيير.") },
            confirmButton = {
                Button(onClick = {
                    val plan = demandPlan ?: return@Button
                    scope.launch {
                        try {
                            container.planningService.autoDistributeWeeklySalesBudget(plan, user.id)
                            confirmRedistributeBudget = false
                            message = "تمت إعادة توزيع الموازنة الأسبوعية."
                        } catch (t: Throwable) {
                            message = t.message ?: "تعذر إعادة التوزيع"
                        }
                    }
                }) { Text("إعادة توزيع") }
            },
            dismissButton = { TextButton(onClick = { confirmRedistributeBudget = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun WeeklySalesBudgetDialog(
    plan: DemandPlanEntity,
    current: List<SalesBudgetWeekEntity>,
    onDismiss: () -> Unit,
    onSave: (List<Double>, String) -> Unit
) {
    val days = remember(plan.planYear, plan.planMonth) { YearMonth.of(plan.planYear, plan.planMonth).lengthOfMonth() }
    val weeks = remember(days) { com.fush.erp.domain.PlanningMath.activeBudgetWeeks(days) }
    val defaults = remember(plan.id, current) {
        if (current.isEmpty()) com.fush.erp.domain.PlanningMath.distributeMonthlyTarget(plan.plannedQtyBase, days)
        else (1..weeks).map { week -> current.firstOrNull { it.weekNo == week }?.plannedQtyBase ?: 0.0 }
    }
    val texts = remember(plan.id, current) {
        mutableStateListOf(*defaults.map { String.format(Locale.US, "%.1f", it) }.toTypedArray())
    }
    var note by remember(plan.id, current) { mutableStateOf(current.firstOrNull()?.note.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    val parsed = texts.map { it.trim().replace(',', '.').toDoubleOrNull() }
    val total = parsed.filterNotNull().sum()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("الموازنة الأسبوعية — ${planningMonthNames[plan.planMonth - 1]} ${plan.planYear}") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text("الهدف الشهري المعتمد: ${fmtPlanQty(plan.plannedQtyBase)}")
                    Text("مجموع الأسابيع الآن: ${fmtPlanQty(total)}", color = if (kotlin.math.abs(total - plan.plannedQtyBase) <= 0.1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }
                items((1..weeks).toList()) { week ->
                    OutlinedTextField(
                        value = texts[week - 1],
                        onValueChange = { texts[week - 1] = it },
                        label = { Text(weekBudgetLabel(week, days)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("ملاحظة الموازنة") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val values = texts.map { it.trim().replace(',', '.').toDoubleOrNull() }
                if (values.any { it == null || !it.isFinite() || it < 0.0 }) {
                    error = "أدخل كميات أسبوعية صحيحة غير سالبة."
                } else {
                    val clean = values.map { it!! }
                    try {
                        com.fush.erp.domain.PlanningMath.validateWeeklyBudget(plan.plannedQtyBase, clean)
                        onSave(clean, note)
                    } catch (t: Throwable) {
                        error = t.message ?: "مجموع الأسابيع يجب أن يساوي الهدف الشهري."
                    }
                }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun DemandPlanEditDialog(
    forecast: DemandForecastSnapshot,
    current: DemandPlanEntity?,
    onDismiss: () -> Unit,
    onSave: (Double, String) -> Unit
) {
    var plannedText by remember(forecast.forecastYear, forecast.forecastMonth, current?.id, current?.revision) {
        mutableStateOf(String.format(Locale.US, "%.1f", current?.plannedQtyBase ?: forecast.forecastQtyBase))
    }
    var note by remember(forecast.forecastYear, forecast.forecastMonth, current?.id, current?.revision) { mutableStateOf(current?.note.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    val planned = plannedText.trim().replace(',', '.').toDoubleOrNull()
    val adjustment = planned?.minus(forecast.forecastQtyBase)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("خطة طلب ${planningMonthNames[forecast.forecastMonth - 1]} ${forecast.forecastYear}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("توقع النظام الحالي: ${fmtPlanQty(forecast.forecastQtyBase)}")
                OutlinedTextField(
                    value = plannedText,
                    onValueChange = { plannedText = it },
                    label = { Text("الكمية المخططة المعتمدة داخليًا") },
                    supportingText = { Text("يمكن أن تساوي توقع النظام أو تعدلها حسب السوق والمخزون والقرارات الإدارية.") },
                    singleLine = true
                )
                adjustment?.let { Text("فرق التعديل عن توقع النظام: ${fmtPlanSignedQty(it)}") }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("سبب / ملاحظة التعديل") },
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val qty = plannedText.trim().replace(',', '.').toDoubleOrNull()
                if (qty == null || !qty.isFinite() || qty < 0.0) {
                    error = "أدخل كمية صحيحة غير سالبة."
                } else if (kotlin.math.abs(qty - forecast.forecastQtyBase) > 0.001 && note.trim().isBlank()) {
                    error = "اكتب سبب التعديل عندما تختلف الخطة عن توقع النظام."
                } else {
                    onSave(qty, note)
                }
            }) { Text("حفظ مسودة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun ReopenDemandPlanDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إعادة فتح الخطة المعتمدة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("سيتم إنشاء إصدار جديد قابل للتعديل مع تسجيل السبب والمستخدم في سجل التدقيق.")
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("سبب إعادة الفتح") },
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (reason.trim().isBlank()) error = "سبب إعادة الفتح مطلوب." else onConfirm(reason.trim())
            }) { Text("إعادة فتح") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun SeasonalityEditDialog(
    month: Int,
    current: DemandSeasonalityEntity?,
    onDismiss: () -> Unit,
    onSave: (Double, String) -> Unit
) {
    var factorText by remember(month, current?.id) {
        mutableStateOf(String.format(Locale.US, "%.2f", current?.demandFactor ?: 1.0))
    }
    var note by remember(month, current?.id) { mutableStateOf(current?.note.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("موسمية ${planningMonthNames[month - 1]}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = factorText,
                    onValueChange = { factorText = it },
                    label = { Text("معامل الطلب") },
                    supportingText = { Text("مثال: 1.00 محايد، 1.20 أعلى 20%، 0.80 أقل 20%") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("ملاحظة / سبب الاعتماد") },
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val factor = factorText.trim().replace(',', '.').toDoubleOrNull()
                if (factor == null || factor !in 0.0..10.0) {
                    error = "أدخل معاملًا صحيحًا من 0 إلى 10."
                } else {
                    onSave(factor, note)
                }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun InventoryPlanningPolicyDialog(
    target: PlanningPolicyEditorTarget,
    onDismiss: () -> Unit,
    onSave: (Double, Double, String) -> Unit
) {
    var safetyText by remember(target.itemId, target.safetyStockDays) { mutableStateOf(String.format(Locale.US, "%.1f", target.safetyStockDays)) }
    var leadText by remember(target.itemId, target.leadTimeDays) { mutableStateOf(String.format(Locale.US, "%.1f", target.leadTimeDays)) }
    var note by remember(target.itemId, target.note) { mutableStateOf(target.note) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("سياسة المخزون — ${target.label}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("القيم الافتراضية صفر؛ لا يفرض النظام مخزون أمان أو زمن توريد من عنده.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(safetyText, { safetyText = it }, label = { Text("أيام مخزون الأمان") }, singleLine = true)
                OutlinedTextField(leadText, { leadText = it }, label = { Text("زمن التوريد / التجهيز بالأيام") }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("ملاحظة / أساس السياسة") }, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val safety = safetyText.trim().replace(',', '.').toDoubleOrNull()
                val lead = leadText.trim().replace(',', '.').toDoubleOrNull()
                if (safety == null || lead == null || !safety.isFinite() || !lead.isFinite() || safety < 0.0 || lead < 0.0) {
                    error = "أدخل أيامًا صحيحة غير سالبة."
                } else onSave(safety, lead, note)
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun ReopenProductionPlanDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إعادة فتح خطة الإنتاج") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("لن يعاد الحساب تلقائيًا حتى تضغط إعادة حساب الخطة بعد فتحها.")
                OutlinedTextField(reason, { reason = it }, label = { Text("سبب إعادة الفتح") }, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = { if (reason.trim().isBlank()) error = "السبب مطلوب." else onConfirm(reason.trim()) }) { Text("إعادة فتح") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun ProductionMaterialPlanningRow(
    item: String,
    required: String,
    stock: String,
    safety: String,
    reorder: String,
    purchase: String,
    ending: String,
    policy: String,
    header: Boolean = false,
    onEditPolicy: (() -> Unit)? = null
) {
    Row(Modifier.width(1040.dp).padding(vertical = 2.dp)) {
        val values = listOf(item, required, stock, safety, reorder, purchase, ending, policy)
        values.forEachIndexed { index, value ->
            Surface(tonalElevation = if (header) 2.dp else 0.dp, modifier = Modifier.width(if (index == 0) 230.dp else if (index == 7) 180.dp else 90.dp)) {
                Text(value, style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp))
            }
        }
        if (!header && onEditPolicy != null) {
            TextButton(onClick = onEditPolicy, modifier = Modifier.width(90.dp)) { Text("السياسة") }
        } else {
            Spacer(Modifier.width(90.dp))
        }
    }
}

@Composable
private fun SeasonalComparisonRow(
    province: String,
    summerFactor: String,
    winterFactor: String,
    summerForecast: String,
    winterForecast: String,
    summerActual: String,
    winterActual: String,
    header: Boolean = false
) {
    Row(Modifier.width(820.dp).padding(vertical = 2.dp)) {
        listOf(province, summerFactor, winterFactor, summerForecast, winterForecast, summerActual, winterActual).forEachIndexed { index, value ->
            Surface(
                tonalElevation = if (header) 2.dp else 0.dp,
                modifier = Modifier.width(if (index == 0) 150.dp else 110.dp)
            ) {
                Text(
                    value,
                    style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun PlanningMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(10.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun fmtPlanQty(value: Double): String = String.format(Locale.US, "%,.1f", value)
private fun fmtPlanFactor(value: Double): String = String.format(Locale.US, "%.2f", value)
private fun fmtPlanSignedQty(value: Double): String = String.format(Locale.US, "%+,.1f", value)
private fun fmtPlanPct(value: Double): String = String.format(Locale.US, "%.1f%%", value)
private fun fmtPlanSignedPct(value: Double): String = String.format(Locale.US, "%+.1f%%", value)
private fun fmtPlanMoney(value: Double): String = String.format(Locale.US, "%,.0f", value)

private fun weekBudgetLabel(week: Int, daysInMonth: Int): String {
    val start = (week - 1) * 7 + 1
    val end = if (week < 5) minOf(week * 7, daysInMonth) else daysInMonth
    return "الأسبوع $week ($start–$end)"
}
