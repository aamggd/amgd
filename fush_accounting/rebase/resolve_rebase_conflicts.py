from pathlib import Path
import sys

repo = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()

# Keep the official Professional UI application/version identity.
(repo / "app/build.gradle.kts").write_text(
    (repo / "app/build.gradle.kts").read_text().replace(
        '''<<<<<<< HEAD\n        versionCode = 77\n        versionName = "0.15.4.38-ui-inventory-master-data"\n=======\n        versionCode = 75\n        versionName = "0.15.4.38-treasury-bank-reconciliation"\n>>>>>>> accounting-old''',
        '''        versionCode = 77\n        versionName = "0.15.4.38-ui-inventory-master-data"'''
    )
)

# Accounting / treasury screen: retain modern UI and insert cash-count + bank-reconciliation controls.
p = repo / "app/src/main/java/com/fush/erp/ui/screens/AccountingScreens.kt"
s = p.read_text()
old = '''<<<<<<< HEAD
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FushSectionHeader(
                    title = "الخزينة والبنوك",
                    subtitle = "الأرصدة الدفترية والسندات والتحويلات بين الصناديق والحسابات البنكية",
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = { showAdd = true }, shape = MaterialTheme.shapes.medium) { Text("إضافة حساب") }
            }
=======
            Text("الخزينة والبنوك", style = MaterialTheme.typography.headlineSmall)
            Text("الأرصدة مرتبة في جدول. اسحب أفقياً لعرض كل الأعمدة.", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("RECEIPT" to "سند قبض", "PAYMENT" to "سند صرف", "INCOME" to "إيراد", "TRANSFER" to "تحويل").forEach { (type, title) ->
                    Button(onClick = { voucherType = type }) { Text(title) }
                }
                OutlinedButton(onClick = { showCashCount = true }) { Text("جرد صندوق") }
                OutlinedButton(onClick = { showBankStatement = true }) { Text("كشف بنكي") }
                OutlinedButton(onClick = { showAdd = true }) { Text("إضافة خزينة/بنك") }
            }
            Text(
                "رقابة الجرد والمطابقة الحالية تعمل على الخزائن بالعملة الأساسية YER_NEW. العملات الأجنبية تتطلب دفتر كمية عملة وإعادة تقييم منفصلين قبل اعتماد الإقفال.",
                style = MaterialTheme.typography.bodySmall
            )
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
>>>>>>> accounting-old'''
new = '''            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FushSectionHeader(
                    title = "الخزينة والبنوك",
                    subtitle = "الأرصدة الدفترية والسندات والجرد والمطابقة البنكية في مساحة رقابية موحّدة",
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = { showAdd = true }, shape = MaterialTheme.shapes.medium) { Text("إضافة خزينة/بنك") }
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    "جرد الصندوق والمطابقة البنكية الحالية معتمدان للخزائن بالعملة الأساسية YER_NEW. العملات الأجنبية تحتاج دفتر العملة الأصلية وإعادة تقييم قبل اعتماد الإقفال.",
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }'''
if old not in s:
    raise SystemExit("AccountingScreens conflict shape changed; refusing unsafe automatic resolution")
s = s.replace(old, new, 1)
needle = '''            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { voucherType = "INCOME" }, modifier = Modifier.weight(1f), enabled = balances.isNotEmpty()) { Text("إيراد") }
                OutlinedButton(onClick = { voucherType = "TRANSFER" }, modifier = Modifier.weight(1f), enabled = balances.size > 1) { Text("تحويل") }
            }
'''
extra = needle + '''            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showCashCount = true }, modifier = Modifier.weight(1f), enabled = balances.any { it.kind == "CASH" }) { Text("جرد صندوق") }
                OutlinedButton(onClick = { showBankStatement = true }, modifier = Modifier.weight(1f), enabled = balances.any { it.kind == "BANK" }) { Text("مطابقة بنك") }
            }
'''
if needle not in s:
    raise SystemExit("AccountingScreens quick-action anchor changed")
s = s.replace(needle, extra, 1)
p.write_text(s)

