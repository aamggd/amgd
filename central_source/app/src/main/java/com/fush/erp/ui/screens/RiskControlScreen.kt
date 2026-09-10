package com.fush.erp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fush.erp.data.AppContainer
import com.fush.erp.data.entity.*
import com.fush.erp.domain.RiskControlMath
import com.fush.erp.ui.*
import kotlinx.coroutines.launch

@Composable
fun RiskControlScreen(container: AppContainer, user: UserEntity, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val risks by container.db.riskControlDao().observeRisks().collectAsState(initial = emptyList())
    val controls by container.db.riskControlDao().observeControls().collectAsState(initial = emptyList())
    val tests by container.db.riskControlDao().observeTests().collectAsState(initial = emptyList())
    val exceptions by container.db.riskControlDao().observeExceptions().collectAsState(initial = emptyList())
    val rules by container.db.riskControlDao().observeSegregationRules().collectAsState(initial = emptyList())
    val openRisks by container.db.riskControlDao().observeOpenRiskCount().collectAsState(initial = 0)
    val highRisks by container.db.riskControlDao().observeHighRiskCount().collectAsState(initial = 0)
    val openExceptions by container.db.riskControlDao().observeOpenExceptionCount().collectAsState(initial = 0)
    val overdueExceptions by container.db.riskControlDao().observeOverdueExceptionCount(System.currentTimeMillis()).collectAsState(initial = 0)

    var tab by remember { mutableStateOf(0) }
    var showRisk by remember { mutableStateOf(false) }
    var showControl by remember { mutableStateOf(false) }
    var showTest by remember { mutableStateOf(false) }
    var showManualException by remember { mutableStateOf(false) }
    var reviewRisk by remember { mutableStateOf<RiskEntity?>(null) }
    var closeException by remember { mutableStateOf<ControlExceptionEntity?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FushSectionHeader(
                    title = "المخاطر والرقابة الداخلية",
                    subtitle = "سجل المخاطر والضوابط والاختبارات والاستثناءات مع فصل المهام ومتابعة البنود المتأخرة.",
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FushMetricCard("مخاطر مفتوحة", openRisks.toString(), Modifier.weight(1f), tone = if (openRisks > 0) FushStatusTone.Warning else FushStatusTone.Success)
                    FushMetricCard("مخاطر عالية", highRisks.toString(), Modifier.weight(1f), tone = if (highRisks > 0) FushStatusTone.Danger else FushStatusTone.Success)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FushMetricCard("استثناءات مفتوحة", openExceptions.toString(), Modifier.weight(1f), tone = if (openExceptions > 0) FushStatusTone.Warning else FushStatusTone.Success)
                    FushMetricCard("استثناءات متأخرة", overdueExceptions.toString(), Modifier.weight(1f), tone = if (overdueExceptions > 0) FushStatusTone.Danger else FushStatusTone.Success)
                }
            }
        }
        item {
            ScrollableTabRow(selectedTabIndex = tab) {
                listOf("المخاطر", "الضوابط", "الاختبارات", "الاستثناءات", "فصل المهام").forEachIndexed { index, title ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
                }
            }
        }
        if (message != null) {
            item { FushOperationMessage(message, onConsumed = { message = null }) }
        }
        when (tab) {
            0 -> {
                item { Button(onClick = { showRisk = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("إضافة خطر") } }
                if (risks.isEmpty()) item { FushEmptyState("لا توجد مخاطر مسجلة", "أضف أول خطر وحدد الاحتمال والأثر وخطة المعالجة والمسؤول.") }
                items(risks, key = { "risk-${it.id}" }) { r ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${r.riskNo} — ${r.title}", style = MaterialTheme.typography.titleMedium)
                            Text("${r.category} • ${r.status} • المسؤول: ${r.ownerRole}")
                            Text("الخطر الأصلي: ${r.inherentScore}/25 (${riskBandAr(r.inherentScore)}) • المتبقي: ${r.residualScore}/25 (${riskBandAr(r.residualScore)})")
                            if (r.description.isNotBlank()) Text(r.description, style = MaterialTheme.typography.bodySmall)
                            if (r.mitigationPlan.isNotBlank()) Text("المعالجة: ${r.mitigationPlan}", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { reviewRisk = r }) { Text("مراجعة / تحديث المعالجة") }
                        }
                    }
                }
            }
            1 -> {
                item { Button(onClick = { showControl = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("إضافة ضابط رقابي") } }
                if (controls.isEmpty()) item { FushEmptyState("لا توجد ضوابط رقابية", "اربط الضوابط بالمخاطر وحدد التكرار والمسؤول والدليل المطلوب.") }
                items(controls, key = { "risk-control-${it.id}" }) { c ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${c.controlCode} — ${c.title}", style = MaterialTheme.typography.titleMedium)
                            Text("${c.controlType} • ${c.frequency} • المسؤول: ${c.ownerRole}")
                            if (c.designDescription.isNotBlank()) Text(c.designDescription, style = MaterialTheme.typography.bodySmall)
                            if (c.evidenceRequired.isNotBlank()) Text("الدليل المطلوب: ${c.evidenceRequired}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            2 -> {
                item { Button(enabled = controls.isNotEmpty(), onClick = { showTest = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("اختبار ضابط") } }
                item { FushInlineState("أي نتيجة FAIL تنشئ استثناءً مفتوحًا تلقائيًا.", tone = FushStatusTone.Info) }
                if (tests.isEmpty()) item { FushEmptyState("لا توجد اختبارات رقابية", "سجّل اختبارًا لأحد الضوابط لتوثيق النتيجة والدليل والملاحظات.") }
                items(tests, key = { "risk-test-${it.id}" }) { t ->
                    val c = controls.firstOrNull { it.id == t.controlId }
                    ListItem(
                        headlineContent = { Text("${c?.controlCode ?: "CTL#${t.controlId}"} • ${t.result}") },
                        supportingContent = { Text("${t.finding.ifBlank { "بدون ملاحظة" }}${if (t.evidenceRef.isNotBlank()) "\nدليل: ${t.evidenceRef}" else ""}") }
                    )
                    HorizontalDivider()
                }
            }
            3 -> {
                item { Button(onClick = { showManualException = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("استثناء يدوي") } }
                if (exceptions.isEmpty()) item { FushEmptyState("لا توجد استثناءات رقابية", "الاستثناءات الناتجة عن فشل الضوابط أو المسجلة يدويًا ستظهر هنا.") }
                items(exceptions, key = { "risk-exception-${it.id}" }) { e ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${e.exceptionNo} — ${e.severity}", style = MaterialTheme.typography.titleMedium)
                            Text("${e.status} • المسؤول: ${e.ownerRole}")
                            Text(e.description)
                            if (e.status != "CLOSED") {
                                if (e.openedBy == user.id) {
                                    Text("فصل المهام: يلزم مستخدم آخر لاعتماد الإغلاق.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                } else {
                                    TextButton(onClick = { closeException = e }) { Text("اعتماد الإغلاق") }
                                }
                            } else if (e.closureNote.isNotBlank()) {
                                Text("الإغلاق: ${e.closureNote}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            else -> {
                item { FushSectionHeader("قواعد فصل المهام", "منشئ العملية لا يعتمد نفس العملية عندما تتطلب القاعدة مستخدمًا مختلفًا.") }
                if (rules.isEmpty()) item { FushEmptyState("لا توجد قواعد فصل مهام", "ستظهر هنا قواعد الفصل بين المنشئ والمعتمد عند تعريفها.") }
                items(rules, key = { "risk-rule-${it.id}" }) { rule ->
                    ListItem(
                        headlineContent = { Text("${rule.ruleCode} — ${rule.actionKey}") },
                        supportingContent = { Text("${rule.initiatorRole} → ${rule.approverRole}\n${rule.description}${if (rule.requireDifferentUser) " • مستخدم مختلف إلزامي" else ""}") }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showRisk) AddRiskDialog(onDismiss = { showRisk = false }) { title, category, description, likelihood, impact, mitigation, owner, dueDays ->
        scope.launch {
            try {
                val dueAt = dueDays?.let { System.currentTimeMillis() + it * 86_400_000L }
                container.riskControlService.createRisk(title, category, description, likelihood, impact, mitigation, owner, dueAt, user.id)
                showRisk = false; message = "تم تسجيل الخطر"
            } catch (e: Exception) { message = e.message ?: "تعذر تسجيل الخطر" }
        }
    }

    if (showControl) AddControlDialog(risks, onDismiss = { showControl = false }) { title, type, frequency, owner, riskId, design, evidence ->
        scope.launch {
            try {
                container.riskControlService.createControl(title, type, frequency, owner, riskId, design, evidence, user.id)
                showControl = false; message = "تم إنشاء الضابط الرقابي"
            } catch (e: Exception) { message = e.message ?: "تعذر إنشاء الضابط" }
        }
    }

    if (showTest) AddControlTestDialog(controls, onDismiss = { showTest = false }) { controlId, result, evidence, finding, severity, dueDays ->
        scope.launch {
            try {
                val dueAt = dueDays?.let { System.currentTimeMillis() + it * 86_400_000L }
                container.riskControlService.recordControlTest(controlId, result, evidence, finding, severity, dueAt, user.id)
                showTest = false; message = if (result == "FAIL") "تم تسجيل الفشل وفتح استثناء تلقائيًا" else "تم تسجيل نجاح الاختبار"
            } catch (e: Exception) { message = e.message ?: "تعذر تسجيل الاختبار" }
        }
    }

    if (showManualException) ManualExceptionDialog(onDismiss = { showManualException = false }) { description, severity, owner, dueDays ->
        scope.launch {
            try {
                val dueAt = dueDays?.let { System.currentTimeMillis() + it * 86_400_000L }
                container.riskControlService.createManualException(description, severity, owner, dueAt, user.id)
                showManualException = false; message = "تم فتح الاستثناء"
            } catch (e: Exception) { message = e.message ?: "تعذر فتح الاستثناء" }
        }
    }

    reviewRisk?.let { r ->
        ReviewRiskDialog(r, onDismiss = { reviewRisk = null }) { l, i, status, mitigation ->
            scope.launch {
                try {
                    container.riskControlService.reviewRisk(r, l, i, status, mitigation, user.id)
                    reviewRisk = null; message = "تم تحديث تقييم الخطر"
                } catch (e: Exception) { message = e.message ?: "تعذر تحديث الخطر" }
            }
        }
    }

    closeException?.let { e ->
        CloseExceptionDialog(onDismiss = { closeException = null }) { note ->
            scope.launch {
                try {
                    container.riskControlService.closeException(e, note, user.id)
                    closeException = null; message = "تم اعتماد إغلاق الاستثناء"
                } catch (x: Exception) { message = x.message ?: "تعذر إغلاق الاستثناء" }
            }
        }
    }
}

private fun riskBandAr(score: Int): String = when (RiskControlMath.band(score)) {
    "CRITICAL" -> "حرج"
    "HIGH" -> "عالٍ"
    "MEDIUM" -> "متوسط"
    else -> "منخفض"
}

@Composable
private fun AddRiskDialog(onDismiss: () -> Unit, onSave: (String,String,String,Int,Int,String,String,Long?) -> Unit) {
    var title by remember { mutableStateOf("") }; var category by remember { mutableStateOf("OPERATIONAL") }; var description by remember { mutableStateOf("") }
    var likelihood by remember { mutableStateOf("3") }; var impact by remember { mutableStateOf("3") }; var mitigation by remember { mutableStateOf("") }; var owner by remember { mutableStateOf("ADMIN") }; var due by remember { mutableStateOf("30") }
    val l = likelihood.toIntOrNull(); val i = impact.toIntOrNull(); val score = if (l in 1..5 && i in 1..5) l!! * i!! else null
    AlertDialog(onDismissRequest=onDismiss, title={Text("إضافة خطر")}, text={ LazyColumn(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        item { OutlinedTextField(title,{title=it},label={Text("عنوان الخطر")},singleLine=true) }
        item { OutlinedTextField(category,{category=it},label={Text("الفئة: مالي / تشغيلي / جودة / مخزون / سوق")},singleLine=true) }
        item { OutlinedTextField(description,{description=it},label={Text("الوصف")}) }
        item { OutlinedTextField(likelihood,{likelihood=it},label={Text("الاحتمال 1–5")},singleLine=true) }
        item { OutlinedTextField(impact,{impact=it},label={Text("الأثر 1–5")},singleLine=true) }
        item { score?.let { Text("الدرجة: $it/25 — ${riskBandAr(it)}", color = if (it >= 15) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) } }
        item { OutlinedTextField(mitigation,{mitigation=it},label={Text("خطة المعالجة")}) }
        item { OutlinedTextField(owner,{owner=it},label={Text("الدور المسؤول")},singleLine=true) }
        item { OutlinedTextField(due,{due=it},label={Text("المهلة بالأيام")},singleLine=true) }
    }}, confirmButton={Button(enabled=title.isNotBlank()&&l in 1..5&&i in 1..5,onClick={onSave(title,category,description,l!!,i!!,mitigation,owner,due.toLongOrNull())}){Text("حفظ")}}, dismissButton={TextButton(onClick=onDismiss){Text("إلغاء")}})
}

@Composable
private fun AddControlDialog(risks: List<RiskEntity>, onDismiss: () -> Unit, onSave: (String,String,String,String,Long?,String,String) -> Unit) {
    var title by remember { mutableStateOf("") }; var type by remember { mutableStateOf("PREVENTIVE") }; var frequency by remember { mutableStateOf("MONTHLY") }; var owner by remember { mutableStateOf("ADMIN") }
    var selectedRisk by remember { mutableStateOf<RiskEntity?>(null) }; var expanded by remember { mutableStateOf(false) }; var design by remember { mutableStateOf("") }; var evidence by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=onDismiss, title={Text("ضابط رقابي جديد")}, text={ FushDialogForm {
        OutlinedTextField(title,{title=it},label={Text("اسم الضابط")},singleLine=true)
        OutlinedTextField(type,{type=it},label={Text("النوع: PREVENTIVE / DETECTIVE")},singleLine=true)
        OutlinedTextField(frequency,{frequency=it},label={Text("التكرار: DAILY / WEEKLY / MONTHLY")},singleLine=true)
        OutlinedTextField(owner,{owner=it},label={Text("الدور المسؤول")},singleLine=true)
        Box { OutlinedButton(onClick={expanded=true}) { Text(selectedRisk?.let { "مرتبط: ${it.riskNo}" } ?: "ربط بخطر (اختياري)") }; DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) { DropdownMenuItem(text={Text("بدون ربط")},onClick={selectedRisk=null;expanded=false}); risks.forEach { r -> DropdownMenuItem(text={Text("${r.riskNo} — ${r.title}")},onClick={selectedRisk=r;expanded=false}) } } }
        OutlinedTextField(design,{design=it},label={Text("وصف تصميم الرقابة")})
        OutlinedTextField(evidence,{evidence=it},label={Text("الدليل المطلوب")})
    }}, confirmButton={Button(enabled=title.isNotBlank(),onClick={onSave(title,type,frequency,owner,selectedRisk?.id,design,evidence)}){Text("حفظ")}}, dismissButton={TextButton(onClick=onDismiss){Text("إلغاء")}})
}

@Composable
private fun AddControlTestDialog(controls: List<InternalControlEntity>, onDismiss: () -> Unit, onSave: (Long,String,String,String,String,Long?) -> Unit) {
    var control by remember { mutableStateOf(controls.firstOrNull()) }; var expanded by remember { mutableStateOf(false) }; var result by remember { mutableStateOf("PASS") }
    var evidence by remember { mutableStateOf("") }; var finding by remember { mutableStateOf("") }; var severity by remember { mutableStateOf("MEDIUM") }; var due by remember { mutableStateOf("7") }
    AlertDialog(onDismissRequest=onDismiss, title={Text("اختبار ضابط")}, text={ FushDialogForm {
        Box { OutlinedButton(onClick={expanded=true}) { Text(control?.let { "${it.controlCode} — ${it.title}" } ?: "اختر الضابط") }; DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) { controls.forEach { c -> DropdownMenuItem(text={Text("${c.controlCode} — ${c.title}")},onClick={control=c;expanded=false}) } } }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { FilterChip(selected=result=="PASS",onClick={result="PASS"},label={Text("PASS")}); FilterChip(selected=result=="FAIL",onClick={result="FAIL"},label={Text("FAIL")}) }
        OutlinedTextField(evidence,{evidence=it},label={Text("مرجع الدليل")},singleLine=true)
        OutlinedTextField(finding,{finding=it},label={Text("الملاحظة / النتيجة")})
        if (result=="FAIL") { OutlinedTextField(severity,{severity=it},label={Text("الخطورة: LOW / MEDIUM / HIGH / CRITICAL")},singleLine=true); OutlinedTextField(due,{due=it},label={Text("مهلة معالجة الاستثناء بالأيام")},singleLine=true) }
    }}, confirmButton={Button(enabled=control!=null,onClick={onSave(control!!.id,result,evidence,finding,severity,if(result=="FAIL") due.toLongOrNull() else null)}){Text("تسجيل")}}, dismissButton={TextButton(onClick=onDismiss){Text("إلغاء")}})
}

@Composable
private fun ManualExceptionDialog(onDismiss: () -> Unit, onSave: (String,String,String,Long?) -> Unit) {
    var description by remember { mutableStateOf("") }; var severity by remember { mutableStateOf("MEDIUM") }; var owner by remember { mutableStateOf("ADMIN") }; var due by remember { mutableStateOf("7") }
    AlertDialog(onDismissRequest=onDismiss,title={Text("استثناء رقابي يدوي")},text={FushDialogForm{
        OutlinedTextField(description,{description=it},label={Text("وصف الاستثناء")}); OutlinedTextField(severity,{severity=it},label={Text("الخطورة")},singleLine=true); OutlinedTextField(owner,{owner=it},label={Text("الدور المسؤول")},singleLine=true); OutlinedTextField(due,{due=it},label={Text("المهلة بالأيام")},singleLine=true)
    }},confirmButton={Button(enabled=description.isNotBlank(),onClick={onSave(description,severity,owner,due.toLongOrNull())}){Text("فتح")}},dismissButton={TextButton(onClick=onDismiss){Text("إلغاء")}})
}

@Composable
private fun ReviewRiskDialog(row: RiskEntity, onDismiss: () -> Unit, onSave: (Int,Int,String,String) -> Unit) {
    var likelihood by remember { mutableStateOf(row.residualLikelihood.toString()) }; var impact by remember { mutableStateOf(row.residualImpact.toString()) }; var status by remember { mutableStateOf(row.status) }; var mitigation by remember { mutableStateOf(row.mitigationPlan) }
    val l=likelihood.toIntOrNull(); val i=impact.toIntOrNull(); val score=if(l in 1..5&&i in 1..5) l!!*i!! else null
    AlertDialog(onDismissRequest=onDismiss,title={Text("مراجعة ${row.riskNo}")},text={FushDialogForm{
        OutlinedTextField(likelihood,{likelihood=it},label={Text("الاحتمال المتبقي 1–5")},singleLine=true); OutlinedTextField(impact,{impact=it},label={Text("الأثر المتبقي 1–5")},singleLine=true); score?.let{Text("الدرجة المتبقية: $it/25 — ${riskBandAr(it)}")}; OutlinedTextField(status,{status=it},label={Text("الحالة: OPEN / MITIGATING / ACCEPTED / CLOSED")},singleLine=true); OutlinedTextField(mitigation,{mitigation=it},label={Text("خطة المعالجة")})
    }},confirmButton={Button(enabled=l in 1..5&&i in 1..5,onClick={onSave(l!!,i!!,status.trim().uppercase(),mitigation)}){Text("تحديث")}},dismissButton={TextButton(onClick=onDismiss){Text("إلغاء")}})
}

@Composable
private fun CloseExceptionDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var note by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=onDismiss,title={Text("اعتماد إغلاق الاستثناء")},text={OutlinedTextField(note,{note=it},label={Text("دليل/ملاحظة الإغلاق")})},confirmButton={Button(enabled=note.isNotBlank(),onClick={onSave(note)}){Text("اعتماد الإغلاق")}},dismissButton={TextButton(onClick=onDismiss){Text("إلغاء")}})
}
