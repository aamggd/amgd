package com.fush.erp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fush.erp.data.AppContainer
import com.fush.erp.data.entity.AssetEntity
import com.fush.erp.data.entity.MaintenanceWorkOrderEntity
import com.fush.erp.data.entity.MaintenancePlanEntity
import com.fush.erp.data.entity.UserEntity
import com.fush.erp.ui.FushMetricCard
import com.fush.erp.ui.FushSectionHeader
import com.fush.erp.ui.FushStatusPill
import com.fush.erp.ui.FushStatusTone
import com.fush.erp.ui.FushInlineState
import com.fush.erp.ui.FushEmptyState
import com.fush.erp.ui.FushDialogForm
import com.fush.erp.ui.FushOperationMessage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MaintenanceScreen(container: AppContainer, user: UserEntity, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val assets by container.db.maintenanceDao().observeAssets().collectAsState(initial = emptyList())
    val workOrders by container.db.maintenanceDao().observeOpenWorkOrders().collectAsState(initial = emptyList())
    val breakdowns by container.db.maintenanceDao().observeBreakdowns().collectAsState(initial = emptyList())
    val safetyIncidents by container.db.maintenanceDao().observeSafetyIncidents().collectAsState(initial = emptyList())
    val now = System.currentTimeMillis()
    val overduePlans by container.db.maintenanceDao().observeOverduePlanCount(now).collectAsState(initial = 0)
    val overdueChecks by container.db.maintenanceDao().observeOverdueEquipmentCheckCount(now).collectAsState(initial = 0)
    var showAsset by remember { mutableStateOf(false) }
    var showBreakdown by remember { mutableStateOf(false) }
    var showPreventive by remember { mutableStateOf(false) }
    var inspectAsset by remember { mutableStateOf<AssetEntity?>(null) }
    var calibrateAsset by remember { mutableStateOf<AssetEntity?>(null) }
    var closeWorkOrder by remember { mutableStateOf<MaintenanceWorkOrderEntity?>(null) }
    var showSafetyIncident by remember { mutableStateOf(false) }
    var showSafetyInspection by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("ALL") }
    var criticalityFilter by remember { mutableStateOf("ALL") }

    val assetById = remember(assets) { assets.associateBy { it.id } }
    val activeAssets = assets.count { it.status == "ACTIVE" }
    val unavailableAssets = assets.count { it.status != "ACTIVE" }
    val criticalAssets = assets.count { it.criticality == "CRITICAL" }
    val overdueWorkOrders = workOrders.count { it.dueAt != null && it.dueAt < now }
    val openSafety = safetyIncidents.count { it.status != "CLOSED" }
    val recurringBreakdowns = breakdowns.count { it.recurring && it.status != "CLOSED" }
    val totalDowntime = breakdowns.sumOf { it.downtimeMinutes }
    val filteredAssets = remember(assets, search, statusFilter, criticalityFilter) {
        val q = search.trim().lowercase(Locale.ROOT)
        assets.filter { asset ->
            val statusMatches = statusFilter == "ALL" || asset.status == statusFilter
            val criticalityMatches = criticalityFilter == "ALL" || asset.criticality == criticalityFilter
            val searchMatches = q.isBlank() || listOf(
                asset.code, asset.nameAr, asset.nameEn, asset.assetType, asset.location, asset.serialNo
            ).any { it.lowercase(Locale.ROOT).contains(q) }
            statusMatches && criticalityMatches && searchMatches
        }
    }

    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            FushSectionHeader(
                title = "الأصول والصيانة والسلامة",
                subtitle = "لوحة تشغيل المعدات: الجاهزية، الفحوص والمعايرة، الصيانة الوقائية والتصحيحية، الأعطال والسلامة."
            )
            FushOperationMessage(message, onConsumed = { message = null })
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FushMetricCard("الأصول الجاهزة", activeAssets.toString(), Modifier.weight(1f), helper = "من ${assets.size} أصل", tone = FushStatusTone.Success)
                FushMetricCard(
                    "خارج الجاهزية", unavailableAssets.toString(), Modifier.weight(1f), helper = "متوقف أو تحت الصيانة",
                    tone = if (unavailableAssets > 0) FushStatusTone.Warning else FushStatusTone.Success
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FushMetricCard(
                    "صيانة متأخرة", (overduePlans + overdueWorkOrders).toString(), Modifier.weight(1f),
                    helper = "خطط $overduePlans • أوامر $overdueWorkOrders",
                    tone = if (overduePlans + overdueWorkOrders > 0) FushStatusTone.Danger else FushStatusTone.Success
                )
                FushMetricCard(
                    "فحص/معايرة متأخر", overdueChecks.toString(), Modifier.weight(1f), helper = "يتطلب إجراء قبل التشغيل",
                    tone = if (overdueChecks > 0) FushStatusTone.Danger else FushStatusTone.Success
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FushMetricCard("أصول حرجة", criticalAssets.toString(), Modifier.weight(1f), helper = "Critical", tone = if (criticalAssets > 0) FushStatusTone.Info else FushStatusTone.Neutral)
                FushMetricCard(
                    "سلامة مفتوحة", openSafety.toString(), Modifier.weight(1f), helper = "أعطال متكررة $recurringBreakdowns",
                    tone = if (openSafety > 0 || recurringBreakdowns > 0) FushStatusTone.Warning else FushStatusTone.Success
                )
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FushSectionHeader("إجراءات الصيانة والسلامة", "سجّل الأصل ثم حافظ على دورة الفحص والصيانة والإفراج عن المعدة.")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showAsset = true }, modifier = Modifier.weight(1f)) { Text("إضافة أصل") }
                        OutlinedButton(onClick = { showBreakdown = true }, enabled = assets.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("بلاغ عطل") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showPreventive = true }, enabled = assets.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("أمر وقائي") }
                        OutlinedButton(onClick = { showSafetyInspection = true }, modifier = Modifier.weight(1f)) { Text("فحص سلامة") }
                    }
                    OutlinedButton(onClick = { showSafetyIncident = true }, modifier = Modifier.fillMaxWidth()) { Text("تسجيل حادث / انسكاب") }
                    Text(
                        "هدف التشغيل: صفر معدات متأخرة عن الفحص أو المعايرة، مع إغلاق أوامر الصيانة في موعدها.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FushSectionHeader("سجل الأصول", "ابحث عن المعدة أو صفِّ السجل حسب الجاهزية والأهمية.")
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        label = { Text("بحث بالكود أو الاسم أو الموقع أو الرقم التسلسلي") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    SelectionField(
                        "الحالة",
                        maintenanceStatusFilterAr(statusFilter),
                        listOf("ALL", "ACTIVE", "OUT_OF_SERVICE", "UNDER_MAINTENANCE"),
                        ::maintenanceStatusFilterAr
                    ) { statusFilter = it }
                    SelectionField(
                        "الأهمية",
                        maintenanceCriticalityFilterAr(criticalityFilter),
                        listOf("ALL", "LOW", "MEDIUM", "HIGH", "CRITICAL"),
                        ::maintenanceCriticalityFilterAr
                    ) { criticalityFilter = it }
                    Text("النتائج: ${filteredAssets.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (filteredAssets.isEmpty()) {
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    FushEmptyState("لا توجد أصول مطابقة", "غيّر البحث أو الفلاتر، أو أضف أصلًا تشغيليًا جديدًا.", Modifier.padding(18.dp))
                }
            }
        }
        items(filteredAssets, key = { "maintenance-asset-${it.id}" }) { asset ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${asset.code} — ${asset.nameAr}", style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOf(asset.location, maintenanceAssetTypeAr(asset.assetType)).filter { it.isNotBlank() }.joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        FushStatusPill(assetStatusAr(asset.status), maintenanceAssetStatusTone(asset.status))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        FushStatusPill("الأهمية: ${maintenanceCriticalityAr(asset.criticality)}", maintenanceCriticalityTone(asset.criticality))
                        if (asset.calibrationRequired) FushStatusPill("معايرة مطلوبة", FushStatusTone.Info)
                    }
                    if (asset.serialNo.isNotBlank()) Text("الرقم التسلسلي: ${asset.serialNo}", style = MaterialTheme.typography.bodySmall)
                    if (asset.usageHours > 0 || asset.usageBatches > 0) {
                        Text("الاستخدام: ${asset.usageHours} ساعة • ${asset.usageBatches} دفعة", style = MaterialTheme.typography.bodySmall)
                    }
                    asset.inspectionDueAt?.let {
                        Text(
                            "الفحص القادم: ${fmtDate(it)}${if (it < now) " • متأخر" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (it < now) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (asset.calibrationRequired) asset.calibrationDueAt?.let {
                        Text(
                            "المعايرة القادمة: ${fmtDate(it)}${if (it < now) " • متأخرة" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (it < now) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { inspectAsset = asset }, modifier = Modifier.weight(1f)) { Text("فحص") }
                        if (asset.calibrationRequired || asset.assetType == "MEASURING_TOOL") {
                            OutlinedButton(onClick = { calibrateAsset = asset }, modifier = Modifier.weight(1f)) { Text("معايرة") }
                        }
                    }
                }
            }
        }
        item {
            FushSectionHeader("أوامر الصيانة المفتوحة", "الأولوية للأوامر المتأخرة أو المرتبطة بأصول غير جاهزة.")
        }
        if (workOrders.isEmpty()) {
            item { FushInlineState("لا توجد أوامر صيانة مفتوحة.") }
        }
        items(workOrders, key = { "maintenance-work-order-${it.id}" }) { wo ->
            val overdue = wo.dueAt != null && wo.dueAt < now
            val asset = assetById[wo.assetId]
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(wo.workOrderNo, style = MaterialTheme.typography.titleMedium)
                            Text(asset?.let { "${it.code} — ${it.nameAr}" } ?: "أصل رقم ${wo.assetId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FushStatusPill(if (overdue) "متأخر" else maintenanceWorkTypeAr(wo.workType), if (overdue) FushStatusTone.Danger else FushStatusTone.Info)
                    }
                    Text(wo.problem)
                    wo.dueAt?.let {
                        Text("الاستحقاق: ${fmtDate(it)}", color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (wo.downtimeMinutes > 0) Text("التوقف المسجل: ${wo.downtimeMinutes} دقيقة", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { closeWorkOrder = wo }, modifier = Modifier.fillMaxWidth()) { Text("إغلاق أمر الصيانة") }
                }
            }
        }
        item {
            FushSectionHeader("الأعطال", "إجمالي التوقف المسجل: $totalDowntime دقيقة. الأعطال المتكررة تحتاج تحليل سبب جذري.")
        }
        if (breakdowns.isEmpty()) item { FushInlineState("لا توجد أعطال مسجلة.", tone = FushStatusTone.Success) }
        items(breakdowns.take(20), key = { "maintenance-breakdown-${it.id}" }) { b ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(b.breakdownNo, style = MaterialTheme.typography.titleSmall)
                        FushStatusPill(maintenanceSeverityAr(b.severity), maintenanceSeverityTone(b.severity))
                    }
                    Text(assetById[b.assetId]?.let { "${it.code} — ${it.nameAr}" } ?: "أصل رقم ${b.assetId}", style = MaterialTheme.typography.bodySmall)
                    Text(b.description)
                    Text("${fmtDate(b.occurredAt)} • توقف ${b.downtimeMinutes} دقيقة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FushStatusPill(if (b.status == "CLOSED") "مغلق" else "مفتوح", if (b.status == "CLOSED") FushStatusTone.Success else FushStatusTone.Warning)
                        if (b.recurring) FushStatusPill("عطل متكرر", FushStatusTone.Danger)
                    }
                }
            }
        }
        item { FushSectionHeader("السلامة", "الحوادث المفتوحة واحتياج CAPA يجب أن يظلا ظاهرين حتى الإغلاق.") }
        if (safetyIncidents.isEmpty()) item { FushInlineState("لا توجد حوادث سلامة مسجلة.", tone = FushStatusTone.Success) }
        items(safetyIncidents.take(20), key = { "maintenance-safety-incident-${it.id}" }) { incident ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(incident.incidentNo, style = MaterialTheme.typography.titleSmall)
                        FushStatusPill(
                            if (incident.status == "CLOSED") "مغلق" else "مفتوح",
                            if (incident.status == "CLOSED") FushStatusTone.Success else FushStatusTone.Danger
                        )
                    }
                    Text("${incident.area} • ${maintenanceIncidentTypeAr(incident.incidentType)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(incident.description)
                    if (incident.capaRequired) FushStatusPill("CAPA مطلوب", FushStatusTone.Warning)
                }
            }
        }
    }

    if (showAsset) AddAssetDialog(onDismiss = { showAsset = false }) { code, name, type, location, criticality, calibration ->
        scope.launch {
            try {
                container.maintenanceService.createAsset(code, name, type, location, criticality = criticality, calibrationRequired = calibration, createdBy = user.id)
                message = "تمت إضافة الأصل"
                showAsset = false
            } catch (e: Exception) { message = e.message ?: "تعذر إضافة الأصل" }
        }
    }

    if (showBreakdown) BreakdownDialog(assets, onDismiss = { showBreakdown = false }) { asset, severity, desc, downtime ->
        scope.launch {
            try {
                container.maintenanceService.reportBreakdown(asset.id, severity, desc, downtime, user.id)
                message = "تم تسجيل العطل وإنشاء أمر صيانة تصحيحي"
                showBreakdown = false
            } catch (e: Exception) { message = e.message ?: "تعذر تسجيل العطل" }
        }
    }

    inspectAsset?.let { asset ->
        AssetInspectionDialog(asset, onDismiss = { inspectAsset = null }) { type, result, checklist, findings, action ->
            scope.launch {
                try {
                    container.maintenanceService.recordAssetInspection(asset.id, type, result, checklist, findings, action, user.id)
                    message = if (result == "PASS") "تم حفظ الفحص بنجاح" else "فشل الفحص وتم إيقاف الأصل عن التشغيل"
                    inspectAsset = null
                } catch (e: Exception) { message = e.message ?: "تعذر حفظ الفحص" }
            }
        }
    }

    calibrateAsset?.let { asset ->
        CalibrationDialog(asset, onDismiss = { calibrateAsset = null }) { result, ref, error, tolerance, days, cert, notes ->
            scope.launch {
                try {
                    val due = days?.let { System.currentTimeMillis() + it.toLong() * 86_400_000L }
                    container.maintenanceService.recordCalibration(asset.id, result, ref, error, tolerance, due, cert, notes, user.id)
                    message = if (result == "PASS") "تم تسجيل المعايرة" else "فشلت المعايرة وتم إيقاف الأصل"
                    calibrateAsset = null
                } catch (e: Exception) { message = e.message ?: "تعذر حفظ المعايرة" }
            }
        }
    }

    closeWorkOrder?.let { wo ->
        CloseWorkOrderDialog(wo, onDismiss = { closeWorkOrder = null }) { action, tech, cost, downtime, returnToService ->
            scope.launch {
                try {
                    container.maintenanceService.completeWorkOrder(wo.id, action, tech, cost, downtime, returnToService, if (returnToService) user.id else null, user.id)
                    message = "تم إغلاق أمر الصيانة${if (returnToService) " وإعادة الأصل للخدمة" else ""}"
                    closeWorkOrder = null
                } catch (e: Exception) { message = e.message ?: "تعذر إغلاق الأمر" }
            }
        }
    }

    if (showPreventive) PreventiveWorkOrderDialog(container, assets, onDismiss = { showPreventive = false }) { plan ->
        scope.launch {
            try {
                container.maintenanceService.openPreventiveWorkOrder(plan.id, plan.nextDueAt, user.id)
                message = "تم إنشاء أمر صيانة وقائية"
                showPreventive = false
            } catch (e: Exception) { message = e.message ?: "تعذر إنشاء الأمر الوقائي" }
        }
    }

    if (showSafetyInspection) SafetyInspectionDialog(onDismiss = { showSafetyInspection = false }) { area, type, result, findings, action ->
        scope.launch {
            try {
                container.maintenanceService.recordSafetyInspection(area, type, result, findings, action, null, user.id)
                message = "تم تسجيل فحص السلامة"
                showSafetyInspection = false
            } catch (e: Exception) { message = e.message ?: "تعذر تسجيل فحص السلامة" }
        }
    }

    if (showSafetyIncident) SafetyIncidentDialog(onDismiss = { showSafetyIncident = false }) { type, area, desc, impact, action, capa ->
        scope.launch {
            try {
                container.maintenanceService.reportSafetyIncident(type, area, desc, impact, action, capa, user.id)
                message = "تم تسجيل حادث السلامة"
                showSafetyIncident = false
            } catch (e: Exception) { message = e.message ?: "تعذر تسجيل الحادث" }
        }
    }
}

