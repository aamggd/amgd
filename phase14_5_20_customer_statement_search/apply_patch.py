from pathlib import Path

ROOT = Path('FushERP_Mobile_Phase5')


def repl(rel: str, old: str, new: str) -> None:
    p = ROOT / rel
    text = p.read_text(encoding='utf-8')
    if old not in text:
        raise SystemExit(f'Pattern not found in {rel}: {old[:120]!r}')
    p.write_text(text.replace(old, new, 1), encoding='utf-8')


def insert_after(rel: str, anchor: str, addition: str) -> None:
    p = ROOT / rel
    text = p.read_text(encoding='utf-8')
    if addition in text:
        return
    if anchor not in text:
        raise SystemExit(f'Anchor not found in {rel}: {anchor[:120]!r}')
    p.write_text(text.replace(anchor, anchor + addition, 1), encoding='utf-8')

repl('app/build.gradle.kts', 'versionCode = 58', 'versionCode = 59')
repl('app/build.gradle.kts', 'versionName = "0.15.4.19-phase14.5-collection-details"', 'versionName = "0.15.4.20-phase14.5-customer-statement-search"')

insert_after(
    'app/src/main/java/com/fush/erp/data/entity/SalesEntities.kt',
    '''data class CustomerReceivableRow(\n    val customerId: Long,\n    val customerName: String,\n    val province: String,\n    val classification: String,\n    val creditLimitBase: Double,\n    val totalDueBase: Double,\n    val paidBase: Double,\n    val outstandingBase: Double,\n    val overdueBase: Double\n)\n''',
    '''\n\ndata class CustomerLedgerEventRow(\n    val eventDate: Long,\n    val eventOrder: Int,\n    val eventType: String,\n    val referenceNo: String,\n    val invoiceNo: String,\n    val currencyCode: String,\n    val amountOriginal: Double,\n    val debitBase: Double,\n    val creditBase: Double,\n    val notes: String\n)\n'''
)

insert_after(
    'app/src/main/java/com/fush/erp/data/dao/SalesDaos.kt',
    '''    suspend fun customerOutstandingBase(customerId: Long): Double\n''',
    '''\n    @Query("""\n        SELECT si.invoiceDate AS eventDate,\n               10 AS eventOrder,\n               'INVOICE' AS eventType,\n               si.invoiceNo AS referenceNo,\n               si.invoiceNo AS invoiceNo,\n               si.currencyCode AS currencyCode,\n               si.totalOriginal AS amountOriginal,\n               si.totalBase AS debitBase,\n               0.0 AS creditBase,\n               si.notes AS notes\n        FROM sales_invoices si\n        WHERE si.customerId = :customerId AND si.status = 'POSTED'\n\n        UNION ALL\n\n        SELECT sr.returnDate AS eventDate,\n               20 AS eventOrder,\n               'SALES_RETURN' AS eventType,\n               sr.returnNo AS referenceNo,\n               si.invoiceNo AS invoiceNo,\n               sr.currencyCode AS currencyCode,\n               -sr.totalOriginal AS amountOriginal,\n               0.0 AS debitBase,\n               sr.totalBase AS creditBase,\n               sr.reason AS notes\n        FROM sales_returns sr\n        JOIN sales_invoices si ON si.id = sr.salesInvoiceId\n        WHERE sr.customerId = :customerId AND sr.status = 'POSTED'\n\n        UNION ALL\n\n        SELECT sr.returnDate AS eventDate,\n               25 AS eventOrder,\n               'CASH_REFUND' AS eventType,\n               sr.returnNo AS referenceNo,\n               si.invoiceNo AS invoiceNo,\n               sr.currencyCode AS currencyCode,\n               sr.totalOriginal AS amountOriginal,\n               sr.totalBase AS debitBase,\n               0.0 AS creditBase,\n               sr.reason AS notes\n        FROM sales_returns sr\n        JOIN sales_invoices si ON si.id = sr.salesInvoiceId\n        WHERE sr.customerId = :customerId\n          AND sr.status = 'POSTED'\n          AND sr.settlementType = 'CASH_REFUND'\n\n        UNION ALL\n\n        SELECT cr.receiptDate AS eventDate,\n               30 AS eventOrder,\n               'RECEIPT' AS eventType,\n               cr.receiptNo AS referenceNo,\n               COALESCE((SELECT GROUP_CONCAT(si2.invoiceNo, ', ')\n                         FROM customer_receipt_allocations cra\n                         JOIN sales_invoices si2 ON si2.id = cra.invoiceId\n                         WHERE cra.receiptId = cr.id), '') AS invoiceNo,\n               cr.currencyCode AS currencyCode,\n               -cr.amountOriginal AS amountOriginal,\n               0.0 AS debitBase,\n               cr.amountBase AS creditBase,\n               cr.notes AS notes\n        FROM customer_receipts cr\n        WHERE cr.customerId = :customerId\n\n        ORDER BY eventDate, eventOrder, referenceNo\n    """)\n    suspend fun customerLedgerEvents(customerId: Long): List<CustomerLedgerEventRow>\n'''
)

