package com.fush.erp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fush.erp.data.entity.CustomerEntity
import com.fush.erp.data.entity.CustomerLedgerEventRow
import com.fush.erp.data.entity.CustomerReceiptEntity
import com.fush.erp.data.entity.PartyVoucherEntity
import com.fush.erp.data.entity.SalesInvoiceEntity
import com.fush.erp.data.entity.SalesReturnEntity
import com.fush.erp.ui.export.ReportExportActions
import com.fush.erp.ui.export.ReportExportDocument
import com.fush.erp.ui.export.ReportExportTable
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Date
import java.util.Locale

internal data class CustomerStatementLine(
    val event: CustomerLedgerEventRow,
    val runningBalanceBase: Double,
)

internal data class CustomerStatementSnapshot(
    val openingBalanceBase: Double,
    val periodDebitBase: Double,
    val periodCreditBase: Double,
    val closingBalanceBase: Double,
    val lines: List<CustomerStatementLine>,
)

internal fun buildCustomerStatementSnapshot(
    events: List<CustomerLedgerEventRow>,
    fromInclusive: Long,
    toInclusive: Long,
): CustomerStatementSnapshot {
    require(fromInclusive <= toInclusive) { "CUSTOMER_STATEMENT_PERIOD_INVALID" }
    val ordered = events.sortedWith(compareBy<CustomerLedgerEventRow> { it.eventDate }.thenBy { it.eventOrder }.thenBy { it.referenceNo })
    val opening = ordered.asSequence()
        .filter { it.eventDate < fromInclusive }
        .sumOf { it.debitBase - it.creditBase }

    var running = opening
    val period = ordered.filter { it.eventDate in fromInclusive..toInclusive }
    val lines = period.map { event ->
        running += event.debitBase - event.creditBase
        CustomerStatementLine(event, running)
    }
    val debit = period.sumOf { it.debitBase }
    val credit = period.sumOf { it.creditBase }
    return CustomerStatementSnapshot(
        openingBalanceBase = opening,
        periodDebitBase = debit,
        periodCreditBase = credit,
        closingBalanceBase = opening + debit - credit,
        lines = lines,
    )
}

