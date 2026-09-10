package com.fush.erp.ui.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
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

data class ReportExportDocument(
    val title: String,
    val subtitle: String = "",
    val summary: List<Pair<String, String>> = emptyList(),
    val tables: List<ReportExportTable> = emptyList(),
    val notes: List<String> = emptyList()
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

    fun exportPdf(context: Context, document: ReportExportDocument, baseName: String): Uri {
        val bytes = buildPdf(document)
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
        val bytes = buildPdf(document)
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
        val pdfBytes = buildPdf(document)
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
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
        document.tables.any { it.headers.size >= 6 || it.rows.any { row -> row.size >= 6 } }

    private fun buildPdf(document: ReportExportDocument): ByteArray {
        val landscape = requiresLandscape(document)
        val pageWidth = if (landscape) A4_LONG else A4_SHORT
        val pageHeight = if (landscape) A4_SHORT else A4_LONG
        val contentWidth = pageWidth - (MARGIN * 2)
        val pageBottom = pageHeight - MARGIN - FOOTER_SPACE
        val pdf = PdfDocument()

        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 30, 30)
            textSize = 9.5f
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val label = Paint(body).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val heading = Paint(body).apply { textSize = 18f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val section = Paint(body).apply { textSize = 12.5f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val small = Paint(body).apply { textSize = 8f; color = Color.DKGRAY }
        val tableHeader = Paint(body).apply { textSize = 8.5f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val tableBody = Paint(body).apply { textSize = 8.3f }
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

        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f
        val generatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())

        fun finishPage() {
            val current = page ?: return
            val footer = Paint(small).apply { textAlign = Paint.Align.CENTER }
            current.canvas.drawText(
                "Fush ERP • $generatedAt • صفحة $pageNo",
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
            y = MARGIN
            val canvas = page!!.canvas
            canvas.drawText("Fush ERP", pageWidth - MARGIN, y + small.textSize, small)
            canvas.drawText(
                if (landscape) "A4 أفقي" else "A4 عمودي",
                MARGIN,
                y + small.textSize,
                Paint(small).apply { textAlign = Paint.Align.LEFT }
            )
            y += 18f
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
                page!!.canvas.drawText(line, pageWidth - MARGIN - indent, y + paint.textSize, paint)
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
            val xRight = pageWidth - MARGIN
            canvas.drawText(keyText, xRight, y + label.textSize, label)
            val valueRight = xRight - keyWidth - 12f
            for ((index, line) in lines.withIndex()) {
                canvas.drawText(line, valueRight, y + valuePaint.textSize, valuePaint)
                if (index < lines.lastIndex) y += valuePaint.textSize + 4f
            }
            y += body.textSize + 8f
        }

        fun divider() {
            ensure(12f)
            page!!.canvas.drawLine(MARGIN, y, pageWidth - MARGIN, y, dividerPaint)
            y += 9f
        }

        fun columnWidths(headers: List<String>): List<Float> {
            if (headers.isEmpty()) return emptyList()
            val weights = headers.map { header ->
                when {
                    header.contains("البيان") || header.contains("الوصف") || header.contains("الحساب") || header.contains("الطرف") -> 1.8f
                    header.contains("التاريخ") || header.contains("المرجع") || header.contains("المستند") -> 1.2f
                    else -> 1f
                }
            }
            val total = weights.sum().coerceAtLeast(1f)
            return weights.map { contentWidth * it / total }
        }

        fun cellLines(text: String, paint: Paint, width: Float, maxLines: Int): List<String> {
            val all = wrapText(text.ifBlank { "—" }, paint, (width - CELL_PADDING * 2).coerceAtLeast(24f))
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
            return (maxCount * lineHeight + CELL_PADDING * 2).coerceAtLeast(24f)
        }

        fun drawRow(cells: List<String>, widths: List<Float>, paint: Paint, isHeader: Boolean, alternate: Boolean = false) {
            val height = rowHeight(cells, widths, paint, if (isHeader) 3 else 4)
            ensure(height)
            val canvas = page!!.canvas
            var right = pageWidth - MARGIN
            for (i in widths.indices) {
                val width = widths[i]
                val left = right - width
                if (isHeader) canvas.drawRect(left, y, right, y + height, headerFill)
                else if (alternate) canvas.drawRect(left, y, right, y + height, alternateFill)
                canvas.drawRect(left, y, right, y + height, borderPaint)
                val lines = cellLines(cells.getOrElse(i) { "" }, paint, width, if (isHeader) 3 else 4)
                val lineHeight = paint.textSize + 3.5f
                lines.forEachIndexed { index, line ->
                    canvas.drawText(line, right - CELL_PADDING, y + CELL_PADDING + paint.textSize + index * lineHeight, paint)
                }
                right = left
            }
            y += height
        }

        fun drawTable(table: ReportExportTable) {
            drawWrapped(table.title, section, extraAfter = 5f)
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
            val headerHeight = rowHeight(table.headers, widths, tableHeader, 3)
            ensure(headerHeight + 26f)
            drawRow(table.headers, widths, tableHeader, isHeader = true)
            table.rows.forEachIndexed { index, row ->
                val height = rowHeight(row, widths, tableBody, 4)
                if (y + height > pageBottom) {
                    startPage()
                    drawWrapped("${table.title} — تابع", small, extraAfter = 5f)
                    drawRow(table.headers, widths, tableHeader, isHeader = true)
                }
                drawRow(row, widths, tableBody, isHeader = false, alternate = index % 2 == 1)
            }
            y += 9f
        }

        startPage()
        drawWrapped(document.title, heading, extraAfter = 4f)
        if (document.subtitle.isNotBlank()) drawWrapped(document.subtitle, small, extraAfter = 8f)
        divider()

        if (document.summary.isNotEmpty()) {
            drawWrapped("الملخص", section)
            document.summary.forEach { (k, v) -> drawKeyValue(k, v) }
            divider()
        }

        document.tables.forEach(::drawTable)

        if (document.notes.isNotEmpty()) {
            drawWrapped("ملاحظات", section)
            document.notes.forEach { drawWrapped("• $it", body, extraAfter = 2f) }
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
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }?.forEach { it.delete() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { it.write(bytes) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, fileName.substringBeforeLast('.'))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
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
        val lines = mutableListOf<String>()
        text.split('\n').forEach { paragraph ->
            val words = paragraph.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.isEmpty()) {
                lines += ""
            } else {
                var current = ""
                for (word in words) {
                    val candidate = if (current.isBlank()) word else "$current $word"
                    if (paint.measureText(candidate) <= maxWidth || current.isBlank()) {
                        current = candidate
                    } else {
                        lines += current
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

    private fun stamp(): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

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
