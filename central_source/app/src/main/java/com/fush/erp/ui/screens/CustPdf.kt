package com.fush.erp.ui.screens

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fush.erp.data.entity.CustomerLedgerEventRow
import com.fush.erp.ui.export.ReportExportActions
import com.fush.erp.ui.export.ReportExportDocument
import com.fush.erp.ui.export.ReportExportTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class CustPdfTimedRow(val at: Long, val cells: List<String>)

private data class CustPdfSnapshot(
    val id: Long,
    val code: String,
    val nameAr: String,
    val phone: String,
    val address: String,
    val province: String,
    val channel: String,
    val classification: String,
    val currencyCode: String,
    val creditLimitBase: Double,
    val creditDays: Int,
    val allowCredit: Boolean,
    val salesRepName: String,
    val invoices: List<CustPdfTimedRow>,
    val receipts: List<CustPdfTimedRow>,
    val returns: List<CustPdfTimedRow>,
    val vouchers: List<CustPdfTimedRow>,
)

/**
 * Professional customer-statement renderer intentionally compiled as an isolated unit.
 *
 * Its erased JVM/Dex signature is exactly (LazyListScope, List) -> Unit so the tested
 * renderer can replace the legacy customerLedgerItems call without changing Room or
 * the customer-profile state model. Accounting balances still come from the canonical
 * CustomerLedgerEventRow list already loaded by CustomerProfileScreen.
 */