insert_after(
    'app/src/main/java/com/fush/erp/data/dao/SalesDaos.kt',
    '''    suspend fun returnedBaseForInvoice(invoiceId: Long): Double\n''',
    '''\n    @Query("SELECT COALESCE(SUM(totalBase), 0) FROM sales_returns WHERE salesInvoiceId = :invoiceId AND status = 'POSTED' AND settlementType = 'CUSTOMER_CREDIT'")\n    suspend fun customerCreditReturnedBaseForInvoice(invoiceId: Long): Double\n'''
)

sales_dao = ROOT / 'app/src/main/java/com/fush/erp/data/dao/SalesDaos.kt'
sales_text = sales_dao.read_text(encoding='utf-8')
sales_text = sales_text.replace("AND sr.status = 'POSTED' AND sx2.paymentType = 'CREDIT'", "AND sr.status = 'POSTED' AND sr.settlementType = 'CUSTOMER_CREDIT' AND sx2.paymentType = 'CREDIT'")
sales_text = sales_text.replace("sr.salesInvoiceId = si.id AND sr.status = 'POSTED'", "sr.salesInvoiceId = si.id AND sr.status = 'POSTED' AND sr.settlementType = 'CUSTOMER_CREDIT'")
sales_text = sales_text.replace("sr0.salesInvoiceId = si.id AND sr0.status = 'POSTED'", "sr0.salesInvoiceId = si.id AND sr0.status = 'POSTED' AND sr0.settlementType = 'CUSTOMER_CREDIT'")
sales_text = sales_text.replace("srP.salesInvoiceId = si.id AND srP.status = 'POSTED'", "srP.salesInvoiceId = si.id AND srP.status = 'POSTED' AND srP.settlementType = 'CUSTOMER_CREDIT'")
sales_text = sales_text.replace("sr2.salesInvoiceId = si.id AND sr2.status = 'POSTED'", "sr2.salesInvoiceId = si.id AND sr2.status = 'POSTED' AND sr2.settlementType = 'CUSTOMER_CREDIT'")
sales_dao.write_text(sales_text, encoding='utf-8')

repl(
    'app/src/main/java/com/fush/erp/domain/SalesService.kt',
    '        val returned = db.salesDao().returnedBaseForInvoice(invoiceId)\n        return (invoice.totalBase - received - returned).coerceAtLeast(0.0)',
    '        val returned = db.salesDao().customerCreditReturnedBaseForInvoice(invoiceId)\n        return (invoice.totalBase - received - returned).coerceAtLeast(0.0)'
)