private fun maintenanceStatusFilterAr(value: String) = when (value) {
    "ALL" -> "كل الحالات"
    "ACTIVE" -> "جاهز"
    "OUT_OF_SERVICE" -> "متوقف"
    "UNDER_MAINTENANCE" -> "تحت الصيانة"
    else -> value
}

private fun maintenanceCriticalityFilterAr(value: String) = if (value == "ALL") "كل درجات الأهمية" else maintenanceCriticalityAr(value)

private fun maintenanceAssetStatusTone(status: String) = when (status) {
    "ACTIVE" -> FushStatusTone.Success
    "OUT_OF_SERVICE" -> FushStatusTone.Danger
    "UNDER_MAINTENANCE" -> FushStatusTone.Warning
    else -> FushStatusTone.Neutral
}

private fun maintenanceCriticalityTone(value: String) = when (value) {
    "CRITICAL" -> FushStatusTone.Danger
    "HIGH" -> FushStatusTone.Warning
    "MEDIUM" -> FushStatusTone.Info
    else -> FushStatusTone.Neutral
}

private fun maintenanceSeverityTone(value: String) = when (value) {
    "CRITICAL" -> FushStatusTone.Danger
    "HIGH" -> FushStatusTone.Warning
    "MEDIUM" -> FushStatusTone.Info
    else -> FushStatusTone.Neutral
}

