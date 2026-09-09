package com.fush.erp.attachments

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/** Durable attachment storage. DB values use a portable app-relative reference. */
object AttachmentStorage {
    private const val SCHEME = "fush-attachment://"
    private const val ROOT_DIR = "attachments"

    data class Stored(val reference: String, val fileName: String, val mimeType: String)

    fun root(context: Context): File = File(context.filesDir, ROOT_DIR).apply { mkdirs() }
    fun isManaged(reference: String): Boolean = reference.startsWith(SCHEME)

    fun importFromUri(context: Context, source: Uri, bucket: String, preferredName: String? = null): Stored {
        val resolver = context.contentResolver
        val mime = resolver.getType(source).orEmpty()
        val fileName = sanitizeName(preferredName?.ifBlank { null } ?: source.lastPathSegment ?: "attachment")
        val relative = "${sanitizeSegment(bucket)}/${com.fush.erp.domain.TrustedTimeService.now()}-${System.nanoTime()}-$fileName"
        val destination = fileForRelative(context, relative)
        destination.parentFile?.mkdirs()
        resolver.openInputStream(source)?.use { input ->
            FileOutputStream(destination).use { output -> input.copyTo(output) }
        } ?: error("تعذر فتح المرفق المحدد")
        require(destination.isFile && destination.length() > 0L) { "المرفق المحدد فارغ أو غير قابل للقراءة" }
        return Stored(SCHEME + relative, fileName, mime)
    }

    fun migrateLegacyReference(context: Context, reference: String, bucket: String, fileName: String): String {
        if (isManaged(reference)) {
            require(resolveFile(context, reference).isFile) { "المرفق الداخلي غير موجود: $fileName" }
            return reference
        }
        val source = Uri.parse(reference)
        return importFromUri(context, source, bucket, fileName).reference
    }

    fun resolveFile(context: Context, reference: String): File {
        require(isManaged(reference)) { "المرجع ليس مرفق FUSH داخليًا" }
        val relative = reference.removePrefix(SCHEME)
        return fileForRelative(context, relative)
    }

    fun contentUri(context: Context, reference: String): Uri = if (isManaged(reference)) {
        val file = resolveFile(context, reference)
        require(file.isFile) { "ملف المرفق غير موجود" }
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    } else Uri.parse(reference)

    fun openIntent(context: Context, reference: String, mimeType: String): Intent {
        val uri = contentUri(context, reference)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, "FUSH attachment", uri)
        }
    }

    fun shareIntent(context: Context, reference: String, fileName: String, mimeType: String): Intent {
        val uri = contentUri(context, reference)
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType.ifBlank { "application/octet-stream" }
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, fileName, uri)
        }
    }

    fun exportToDownloads(context: Context, reference: String, fileName: String, mimeType: String): Uri {
        val safeName = sanitizeName(fileName)
        val source = if (isManaged(reference)) resolveFile(context, reference).inputStream()
        else context.contentResolver.openInputStream(Uri.parse(reference)) ?: error("تعذر فتح المرفق")
        source.use { input ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType.ifBlank { "application/octet-stream" })
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/FushERP/Attachments")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)) { "تعذر إنشاء نسخة المرفق" }
                try {
                    resolver.openOutputStream(uri)?.use { output -> input.copyTo(output) } ?: error("تعذر نسخ المرفق")
                    values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING, 0); resolver.update(uri, values, null, null)
                    return uri
                } catch (t: Throwable) {
                    resolver.delete(uri, null, null); throw t
                }
            }
            val outDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "FushERP/Attachments").apply { mkdirs() }
            val out = File(outDir, safeName)
            FileOutputStream(out).use { output -> input.copyTo(output) }
            return FileProvider.getUriForFile(context, "${context.packageName}.files", out)
        }
    }

    private fun fileForRelative(context: Context, relative: String): File {
        require(relative.isNotBlank() && !relative.startsWith('/') && !relative.contains("..")) { "مسار المرفق غير آمن" }
        val root = root(context).canonicalFile
        val file = File(root, relative).canonicalFile
        require(file.path.startsWith(root.path + File.separator)) { "مسار المرفق خارج التخزين المسموح" }
        return file
    }

    private fun sanitizeSegment(value: String): String = value.lowercase().replace(Regex("[^a-z0-9_-]+"), "-").trim('-').ifBlank { "general" }
    private fun sanitizeName(value: String): String = value.substringAfterLast('/').substringAfterLast('\\')
        .replace(Regex("[\\/:*?\"<>|\\u0000-\\u001F]+"), "_").trim().take(120).ifBlank { "attachment" }
}