report_dao = ROOT / 'app/src/main/java/com/fush/erp/data/dao/ReportDao.kt'
report_text = report_dao.read_text(encoding='utf-8')
report_text = report_text.replace("sr.salesInvoiceId=si.id AND sr.status='POSTED' AND sr.returnDate <= :to", "sr.salesInvoiceId=si.id AND sr.status='POSTED' AND sr.settlementType='CUSTOMER_CREDIT' AND sr.returnDate <= :to")
report_text = report_text.replace("sr2.salesInvoiceId=si2.id AND sr2.status='POSTED' AND sr2.returnDate <= :to", "sr2.salesInvoiceId=si2.id AND sr2.status='POSTED' AND sr2.settlementType='CUSTOMER_CREDIT' AND sr2.returnDate <= :to")
report_text = report_text.replace("sro.salesInvoiceId=sio.id AND sro.status='POSTED' AND sro.returnDate <= :to", "sro.salesInvoiceId=sio.id AND sro.status='POSTED' AND sro.settlementType='CUSTOMER_CREDIT' AND sro.returnDate <= :to")
report_dao.write_text(report_text, encoding='utf-8')

repl(
    'app/src/main/java/com/fush/erp/ui/screens/SalesScreens.kt',
    '''    var detailInvoice by remember { mutableStateOf<SalesInvoiceSummary?>(null) }\n    var message by remember { mutableStateOf<String?>(null) }\n''',
    '''    var detailInvoice by remember { mutableStateOf<SalesInvoiceSummary?>(null) }\n    var statementCustomer by remember { mutableStateOf<CustomerEntity?>(null) }\n    var customerSearch by remember { mutableStateOf("") }\n    var message by remember { mutableStateOf<String?>(null) }\n    val filteredCustomers = remember(customers, customerSearch) {\n        val q = customerSearch.trim().lowercase(Locale.ROOT)\n        if (q.isBlank()) customers else customers.filter { customer ->\n            listOf(customer.nameAr, customer.nameEn, customer.code, customer.phone, customer.province, customer.address)\n                .any { it.lowercase(Locale.ROOT).contains(q) }\n        }\n    }\n'''
)

repl(
    'app/src/main/java/com/fush/erp/ui/screens/SalesScreens.kt',
    '''        item { Text("العملاء (${customers.size})", style = MaterialTheme.typography.titleMedium) }\n        items(customers) { customer ->\n''',
    '''        item {\n            Text("العملاء (${customers.size})", style = MaterialTheme.typography.titleMedium)\n            OutlinedTextField(\n                value = customerSearch,\n                onValueChange = { customerSearch = it },\n                label = { Text("بحث عن العميل") },\n                placeholder = { Text("الاسم، الكود، الهاتف، المحافظة أو العنوان") },\n                singleLine = true,\n                modifier = Modifier.fillMaxWidth()\n            )\n            if (customerSearch.isNotBlank()) {\n                Text("نتائج البحث: ${filteredCustomers.size}", style = MaterialTheme.typography.bodySmall)\n            }\n        }\n        items(filteredCustomers, key = { it.id }) { customer ->\n'''
)

repl(
    'app/src/main/java/com/fush/erp/ui/screens/SalesScreens.kt',
    '''                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {\n                        TextButton(onClick = { editCustomer = customer }) { Text("تعديل بيانات العميل") }\n                    }\n''',
    '''                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {\n                        TextButton(onClick = { statementCustomer = customer }) { Text("كشف حساب تفصيلي") }\n                        TextButton(onClick = { editCustomer = customer }) { Text("تعديل بيانات العميل") }\n                    }\n'''
)

repl(
    'app/src/main/java/com/fush/erp/ui/screens/SalesScreens.kt',
    '''    if (showCustomer) {\n''',
    '''    statementCustomer?.let { customer ->\n        CustomerAccountStatementDialog(container = container, customer = customer, onDismiss = { statementCustomer = null })\n    }\n\n    if (showCustomer) {\n'''
)

