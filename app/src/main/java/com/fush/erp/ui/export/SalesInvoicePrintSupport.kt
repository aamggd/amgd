package com.fush.erp.ui.export

import com.fush.erp.data.entity.AdditionalChargeInvoiceDetailRow
import com.fush.erp.data.entity.ItemEntity
import com.fush.erp.data.entity.SalesAllocationEntity
import com.fush.erp.data.entity.SalesInvoiceEntity
import com.fush.erp.data.entity.SalesInvoiceSummary
import com.fush.erp.data.entity.SalesLineEntity
import com.fush.erp.data.entity.ShipmentInvoiceCostRow
import com.fush.erp.data.entity.UnitEntity
import com.fush.erp.data.entity.WarehouseEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Customer-facing sales invoice PDF/print model.
 * Intentionally excludes internal cost, COGS, commission and shipment-company-cost details.
 */
object SalesInvoicePrintSupport {
    fun document(
        invoice: SalesInvoiceEntity,
        summary: SalesInvoiceSummary,
        lines: List<SalesLineEntity>,
        items: List<ItemEntity>,
        units: List<UnitEntity>,
        warehouse: WarehouseEntity?,
        allocationsByLine: Map<Long, List<SalesAllocationEntity>>,
        additionalCharges: List<AdditionalChargeInvoiceDetailRow>,
        shipmentCosts: List<ShipmentInvoiceCostRow> = emptyList(),
    ): ReportExportDocument {
        val itemById = items.associateBy { it.id }
        val unitById = units.associateBy { it.id }

        val itemRows = lines.map { line ->
            val item = itemById[line.itemId]
            val unit = unitById[line.unitId]
            val unitLabel = unit?.nameAr?.ifBlank { unit.nameEn } ?: "وحدة"
            val qtyLabel = buildString {
                append("${money(line.quantity)} $unitLabel")
                if (line.freeQuantity > 0.000001) append(" + مجاني ${money(line.freeQuantity)}")
            }
            listOf(
                item?.nameAr?.ifBlank { item.nameEn } ?: "صنف #${line.itemId}",
                qtyLabel,
                "${money(line.unitPriceOriginal)} ${invoice.currencyCode}",
                "${money(line.discountOriginal)} ${invoice.currencyCode}",
                "${money(line.netOriginal)} ${invoice.currencyCode}",
            )
        }

        val lotRows = lines.flatMap { line ->
            val item = itemById[line.itemId]
            allocationsByLine[line.id].orEmpty().map { allocation ->
                listOf(
                    item?.nameAr?.ifBlank { item.nameEn } ?: "صنف #${line.itemId}",
                    allocation.lotNo ?: "بدون تشغيلة",
                    money(allocation.quantityBase + allocation.freeQuantityBase),
                    allocation.expiryDate?.let(::date) ?: "—",
                )
            }
        }

        val customerCharges = additionalCharges.filter { it.bearer == "CUSTOMER" }
        val chargeRows = customerCharges.map { charge ->
            listOf(
                charge.chargeTypeName,
                charge.chargeNo,
                "${money(charge.allocatedInvoiceOriginal)} ${invoice.currencyCode}",
            )
        }

        val shipmentCustomerChargeBase = shipmentCosts.sumOf { it.customerChargeBase }
        val shipmentCustomerChargeOriginal = if (invoice.exchangeRate > 0.0) shipmentCustomerChargeBase / invoice.exchangeRate else 0.0
        val shipmentChargeRows = shipmentCosts.filter { it.customerChargeBase > 0.000001 }.map { cost ->
            listOf(
                "${cost.shipmentNo} — ${cost.destinationProvince}",
                cost.paymentVoucherNo,
                "${money(cost.customerChargeBase / invoice.exchangeRate)} ${invoice.currencyCode}",
            )
        }

        val financialRows = buildList {
            add(listOf("إجمالي الأصناف قبل الخصم", "${money(invoice.grossOriginal)} ${invoice.currencyCode}"))
            if (invoice.discountOriginal > 0.000001) {
                add(listOf("الخصم (${money(invoice.discountPct)}%)", "${money(invoice.discountOriginal)} ${invoice.currencyCode}"))
            }
            if (invoice.transportOriginal != 0.0) add(listOf("النقل", "${money(invoice.transportOriginal)} ${invoice.currencyCode}"))
            if (invoice.feesOriginal != 0.0) add(listOf("الرسوم", "${money(invoice.feesOriginal)} ${invoice.currencyCode}"))
            if (invoice.riskMarginOriginal != 0.0) add(listOf("هامش المخاطر", "${money(invoice.riskMarginOriginal)} ${invoice.currencyCode}"))
            if (customerCharges.isNotEmpty()) {
                add(listOf("رسوم إضافية محملة للعميل", "${money(customerCharges.sumOf { it.allocatedInvoiceOriginal })} ${invoice.currencyCode}"))
            }
            if (shipmentCustomerChargeOriginal > 0.000001) {
                add(listOf("حصة نقل/شحن محملة للعميل", "${money(shipmentCustomerChargeOriginal)} ${invoice.currencyCode}"))
            }
            add(listOf("الإجمالي النهائي", "${money(invoice.totalOriginal)} ${invoice.currencyCode}"))
            if (invoice.paymentType == "CREDIT") add(listOf("الرصيد المتبقي - أساسي", money(summary.outstandingBase)))
        }

        val paymentLabel = if (invoice.paymentType == "CREDIT") "آجل" else "نقدي"
        val statusLabel = when (invoice.status) {
            "POSTED" -> "مرحلة"
            "DRAFT" -> "مسودة"
            else -> invoice.status
        }

        return ReportExportDocument(
            title = "فاتورة مبيعات",
            subtitle = "FUSH ERP • ${invoice.invoiceNo}",
            summary = buildList {
                add("رقم الفاتورة" to invoice.invoiceNo)
                add("التاريخ" to date(invoice.invoiceDate))
                add("العميل" to summary.customerName)
                add("نوع البيع" to paymentLabel)
                invoice.dueDate?.let { add("تاريخ الاستحقاق" to date(it)) }
                if (invoice.province.isNotBlank()) add("المحافظة" to invoice.province)
                if (invoice.channel.isNotBlank()) add("القناة" to invoice.channel)
                if (invoice.salesRepNameSnapshot.isNotBlank()) add("مندوب المبيعات" to invoice.salesRepNameSnapshot)
                warehouse?.let { add("المخزن" to "${it.nameAr} • ${it.code}") }
                add("العملة" to invoice.currencyCode)
                add("سعر الصرف" to moneyRaw(invoice.exchangeRate))
                add("الحالة" to statusLabel)
            },
            tables = buildList {
                add(
                    ReportExportTable(
                        title = "تفاصيل الأصناف (${lines.size})",
                        headers = listOf("الصنف", "الكمية", "سعر الوحدة", "الخصم", "الصافي"),
                        rows = itemRows,
                    )
                )
                if (chargeRows.isNotEmpty()) {
                    add(
                        ReportExportTable(
                            title = "الرسوم الإضافية على العميل",
                            headers = listOf("النوع", "المرجع", "المبلغ"),
                            rows = chargeRows,
                        )
                    )
                }
                if (shipmentChargeRows.isNotEmpty()) {
                    add(
                        ReportExportTable(
                            title = "رسوم الشحن المحملة على العميل من المصروف الفعلي",
                            headers = listOf("الشحنة", "سند المصروف", "حصة العميل"),
                            rows = shipmentChargeRows,
                        )
                    )
                }
                add(
                    ReportExportTable(
                        title = "الملخص المالي",
                        headers = listOf("البيان", "القيمة"),
                        rows = financialRows,
                    )
                )
                if (lotRows.isNotEmpty()) {
                    add(
                        ReportExportTable(
                            title = "التشغيلات / الدُفعات",
                            headers = listOf("الصنف", "التشغيلة", "كمية أساسية", "الصلاحية"),
                            rows = lotRows,
                        )
                    )
                }
            },
            notes = buildList {
                if (invoice.notes.isNotBlank()) add("ملاحظات: ${invoice.notes}")
                add("توقيع العميل / المستلم: ____________________    |    توقيع مندوب المبيعات: ____________________")
            },
            headerStyle = ReportHeaderStyle.FUSH_RED_FULL_WIDTH,
            singlePagePreferred = true,
        )
    }

    fun baseName(invoiceNo: String): String = "FUSH-Sales-Invoice-${safeFilePart(invoiceNo)}"

    fun jobName(invoiceNo: String): String = "FUSH Sales Invoice $invoiceNo"

    private fun money(value: Double): String = String.format(Locale.US, "%,.2f", value)

    private fun moneyRaw(value: Double): String = String.format(Locale.US, "%.6f", value).trimEnd('0').trimEnd('.')

    private fun date(epochMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Aden")
        }.format(Date(epochMillis))

    private fun safeFilePart(value: String): String = value
        .trim()
        .replace(Regex("[^A-Za-z0-9._-]+"), "-")
        .trim('-')
        .ifBlank { "invoice" }
}
