package com.fush.erp.storage

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Stores expense evidence inside app-owned storage instead of depending on a
 * document provider continuing to grant access to a content:// URI forever.
 *
 * The database stores a logical managed URI. The physical app-data path is
 * deliberately not persisted, so a restored database does not depend on the
 * Android user id or the device-specific /data path.
 */
class ExpenseAttachmentStorage(context: Context) {
    private val appContext = context.applicationContext

    data class StoredAttachment(
        val uri: String,
        val sizeBytes: Long
    )

    fun storeFrom(sourceUri: Uri, displayName: String): StoredAttachment {
        val directory = File(appContext.filesDir, DIRECTORY_NAME)
        require(directory.exists() || directory.mkdirs()) { "تعذر إنشاء مجلد مرفقات المصروفات" }

        val storedName = UUID.randomUUID().toString() + safeExtension(displayName)
        val destination = File(directory, storedName)
        val temporary = File(directory, ".$storedName.part")

        try {
            val input = requireNotNull(appContext.contentResolver.openInputStream(sourceUri)) {
                "تعذر قراءة ملف المرفق"
            }
            var total = 0L
            input.use { source ->
                FileOutputStream(temporary).use { target ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = source.read(buffer)
                        if (read <= 0) break
                        total += read
                        require(total <= MAX_ATTACHMENT_BYTES) {
                            "حجم المرفق يتجاوز الحد المسموح (${MAX_ATTACHMENT_BYTES / (1024 * 1024)} MB)"
                        }
                        target.write(buffer, 0, read)
                    }
                    target.fd.sync()
                }
            }
            require(total > 0L) { "ملف المرفق فارغ" }
            require(temporary.renameTo(destination)) { "تعذر تثبيت ملف المرفق داخل التطبيق" }
            return StoredAttachment(managedUriFor(storedName), total)
        } catch (t: Throwable) {
            temporary.delete()
            destination.delete()
            throw t
        }
    }

    /**
     * Compatibility bridge for the current Compose caller. `storeFrom` is the
     * canonical API name; Kotlin permits this escaped declaration while the
     * call remains source-compatible as `.import(...)`.
     */
    @Deprecated("Use storeFrom", ReplaceWith("storeFrom(sourceUri, displayName)"))
    fun `import`(sourceUri: Uri, displayName: String): StoredAttachment = storeFrom(sourceUri, displayName)

    fun resolveManagedFile(uri: String): File? {
        val fileName = managedFileName(uri) ?: return null
        val directory = File(appContext.filesDir, DIRECTORY_NAME)
        val candidate = File(directory, fileName)
        return runCatching {
            val canonicalDirectory = directory.canonicalFile
            val canonicalCandidate = candidate.canonicalFile
            canonicalCandidate.takeIf { it.parentFile == canonicalDirectory }
        }.getOrNull()
    }

    fun deleteManaged(uri: String): Boolean = resolveManagedFile(uri)?.let { !it.exists() || it.delete() } ?: false

    /**
     * Removes only app-managed files that no database row references. External
     * legacy content:// URIs are never touched.
     */
    fun pruneOrphans(referencedUris: Set<String>): Int {
        val directory = File(appContext.filesDir, DIRECTORY_NAME)
        if (!directory.isDirectory) return 0
        val referencedNames = referencedUris.mapNotNull(::managedFileName).toSet()
        var deleted = 0
        directory.listFiles().orEmpty().forEach { file ->
            if (!file.isFile) return@forEach
            val orphan = file.name.endsWith(".part") || file.name !in referencedNames
            if (orphan && file.delete()) deleted++
        }
        return deleted
    }

    companion object {
        const val DIRECTORY_NAME = "expense-attachments"
        const val MANAGED_URI_PREFIX = "fush-attachment://local/"
        const val MAX_ATTACHMENT_BYTES: Long = 25L * 1024L * 1024L

        private val FILE_NAME_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
        private val EXTENSION_PATTERN = Regex("[A-Za-z0-9]{1,10}")

        internal fun managedUriFor(fileName: String): String {
            require(FILE_NAME_PATTERN.matches(fileName)) { "اسم ملف المرفق الداخلي غير صالح" }
            require(!fileName.contains("..")) { "اسم ملف المرفق الداخلي غير آمن" }
            return MANAGED_URI_PREFIX + fileName
        }

        internal fun managedFileName(uri: String): String? {
            if (!uri.startsWith(MANAGED_URI_PREFIX)) return null
            val fileName = uri.removePrefix(MANAGED_URI_PREFIX)
            if (!FILE_NAME_PATTERN.matches(fileName)) return null
            if (fileName.contains("..") || fileName.contains('/') || fileName.contains('\\')) return null
            return fileName
        }

        internal fun safeExtension(displayName: String): String {
            val raw = displayName.substringAfterLast('.', "").trim().lowercase()
            return if (EXTENSION_PATTERN.matches(raw)) ".$raw" else ""
        }
    }
}
