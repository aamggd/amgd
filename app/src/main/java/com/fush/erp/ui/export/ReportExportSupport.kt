package com.fush.erp.ui.export

import com.fush.erp.R
import com.fush.erp.domain.TrustedTimeService
import android.app.Activity
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class ReportHeaderStyle {
    DEFAULT,
    FUSH_RED_FULL_WIDTH,
}

data class ReportExportDocument(
    val title: String,
    val subtitle: String = "",
    val summary: List<Pair<String, String>> = emptyList(),
    val tables: List<ReportExportTable> = emptyList(),
    val notes: List<String> = emptyList(),
    val headerStyle: ReportHeaderStyle = ReportHeaderStyle.DEFAULT,
    /** v186: compact customer document layout intended to fit a normal invoice on one A4 page. */
    val singlePagePreferred: Boolean = false,
)

data class ReportExportTable(
    val title: String,
    val headers: List<String>,
    val rows: List<List<String>>
)

object ReportExportSupport {
    private const val A4_SHORT = 595
    private const val A4_LONG = 842
    private const val MARGIN = 32f
    private const val FOOTER_SPACE = 22f
    private const val CELL_PADDING = 5f
    private const val BRAND_HEADER_HEIGHT = 68f
    private const val BRAND_FACEBOOK = "facebook.com/share/1BW7Ur6jTP/"
    private const val BRAND_LABEL = "FUSH • فوش"

    fun exportPdf(context: Context, document: ReportExportDocument, baseName: String): Uri {
        val bytes = buildPdf(context, document)
        return saveBytes(context, "$baseName-${stamp()}.pdf", "application/pdf", bytes)
    }

    fun exportXlsx(context: Context, document: ReportExportDocument, baseName: String): Uri {
        val bytes = buildXlsx(document)
        return saveBytes(
            context,
            "$baseName-${stamp()}.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            bytes
        )
    }

    fun sharePdf(context: Context, document: ReportExportDocument, baseName: String) {
        val bytes = buildPdf(context, document)
        shareBytes(
            context = context,
            fileName = "$baseName-${stamp()}.pdf",
            mime = "application/pdf",
            bytes = bytes,
            chooserTitle = "مشاركة تقرير PDF"
        )
    }

    fun shareXlsx(context: Context, document: ReportExportDocument, baseName: String) {
        val bytes = buildXlsx(document)
        shareBytes(
            context = context,
            fileName = "$baseName-${stamp()}.xlsx",
            mime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            bytes = bytes,
            chooserTitle = "مشاركة تقرير Excel"
        )
    }

    fun printPreview(context: Context, document: ReportExportDocument, jobName: String) {
        val pdfBytes = buildPdf(context, document)
        val activity = context.findActivity() ?: error("تعذر فتح شاشة الطباعة خارج واجهة التطبيق")
        val printManager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val adapter = ByteArrayPdfPrintAdapter(jobName, pdfBytes)
        val mediaSize = if (requiresLandscape(document)) {
            PrintAttributes.MediaSize.ISO_A4.asLandscape()
        } else {
            PrintAttributes.MediaSize.ISO_A4
        }
        val attributes = PrintAttributes.Builder()
            .setMediaSize(mediaSize)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .build()
        printManager.print(jobName, adapter, attributes)
    }

    private fun requiresLandscape(document: ReportExportDocument): Boolean =
        !document.singlePagePreferred &&
            document.tables.any { it.headers.size >= 6 || it.rows.any { row -> row.size >= 6 } }