private fun maintenanceCriticalityAr(value: String) = when (value) {
    "LOW" -> "منخفضة"
    "MEDIUM" -> "متوسطة"
    "HIGH" -> "عالية"
    "CRITICAL" -> "حرجة"
    else -> value
}

private fun maintenanceSeverityAr(value: String) = when (value) {
    "LOW" -> "منخفض"
    "MEDIUM" -> "متوسط"
    "HIGH" -> "عالٍ"
    "CRITICAL" -> "حرج"
    else -> value
}

private fun maintenanceAssetTypeAr(value: String) = when (value) {
    "FILLING_MACHINE" -> "آلة تعبئة"
    "BURNER" -> "شعلة"
    "VESSEL" -> "وعاء / خزان"
    "MEASURING_TOOL" -> "أداة قياس"
    "SAFETY_EQUIPMENT" -> "معدات سلامة"
    "OTHER" -> "أخرى"
    else -> value
}

private fun maintenanceWorkTypeAr(value: String) = when (value) {
    "PREVENTIVE" -> "وقائي"
    "CORRECTIVE" -> "تصحيحي"
    else -> value
}

private fun maintenanceIncidentTypeAr(value: String) = when (value) {
    "ACCIDENT" -> "حادث"
    "NEAR_MISS" -> "شبه حادث"
    "SPILL" -> "انسكاب"
    "FIRE" -> "حريق"
    "GAS_LEAK" -> "تسرب غاز"
    "OTHER" -> "أخرى"
    else -> value
}

