package com.fush.erp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.fush.erp.domain.CustomerStatementMath
import com.fush.erp.ui.export.ReportExportActions
import com.fush.erp.ui.export.ReportExportDocument
import com.fush.erp.ui.export.ReportExportTable
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CustomerStatementExportPanel(
    customer: CustomerEntity,
    events: List<CustomerLedgerEventRow>,
    invoices: List<SalesInvoiceEntity>,
    receipts: List<CustomerReceiptEntity>,
    returns: List<SalesReturnEntity>,
    vouchers: List<PartyVoucherEntity>,
    modifier: Modifier = Modifier,
) {
    val orderedEvents = remember(events) {
        events.sortedWith(compareBy<CustomerLedgerEventRow> { it.eventDate }.thenBy { it.eventOrder })
    }
    val earliest = remember(orderedEvents) {
        orderedEvents.firstOrNull()?.eventDate ?: com.fush.erp.domain.TrustedTimeService.now()
    }
    var fromText by remember(customer.id) { mutableStateOf(customerStatementDate(earliest)) }
    var toText by remember(customer.id) { mutableStateOf(customerStatementDate(com.fush.erp.domain.TrustedTimeService.now())) }
    val from = remember(fromText) { customerStatementParseDate(fromText, endOfDay = false) }
    val to = remember(toText) { customerStatementParseDate(toText, endOfDay = true) }
    val periodValid = from != null && to != null && from <= to

    val report = remember(customer, orderedEvents, invoices, receipts, returns, vouchers, from, to) {
        if (periodValid && from != null && to != null) {
            buildCustomerStatementDocument(customer, orderedEvents, invoices, receipts, returns, vouchers, from, to)
        } else null
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 2.dp,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("طباعة كشف حساب محاسبي", style = MaterialTheme.typography.titleLarge)
            Text(
                "تقرير A4 متكامل: رصيد افتتاحي، مدين، دائن، رصيد متحرك، الفواتير، التحصيلات، المرتجعات والسندات.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "${customer.nameAr} • ${customer.code} • ${customer.currencyCode}",
                style = MaterialTheme.typography.titleMedium,
            )
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
            if (report != null && from != null && to != null) {
                val summary = CustomerStatementMath.summarize(orderedEvents, from, to)
                Text("الرصيد الافتتاحي: ${customerStatementMoney(summary.openingBalanceBase)}")
                Text(
                    "إجمالي المدين: ${customerStatementMoney(summary.debitBase)} • " +
                        "إجمالي الدائن: ${customerStatementMoney(summary.creditBase)}"
                )
                Text(
                    "الرصيد الختامي: ${customerStatementMoney(summary.closingBalanceBase)} • " +
                        "عدد الحركات: ${summary.movementCount}",
                    style = MaterialTheme.typography.titleMedium,
                )
                ReportExportActions(
                    document = report,
                    baseName = "FUSH-Customer-Statement-${safeCustomerFilePart(customer.code)}",
                    printJobName = "كشف حساب ${customer.nameAr}",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun buildCustomerStatementDocument(
    customer: CustomerEntity,
    events: List<CustomerLedgerEventRow>,
    invoices: List<SalesInvoiceEntity>,
    receipts: List<CustomerReceiptEntity>,
    returns: List<SalesReturnEntity>,
    vouchers: List<PartyVoucherEntity>,
    from: Long,
    to: Long,
): ReportExportDocument {
    val summary = CustomerStatementMath.summarize(events, from, to)
    var running = 0.0
    val movementRows = mutableListOf<List<String>>()
    movementRows += listOf(
        customerStatementDate(from), "رصيد افتتاحي", "—", "—", customer.currencyCode,
        "—", "—", "—", customerStatementMoney(summary.openingBalanceBase), "رصيد ما قبل بداية الفترة"
    )
    events.sortedWith(compareBy<CustomerLedgerEventRow> { it.eventDate }.thenBy { it.eventOrder }).forEach { event ->
        running += event.debitBase - event.creditBase
        if (event.eventDate in from..to) {
            movementRows += listOf(
                customerStatementDate(event.eventDate),
                customerStatementEventLabel(event.eventType),
                event.referenceNo.ifBlank { "—" },
                event.invoiceNo.ifBlank { "—" },
                event.currencyCode.ifBlank { customer.currencyCode },
                customerStatementMoney(event.amountOriginal),
                customerStatementMoneyDash(event.debitBase),
                customerStatementMoneyDash(event.creditBase),
                customerStatementMoney(running),
                event.notes.ifBlank { "—" },
            )
        }
    }

    val invoiceById = invoices.associateBy { it.id }
    val periodInvoices = invoices.filter { it.invoiceDate in from..to }.sortedBy { it.invoiceDate }
    val periodReceipts = receipts.filter { it.receiptDate in from..to }.sortedBy { it.receiptDate }
    val periodReturns = returns.filter { it.returnDate in from..to }.sortedBy { it.returnDate }
    val periodVouchers = vouchers.filter { it.voucherDate in from..to }.sortedBy { it.voucherDate }

    val tables = listOf(
        ReportExportTable(
            title = "الحركات المحاسبية والرصيد المتحرك",
            headers = listOf("التاريخ", "نوع الحركة", "المستند", "الفاتورة / المرجع", "العملة", "المبلغ الأصلي", "مدين", "دائن", "الرصيد", "البيان"),
            rows = movementRows,
        ),
        ReportExportTable(
            title = "فواتير المبيعات خلال الفترة",
            headers = listOf("التاريخ", "رقم الفاتورة", "البيع", "الاستحقاق", "العملة", "الإجمالي الأصلي", "سعر الصرف", "الإجمالي الأساسي", "الخصم %", "المندوب", "الحالة / ملاحظات"),
            rows = periodInvoices.map { invoice ->
                listOf(
                    customerStatementDate(invoice.invoiceDate),
                    invoice.invoiceNo,
                    if (invoice.paymentType == "CASH") "نقدي" else "آجل",
                    invoice.dueDate?.let(::customerStatementDate) ?: "—",
                    invoice.currencyCode,
                    customerStatementMoney(invoice.totalOriginal),
                    customerStatementRate(invoice.exchangeRate),
                    customerStatementMoney(invoice.totalBase),
                    customerStatementRate(invoice.discountPct),
                    invoice.salesRepNameSnapshot.ifBlank { customer.salesRepName.ifBlank { "—" } },
                    customerStatementStatus(invoice.status) + invoice.notes.takeIf { it.isNotBlank() }?.let { " • $it" }.orEmpty(),
                )
            },
        ),
        ReportExportTable(
            title = "تحصيلات العميل خلال الفترة",
            headers = listOf("التاريخ", "رقم التحصيل", "الحركة", "العملة", "نقدي أصلي", "سعر الصرف", "نقدي أساسي", "خصم أصلي", "خصم أساسي", "إجمالي التسوية الأساسي", "سبب الخصم / ملاحظات"),
            rows = periodReceipts.map { receipt ->
                listOf(
                    customerStatementDate(receipt.receiptDate),
                    receipt.receiptNo,
                    if (receipt.reversalOfReceiptId == null) "تحصيل" else "عكس تحصيل",
                    receipt.currencyCode,
                    customerStatementMoney(receipt.amountOriginal),
                    customerStatementRate(receipt.exchangeRate),
                    customerStatementMoney(receipt.amountBase),
                    customerStatementMoney(receipt.discountOriginal),
                    customerStatementMoney(receipt.discountBase),
                    customerStatementMoney(receipt.amountBase + receipt.discountBase),
                    listOf(receipt.discountReason, receipt.notes).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { "—" },
                )
            },
        ),
        ReportExportTable(
            title = "مرتجعات المبيعات خلال الفترة",
            headers = listOf("التاريخ", "رقم المرتجع", "الفاتورة", "نوع التسوية", "العملة", "المبلغ الأصلي", "سعر الصرف", "المبلغ الأساسي", "التكلفة", "الحالة / السبب"),
            rows = periodReturns.map { row ->
                listOf(
                    customerStatementDate(row.returnDate),
                    row.returnNo,
                    invoiceById[row.salesInvoiceId]?.invoiceNo ?: "#${row.salesInvoiceId}",
                    customerStatementSettlement(row.settlementType),
                    row.currencyCode,
                    customerStatementMoney(row.totalOriginal),
                    customerStatementRate(row.exchangeRate),
                    customerStatementMoney(row.totalBase),
                    customerStatementMoney(row.totalCostBase),
                    "${customerStatementStatus(row.status)} • ${row.reason.ifBlank { "—" }}",
                )
            },
        ),
        ReportExportTable(
            title = "سندات العميل خلال الفترة",
            headers = listOf("التاريخ", "رقم السند", "النوع", "الحالة", "العملة", "المبلغ الأصلي", "سعر الصرف", "المبلغ الأساسي", "المرجع", "البيان / العكس"),
            rows = periodVouchers.map { row ->
                listOf(
                    customerStatementDate(row.voucherDate),
                    row.voucherNo,
                    if (row.voucherType == "RECEIPT") "سند قبض" else "سند صرف",
                    customerStatementStatus(row.status),
                    row.currencyCode,
                    customerStatementMoney(row.amountOriginal),
                    customerStatementRate(row.exchangeRate),
                    customerStatementMoney(row.amountBase),
                    row.referenceNo.ifBlank { "—" },
                    listOf(
                        row.description.takeIf { it.isNotBlank() },
                        row.reversalReason.takeIf { it.isNotBlank() }?.let { "سبب العكس: $it" },
                    ).filterNotNull().joinToString(" • ").ifBlank { "—" },
                )
            },
        ),
    )

    return ReportExportDocument(
        title = "كشف حساب العميل",
        subtitle = "FUSH ERP • كشف محاسبي تفصيلي • ${customerStatementDate(from)} إلى ${customerStatementDate(to)}",
        summary = listOf(
            "اسم العميل" to customer.nameAr,
            "كود العميل" to customer.code,
            "الهاتف" to customer.phone.ifBlank { "—" },
            "العنوان" to customer.address.ifBlank { "—" },
            "المحافظة" to customer.province.ifBlank { "—" },
            "قناة البيع" to customerStatementChannel(customer.channel),
            "التصنيف" to customer.classification.ifBlank { "—" },
            "عملة العميل" to customer.currencyCode,
            "مندوب المبيعات" to customer.salesRepName.ifBlank { "—" },
            "السياسة الائتمانية" to if (customer.allowCredit) {
                "مسموح • ${customer.creditDays} يوم • سقف ${customerStatementMoney(customer.creditLimitBase)} بالعملة الأساسية"
            } else "نقدي / الائتمان غير مفعّل",
            "عدد فواتير الفترة" to periodInvoices.size.toString(),
            "عدد التحصيلات" to periodReceipts.size.toString(),
            "عدد المرتجعات" to periodReturns.size.toString(),
            "عدد السندات" to periodVouchers.size.toString(),
            "الرصيد الافتتاحي" to customerStatementMoney(summary.openingBalanceBase),
            "إجمالي المدين" to customerStatementMoney(summary.debitBase),
            "إجمالي الدائن" to customerStatementMoney(summary.creditBase),
            "الرصيد الختامي للفترة" to customerStatementMoney(summary.closingBalanceBase),
            "الرصيد الحالي" to customerStatementMoney(summary.currentBalanceBase),
        ),
        tables = tables,
        notes = listOf(
            "الرصيد الافتتاحي هو صافي حركات العميل السابقة لبداية الفترة.",
            "المدين والدائن والرصيد المتحرك مأخوذة من نفس سجل كشف حساب العميل المستخدم داخل التطبيق.",
            "القيم الأساسية تظهر في أعمدة المدين والدائن والرصيد، ويظهر المبلغ الأصلي وعملته بصورة مستقلة.",
            "الرصيد الموجب يعني مبلغًا مستحقًا على العميل، والرصيد السالب يعني رصيدًا دائنًا لصالح العميل.",
            "الحركات المعكوسة تبقى ظاهرة عندما تكون موجودة في سجل العميل للحفاظ على الأثر المحاسبي والتدقيقي.",
        ),
    )
}

private fun customerStatementParseDate(value: String, endOfDay: Boolean): Long? {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    return runCatching {
        val base = formatter.parse(value.trim())?.time ?: return null
        if (endOfDay) base + 86_399_999L else base
    }.getOrNull()
}

private fun customerStatementDate(value: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(value))
private fun customerStatementMoney(value: Double): String = DecimalFormat("#,##0.00").format(value)
private fun customerStatementMoneyDash(value: Double): String = if (kotlin.math.abs(value) < 0.000001) "—" else customerStatementMoney(value)
private fun customerStatementRate(value: Double): String = DecimalFormat("#,##0.########").format(value)
private fun safeCustomerFilePart(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-').ifBlank { "customer" }

private fun customerStatementEventLabel(value: String): String = when (value) {
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

private fun customerStatementStatus(value: String): String = when (value) {
    "POSTED" -> "مرحّل"
    "REVERSED" -> "معكوس"
    "DRAFT" -> "مسودة"
    "CANCELLED" -> "ملغي"
    else -> value.ifBlank { "—" }
}

private fun customerStatementSettlement(value: String): String = when (value) {
    "CUSTOMER_CREDIT" -> "رصيد للعميل"
    "CASH_REFUND" -> "رد نقدي"
    else -> value.ifBlank { "—" }
}

private fun customerStatementChannel(value: String): String = when (value) {
    "WHOLESALE" -> "جملة"
    "RETAIL" -> "تجزئة"
    "DISTRIBUTOR" -> "موزع"
    else -> value.ifBlank { "—" }
}
