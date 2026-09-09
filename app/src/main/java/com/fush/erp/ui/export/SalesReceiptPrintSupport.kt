package com.fush.erp.ui.export

import com.fush.erp.data.AppContainer
import com.fush.erp.data.entity.CustomerReceiptEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

object SalesReceiptPrintSupport {
    suspend fun document(container: AppContainer, receiptId: Long): ReportExportDocument {
        val receipt = requireNotNull(container.db.salesDao().receiptById(receiptId)) { "سند التحصيل غير موجود" }
        val customer = requireNotNull(container.db.customerDao().byId(receipt.customerId)) { "العميل المرتبط بالسند غير موجود" }
        val treasury = receipt.treasuryAccountId?.let { container.db.accountingDao().treasuryById(it) }
        val allocations = container.db.salesDao().receiptAllocations(receipt.id)
        val isReversal = receipt.reversalOfReceiptId != null

        val allocationRows = allocations.map { allocation ->
            val invoice = container.db.salesDao().invoiceById(allocation.invoiceId)
            listOf(
                invoice?.invoiceNo ?: "#${allocation.invoiceId}",
                money(abs(allocation.amountBase)),
                money(abs(allocation.discountBase)),
                money(abs(allocation.amountBase) + abs(allocation.discountBase)),
            )
        }

        val cashOriginal = abs(receipt.amountOriginal)
        val discountOriginal = abs(receipt.discountOriginal)
        val settlementOriginal = cashOriginal + discountOriginal
        val title = if (isReversal) "سند عكس تحصيل عميل" else "سند قبض / تحصيل عميل"
        val status = if (isReversal) "مستند عكس" else "مرحل"
        val treasuryLabel = treasury?.let { row ->
            buildString {
                append(row.nameAr)
                if (row.code.isNotBlank()) append(" • ${row.code}")
                if (row.bankName.isNotBlank()) append(" • ${row.bankName}")
            }
        } ?: "غير محدد"

        return ReportExportDocument(
            title = title,
            subtitle = "FUSH ERP • ${receipt.receiptNo}",
            headerStyle = ReportHeaderStyle.FUSH_RED_FULL_WIDTH,
            singlePagePreferred = true,
            summary = buildList {
                add("رقم السند" to receipt.receiptNo)
                add("التاريخ" to date(receipt.receiptDate))
                add("العميل" to "${customer.nameAr} • ${customer.code}")
                if (customer.phone.isNotBlank()) add("الهاتف" to customer.phone)
                add("المبلغ المقبوض" to "${money(cashOriginal)} ${receipt.currencyCode}")
                if (discountOriginal > 0.000001) {
                    add("خصم التحصيل" to "${money(discountOriginal)} ${receipt.currencyCode}")
                    add("سبب الخصم" to receipt.discountReason.ifBlank { "—" })
                }
                add("إجمالي تسوية الذمة" to "${money(settlementOriginal)} ${receipt.currencyCode}")
                add("المبلغ الأساسي" to money(abs(receipt.amountBase)))
                add("سعر الصرف" to money(receipt.exchangeRate))
                add("الخزينة / البنك" to treasuryLabel)
                add("الحالة" to status)
            },
            tables = if (allocationRows.isEmpty()) emptyList() else listOf(
                ReportExportTable(
                    title = "تخصيص السند على الفواتير",
                    headers = listOf("الفاتورة", "نقدي أساسي", "خصم أساسي", "إجمالي التسوية"),
                    rows = allocationRows,
                )
            ),
            notes = buildList {
                if (receipt.notes.isNotBlank()) add("ملاحظات: ${receipt.notes}")
                add("توقيع المستلم: ____________________")
                add("توقيع المحصل / الموظف: ____________________")
            },
        )
    }

    internal fun baseName(receipt: CustomerReceiptEntity): String =
        "FUSH-Customer-Receipt-${safeFilePart(receipt.receiptNo)}"

    internal fun jobName(receiptNo: String): String = "FUSH Receipt $receiptNo"

    private fun money(value: Double): String = String.format(Locale.US, "%,.2f", value)

    private fun date(epochMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Aden")
        }.format(Date(epochMillis))

    private fun safeFilePart(value: String): String = value
        .trim()
        .replace(Regex("[^A-Za-z0-9._-]+"), "-")
        .trim('-')
        .ifBlank { "receipt" }
}