@Composable
private fun AddAssetDialog(onDismiss: () -> Unit, onSave: (String, String, String, String, String, Boolean) -> Unit) {
    var code by remember { mutableStateOf("") }; var name by remember { mutableStateOf("") }; var location by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("OTHER") }; var criticality by remember { mutableStateOf("MEDIUM") }; var calibration by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("إضافة أصل/معدة") }, text = {
        FushDialogForm {
            OutlinedTextField(code, { code = it }, label = { Text("الكود") }, singleLine = true)
            OutlinedTextField(name, { name = it }, label = { Text("الاسم") }, singleLine = true)
            OutlinedTextField(location, { location = it }, label = { Text("الموقع") }, singleLine = true)
            StringSelectionField("النوع", type, listOf("FILLING_MACHINE","BURNER","VESSEL","MEASURING_TOOL","SAFETY_EQUIPMENT","OTHER")) { type = it }
            StringSelectionField("الأهمية", criticality, listOf("LOW","MEDIUM","HIGH","CRITICAL")) { criticality = it }
            Row { Checkbox(calibration, { calibration = it }); Text("يحتاج معايرة") }
        }
    }, confirmButton = { Button(enabled = code.isNotBlank() && name.isNotBlank(), onClick = { onSave(code,name,type,location,criticality,calibration) }) { Text("حفظ") } }, dismissButton = { TextButton(onClick=onDismiss){Text("إلغاء")} })
}