statement = '''@Composable\nprivate fun CustomerAccountStatementDialog(\n    container: AppContainer,\n    customer: CustomerEntity,\n    onDismiss: () -> Unit\n) {\n    val events by produceState(initialValue = emptyList<CustomerLedgerEventRow>(), key1 = customer.id) {\n        value = container.db.salesDao().customerLedgerEvents(customer.id)\n    }\n    val running = remember(events) {\n        var balance = 0.0\n        events.map { event ->\n            balance += event.debitBase - event.creditBase\n            event to balance\n        }\n    }\n    val totalDebit = remember(events) { events.sumOf { it.debitBase } }\n    val totalCredit = remember(events) { events.sumOf { it.creditBase } }\n    val closing = running.lastOrNull()?.second ?: 0.0\n\n    Dialog(onDismissRequest = onDismiss) {\n        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 8.dp, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.95f)) {\n            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {\n                Text("كشف حساب العميل", style = MaterialTheme.typography.headlineSmall)\n                Text("${customer.nameAr} • ${customer.code}")\n                Text("${customer.province} • ${salesChannelLabel(customer.channel)} • ${customer.currencyCode}", style = MaterialTheme.typography.bodySmall)\n                HorizontalDivider()\n                Text("إجمالي المدين: ${salesMoney(totalDebit)} ريال", style = MaterialTheme.typography.titleSmall)\n                Text("إجمالي الدائن: ${salesMoney(totalCredit)} ريال", style = MaterialTheme.typography.titleSmall)\n                Text(if (closing >= 0.0) "الرصيد المستحق على العميل: ${salesMoney(closing)} ريال" else "رصيد لصالح العميل: ${salesMoney(-closing)} ريال", style = MaterialTheme.typography.titleMedium, color = if (closing > 0.000001) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)\n                HorizontalDivider()\n                Text("الحركة التفصيلية", style = MaterialTheme.typography.titleMedium)\n                if (running.isEmpty()) Text("لا توجد حركات على حساب هذا العميل.")\n                running.forEach { (event, balance) ->\n                    val type = when (event.eventType) {\n                        "INVOICE" -> "فاتورة بيع"\n                        "RECEIPT" -> "تحصيل"\n                        "SALES_RETURN" -> "مرتجع مبيعات"\n                        "CASH_REFUND" -> "رد نقدي للعميل"\n                        else -> event.eventType\n                    }\n                    ElevatedCard(Modifier.fillMaxWidth()) {\n                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {\n                            Text("${salesDate(event.eventDate)} • $type • ${event.referenceNo}", style = MaterialTheme.typography.titleSmall)\n                            if (event.invoiceNo.isNotBlank()) Text("الفاتورة: ${event.invoiceNo}")\n                            Text("المبلغ الأصلي: ${salesMoney(kotlin.math.abs(event.amountOriginal))} ${event.currencyCode}")\n                            Text("مدين: ${salesMoney(event.debitBase)} • دائن: ${salesMoney(event.creditBase)}")\n                            Text(if (balance >= 0.0) "الرصيد بعد الحركة: ${salesMoney(balance)} على العميل" else "الرصيد بعد الحركة: ${salesMoney(-balance)} لصالح العميل", style = MaterialTheme.typography.bodySmall)\n                            if (event.notes.isNotBlank()) Text(event.notes, style = MaterialTheme.typography.bodySmall)\n                        }\n                    }\n                }\n                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Button(onClick = onDismiss) { Text("إغلاق") } }\n            }\n        }\n    }\n}\n\n'''
repl('app/src/main/java/com/fush/erp/ui/screens/SalesScreens.kt', '@Composable\nprivate fun AddCustomerDialog(\n', statement + '@Composable\nprivate fun AddCustomerDialog(\n')

(ROOT / 'PHASE14_5_20_SCOPE.md').write_text('''# Phase 14.5.20 — Customer Statement and Search\n\n- Added customer search by Arabic/English name, customer code, phone, province, or address.\n- Added a detailed customer account statement from each customer card.\n- Statement shows posted sales invoices, receipts, sales returns, and cash-refund counterpart movements.\n- Running balance is calculated after every event in base currency.\n- Cash sales appear as invoice + automatic receipt and therefore settle to zero.\n- Cash-refund returns appear as return credit + refund debit so the customer balance stays correct.\n- Corrected receivable/outstanding calculations so only CUSTOMER_CREDIT returns reduce accounts receivable; CASH_REFUND affects cash instead.\n- Summary shows total debit, total credit, and current customer balance.\n- Room schema remains 23; no migration is required.\n''', encoding='utf-8')
print('patched')