internal fun buildCustomerStatementDocument(
    customer: CustomerEntity,
    events: List<CustomerLedgerEventRow>,
    invoices: List<SalesInvoiceEntity>,
    receipts: List<CustomerReceiptEntity>,
    returns: List<SalesReturnEntity>,
    vouchers: List<PartyVoucherEntity>,
    fromInclusive: Long,
    toInclusive: Long,
): ReportExportDocument {
    val snapshot = buildCustomerStatementSnapshot(events, fromInclusive, toInclusive)
    val periodLabel = "${statementDate(fromInclusive)} إلى ${statementDate(toInclusive)}"
    val fullCurrentBalance = events.sumOf { it.debitBase - it.creditBase }

    val movementRows = buildList {
        if (kotlin.math.abs(snapshot.openingBalanceBase) >= 0.000001) {
            add(
                listOf(
                    statementDate(fromInclusive),
                    "رصيد افتتاحي",
                    "—",
                    "—",
                    customer.currencyCode,
                    "—",
                    "—",
                    "—",
                    statementMoney(snapshot.openingBalanceBase),
                    "رصيد ما قبل بداية الفترة",
                )
            )
        }
        snapshot.lines.forEach { line ->
            val event = line.event
            add(
                listOf(
                    statementDate(event.eventDate),
                    customerEventLabel(event.eventType),
                    event.referenceNo.ifBlank { "—" },
                    event.invoiceNo.ifBlank { "—" },
                    event.currencyCode.ifBlank { customer.currencyCode },
                    statementMoney(event.amountOriginal),
                    statementMoneyOrDash(event.debitBase),
                    statementMoneyOrDash(event.creditBase),
                    statementMoney(line.runningBalanceBase),
                    event.notes.ifBlank { "—" },
                )
            )
        }
    }

    val periodInvoices = invoices.filter { it.invoiceDate in fromInclusive..toInclusive }.sortedBy { it.invoiceDate }
    val periodReceipts = receipts.filter { it.receiptDate in fromInclusive..toInclusive }.sortedBy { it.receiptDate }
    val periodReturns = returns.filter { it.returnDate in fromInclusive..toInclusive }.sortedBy { it.returnDate }
    val periodVouchers = vouchers.filter {
        it.voucherDate in fromInclusive..toInclusive || (it.reversedAt?.let { reversed -> reversed in fromInclusive..toInclusive } == true)
    }.sortedBy { it.voucherDate }

    val tables = mutableListOf<ReportExportTable>()
    tables += ReportExportTable(
        title = "الحركات المحاسبية وكشف الرصيد المتحرك",
        headers = listOf("التاريخ", "نوع الحركة", "المستند", "المرجع / الفاتورة", "العملة", "المبلغ الأصلي", "مدين", "دائن", "الرصيد", "البيان"),
        rows = movementRows,
    )
    tables += ReportExportTable(
        title = "فواتير المبيعات خلال الفترة",
        headers = listOf("التاريخ", "رقم الفاتورة", "نوع البيع", "الاستحقاق", "العملة", "الإجمالي الأصلي", "سعر الصرف", "الإجمالي الأساسي", "الحالة", "ملاحظات"),
        rows = periodInvoices.map { invoice ->
            listOf(
                statementDate(invoice.invoiceDate),
                invoice.invoiceNo,
                if (invoice.paymentType == "CASH") "نقدي" else "آجل",
                invoice.dueDate?.let(::statementDate) ?: "—",
                invoice.currencyCode,
                statementMoney(invoice.totalOriginal),
                statementRate(invoice.exchangeRate),
                statementMoney(invoice.totalBase),
                statementStatusLabel(invoice.status),
                invoice.notes.ifBlank { "—" },
            )
        },
    )
    tables += ReportExportTable(
        title = "تحصيلات العميل خلال الفترة",
        headers = listOf("التاريخ", "رقم التحصيل", "الحركة", "العملة", "المبلغ الأصلي", "سعر الصرف", "المبلغ الأساسي", "ملاحظات"),
        rows = periodReceipts.map { receipt ->
            listOf(
                statementDate(receipt.receiptDate),
                receipt.receiptNo,
                if (receipt.reversalOfReceiptId == null) "تحصيل" else "عكس تحصيل",
                receipt.currencyCode,
                statementMoney(receipt.amountOriginal),
                statementRate(receipt.exchangeRate),
                statementMoney(receipt.amountBase),
                receipt.notes.ifBlank { "—" },
            )
        },
    )
    tables += ReportExportTable(
        title = "مرتجعات المبيعات خلال الفترة",
        headers = listOf("التاريخ", "رقم المرتجع", "مرجع الفاتورة", "نوع التسوية", "العملة", "المبلغ الأصلي", "سعر الصرف", "المبلغ الأساسي", "السبب"),
        rows = periodReturns.map { salesReturn ->
            listOf(
                statementDate(salesReturn.returnDate),
                salesReturn.returnNo,
                "#${salesReturn.salesInvoiceId}",
                returnSettlementLabel(salesReturn.settlementType),
                salesReturn.currencyCode,
                statementMoney(salesReturn.totalOriginal),
                statementRate(salesReturn.exchangeRate),
                statementMoney(salesReturn.totalBase),
                salesReturn.reason.ifBlank { "—" },
            )
        },
    )
    tables += ReportExportTable(
        title = "سندات العميل خلال الفترة",
        headers = listOf("التاريخ", "رقم السند", "النوع", "الحالة", "العملة", "المبلغ الأصلي", "سعر الصرف", "المبلغ الأساسي", "المرجع", "البيان / سبب العكس"),
        rows = periodVouchers.map { voucher ->
            listOf(
                statementDate(voucher.voucherDate),
                voucher.voucherNo,
                if (voucher.voucherType == "RECEIPT") "سند قبض" else "سند صرف",
                statementStatusLabel(voucher.status),
                voucher.currencyCode,
                statementMoney(voucher.amountOriginal),
                statementRate(voucher.exchangeRate),
                statementMoney(voucher.amountBase),
                voucher.referenceNo.ifBlank { "—" },
                listOf(voucher.description, voucher.reversalReason.takeIf { it.isNotBlank() }?.let { "سبب العكس: $it" }).filterNotNull().filter { it.isNotBlank() }.joinToString(" • ").ifBlank { "—" },
            )
        },
    )

    val creditPolicy = if (customer.allowCredit) {
        "مسموح • ${customer.creditDays} يوم • سقف ${statementMoney(customer.creditLimitBase)} بالعملة الأساسية"
    } else {
        "بيع نقدي / الائتمان غير مفعّل"
    }

    return ReportExportDocument(
        title = "كشف حساب العميل",
        subtitle = "FUSH ERP • كشف محاسبي تفصيلي • الفترة $periodLabel",
        summary = listOf(
            "اسم العميل" to customer.nameAr,
            "كود العميل" to customer.code,
            "الهاتف" to customer.phone.ifBlank { "—" },
            "العنوان" to customer.address.ifBlank { "—" },
            "المحافظة" to customer.province.ifBlank { "—" },
            "قناة البيع" to customerChannelLabel(customer.channel),
            "التصنيف" to customer.classification,
            "عملة العميل" to customer.currencyCode,
            "مندوب المبيعات" to customer.salesRepName.ifBlank { "—" },
            "السياسة الائتمانية" to creditPolicy,
            "الفترة" to periodLabel,
            "عدد الحركات" to snapshot.lines.size.toString(),
            "الرصيد الافتتاحي" to statementMoney(snapshot.openingBalanceBase),
            "إجمالي المدين" to statementMoney(snapshot.periodDebitBase),
            "إجمالي الدائن" to statementMoney(snapshot.periodCreditBase),
            "الرصيد الختامي للفترة" to statementMoney(snapshot.closingBalanceBase),
            "الرصيد الحالي حتى آخر حركة" to statementMoney(fullCurrentBalance),
        ),
        tables = tables,
        notes = buildList {
            add("قيم المدين والدائن والرصيد المتحرك معروضة بالعملة الأساسية للنظام، بينما يظهر المبلغ الأصلي وعملته في عمود مستقل.")
            add("الرصيد الموجب يعني مبلغًا مستحقًا على العميل، والرصيد السالب يعني رصيدًا دائنًا لصالح العميل.")
            add("يشمل الكشف الحركات المرحلة وعكس التحصيلات وعكس السندات كما هي محفوظة في سجل العميل، للمحافظة على الأثر المحاسبي والتدقيقي.")
            if (snapshot.lines.isEmpty()) add("لا توجد حركات محاسبية للعميل ضمن الفترة المحددة.")
        },
    )
}