@Composable
private fun BreakdownDialog(assets: List<AssetEntity>, onDismiss: () -> Unit, onSave: (AssetEntity,String,String,Int)->Unit) {
    var asset by remember { mutableStateOf<AssetEntity?>(null) }; var severity by remember { mutableStateOf("MEDIUM") }; var desc by remember { mutableStateOf("") }; var downtime by remember { mutableStateOf("0") }
    LaunchedEffect(assets) { if (asset == null) asset = assets.firstOrNull() }
    AlertDialog(onDismissRequest=onDismiss, title={Text("بلاغ عطل")}, text={ FushDialogForm {
        SelectionField("الأصل", asset?.nameAr ?: "اختر", assets, { it.nameAr }) { asset=it }
        StringSelectionField("الخطورة", severity, listOf("LOW","MEDIUM","HIGH","CRITICAL")){severity=it}
        OutlinedTextField(desc,{desc=it},label={Text("وصف العطل")})
        OutlinedTextField(downtime,{downtime=it},label={Text("التوقف بالدقائق")},singleLine=true)
    }}, confirmButton={Button(enabled=asset!=null && desc.isNotBlank() && downtime.toIntOrNull()!=null,onClick={onSave(asset!!,severity,desc,downtime.toInt())}){Text("تسجيل")}}, dismissButton={TextButton(onClick=onDismiss){Text("إلغاء")}})
}