    private fun buildPdf(context: Context, document: ReportExportDocument): ByteArray {
        val landscape = requiresLandscape(document)
        val pageWidth = if (landscape) A4_LONG else A4_SHORT
        val pageHeight = if (landscape) A4_SHORT else A4_LONG
        val compact = document.singlePagePreferred
        val pageMargin = if (compact) 18f else MARGIN
        val footerSpace = if (compact) 16f else FOOTER_SPACE
        val cellPadding = if (compact) 3f else CELL_PADDING
        val contentWidth = pageWidth - (pageMargin * 2)
        val pageBottom = pageHeight - pageMargin - footerSpace
        val pdf = PdfDocument()

        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 30, 30)
            textSize = if (compact) 8.0f else 9.5f
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val label = Paint(body).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val section = Paint(body).apply { textSize = if (compact) 9.5f else 12.5f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val small = Paint(body).apply { textSize = if (compact) 6.8f else 8f; color = Color.DKGRAY }
        val tableHeader = Paint(body).apply { textSize = if (compact) 7.2f else 8.5f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val tableBody = Paint(body).apply { textSize = if (compact) 7.0f else 8.3f }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.LTGRAY; strokeWidth = 0.8f }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(190, 190, 190)
            strokeWidth = 0.7f
            style = Paint.Style.STROKE
        }
        val headerFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(238, 240, 243)
            style = Paint.Style.FILL
        }
        val alternateFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(249, 249, 249)
            style = Paint.Style.FILL
        }
        val brandAccent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(9, 76, 92)
            style = Paint.Style.FILL
        }
        val brandBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(224, 224, 224)
            strokeWidth = 0.8f
            style = Paint.Style.STROKE
        }
        val brandTitle = Paint(body).apply {
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val brandMeta = Paint(small).apply { textSize = 7.2f }
        val brandBitmap = runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.fush_print_logo)
        }.getOrNull()
        val invoiceHeaderBitmap = runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.fush_invoice_header_red)
        }.getOrNull()
        val invoiceRed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(154, 76, 72)
            style = Paint.Style.FILL
        }
        val invoiceHeaderTitle = Paint(body).apply {
            color = Color.rgb(154, 76, 72)
            textSize = 13f
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val invoiceHeaderMeta = Paint(small).apply {
            color = Color.rgb(110, 110, 110)
            textSize = 8.2f
            textAlign = Paint.Align.LEFT
        }

        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f
        val generatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(TrustedTimeService.now()))

        fun finishPage() {
            val current = page ?: return
            val footer = Paint(small).apply { textAlign = Paint.Align.CENTER }
            current.canvas.drawText(
                "$BRAND_LABEL • $BRAND_FACEBOOK • $generatedAt • صفحة $pageNo",
                pageWidth / 2f,
                pageHeight - 14f,
                footer
            )
            pdf.finishPage(current)
            page = null
        }

        fun startPage() {
            finishPage()
            pageNo += 1
            page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNo).create())
            val canvas = page!!.canvas

            if (document.headerStyle == ReportHeaderStyle.FUSH_RED_FULL_WIDTH) {
                // Customer sales invoices use the supplied FUSH letterhead as a true full-width header.
                // No side inset/rounded card: the red branding spans from the left page edge to the right page edge.
                val naturalHeight = invoiceHeaderBitmap?.let { bitmap ->
                    pageWidth.toFloat() * bitmap.height.toFloat() / bitmap.width.toFloat()
                } ?: if (landscape) 155f else 132f
                val invoiceHeaderHeight = if (compact) {
                    naturalHeight.coerceIn(72f, 92f)
                } else {
                    naturalHeight.coerceIn(118f, if (landscape) 175f else 142f)
                }
                val invoiceHeaderRect = RectF(0f, 0f, pageWidth.toFloat(), invoiceHeaderHeight)
                if (invoiceHeaderBitmap != null) {
                    canvas.drawBitmap(
                        invoiceHeaderBitmap,
                        null,
                        invoiceHeaderRect,
                        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
                    )
                } else {
                    canvas.drawRect(invoiceHeaderRect, invoiceRed)
                    val fallback = Paint(invoiceHeaderTitle).apply {
                        color = Color.WHITE
                        textAlign = Paint.Align.CENTER
                        textSize = 22f
                    }
                    canvas.drawText("FUSH • فوش", pageWidth / 2f, invoiceHeaderHeight / 2f + 7f, fallback)
                }

                val titleBaseline = invoiceHeaderHeight + if (compact) 15f else 22f
                canvas.drawText(document.title, pageWidth - pageMargin, titleBaseline, invoiceHeaderTitle)
                if (document.subtitle.isNotBlank()) {
                    canvas.drawText(document.subtitle, pageMargin, titleBaseline, invoiceHeaderMeta)
                }
                canvas.drawRect(
                    pageMargin,
                    titleBaseline + 8f,
                    pageWidth - pageMargin,
                    titleBaseline + 10f,
                    invoiceRed,
                )
                y = titleBaseline + if (compact) 15f else 22f
            } else {
                y = pageMargin
                val headerTop = y
                val headerBottom = headerTop + BRAND_HEADER_HEIGHT
                canvas.drawRoundRect(
                    RectF(pageMargin, headerTop, pageWidth - pageMargin, headerBottom),
                    8f,
                    8f,
                    brandBorder,
                )
                canvas.drawRoundRect(
                    RectF(pageWidth - pageMargin - 5f, headerTop, pageWidth - pageMargin, headerBottom),
                    3f,
                    3f,
                    brandAccent,
                )

                val logoWidth = if (landscape) 190f else 170f
                val logoRect = RectF(
                    pageWidth - pageMargin - logoWidth - 12f,
                    headerTop + 7f,
                    pageWidth - pageMargin - 12f,
                    headerBottom - 7f,
                )
                if (brandBitmap != null) {
                    canvas.drawBitmap(brandBitmap, null, logoRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                } else {
                    canvas.drawText(BRAND_LABEL, logoRect.right, headerTop + 27f, brandTitle)
                }

                val textRight = logoRect.left - 14f
                canvas.drawText(document.title, textRight, headerTop + 21f, brandTitle)
                canvas.drawText(
                    "الصفحة الرسمية: $BRAND_FACEBOOK",
                    textRight,
                    headerTop + 39f,
                    brandMeta,
                )
                canvas.drawText(
                    if (landscape) "A4 أفقي" else "A4 عمودي",
                    textRight,
                    headerTop + 54f,
                    brandMeta,
                )
                y = headerBottom + 12f
            }
        }

        fun ensure(space: Float): Boolean {
            if (page == null) startPage()
            if (y + space > pageBottom) {
                startPage()
                return true
            }
            return false
        }

        fun drawWrapped(text: String, paint: Paint = body, indent: Float = 0f, extraAfter: Float = 3f) {
            val clean = text.ifBlank { "—" }
            val lines = wrapText(clean, paint, contentWidth - indent)
            for (line in lines) {
                ensure(paint.textSize + 8f)
                page!!.canvas.drawText(line, pageWidth - pageMargin - indent, y + paint.textSize, paint)
                y += paint.textSize + 4f
            }
            y += extraAfter
        }

        fun drawKeyValue(key: String, value: String) {
            val keyText = "$key:"
            val keyWidth = label.measureText(keyText)
            val available = contentWidth - keyWidth - 16f
            val valuePaint = Paint(body)
            val lines = wrapText(value.ifBlank { "—" }, valuePaint, available.coerceAtLeast(150f))
            val requiredHeight = label.textSize + 8f + ((lines.size - 1).coerceAtLeast(0) * (valuePaint.textSize + 4f))
            ensure(requiredHeight)
            val canvas = page!!.canvas
            val xRight = pageWidth - pageMargin
            canvas.drawText(keyText, xRight, y + label.textSize, label)
            val valueRight = xRight - keyWidth - 12f
            for ((index, line) in lines.withIndex()) {
                canvas.drawText(line, valueRight, y + valuePaint.textSize, valuePaint)
                if (index < lines.lastIndex) y += valuePaint.textSize + 4f
            }
            y += body.textSize + 8f
        }

        fun divider() {
            ensure(if (compact) 8f else 12f)
            page!!.canvas.drawLine(pageMargin, y, pageWidth - pageMargin, y, dividerPaint)
            y += if (compact) 5f else 9f
        }

        fun drawCompactSummary(rows: List<Pair<String, String>>) {
            val paint = Paint(body).apply { textSize = 7.2f }
            val pairGap = 12f
            val columnWidth = (contentWidth - pairGap) / 2f
            rows.chunked(2).forEach { pairRow ->
                val rendered = pairRow.map { (key, value) ->
                    wrapText("$key: ${value.ifBlank { "—" }}", paint, columnWidth - 4f).take(2)
                }
                val maxLines = rendered.maxOfOrNull { it.size } ?: 1
                val lineHeight = paint.textSize + 2.2f
                val height = maxLines * lineHeight + 3f
                ensure(height)
                val canvas = page!!.canvas
                rendered.forEachIndexed { index, lines ->
                    val right = if (index == 0) pageWidth - pageMargin else pageMargin + columnWidth
                    val left = if (index == 0) pageWidth - pageMargin - columnWidth else pageMargin
                    canvas.save()
                    canvas.clipRect(left, y, right, y + height)
                    lines.forEachIndexed { lineIndex, line ->
                        canvas.drawText(line, right - 2f, y + paint.textSize + lineIndex * lineHeight, paint)
                    }
                    canvas.restore()
                }
                y += height
            }
            y += 2f
        }

        fun columnWidths(headers: List<String>): List<Float> {
            if (headers.isEmpty()) return emptyList()
            val weights = headers.map { header ->
                when {
                    header.contains("البيان") || header.contains("الوصف") || header.contains("الحساب") || header.contains("الطرف") ||
                        header.contains("المنتج") || header.contains("المادة") || header.contains("السبب") || header.contains("المسؤول") -> 1.8f
                    header.contains("رقم الأمر") || header.contains("التشغيلة") || header.contains("التاريخ") ||
                        header.contains("المرجع") || header.contains("المستند") -> 1.35f
                    else -> 1f
                }
            }
            val total = weights.sum().coerceAtLeast(1f)
            return weights.map { contentWidth * it / total }
        }

        fun cellLines(text: String, paint: Paint, width: Float, maxLines: Int): List<String> {
            val all = wrapText(text.ifBlank { "—" }, paint, (width - cellPadding * 2).coerceAtLeast(24f))
            if (all.size <= maxLines) return all
            val kept = all.take(maxLines).toMutableList()
            val last = kept.last().trimEnd()
            kept[kept.lastIndex] = if (last.endsWith("…")) last else "$last…"
            return kept
        }

        fun rowHeight(cells: List<String>, widths: List<Float>, paint: Paint, maxLines: Int): Float {
            val lineHeight = paint.textSize + 3.5f
            val maxCount = widths.indices.maxOfOrNull { i ->
                cellLines(cells.getOrElse(i) { "" }, paint, widths[i], maxLines).size
            } ?: 1
            return (maxCount * lineHeight + cellPadding * 2).coerceAtLeast(if (compact) 18f else 24f)
        }

        fun drawRow(cells: List<String>, widths: List<Float>, paint: Paint, isHeader: Boolean, alternate: Boolean = false) {
            val height = rowHeight(cells, widths, paint, if (isHeader) 3 else 4)
            ensure(height)
            val canvas = page!!.canvas
            var right = pageWidth - pageMargin
            for (i in widths.indices) {
                val width = widths[i]
                val left = right - width
                if (isHeader) canvas.drawRect(left, y, right, y + height, headerFill)
                else if (alternate) canvas.drawRect(left, y, right, y + height, alternateFill)
                canvas.drawRect(left, y, right, y + height, borderPaint)
                val maxLines = if (compact) { if (isHeader) 2 else 3 } else { if (isHeader) 3 else 4 }
                val lines = cellLines(cells.getOrElse(i) { "" }, paint, width, maxLines)
                val lineHeight = paint.textSize + if (compact) 2.4f else 3.5f
                // Hard clipping is the final guard: no report cell is allowed to paint outside its box.
                canvas.save()
                canvas.clipRect(left + 0.5f, y + 0.5f, right - 0.5f, y + height - 0.5f)
                lines.forEachIndexed { index, line ->
                    canvas.drawText(line, right - cellPadding, y + cellPadding + paint.textSize + index * lineHeight, paint)
                }
                canvas.restore()
                right = left
            }
            y += height
        }

        fun drawTable(table: ReportExportTable) {
            drawWrapped(table.title, section, extraAfter = if (compact) 2f else 5f)
            if (table.headers.isEmpty()) {
                drawWrapped("لا توجد أعمدة معرفة للتقرير.", small, extraAfter = 8f)
                divider()
                return
            }
            if (table.rows.isEmpty()) {
                drawWrapped("لا توجد بيانات.", small, extraAfter = 8f)
                divider()
                return
            }
            val widths = columnWidths(table.headers)
            val columnCount = table.headers.size
            val effectiveHeader = Paint(tableHeader).apply {
                if (!compact) textSize = when { columnCount >= 10 -> 6.2f; columnCount >= 8 -> 6.7f; columnCount >= 6 -> 7.3f; else -> textSize }
            }
            val effectiveBody = Paint(tableBody).apply {
                if (!compact) textSize = when { columnCount >= 10 -> 6.0f; columnCount >= 8 -> 6.5f; columnCount >= 6 -> 7.1f; else -> textSize }
            }
            val headerMaxLines = if (compact) 2 else 3
            val bodyMaxLines = if (compact) 3 else 4
            val headerHeight = rowHeight(table.headers, widths, effectiveHeader, headerMaxLines)
            ensure(headerHeight + if (compact) 18f else 26f)
            drawRow(table.headers, widths, effectiveHeader, isHeader = true)
            table.rows.forEachIndexed { index, row ->
                val height = rowHeight(row, widths, effectiveBody, bodyMaxLines)
                if (y + height > pageBottom) {
                    startPage()
                    drawWrapped("${table.title} — تابع", small, extraAfter = 5f)
                    drawRow(table.headers, widths, effectiveHeader, isHeader = true)
                }
                drawRow(row, widths, effectiveBody, isHeader = false, alternate = index % 2 == 1)
            }
            y += if (compact) 5f else 9f
        }

        startPage()
        // Compact invoice header already contains the subtitle beside the title; do not print it twice.
        if (!compact && document.subtitle.isNotBlank()) drawWrapped(document.subtitle, small, extraAfter = 8f)
        divider()

        if (document.summary.isNotEmpty()) {
            drawWrapped(if (compact) "بيانات الفاتورة" else "الملخص", section, extraAfter = if (compact) 2f else 3f)
            if (compact) drawCompactSummary(document.summary)
            else document.summary.forEach { (k, v) -> drawKeyValue(k, v) }
            divider()
        }

        document.tables.forEach(::drawTable)

        if (document.notes.isNotEmpty()) {
            drawWrapped("ملاحظات", section, extraAfter = if (compact) 1f else 3f)
            document.notes.forEach { drawWrapped("• $it", body, extraAfter = if (compact) 1f else 2f) }
        }

        finishPage()
        val output = ByteArrayOutputStream()
        pdf.writeTo(output)
        pdf.close()
        return output.toByteArray()
    }

    private fun buildXlsx(document: ReportExportDocument): ByteArray {
        val rows = mutableListOf<List<String>>()
        rows.add(listOf(document.title))
        if (document.subtitle.isNotBlank()) rows.add(listOf(document.subtitle))
        rows.add(emptyList())
        if (document.summary.isNotEmpty()) {
            rows.add(listOf("الملخص", "القيمة"))
            document.summary.forEach { rows.add(listOf(it.first, it.second)) }
            rows.add(emptyList())
        }
        document.tables.forEach { table ->
            rows.add(listOf(table.title))
            rows.add(table.headers)
            table.rows.forEach { rows.add(it) }
            rows.add(emptyList())
        }
        if (document.notes.isNotEmpty()) {
            rows.add(listOf("ملاحظات"))
            document.notes.forEach { rows.add(listOf(it)) }
        }

        val landscape = requiresLandscape(document)
        val sheetXml = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
            append("<sheetPr><pageSetUpPr fitToPage=\"1\"/></sheetPr>")
            append("<sheetViews><sheetView workbookViewId=\"0\" rightToLeft=\"1\"/></sheetViews>")
            append("<cols><col min=\"1\" max=\"20\" width=\"24\" customWidth=\"1\"/></cols>")
            append("<sheetData>")
            rows.forEachIndexed { rIndex, row ->
                append("<row r=\"${rIndex + 1}\">")
                row.forEachIndexed { cIndex, value ->
                    val ref = "${columnName(cIndex + 1)}${rIndex + 1}"
                    val baseStyle = when {
                        rIndex == 0 -> 2
                        row.size == 1 && value.isNotBlank() -> 1
                        else -> 0
                    }
                    val parsed = parseSpreadsheetCell(value)
                    if (baseStyle == 0 && parsed.kind != SpreadsheetCellKind.TEXT && parsed.number != null) {
                        val style = if (parsed.kind == SpreadsheetCellKind.CURRENCY) 3 else 4
                        append("<c r=\"$ref\" s=\"$style\"><v>")
                        append(parsed.number.toString())
                        append("</v></c>")
                    } else {
                        append("<c r=\"$ref\" t=\"inlineStr\" s=\"$baseStyle\"><is><t xml:space=\"preserve\">")
                        append(xmlEscape(value))
                        append("</t></is></c>")
                    }
                }
                append("</row>")
            }
            append("</sheetData>")
            append("<pageMargins left=\"0.3\" right=\"0.3\" top=\"0.5\" bottom=\"0.5\" header=\"0.2\" footer=\"0.2\"/>")
            append("<pageSetup orientation=\"")
            append(if (landscape) "landscape" else "portrait")
            append("\" fitToWidth=\"1\" fitToHeight=\"0\" paperSize=\"9\"/>")
            append("</worksheet>")
        }

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun entry(path: String, content: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>""")
            entry("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            entry("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="تقرير" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            entry("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
            entry("xl/styles.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="2"><numFmt numFmtId="164" formatCode="#,##0.00 &quot;ر.ي&quot;"/><numFmt numFmtId="165" formatCode="#,##0.##"/></numFmts><fonts count="2"><font><sz val="11"/><name val="Arial"/></font><font><b/><sz val="12"/><name val="Arial"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border/></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="5"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment horizontal="right" vertical="top" wrapText="1"/></xf><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment horizontal="right" vertical="top" wrapText="1"/></xf><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment horizontal="right" vertical="top" wrapText="1"/></xf><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyAlignment="1"><alignment horizontal="right" vertical="top"/></xf><xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyAlignment="1"><alignment horizontal="right" vertical="top"/></xf></cellXfs></styleSheet>""")
            entry("xl/worksheets/sheet1.xml", sheetXml)
        }
        return out.toByteArray()
    }

    private fun shareBytes(
        context: Context,
        fileName: String,
        mime: String,
        bytes: ByteArray,
        chooserTitle: String
    ) {
        val dir = File(context.cacheDir, "report-share").apply { mkdirs() }
        dir.listFiles()?.filter { com.fush.erp.domain.TrustedTimeService.now() - it.lastModified() > 86_400_000L }?.forEach { it.delete() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { it.write(bytes) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = createShareIntent(context, fileName, mime, uri)
        val chooser = Intent.createChooser(intent, chooserTitle)
        if (context.findActivity() == null) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    internal fun createShareIntent(context: Context, fileName: String, mime: String, uri: Uri): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, fileName.substringBeforeLast('.'))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, fileName, uri)
        }

    internal fun pdfBytesForTest(context: Context, document: ReportExportDocument): ByteArray = buildPdf(context, document)

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    private fun saveBytes(context: Context, fileName: String, mime: String, bytes: ByteArray): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/FushERP")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)) { "تعذر إنشاء ملف التصدير" }
            try {
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: error("تعذر فتح ملف التصدير")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "FushERP").apply { mkdirs() }
            val file = File(dir, fileName)
            FileOutputStream(file).use { it.write(bytes) }
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        }
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isBlank()) return listOf("—")
        val safeWidth = maxWidth.coerceAtLeast(8f)

        fun splitLongToken(token: String): List<String> {
            if (paint.measureText(token) <= safeWidth) return listOf(token)
            val chunks = mutableListOf<String>()
            var current = ""
            token.forEach { ch ->
                val candidate = current + ch
                if (current.isNotEmpty() && paint.measureText(candidate) > safeWidth) {
                    chunks += current
                    current = ch.toString()
                } else {
                    current = candidate
                }
            }
            if (current.isNotEmpty()) chunks += current
            return chunks.ifEmpty { listOf(token) }
        }

        val lines = mutableListOf<String>()
        text.split('\n').forEach { paragraph ->
            val rawWords = paragraph.split(Regex("\\s+")).filter { it.isNotBlank() }
            val words = rawWords.flatMap(::splitLongToken)
            if (words.isEmpty()) {
                lines += ""
            } else {
                var current = ""
                for (word in words) {
                    val candidate = if (current.isBlank()) word else "$current $word"
                    if (paint.measureText(candidate) <= safeWidth) {
                        current = candidate
                    } else {
                        if (current.isNotBlank()) lines += current
                        current = word
                    }
                }
                if (current.isNotBlank()) lines += current
            }
        }
        return lines.ifEmpty { listOf("—") }
    }


    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun columnName(index: Int): String {
        var value = index
        val out = StringBuilder()
        while (value > 0) {
            value--
            out.append(('A'.code + (value % 26)).toChar())
            value /= 26
        }
        return out.reverse().toString()
    }

    private fun stamp(): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(TrustedTimeService.now()))

    private class ByteArrayPdfPrintAdapter(
        private val jobName: String,
        private val bytes: ByteArray
    ) : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes?,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback?,
            extras: Bundle?
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback?.onLayoutCancelled()
                return
            }
            callback?.onLayoutFinished(
                PrintDocumentInfo.Builder("$jobName.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                    .build(),
                true
            )
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor?,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback?
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback?.onWriteCancelled()
                return
            }
            try {
                requireNotNull(destination)
                ParcelFileDescriptor.AutoCloseOutputStream(destination).use { it.write(bytes) }
                callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback?.onWriteFailed(e.message ?: "تعذر تجهيز الطباعة")
            }
        }
    }
}