# Customer/supplier profiles: retain professional presentation but enforce invoice-aware settlements.
p = repo / "app/src/main/java/com/fush/erp/ui/screens/PartyScreens.kt"
s = p.read_text()
customer_conflict = '''<<<<<<< HEAD

        ScrollableTabRow(
            selectedTabIndex = tab,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            edgePadding = 10.dp,
        ) {
            tabs.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
=======
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showSettlement = true }) { Text("تحصيل فاتورة") }
            OutlinedButton(onClick = { message = "صرف مبلغ للعميل يجب أن يتم من مرتجع المبيعات مع اختيار رد نقدي حتى تبقى الفاتورة والذمة والأستاذ متطابقة." }) { Text("صرف للعميل") }
>>>>>>> accounting-old
        }'''
supplier_conflict = '''<<<<<<< HEAD

        ScrollableTabRow(
            selectedTabIndex = tab,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            edgePadding = 10.dp,
        ) {
            tabs.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
=======
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showSettlement = true }) { Text("دفع فاتورة") }
            OutlinedButton(onClick = { message = "قبض مبلغ من المورد يجب أن يتم من مرتجع المشتريات مع اختيار استرداد نقدي حتى تبقى الفاتورة والذمة والأستاذ متطابقة." }) { Text("قبض من المورد") }
>>>>>>> accounting-old
        }'''
tabs = '''        ScrollableTabRow(
            selectedTabIndex = tab,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            edgePadding = 10.dp,
        ) {
            tabs.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
        }'''
if customer_conflict not in s or supplier_conflict not in s:
    raise SystemExit("PartyScreens conflict shape changed; refusing unsafe automatic resolution")
s = s.replace(customer_conflict, tabs, 1).replace(supplier_conflict, tabs, 1)

customer_buttons = '''            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { voucherType = "RECEIPT" }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium) { Text("سند قبض") }
                OutlinedButton(onClick = { voucherType = "PAYMENT" }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium) { Text("سند صرف") }
            }
'''
customer_replacement = '''            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showSettlement = true }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium) { Text("تحصيل فاتورة") }
                OutlinedButton(
                    onClick = { message = "صرف مبلغ للعميل يجب أن يتم من مرتجع المبيعات مع اختيار رد نقدي حتى تبقى الفاتورة والذمة والأستاذ متطابقة." },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                ) { Text("صرف للعميل") }
            }
'''
supplier_buttons = '''            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { voucherType = "PAYMENT" }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium) { Text("سند صرف") }
                OutlinedButton(onClick = { voucherType = "RECEIPT" }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium) { Text("سند قبض") }
            }
'''
supplier_replacement = '''            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showSettlement = true }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium) { Text("دفع فاتورة") }
                OutlinedButton(
                    onClick = { message = "قبض مبلغ من المورد يجب أن يتم من مرتجع المشتريات مع اختيار استرداد نقدي حتى تبقى الفاتورة والذمة والأستاذ متطابقة." },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                ) { Text("قبض من المورد") }
            }
'''
if customer_buttons not in s or supplier_buttons not in s:
    raise SystemExit("PartyScreens settlement anchors changed")
s = s.replace(customer_buttons, customer_replacement, 1).replace(supplier_buttons, supplier_replacement, 1)
s = s.replace('    var voucherType by remember { mutableStateOf<String?>(null) }\n', '')
while '    voucherType?.let { type ->' in s:
    start = s.index('    voucherType?.let { type ->')
    candidates = [x for x in (
        s.find('    reverseVoucher?.let', start),
        s.find('    if (showSettlement)', start),
        s.find('\n}\n\n@Composable', start),
    ) if x != -1]
    if not candidates:
        raise SystemExit("Unable to delimit obsolete quick voucher block")
    s = s[:start] + s[min(candidates):]
p.write_text(s)

for f in [repo / "app/build.gradle.kts", repo / "app/src/main/java/com/fush/erp/ui/screens/AccountingScreens.kt", p]:
    text = f.read_text()
    if any(marker in text for marker in ("<<<<<<<", "=======", ">>>>>>>")):
        raise SystemExit(f"Unresolved conflict marker in {f}")

print("ACCOUNTING_REBASE_CONFLICT_RESOLUTION_OK")