@Composable
private fun AssetInspectionDialog(asset: AssetEntity, onDismiss:()->Unit, onSave:(String,String,String,String,String)->Unit) {
    var type by remember { mutableStateOf("PRE_START") }; var result by remember { mutableStateOf("PASS") }; var checklist by remember { mutableStateOf("") }; var findings by remember { mutableStateOf("") }; var action by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=onDismiss,title={Text("فحص ${asset.nameAr}")},text={FushDialogForm{
        StringSelectionField("الفحص",type,listOf("PRE_START","POST_BATCH","WEEKLY","MONTHLY","SAFETY")){type=it}
        StringSelectionField("النتيجة",result,listOf("PASS","FAIL")){result=it}
        OutlinedTextField(checklist,{checklist=it},label={Text("نتيجة قائمة الفحص")})
        OutlinedTextField(findings,{findings=it},label={Text("الملاحظات/النتائج")})
        OutlinedTextField(action,{action=it},label={Text("الإجراء التصحيحي")})
    }},confirmButton={Button(enabled=checklist.isNotBlank(),onClick={onSave(type,result,checklist,findings,action)}){Text("حفظ")}},dismissButton={TextButton(onClick=onDismiss){Text("إلغاء")}})
}

@Composable
private fun CalibrationDialog(asset: AssetEntity,onDismiss:()->Unit,onSave:(String,String,Double?,Double?,Int?,String,String)->Unit){
    var result by remember{mutableStateOf("PASS")}; var ref by remember{mutableStateOf("")}; var error by remember{mutableStateOf("")}; var tolerance by remember{mutableStateOf("")}; var days by remember{mutableStateOf("30")}; var cert by remember{mutableStateOf("")}; var notes by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Text("معايرة ${asset.nameAr}")},text={FushDialogForm{
        StringSelectionField("النتيجة",result,listOf("PASS","FAIL")){result=it}; OutlinedTextField(ref,{ref=it},label={Text("المرجع القياسي")}); OutlinedTextField(error,{error=it},label={Text("الخطأ المقاس")},singleLine=true); OutlinedTextField(tolerance,{tolerance=it},label={Text("حد السماح")},singleLine=true); OutlinedTextField(days,{days=it},label={Text("الاستحقاق القادم بالأيام")},singleLine=true); OutlinedTextField(cert,{cert=it},label={Text("مرجع الشهادة")}); OutlinedTextField(notes,{notes=it},label={Text("ملاحظات")})
    }},confirmButton={Button(onClick={onSave(result,ref,error.toDoubleOrNull(),tolerance.toDoubleOrNull(),days.toIntOrNull(),cert,notes)}){Text("حفظ")}},dismissButton={TextButton(onClick=onDismiss){Text("إلغاء")}})
}

