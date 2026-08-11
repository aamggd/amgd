from pathlib import Path

root = Path('FushERP_Mobile_Phase5')

# Version bump
p = root / 'app/build.gradle.kts'
s = p.read_text(encoding='utf-8')
assert 'versionCode = 25' in s
assert 'versionName = "0.13.6.1-phase13-dashboard-return-fix"' in s
s = s.replace('versionCode = 25', 'versionCode = 26', 1)
s = s.replace('versionName = "0.13.6.1-phase13-dashboard-return-fix"', 'versionName = "0.13.6.2-phase13-dashboard-navigation"', 1)
p.write_text(s, encoding='utf-8')

p = root / 'app/src/main/java/com/fush/erp/ui/screens/HomeShell.kt'
s = p.read_text(encoding='utf-8')

needle = 'import androidx.compose.foundation.layout.*\n'
assert needle in s
s = s.replace(needle, 'import androidx.compose.foundation.clickable\n' + needle, 1)

repls = {
    'ExecutiveMetric("صافي المبيعات", executive?.let { it.grossSalesBase - it.salesReturnsBase }, "ريال", Modifier.weight(1f))':
        'ExecutiveMetric("صافي المبيعات", executive?.let { it.grossSalesBase - it.salesReturnsBase }, "ريال", Modifier.weight(1f)) { onNavigate("المبيعات") }',
    'ExecutiveMetric("صافي التحصيل", executive?.collectionsBase, "ريال", Modifier.weight(1f))':
        'ExecutiveMetric("صافي التحصيل", executive?.collectionsBase, "ريال", Modifier.weight(1f)) { onNavigate("المبيعات") }',
    'ExecutiveMetric("الذمم", executive?.receivablesBase, "ريال", Modifier.weight(1f))':
        'ExecutiveMetric("الذمم", executive?.receivablesBase, "ريال", Modifier.weight(1f)) { onNavigate("المبيعات") }',
    'ExecutiveMetric("المخزون", executive?.inventoryValueBase, "ريال", Modifier.weight(1f))':
        'ExecutiveMetric("المخزون", executive?.inventoryValueBase, "ريال", Modifier.weight(1f)) { onNavigate("المخزون") }',
    'Metric("نقدي %", "%.0f".format(cashSalesPct), Modifier.weight(1f))':
        'Metric("نقدي %", "%.0f".format(cashSalesPct), Modifier.weight(1f)) { onNavigate("المبيعات") }',
    'Metric("إنتاج مقبول", executive?.acceptedQtyBase?.let { "%.0f".format(it) } ?: "—", Modifier.weight(1f))':
        'Metric("إنتاج مقبول", executive?.acceptedQtyBase?.let { "%.0f".format(it) } ?: "—", Modifier.weight(1f)) { onNavigate("الإنتاج") }',
    'Metric("هالك", executive?.scrapQtyBase?.let { "%.0f".format(it) } ?: "—", Modifier.weight(1f))':
        'Metric("هالك", executive?.scrapQtyBase?.let { "%.0f".format(it) } ?: "—", Modifier.weight(1f)) { onNavigate("الإنتاج") }',
    'Metric("العملاء", customerCount.toString(), Modifier.weight(1f))':
        'Metric("العملاء", customerCount.toString(), Modifier.weight(1f)) { onNavigate("المبيعات") }',
    'Metric("فواتير البيع", salesCount.toString(), Modifier.weight(1f))':
        'Metric("فواتير البيع", salesCount.toString(), Modifier.weight(1f)) { onNavigate("المبيعات") }',
    'Metric("التحصيلات", receiptCount.toString(), Modifier.weight(1f))':
        'Metric("التحصيلات", receiptCount.toString(), Modifier.weight(1f)) { onNavigate("المبيعات") }',
    'Metric("الموردون", supplierCount.toString(), Modifier.weight(1f))':
        'Metric("الموردون", supplierCount.toString(), Modifier.weight(1f)) { onNavigate("المشتريات") }',
    'Metric("المشتريات", purchaseCount.toString(), Modifier.weight(1f))':
        'Metric("المشتريات", purchaseCount.toString(), Modifier.weight(1f)) { onNavigate("المشتريات") }',
    'Metric("أوامر الإنتاج", productionCount.toString(), Modifier.weight(1f))':
        'Metric("أوامر الإنتاج", productionCount.toString(), Modifier.weight(1f)) { onNavigate("الإنتاج") }',
    'Metric("الأصناف", itemCount.toString(), Modifier.weight(1f))':
        'Metric("الأصناف", itemCount.toString(), Modifier.weight(1f)) { onNavigate("المواد والأصناف") }',
    'Metric("الموظفون", employeeCount.toString(), Modifier.weight(1f))':
        'Metric("الموظفون", employeeCount.toString(), Modifier.weight(1f)) { onNavigate("الموظفون") }',
    'Metric("صيانة مفتوحة", openMaintenanceCount.toString(), Modifier.weight(1f))':
        'Metric("صيانة مفتوحة", openMaintenanceCount.toString(), Modifier.weight(1f)) { onNavigate("الصيانة") }',
    'Metric("مخاطر مفتوحة", openRiskCount.toString(), Modifier.weight(1f))':
        'Metric("مخاطر مفتوحة", openRiskCount.toString(), Modifier.weight(1f)) { onNavigate("المخاطر") }',
    'Metric("موافقات", pendingApprovalCount.toString(), Modifier.weight(1f))':
        'Metric("موافقات", pendingApprovalCount.toString(), Modifier.weight(1f)) { onNavigate("الحوكمة") }',
    'Metric("الإصدار", "0.13.1", Modifier.weight(1f))':
        'Metric("الإصدار", "0.13.6.2", Modifier.weight(1f))',
}
for old, new in repls.items():
    assert old in s, f'missing: {old}'
    s = s.replace(old, new, 1)

