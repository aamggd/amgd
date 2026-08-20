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

private data class CustPdfSnapshot(
    val code: String,
    val nameAr: String,
    val phone: String,
    val address: String,
    val province: String,
    val classification: String,
    val currencyCode: String,
    val creditLimitBase: Double,
    val creditDays: Int,
    val allowCredit: Boolean,
    val salesRepName: String,
)

/**
 * Binary-portable professional customer statement renderer.
 * Accounting values are read exclusively from the canonical CustomerLedgerEventRow list
 * already loaded by CustomerProfileScreen. The SQLite lookup is optional/read-only and is
 * used only for customer master-data headings; report generation still works if it fails.
 */
fun custPdf(
    scope: LazyListScope,
    running: List<Pair<CustomerLedgerEventRow, Double>>,
) {
    scope.item(key = "fush-customer-statement-export") {
        val context = LocalContext.current
        var snapshot by remember(running) { mutableStateOf<CustPdfSnapshot?>(null) }
        val earliest = remember(running) {
            running.minOfOrNull { it.first.eventDate } ?: System.currentTimeMillis()
        }
        var fromText by remember(running) { mutableStateOf(custPdfDate(earliest)) }
        var toText by remember(running) { mutableStateOf(custPdfDate(System.currentTimeMillis())) }

        LaunchedEffect(running) {
            snapshot = withContext(Dispatchers.IO) { custPdfLoadSnapshot(context, running) }
        }

        val fromParsed = remember(fromText) { custPdfParse(fromText, false) }
        val toParsed = remember(toText) { custPdfParse(toText, true) }
        val start = fromParsed ?: Long.MAX_VALUE
        val end = toParsed ?: Long.MIN_VALUE
        val periodValid = fromParsed != null && toParsed != null && start <= end
        val document = remember(snapshot, running, start, end, periodValid) {
            if (periodValid) custPdfBuildDocument(snapshot, running, start, end) else null
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
                    "PDF A4 احترافي: رصيد افتتاحي، مدين، دائن، رصيد متحرك، وإجماليات وجداول تفصيلية حسب نوع الحركة.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                snapshot?.let {
                    Text("$customerName • ${it.code} • ${it.currencyCode}", style = MaterialTheme.typography.titleMedium)
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
                    Text(
                        "الفترة غير صالحة. استخدم yyyy-MM-dd وتأكد أن تاريخ البداية لا يتجاوز النهاية.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (document != null) {
                    val periodRows = running.count { it.first.eventDate in start..end }
                    val opening = running.asSequence()
                        .filter { it.first.eventDate < start }
                        .sumOf { it.first.debitBase - it.first.creditBase }
                    val debit = running.asSequence()
                        .filter { it.first.eventDate in start..end }
                        .sumOf { it.first.debitBase }
                    val credit = running.asSequence()
                        .filter { it.first.eventDate in start..end }
                        .sumOf { it.first.creditBase }
                    val closing = opening + debit - credit
                    Text("الرصيد الافتتاحي: ${custPdfMoney(opening)}")
                    Text("إجمالي المدين: ${custPdfMoney(debit)} • إجمالي الدائن: ${custPdfMoney(credit)}")
                    Text(
                        "الرصيد الختامي: ${custPdfMoney(closing)} • عدد الحركات: $periodRows",
                        style = MaterialTheme.typography.titleMedium,
                    )
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
                        Text(
                            "المستند: ${event.referenceNo.ifBlank { "—" }}" +
                                event.invoiceNo.takeIf { it.isNotBlank() }?.let { " • الفاتورة: $it" }.orEmpty()
                        )
                        Text(
                            "مدين ${custPdfMoney(event.debitBase)} • دائن ${custPdfMoney(event.creditBase)} • الرصيد ${custPdfMoney(balance)}"
                        )
                        if (event.notes.isNotBlank()) {
                            Text(event.notes, style = MaterialTheme.typography.bodySmall)
                        }
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
    val opening = running.asSequence()
        .filter { it.first.eventDate < from }
        .sumOf { it.first.debitBase - it.first.creditBase }
    val period = running.filter { it.first.eventDate in from..to }
    val debit = period.sumOf { it.first.debitBase }
    val credit = period.sumOf { it.first.creditBase }
    val closing = opening + debit - credit
    val current = running.lastOrNull()?.second ?: 0.0
    val currency = snapshot?.currencyCode?.ifBlank { null }
        ?: period.firstOrNull()?.first?.currencyCode
        ?: "—"

    val allRows = buildList {
        add(
            listOf(
                custPdfDate(from), "رصيد افتتاحي", "—", "—", currency,
                "—", "—", "—", custPdfMoney(opening), "رصيد ما قبل بداية الفترة"
            )
        )
        period.forEach { (event, balance) -> add(custPdfMovementRow(event, balance, currency)) }
    }

    val tables = mutableListOf(
        ReportExportTable(
            title = "الحركات المحاسبية والرصيد المتحرك",
            headers = custPdfHeaders(),
            rows = allRows,
        )
    )

    fun addGroup(title: String, accepted: Set<String>) {
        val rows = period.filter { it.first.eventType in accepted }
            .map { (event, balance) -> custPdfMovementRow(event, balance, currency) }
        if (rows.isNotEmpty()) {
            tables += ReportExportTable(title = title, headers = custPdfHeaders(), rows = rows)
        }
    }

    addGroup("فواتير المبيعات", setOf("INVOICE"))
    addGroup("التحصيلات وعكس التحصيل", setOf("RECEIPT", "RECEIPT_REVERSAL"))
    addGroup("مرتجعات المبيعات ورد المبالغ", setOf("SALES_RETURN", "CASH_REFUND"))
    addGroup(
        "سندات العميل والعكس",
        setOf("VOUCHER_RECEIPT", "CUSTOMER_RECEIPT_VOUCHER", "VOUCHER_PAYMENT", "CUSTOMER_PAYMENT_VOUCHER", "VOUCHER_REVERSAL")
    )

    val creditPolicy = snapshot?.let {
        if (it.allowCredit) {
            "مسموح • ${it.creditDays} يوم • سقف ${custPdfMoney(it.creditLimitBase)} بالعملة الأساسية"
        } else "الائتمان غير مفعّل"
    } ?: "—"

    return ReportExportDocument(
        title = "كشف حساب العميل",
        subtitle = "FUSH ERP • كشف محاسبي تفصيلي • ${custPdfDate(from)} إلى ${custPdfDate(to)}",
        summary = listOf(
            "اسم العميل" to (snapshot?.nameAr?.ifBlank { "—" } ?: "العميل الحالي"),
            "كود العميل" to (snapshot?.code?.ifBlank { "—" } ?: "—"),
            "الهاتف" to (snapshot?.phone?.ifBlank { "—" } ?: "—"),
            "العنوان" to (snapshot?.address?.ifBlank { "—" } ?: "—"),
            "المحافظة" to (snapshot?.province?.ifBlank { "—" } ?: "—"),
            "التصنيف" to (snapshot?.classification?.ifBlank { "—" } ?: "—"),
            "عملة العميل" to currency,
            "مندوب المبيعات" to (snapshot?.salesRepName?.ifBlank { "—" } ?: "—"),
            "السياسة الائتمانية" to creditPolicy,
            "عدد الحركات" to period.size.toString(),
            "الرصيد الافتتاحي" to custPdfMoney(opening),
            "إجمالي المدين" to custPdfMoney(debit),
            "إجمالي الدائن" to custPdfMoney(credit),
            "الرصيد الختامي للفترة" to custPdfMoney(closing),
            "الرصيد الحالي حتى آخر حركة" to custPdfMoney(current),
        ),
        tables = tables,
        notes = listOf(
            "المدين والدائن والرصيد المتحرك مأخوذة من نفس سجل حركات العميل المستخدم في شاشة كشف الحساب.",
            "الرصيد الموجب يعني مبلغًا مستحقًا على العميل، والرصيد السالب يعني رصيدًا دائنًا لصالح العميل.",
            "الفاتورة والتحصيل والمرتجع والسند والعكس تبقى ظاهرة حسب الحركة المحاسبية الفعلية للحفاظ على الأثر التدقيقي.",
        ),
    )
}

private fun custPdfHeaders(): List<String> = listOf(
    "التاريخ", "نوع الحركة", "المستند", "الفاتورة / المرجع", "العملة",
    "المبلغ الأصلي", "مدين", "دائن", "الرصيد", "البيان"
)

private fun custPdfMovementRow(
    event: CustomerLedgerEventRow,
    balance: Double,
    fallbackCurrency: String,
): List<String> = listOf(
    custPdfDate(event.eventDate),
    custPdfEventLabel(event.eventType),
    event.referenceNo.ifBlank { "—" },
    event.invoiceNo.ifBlank { "—" },
    event.currencyCode.ifBlank { fallbackCurrency },
    custPdfMoney(event.amountOriginal),
    custPdfMoneyDash(event.debitBase),
    custPdfMoneyDash(event.creditBase),
    custPdfMoney(balance),
    event.notes.ifBlank { "—" },
)

private fun custPdfLoadSnapshot(
    context: Context,
    running: List<Pair<CustomerLedgerEventRow, Double>>,
): CustPdfSnapshot? {
    val dbFile = context.getDatabasePath("fush_erp.db")
    if (!dbFile.isFile || running.isEmpty()) return null
    return runCatching {
        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val customerId = custPdfFindCustomerId(db, running) ?: return@use null
            db.rawQuery(
                "SELECT code,nameAr,phone,address,province,classification,currencyCode,creditLimitBase,creditDays,allowCredit,salesRepName FROM customers WHERE id=? LIMIT 1",
                arrayOf(customerId.toString()),
            ).use { cursor ->
                if (!cursor.moveToFirst()) {
                    null
                } else {
                    CustPdfSnapshot(
                        code = custPdfText(cursor, 0),
                        nameAr = custPdfText(cursor, 1),
                        phone = custPdfText(cursor, 2),
                        address = custPdfText(cursor, 3),
                        province = custPdfText(cursor, 4),
                        classification = custPdfText(cursor, 5),
                        currencyCode = custPdfText(cursor, 6),
                        creditLimitBase = cursor.getDouble(7),
                        creditDays = cursor.getInt(8),
                        allowCredit = cursor.getInt(9) != 0,
                        salesRepName = custPdfText(cursor, 10),
                    )
                }
            }
        }
    }.getOrNull()
}

private fun custPdfFindCustomerId(
    db: SQLiteDatabase,
    running: List<Pair<CustomerLedgerEventRow, Double>>,
): Long? {
    running.forEach { (event, _) ->
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
        }
    }
    return null
}

private fun custPdfOneLong(db: SQLiteDatabase, sql: String, arg: String): Long? =
    runCatching {
        db.rawQuery(sql, arrayOf(arg)).use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
    }.getOrNull()

private fun custPdfText(cursor: Cursor, index: Int): String =
    if (cursor.isNull(index)) "" else cursor.getString(index).orEmpty()

private fun custPdfParse(value: String, endOfDay: Boolean): Long? {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    return runCatching {
        val base = formatter.parse(value.trim())?.time ?: return null
        if (endOfDay) base + 86_399_999L else base
    }.getOrNull()
}

private fun custPdfDate(value: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(value))

private fun custPdfMoney(value: Double): String = DecimalFormat("#,##0.00").format(value)
private fun custPdfMoneyDash(value: Double): String =
    if (kotlin.math.abs(value) < 0.000001) "—" else custPdfMoney(value)

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