@Composable
private fun CloseWorkOrderDialog(wo: MaintenanceWorkOrderEntity,onDismiss:()->Unit,onSave:(String,String,Double,Int,Boolean)->Unit){
    var action by remember{mutableStateOf("")}; var tech by remember{mutableStateOf("")}; var cost by remember{mutableStateOf("0")}; var downtime by remember{mutableStateOf(wo.downtimeMinutes.toString())}; var returnService by remember{mutableStateOf(true)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("إغلاق ${wo.workOrderNo}")},text={FushDialogForm{
        OutlinedTextField(action,{action=it},label={Text("الإجراء المنفذ")}); OutlinedTextField(tech,{tech=it},label={Text("الفني")}); OutlinedTextField(cost,{cost=it},label={Text("التكلفة")},singleLine=true); OutlinedTextField(downtime,{downtime=it},label={Text("التوقف بالدقائق")},singleLine=true); Row{Checkbox(returnService,{returnService=it});Text("اعتماد إعادة المعدة للخدمة")}
    }},confirmButton={Button(enabled=action.isNotBlank()&&cost.toDoubleOrNull()!=null&&downtime.toIntOrNull()!=null,onClick={onSave(action,tech,cost.toDouble(),downtime.toInt(),returnService)}){Text("إغلاق")}},dismissButton={TextButton(onClick=onDismiss){Text("إلغاء")}})
}