fun custPdf(
    scope: LazyListScope,
    running: List<Pair<CustomerLedgerEventRow, Double>>,
) {
    scope.item(key = "fush-customer-statement-export") {
        val context = LocalContext.current
        var snapshot by remember(running) { mutableStateOf<CustPdfSnapshot?>(null) }
        var loadFinished by remember(running) { mutableStateOf(false) }
        val earliest = remember(running) { running.minOfOrNull { it.first.eventDate } ?: System.currentTimeMillis() }
        var fromText by remember(running) { mutableStateOf(custPdfDate(earliest)) }
        var toText by remember(running) { mutableStateOf(custPdfDate(System.currentTimeMillis())) }

        LaunchedEffect(running) {
            snapshot = withContext(Dispatchers.IO) { custPdfLoadSnapshot(context, running) }
            loadFinished = true
        }

        val from = remember(fromText) { custPdfParseStart(fromText) }
        val to = remember(toText) { custPdfParseEnd(toText) }
        val periodValid = from != null && to != null && from <= to
        val document = remember(snapshot, running, from, to) {
            if (periodValid && from != null && to != null) {
                custPdfBuildDocument(snapshot, running, from, to)
            } else null
        }
        val customerName = snapshot?.nameAr?.ifBlank { null } ?: "العميل الحالي"
        val baseName = "FUSH-Customer-Statement-${snapshot?.code?.ifBlank { "customer" } ?: "customer"}"

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 2.dp,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("كشف حساب العميل — تقرير محاسبي", style = MaterialTheme.typography.titleLarge)
                Text(
                    "PDF A4 احترافي مع الرصيد الافتتاحي والمدين والدائن والرصيد المتحرك وجداول الفواتير والتحصيلات والمرتجعات والسندات.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (snapshot != null) {
                    Text("$customerName • ${snapshot!!.code} • ${snapshot!!.currencyCode}", style = MaterialTheme.typography.titleMedium)
                } else if (loadFinished) {
                    Text(
                        "تعذر تحديد بطاقة العميل من قاعدة البيانات؛ سيظل كشف الحركات المحاسبية قابلاً للتصدير.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedTextField(
                    value = fromText,
                    onValueChange = { fromText = it },
                    label = { Text("من تاريخ (yyyy-MM-dd)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = toText,
                    onValueChange = { toText = it },
                    label = { Text("إلى تاريخ (yyyy-MM-dd)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!periodValid) {
                    Text("الفترة غير صالحة. أدخل التاريخ بصيغة yyyy-MM-dd وتأكد أن تاريخ البداية لا يتجاوز النهاية.", color = MaterialTheme.colorScheme.error)
                }
                if (document != null) {
                    val periodRows = running.count { it.first.eventDate in from!!..to!! }
                    val opening = running.asSequence().filter { it.first.eventDate < from }.sumOf { it.first.debitBase - it.first.creditBase }
                    val debit = running.asSequence().filter { it.first.eventDate in from..to }.sumOf { it.first.debitBase }
                    val credit = running.asSequence().filter { it.first.eventDate in from..to }.sumOf { it.first.creditBase }
                    val closing = opening + debit - credit
                    Text("الرصيد الافتتاحي: ${custPdfMoney(opening)}", style = MaterialTheme.typography.bodyMedium)
                    Text("إجمالي المدين: ${custPdfMoney(debit)} • إجمالي الدائن: ${custPdfMoney(credit)}", style = MaterialTheme.typography.bodyMedium)
                    Text("الرصيد الختامي: ${custPdfMoney(closing)} • الحركات: $periodRows", style = MaterialTheme.typography.titleMedium)
                    ReportExportActions(
                        document = document,
                        baseName = baseName,
                        printJobName = "كشف حساب $customerName",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (running.isEmpty()) {
        scope.item(key = "fush-customer-ledger-empty") {
            Text(
                "لا توجد حركات على حساب هذا العميل.",
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    } else {
        running.forEachIndexed { index, pair ->
            val event = pair.first
            val balance = pair.second
            scope.item(key = "fush-ledger-${event.eventDate}-${event.referenceNo}-$index") {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "${custPdfDate(event.eventDate)} • ${custPdfEventLabel(event.eventType)}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text("المستند: ${event.referenceNo.ifBlank { "—" }}${event.invoiceNo.takeIf { it.isNotBlank() }?.let { " • الفاتورة: $it" } ?: ""}")
                        Text("مدين ${custPdfMoney(event.debitBase)} • دائن ${custPdfMoney(event.creditBase)} • الرصيد ${custPdfMoney(balance)}")
                        if (event.notes.isNotBlank()) Text(event.notes, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun custPdfBuildDocument(
    snapshot: CustPdfSnapshot?,
    running: List<Pair<CustomerLedgerEventRow, Double>>,
    from: Long,
    to: Long,
): ReportExportDocument {
    val opening = running.asSequence().filter { it.first.eventDate < from }.sumOf { it.first.debitBase - it.first.creditBase }
    val period = running.filter { it.first.eventDate in from..to }
    val debit = period.sumOf { it.first.debitBase }
    val credit = period.sumOf { it.first.creditBase }
    val closing = opening + debit - credit
    val current = running.lastOrNull()?.second ?: 0.0
    val name = snapshot?.nameAr?.ifBlank { null } ?: "العميل الحالي"
    val code = snapshot?.code?.ifBlank { null } ?: "—"
    val currency = snapshot?.currencyCode?.ifBlank { null } ?: period.firstOrNull()?.first?.currencyCode ?: "—"
    val periodLabel = "${custPdfDate(from)} إلى ${custPdfDate(to)}"

    val movementRows = buildList {
        add(listOf(custPdfDate(from), "رصيد افتتاحي", "—", "—", currency, "—", "—", "—", custPdfMoney(opening), "رصيد ما قبل بداية الفترة"))
        period.forEach { (event, balance) ->
            add(
                listOf(
                    custPdfDate(event.eventDate),
                    custPdfEventLabel(event.eventType),
                    event.referenceNo.ifBlank { "—" },
                    event.invoiceNo.ifBlank { "—" },
                    event.currencyCode.ifBlank { currency },
                    custPdfMoney(event.amountOriginal),
                    custPdfMoneyDash(event.debitBase),
                    custPdfMoneyDash(event.creditBase),
                    custPdfMoney(balance),
                    event.notes.ifBlank { "—" },
                )
            )
        }
    }

    val tables = mutableListOf<ReportExportTable>()
    tables += ReportExportTable(
        title = "الحركات المحاسبية والرصيد المتحرك",
        headers = listOf("التاريخ", "نوع الحركة", "المستند", "الفاتورة / المرجع", "العملة", "المبلغ الأصلي", "مدين", "دائن", "الرصيد", "البيان"),
        rows = movementRows,
    )
    snapshot?.let { s ->
        tables += ReportExportTable(
            title = "فواتير المبيعات خلال الفترة",
            headers = listOf("التاريخ", "رقم الفاتورة", "نوع البيع", "الاستحقاق", "العملة", "الإجمالي الأصلي", "سعر الصرف", "الإجمالي الأساسي", "الحالة", "ملاحظات"),
            rows = s.invoices.filter { it.at in from..to }.map { it.cells },
        )
        tables += ReportExportTable(
            title = "تحصيلات العميل خلال الفترة",
            headers = listOf("التاريخ", "رقم التحصيل", "الحركة", "العملة", "المبلغ الأصلي", "سعر الصرف", "المبلغ الأساسي", "ملاحظات"),
            rows = s.receipts.filter { it.at in from..to }.map { it.cells },
        )
        tables += ReportExportTable(
            title = "مرتجعات المبيعات خلال الفترة",
            headers = listOf("التاريخ", "رقم المرتجع", "الفاتورة", "نوع التسوية", "العملة", "المبلغ الأصلي", "سعر الصرف", "المبلغ الأساسي", "الحالة", "السبب"),
            rows = s.returns.filter { it.at in from..to }.map { it.cells },
        )
        tables += ReportExportTable(
            title = "سندات العميل خلال الفترة",
            headers = listOf("التاريخ", "رقم السند", "النوع", "الحالة", "العملة", "المبلغ الأصلي", "سعر الصرف", "المبلغ الأساسي", "المرجع", "البيان / سبب العكس"),
            rows = s.vouchers.filter { it.at in from..to }.map { it.cells },
        )
    }

    val creditPolicy = snapshot?.let {
        if (it.allowCredit) "مسموح • ${it.creditDays} يوم • سقف ${custPdfMoney(it.creditLimitBase)} بالعملة الأساسية" else "الائتمان غير مفعّل"
    } ?: "—"

    return ReportExportDocument(
        title = "كشف حساب العميل",
        subtitle = "FUSH ERP • كشف محاسبي تفصيلي • الفترة $periodLabel",
        summary = listOf(
            "اسم العميل" to name,
            "كود العميل" to code,
            "الهاتف" to (snapshot?.phone?.ifBlank { "—" } ?: "—"),
            "العنوان" to (snapshot?.address?.ifBlank { "—" } ?: "—"),
            "المحافظة" to (snapshot?.province?.ifBlank { "—" } ?: "—"),
            "قناة البيع" to custPdfChannel(snapshot?.channel.orEmpty()),
            "التصنيف" to (snapshot?.classification?.ifBlank { "—" } ?: "—"),
            "عملة العميل" to currency,
            "مندوب المبيعات" to (snapshot?.salesRepName?.ifBlank { "—" } ?: "—"),
            "السياسة الائتمانية" to creditPolicy,
            "الفترة" to periodLabel,
            "عدد الحركات" to period.size.toString(),
            "الرصيد الافتتاحي" to custPdfMoney(opening),
            "إجمالي المدين" to custPdfMoney(debit),
            "إجمالي الدائن" to custPdfMoney(credit),
            "الرصيد الختامي للفترة" to custPdfMoney(closing),
            "الرصيد الحالي حتى آخر حركة" to custPdfMoney(current),
        ),
        tables = tables,
        notes = buildList {
            add("المدين والدائن والرصيد المتحرك محسوبة من نفس سجل حركات العميل المستخدم في شاشة كشف الحساب.")
            add("القيم الأساسية تظهر في أعمدة المدين والدائن والرصيد، بينما يظهر المبلغ الأصلي وعملته في عمود مستقل.")
            add("الرصيد الموجب يعني مبلغًا مستحقًا على العميل، والرصيد السالب يعني رصيدًا دائنًا لصالح العميل.")
            add("الحركات المعكوسة تبقى ظاهرة ضمن السجل للحفاظ على الأثر المحاسبي والتدقيقي.")
            if (period.isEmpty()) add("لا توجد حركات محاسبية للعميل ضمن الفترة المحددة.")
        },
    )
}

private fun custPdfLoadSnapshot(
    context: Context,
    running: List<Pair<CustomerLedgerEventRow, Double>>,
): CustPdfSnapshot? {
    val dbFile = context.getDatabasePath("fush_erp.db")
    if (!dbFile.isFile) return null
    return runCatching {
        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val customerId = custPdfFindCustomerId(db, running) ?: return@use null
            val customer = db.rawQuery(
                "SELECT code,nameAr,phone,address,province,channel,classification,currencyCode,creditLimitBase,creditDays,allowCredit,salesRepName FROM customers WHERE id=? LIMIT 1",
                arrayOf(customerId.toString()),
            ).use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                CustPdfSnapshot(
                    id = customerId,
                    code = custPdfText(cursor, 0),
                    nameAr = custPdfText(cursor, 1),
                    phone = custPdfText(cursor, 2),
                    address = custPdfText(cursor, 3),
                    province = custPdfText(cursor, 4),
                    channel = custPdfText(cursor, 5),
                    classification = custPdfText(cursor, 6),
                    currencyCode = custPdfText(cursor, 7),
                    creditLimitBase = cursor.getDouble(8),
                    creditDays = cursor.getInt(9),
                    allowCredit = cursor.getInt(10) != 0,
                    salesRepName = custPdfText(cursor, 11),
                    invoices = emptyList(), receipts = emptyList(), returns = emptyList(), vouchers = emptyList(),
                )
            }
            customer.copy(
                invoices = custPdfLoadInvoices(db, customerId),
                receipts = custPdfLoadReceipts(db, customerId),
                returns = custPdfLoadReturns(db, customerId),
                vouchers = custPdfLoadVouchers(db, customerId),
            )
        }
    }.getOrNull()
}

private fun custPdfFindCustomerId(db: SQLiteDatabase, running: List<Pair<CustomerLedgerEventRow, Double>>): Long? {
    for ((event, _) in running) {
        val invoiceNo = event.invoiceNo.trim()
        val reference = event.referenceNo.trim()
        if (invoiceNo.isNotBlank()) {
            custPdfOneLong(db, "SELECT customerId FROM sales_invoices WHERE invoiceNo=? LIMIT 1", invoiceNo)?.let { return it }
        }
        if (reference.isNotBlank()) {
            custPdfOneLong(db, "SELECT customerId FROM sales_invoices WHERE invoiceNo=? LIMIT 1", reference)?.let { return it }
            custPdfOneLong(db, "SELECT customerId FROM customer_receipts WHERE receiptNo=? LIMIT 1", reference)?.let { return it }
            custPdfOneLong(db, "SELECT customerId FROM sales_returns WHERE returnNo=? LIMIT 1", reference)?.let { return it }
            custPdfOneLong(db, "SELECT customerId FROM party_vouchers WHERE voucherNo=? AND customerId IS NOT NULL LIMIT 1", reference)?.let { return it }
            custPdfOneLong(db, "SELECT customerId FROM party_vouchers WHERE referenceNo=? AND customerId IS NOT NULL LIMIT 1", reference)?.let { return it }
        }
    }
    return null
}

private fun custPdfLoadInvoices(db: SQLiteDatabase, customerId: Long): List<CustPdfTimedRow> =
    db.rawQuery(
        "SELECT invoiceDate,invoiceNo,paymentType,dueDate,currencyCode,totalOriginal,exchangeRate,totalBase,status,notes FROM sales_invoices WHERE customerId=? ORDER BY invoiceDate,invoiceNo",
        arrayOf(customerId.toString()),
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                val at = c.getLong(0)
                add(CustPdfTimedRow(at, listOf(
                    custPdfDate(at), custPdfText(c,1), if (custPdfText(c,2)=="CASH") "نقدي" else "آجل",
                    if (c.isNull(3)) "—" else custPdfDate(c.getLong(3)), custPdfText(c,4), custPdfMoney(c.getDouble(5)),
                    custPdfRate(c.getDouble(6)), custPdfMoney(c.getDouble(7)), custPdfStatus(custPdfText(c,8)), custPdfText(c,9).ifBlank { "—" }
                )))
            }
        }
    }

private fun custPdfLoadReceipts(db: SQLiteDatabase, customerId: Long): List<CustPdfTimedRow> =
    db.rawQuery(
        "SELECT receiptDate,receiptNo,reversalOfReceiptId,currencyCode,amountOriginal,exchangeRate,amountBase,notes FROM customer_receipts WHERE customerId=? ORDER BY receiptDate,receiptNo",
        arrayOf(customerId.toString()),
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                val at = c.getLong(0)
                add(CustPdfTimedRow(at, listOf(
                    custPdfDate(at), custPdfText(c,1), if (c.isNull(2)) "تحصيل" else "عكس تحصيل", custPdfText(c,3),
                    custPdfMoney(c.getDouble(4)), custPdfRate(c.getDouble(5)), custPdfMoney(c.getDouble(6)), custPdfText(c,7).ifBlank { "—" }
                )))
            }
        }
    }

private fun custPdfLoadReturns(db: SQLiteDatabase, customerId: Long): List<CustPdfTimedRow> =
    db.rawQuery(
        "SELECT sr.returnDate,sr.returnNo,COALESCE(si.invoiceNo,''),sr.settlementType,sr.currencyCode,sr.totalOriginal,sr.exchangeRate,sr.totalBase,sr.status,sr.reason FROM sales_returns sr LEFT JOIN sales_invoices si ON si.id=sr.salesInvoiceId WHERE sr.customerId=? ORDER BY sr.returnDate,sr.returnNo",
        arrayOf(customerId.toString()),
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                val at = c.getLong(0)
                add(CustPdfTimedRow(at, listOf(
                    custPdfDate(at), custPdfText(c,1), custPdfText(c,2).ifBlank { "—" }, custPdfSettlement(custPdfText(c,3)), custPdfText(c,4),
                    custPdfMoney(c.getDouble(5)), custPdfRate(c.getDouble(6)), custPdfMoney(c.getDouble(7)), custPdfStatus(custPdfText(c,8)), custPdfText(c,9).ifBlank { "—" }
                )))
            }
        }
    }

private fun custPdfLoadVouchers(db: SQLiteDatabase, customerId: Long): List<CustPdfTimedRow> =
    db.rawQuery(
        "SELECT voucherDate,voucherNo,voucherType,status,currencyCode,amountOriginal,exchangeRate,amountBase,referenceNo,description,reversalReason FROM party_vouchers WHERE customerId=? ORDER BY voucherDate,voucherNo",
        arrayOf(customerId.toString()),
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                val at = c.getLong(0)
                val description = listOf(custPdfText(c,9), custPdfText(c,10).takeIf { it.isNotBlank() }?.let { "سبب العكس: $it" }).filterNotNull().filter { it.isNotBlank() }.joinToString(" • ").ifBlank { "—" }
                add(CustPdfTimedRow(at, listOf(
                    custPdfDate(at), custPdfText(c,1), if (custPdfText(c,2)=="RECEIPT") "سند قبض" else "سند صرف", custPdfStatus(custPdfText(c,3)),
                    custPdfText(c,4), custPdfMoney(c.getDouble(5)), custPdfRate(c.getDouble(6)), custPdfMoney(c.getDouble(7)), custPdfText(c,8).ifBlank { "—" }, description
                )))
            }
        }
    }

private fun custPdfOneLong(db: SQLiteDatabase, sql: String, arg: String): Long? =
    runCatching { db.rawQuery(sql, arrayOf(arg)).use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null } }.getOrNull()

private fun custPdfText(cursor: Cursor, index: Int): String = if (cursor.isNull(index)) "" else cursor.getString(index).orEmpty()

private fun custPdfParseStart(value: String): Long? = custPdfParse(value, false)
private fun custPdfParseEnd(value: String): Long? = custPdfParse(value, true)

private fun custPdfParse(value: String, endOfDay: Boolean): Long? {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    return runCatching {
        val base = formatter.parse(value.trim())?.time ?: return null
        if (endOfDay) base + 86_399_999L else base
    }.getOrNull()
}

private fun custPdfDate(value: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(value))
private fun custPdfMoney(value: Double): String = DecimalFormat("#,##0.00").format(value)
private fun custPdfMoneyDash(value: Double): String = if (kotlin.math.abs(value) < 0.000001) "—" else custPdfMoney(value)
private fun custPdfRate(value: Double): String = DecimalFormat("#,##0.########").format(value)

private fun custPdfEventLabel(value: String): String = when (value) {
    "INVOICE" -> "فاتورة مبيعات"
    "RECEIPT" -> "تحصيل"
    "RECEIPT_REVERSAL" -> "عكس تحصيل"
    "SALES_RETURN" -> "مرتجع مبيعات"
    "CASH_REFUND" -> "رد نقدي"
    "VOUCHER_RECEIPT", "CUSTOMER_RECEIPT_VOUCHER" -> "سند قبض"
    "VOUCHER_PAYMENT", "CUSTOMER_PAYMENT_VOUCHER" -> "سند صرف"
    "VOUCHER_REVERSAL" -> "عكس سند"
    else -> value.ifBlank { "حركة محاسبية" }
}

private fun custPdfStatus(value: String): String = when (value) {
    "POSTED" -> "مرحّل"
    "REVERSED" -> "معكوس"
    "DRAFT" -> "مسودة"
    "CANCELLED" -> "ملغي"
    else -> value.ifBlank { "—" }
}

private fun custPdfSettlement(value: String): String = when (value) {
    "CUSTOMER_CREDIT" -> "رصيد للعميل"
    "CASH_REFUND" -> "رد نقدي"
    else -> value.ifBlank { "—" }
}

private fun custPdfChannel(value: String): String = when (value) {
    "WHOLESALE" -> "جملة"
    "RETAIL" -> "تجزئة"
    "DISTRIBUTOR" -> "موزع"
    else -> value.ifBlank { "—" }
}