@Composable
internal fun CustomerStatementExportPanel(
    customer: CustomerEntity,
    events: List<CustomerLedgerEventRow>,
    invoices: List<SalesInvoiceEntity>,
    receipts: List<CustomerReceiptEntity>,
    returns: List<SalesReturnEntity>,
    vouchers: List<PartyVoucherEntity>,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) }
    var fromText by remember(customer.id) { mutableStateOf(today) }
    var toText by remember(customer.id) { mutableStateOf(today) }

    LaunchedEffect(customer.id, events.firstOrNull()?.eventDate) {
        if (events.isNotEmpty()) {
            fromText = statementDate(events.minOf { it.eventDate })
        }
        toText = today
    }

    val fromMillis = remember(fromText) { parseStatementStart(fromText) }
    val toMillis = remember(toText) { parseStatementEnd(toText) }
    val validPeriod = fromMillis != null && toMillis != null && fromMillis <= toMillis
    val document = remember(customer, events, invoices, receipts, returns, vouchers, fromMillis, toMillis) {
        if (fromMillis != null && toMillis != null && fromMillis <= toMillis) {
            buildCustomerStatementDocument(customer, events, invoices, receipts, returns, vouchers, fromMillis, toMillis)
        } else {
            null
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("طباعة كشف حساب محاسبي", style = MaterialTheme.typography.titleMedium)
            Text(
                "اختر الفترة ثم احفظ PDF أو Excel أو افتح معاينة الطباعة. الرصيد في التقرير يُحسب من نفس حركات كشف الحساب الظاهرة هنا.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = fromText,
                    onValueChange = { fromText = it },
                    label = { Text("من تاريخ") },
                    supportingText = { Text("YYYY-MM-DD") },
                    singleLine = true,
                    isError = fromMillis == null,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = toText,
                    onValueChange = { toText = it },
                    label = { Text("إلى تاريخ") },
                    supportingText = { Text("YYYY-MM-DD") },
                    singleLine = true,
                    isError = toMillis == null || (fromMillis != null && toMillis != null && fromMillis > toMillis),
                    modifier = Modifier.weight(1f),
                )
            }
            if (!validPeriod) {
                Text("أدخل فترة صحيحة بصيغة YYYY-MM-DD.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (document != null) {
                ReportExportActions(
                    document = document,
                    baseName = "FushERP-Customer-${customer.code}-Statement",
                    printJobName = "كشف حساب ${customer.nameAr}",
                    enabled = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun parseStatementStart(text: String): Long? = parseStatementLocalDate(text)
    ?.atStartOfDay(ZoneId.systemDefault())
    ?.toInstant()
    ?.toEpochMilli()

private fun parseStatementEnd(text: String): Long? = parseStatementLocalDate(text)
    ?.plusDays(1)
    ?.atStartOfDay(ZoneId.systemDefault())
    ?.toInstant()
    ?.toEpochMilli()
    ?.minus(1L)

private fun parseStatementLocalDate(text: String): LocalDate? = try {
    LocalDate.parse(text.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
} catch (_: DateTimeParseException) {
    null
}

private fun customerEventLabel(type: String): String = when (type.trim().uppercase(Locale.ROOT)) {
    "INVOICE" -> "فاتورة مبيعات"
    "SALES_RETURN" -> "مرتجع مبيعات"
    "CASH_REFUND" -> "استرداد نقدي"
    "RECEIPT" -> "تحصيل عميل"
    "RECEIPT_REVERSAL" -> "عكس تحصيل"
    "VOUCHER_RECEIPT" -> "سند قبض"
    "VOUCHER_PAYMENT" -> "سند صرف"
    "VOUCHER_REVERSAL" -> "عكس سند"
    else -> type.ifBlank { "حركة" }
}

private fun returnSettlementLabel(value: String): String = when (value.trim().uppercase(Locale.ROOT)) {
    "CUSTOMER_CREDIT" -> "تخفيض ذمة العميل"
    "CASH_REFUND" -> "استرداد نقدي"
    else -> value.ifBlank { "—" }
}

private fun customerChannelLabel(value: String): String = when (value.trim().uppercase(Locale.ROOT)) {
    "RETAIL" -> "تجزئة"
    "WHOLESALE" -> "جملة"
    "DISTRIBUTOR" -> "موزع"
    else -> value.ifBlank { "—" }
}

private fun statementStatusLabel(value: String): String = when (value.trim().uppercase(Locale.ROOT)) {
    "POSTED" -> "مرحل"
    "REVERSED" -> "معكوس"
    "DRAFT" -> "مسودة"
    else -> value.ifBlank { "—" }
}

private fun statementMoney(value: Double): String = String.format(Locale.US, "%,.2f", value)
private fun statementMoneyOrDash(value: Double): String = if (kotlin.math.abs(value) < 0.000001) "—" else statementMoney(value)
private fun statementRate(value: Double): String = String.format(Locale.US, "%,.6f", value)
private fun statementDate(value: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(value))