@Composable
private fun SafetyIncidentDialog(onDismiss:()->Unit,onSave:(String,String,String,String,String,Boolean)->Unit){
    var type by remember{mutableStateOf("NEAR_MISS")};var area by remember{mutableStateOf("")};var desc by remember{mutableStateOf("")};var impact by remember{mutableStateOf("")};var action by remember{mutableStateOf("")};var capa by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("حادث/واقعة سلامة")},text={FushDialogForm{
        StringSelectionField("النوع",type,listOf("ACCIDENT","NEAR_MISS","SPILL","FIRE","GAS_LEAK","OTHER")){type=it};OutlinedTextField(area,{area=it},label={Text("الموقع")});OutlinedTextField(desc,{desc=it},label={Text("الوصف")});OutlinedTextField(impact,{impact=it},label={Text("الإصابة/الأثر")});OutlinedTextField(action,{action=it},label={Text("الإجراء الفوري")});Row{Checkbox(capa,{capa=it});Text("يتطلب CAPA")}
    }},confirmButton={Button(enabled=area.isNotBlank()&&desc.isNotBlank(),onClick={onSave(type,area,desc,impact,action,capa)}){Text("تسجيل")}},dismissButton={TextButton(onClick=onDismiss){Text("إلغاء")}})
}

@Composable
private fun PreventiveWorkOrderDialog(container: AppContainer, assets: List<AssetEntity>, onDismiss:()->Unit, onSave:(MaintenancePlanEntity)->Unit){
    var asset by remember{mutableStateOf<AssetEntity?>(null)}
    var plans by remember{mutableStateOf<List<MaintenancePlanEntity>>(emptyList())}
    var plan by remember{mutableStateOf<MaintenancePlanEntity?>(null)}
    LaunchedEffect(assets){ if(asset==null) asset=assets.firstOrNull() }
    LaunchedEffect(asset?.id){
        plans = asset?.let { container.db.maintenanceDao().plansForAsset(it.id) } ?: emptyList()
        plan = plans.firstOrNull { it.frequencyType !in setOf("BEFORE_EACH_RUN","AFTER_EACH_BATCH") } ?: plans.firstOrNull()
    }
    AlertDialog(onDismissRequest=onDismiss,title={Text("أمر صيانة وقائية")},text={FushDialogForm{
        SelectionField("الأصل",asset?.nameAr?:"اختر",assets,{it.nameAr}){asset=it}
        SelectionField("الخطة",plan?.nameAr?:"لا توجد خطة",plans,{"${it.nameAr} • ${it.frequencyType}"}){plan=it}
        plan?.checklist?.takeIf{it.isNotBlank()}?.let{Text("قائمة الفحص: $it",style=MaterialTheme.typography.bodySmall)}
    }},confirmButton={Button(enabled=plan!=null,onClick={onSave(plan!!)}){Text("إنشاء")}},dismissButton={TextButton(onClick=onDismiss){Text("إلغاء")}})
}

@Composable
private fun SafetyInspectionDialog(onDismiss:()->Unit,onSave:(String,String,String,String,String)->Unit){
    var area by remember{mutableStateOf("")};var type by remember{mutableStateOf("DAILY")};var result by remember{mutableStateOf("PASS")};var findings by remember{mutableStateOf("")};var action by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Text("فحص السلامة")},text={FushDialogForm{
        OutlinedTextField(area,{area=it},label={Text("المنطقة")});StringSelectionField("نوع الفحص",type,listOf("DAILY","GAS","FIRE_EXTINGUISHER","PPE","EVACUATION","OTHER")){type=it};StringSelectionField("النتيجة",result,listOf("PASS","FAIL")){result=it};OutlinedTextField(findings,{findings=it},label={Text("النتائج")});OutlinedTextField(action,{action=it},label={Text("الإجراء التصحيحي")})
    }},confirmButton={Button(enabled=area.isNotBlank(),onClick={onSave(area,type,result,findings,action)}){Text("حفظ")}},dismissButton={TextButton(onClick=onDismiss){Text("إلغاء")}})
}

private fun assetStatusAr(status:String)=when(status){"ACTIVE"->"جاهز";"OUT_OF_SERVICE"->"متوقف";"UNDER_MAINTENANCE"->"تحت الصيانة";else->status}
private fun fmtDate(value:Long):String=SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(value))
