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
    private const val PDF_PAGE_WIDTH = 595
    private const val PDF_PAGE_HEIGHT = 842
    private const val MARGIN = 36f
    private const val CONTENT_WIDTH = PDF_PAGE_WIDTH - (MARGIN * 2)

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

    fun shareExport(context: Context, uri: Uri, mimeType: String, title: String = "مشاركة تقرير Fush ERP") {
        val shareUri = if (uri.scheme == "file") {
            FileProvider.getUriForFile(context, "${context.packageName}.files", File(requireNotNull(uri.path)))
        } else uri
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    fun printPreview(context: Context, document: ReportExportDocument, jobName: String) {
        val pdfBytes = buildPdf(document)
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val adapter = ByteArrayPdfPrintAdapter(jobName, pdfBytes)
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .build()
        printManager.print(jobName, adapter, attributes)
    }

    private fun buildPdf(document: ReportExportDocument): ByteArray {
        val pdf = PdfDocument()
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 30, 30)
            textSize = 10.5f
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val label = Paint(body).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val heading = Paint(body).apply { textSize = 20f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val section = Paint(body).apply { textSize = 14f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val small = Paint(body).apply { textSize = 8.5f; color = Color.DKGRAY }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.LTGRAY; strokeWidth = 0.8f }

        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        fun startPage() {
            page?.let { pdf.finishPage(it) }
            pageNo += 1
            page = pdf.startPage(PdfDocument.PageInfo.Builder(PDF_PAGE_WIDTH, PDF_PAGE_HEIGHT, pageNo).create())
            y = MARGIN
            val canvas = page!!.canvas
            canvas.drawText("Fush ERP", PDF_PAGE_WIDTH - MARGIN, y, small)
            canvas.drawText("صفحة $pageNo", MARGIN + 34f, y, Paint(small).apply { textAlign = Paint.Align.LEFT })
            y += 18f
        }

        fun ensure(space: Float) {
            if (page == null) startPage()
            if (y + space > PDF_PAGE_HEIGHT - MARGIN) startPage()
        }

        fun drawWrapped(text: String, paint: Paint = body, indent: Float = 0f, extraAfter: Float = 3f) {
            val clean = text.ifBlank { "—" }
            val lines = wrapText(clean, paint, CONTENT_WIDTH - indent)
            for (line in lines) {
                ensure(paint.textSize + 8f)
                page!!.canvas.drawText(line, PDF_PAGE_WIDTH - MARGIN - indent, y + paint.textSize, paint)
                y += paint.textSize + 4f
            }
            y += extraAfter
        }

        fun drawKeyValue(key: String, value: String) {
            val keyText = "$key:"
            val keyWidth = label.measureText(keyText)
            val available = CONTENT_WIDTH - keyWidth - 16f
            val valuePaint = Paint(body)
            val lines = wrapText(value.ifBlank { "—" }, valuePaint, available.coerceAtLeast(150f))
            val requiredHeight = label.textSize + 8f + ((lines.size - 1).coerceAtLeast(0) * (valuePaint.textSize + 4f))
            ensure(requiredHeight)
            val canvas = page!!.canvas
            val xRight = PDF_PAGE_WIDTH - MARGIN
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
            page!!.canvas.drawLine(MARGIN, y, PDF_PAGE_WIDTH - MARGIN, y, linePaint)
            y += 10f
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

        for (table in document.tables) {
            drawWrapped(table.title, section, extraAfter = 5f)
            if (table.rows.isEmpty()) {
                drawWrapped("لا توجد بيانات.", small, extraAfter = 8f)
                divider()
                continue
            }
            table.rows.forEachIndexed { index, row ->
                ensure(30f)
                drawWrapped("سجل ${index + 1}", label, extraAfter = 2f)
                val count = minOf(table.headers.size, row.size)
                for (i in 0 until count) drawKeyValue(table.headers[i], row[i])
                divider()
            }
        }

        if (document.notes.isNotEmpty()) {
            drawWrapped("ملاحظات", section)
            document.notes.forEach { drawWrapped("• $it", body, extraAfter = 2f) }
        }

        page?.let { pdf.finishPage(it) }
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

        val sheetXml = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
            append("<sheetViews><sheetView workbookViewId=\"0\" rightToLeft=\"1\"/></sheetViews>")
            append("<cols><col min=\"1\" max=\"20\" width=\"24\" customWidth=\"1\"/></cols>")
            append("<sheetData>")
            rows.forEachIndexed { rIndex, row ->
                append("<row r=\"${rIndex + 1}\">")
                row.forEachIndexed { cIndex, value ->
                    val ref = "${columnName(cIndex + 1)}${rIndex + 1}"
                    val style = when {
                        rIndex == 0 -> 2
                        row.size == 1 && value.isNotBlank() -> 1
                        else -> 0
                    }
                    append("<c r=\"$ref\" t=\"inlineStr\" s=\"$style\"><is><t xml:space=\"preserve\">")
                    append(xmlEscape(value))
                    append("</t></is></c>")
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun entry(path: String, content: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("[Content_Types].xml", """<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>""")
            entry("_rels/.rels", """<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>""")
            entry("xl/_rels/workbook.xml.rels", """<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>""")
            entry("xl/workbook.xml", """<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"تقرير Fush ERP\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>""")
            entry("xl/styles.xml", """<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><fonts count=\"3\"><font><sz val=\"11\"/><name val=\"Arial\"/></font><font><b/><sz val=\"11\"/><name val=\"Arial\"/></font><font><b/><sz val=\"14\"/><name val=\"Arial\"/></font></fonts><fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills><borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"3\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/><xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\"/><xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"0\"/></cellXfs></styleSheet>""")
            entry("xl/worksheets/sheet1.xml", sheetXml)
        }
        return out.toByteArray()
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
                resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("تعذر فتح ملف التصدير")
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
            Uri.fromFile(file)
        }
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isBlank()) return listOf("—")
        val lines = mutableListOf<String>()
        text.split('\n').forEach { paragraph ->
            val words = paragraph.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.isEmpty()) lines += "" else {
                var current = ""
                for (word in words) {
                    val candidate = if (current.isBlank()) word else "$current $word"
                    if (paint.measureText(candidate) <= maxWidth || current.isBlank()) current = candidate
                    else { lines += current; current = word }
                }
                if (current.isNotBlank()) lines += current
            }
        }
        return lines.ifEmpty { listOf("—") }
    }

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    private fun columnName(index: Int): String {
        var value = index
        val out = StringBuilder()
        while (value > 0) { value--; out.append(('A'.code + (value % 26)).toChar()); value /= 26 }
        return out.reverse().toString()
    }

    private fun stamp(): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    private class ByteArrayPdfPrintAdapter(private val jobName: String, private val bytes: ByteArray) : PrintDocumentAdapter() {
        override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes?, cancellationSignal: CancellationSignal?, callback: LayoutResultCallback?, extras: Bundle?) {
            if (cancellationSignal?.isCanceled == true) { callback?.onLayoutCancelled(); return }
            callback?.onLayoutFinished(PrintDocumentInfo.Builder("$jobName.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN).build(), true)
        }
        override fun onWrite(pages: Array<out PageRange>?, destination: ParcelFileDescriptor?, cancellationSignal: CancellationSignal?, callback: WriteResultCallback?) {
            if (cancellationSignal?.isCanceled == true) { callback?.onWriteCancelled(); return }
            try { requireNotNull(destination); ParcelFileDescriptor.AutoCloseOutputStream(destination).use { it.write(bytes) }; callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES)) }
            catch (e: Exception) { callback?.onWriteFailed(e.message ?: "تعذر تجهيز الطباعة") }
        }
    }
}
