package com.fush.erp.ui.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReportExportSupportAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun pdfRendererProducesRealPdfBytes() {
        val bytes = ReportExportSupport.pdfBytesForTest(
            context,
            ReportExportDocument(
                title = "اختبار الطباعة",
                summary = listOf("الإجمالي" to "100"),
                tables = listOf(ReportExportTable("بيانات", listOf("البيان", "القيمة"), listOf(listOf("A", "100"))))
            )
        )
        assertTrue(bytes.size > 100)
        assertTrue(bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "%PDF")
    }

    @Test
    fun shareIntentCarriesStreamClipDataAndReadGrant() {
        val dir = File(context.cacheDir, "report-share").apply { mkdirs() }
        val file = File(dir, "share-test.pdf").apply { writeBytes("%PDF-test".toByteArray()) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = ReportExportSupport.createShareIntent(context, file.name, "application/pdf", uri)
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals(uri, intent.getParcelableExtra(Intent.EXTRA_STREAM))
        assertNotNull(intent.clipData)
        assertEquals(uri, intent.clipData!!.getItemAt(0).uri)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun printPreviewFailsClearlyWhenNoActivityIsAvailable() {
        val failure = runCatching {
            ReportExportSupport.printPreview(context, ReportExportDocument(title = "Print"), "FUSH test")
        }.exceptionOrNull()
        assertNotNull(failure)
        assertTrue(failure!!.message.orEmpty().contains("شاشة الطباعة"))
    }
}