old = 'Text("نبضة النظام", style = MaterialTheme.typography.titleLarge)\n            Row('
new = 'Text("نبضة النظام", style = MaterialTheme.typography.titleLarge)\n            Text("اضغط على أي بطاقة لفتح القسم المرتبط بها.", style = MaterialTheme.typography.bodySmall)\n            Spacer(Modifier.height(6.dp))\n            Row('
assert old in s
s = s.replace(old, new, 1)

old = '''@Composable
private fun ExecutiveMetric(label: String, value: Double?, suffix: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value?.let { java.lang.String.format(java.util.Locale.US, "%,.0f", it) } ?: "—", style = MaterialTheme.typography.titleLarge)
            Text("$label • $suffix", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}
'''
new = '''@Composable
private fun ExecutiveMetric(
    label: String,
    value: Double?,
    suffix: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val cardModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    ElevatedCard(cardModifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value?.let { java.lang.String.format(java.util.Locale.US, "%,.0f", it) } ?: "—", style = MaterialTheme.typography.titleLarge)
            Text("$label • $suffix", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Metric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val cardModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    ElevatedCard(cardModifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}
'''
assert old in s
s = s.replace(old, new, 1)

p.write_text(s, encoding='utf-8')

(root / 'PHASE13_6_2_SCOPE.md').write_text('''# Phase 13.6.2 — Dashboard navigation\n\nDashboard KPI and system pulse cards now open their related ERP section:\n- sales KPIs / customers / sales invoices / receipts -> Sales\n- inventory value -> Inventory\n- suppliers / purchases -> Purchases\n- production KPIs / production orders -> Production\n- items -> Master Data (Items)\n- employees -> Employees\n- maintenance -> Maintenance\n- risks -> Risk Control\n- approvals -> Governance\n\nThe version pulse card is informational and shows 0.13.6.2.\n''', encoding='utf-8')
