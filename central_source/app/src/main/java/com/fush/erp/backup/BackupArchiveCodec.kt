package com.fush.erp.backup

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupManifest(
    val formatVersion: Int,
    val packageId: String,
    val appVersion: String,
    val schemaVersion: Int,
    val createdAt: Long,
    val databaseSha256: String
)

object BackupArchiveCodec {
    const val FORMAT_VERSION = 1
    const val MANIFEST_ENTRY = "backup-manifest.properties"
    const val DATABASE_ENTRY = "database/fush_erp.db"

    fun writeArchive(databaseFile: File, outputFile: File, manifest: BackupManifest) {
        require(databaseFile.isFile) { "ملف قاعدة البيانات غير موجود" }
        outputFile.parentFile?.mkdirs()
        outputFile.delete()
        try {
            ZipOutputStream(FileOutputStream(outputFile)).use { zip ->
            val props = Properties().apply {
                setProperty("formatVersion", manifest.formatVersion.toString())
                setProperty("packageId", manifest.packageId)
                setProperty("appVersion", manifest.appVersion)
                setProperty("schemaVersion", manifest.schemaVersion.toString())
                setProperty("createdAt", manifest.createdAt.toString())
                setProperty("databaseSha256", manifest.databaseSha256.lowercase())
            }
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            props.store(zip, "Fush ERP backup manifest")
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(DATABASE_ENTRY))
                FileInputStream(databaseFile).use { it.copyTo(zip) }
                zip.closeEntry()
            }
        } catch (t: Throwable) {
            outputFile.delete()
            throw t
        }
    }

    fun extractAndVerify(input: InputStream, destinationDatabase: File): BackupManifest {
        destinationDatabase.parentFile?.mkdirs()
        destinationDatabase.delete()
        var manifest: BackupManifest? = null
        var databaseFound = false
        try {
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(!entry.name.contains("..") && !entry.name.startsWith('/')) { "ملف النسخة الاحتياطية غير آمن" }
                    when (entry.name) {
                        MANIFEST_ENTRY -> {
                            require(manifest == null) { "ملف النسخة يحتوي بيانات تعريف مكررة" }
                            val props = Properties().apply { load(zip) }
                            manifest = BackupManifest(
                                formatVersion = props.getProperty("formatVersion")?.toIntOrNull()
                                    ?: error("نسخة احتياطية بلا إصدار تنسيق"),
                                packageId = props.getProperty("packageId") ?: error("نسخة احتياطية بلا معرف تطبيق"),
                                appVersion = props.getProperty("appVersion") ?: "غير معروف",
                                schemaVersion = props.getProperty("schemaVersion")?.toIntOrNull()
                                    ?: error("نسخة احتياطية بلا إصدار قاعدة بيانات"),
                                createdAt = props.getProperty("createdAt")?.toLongOrNull()
                                    ?: error("نسخة احتياطية بلا تاريخ"),
                                databaseSha256 = props.getProperty("databaseSha256")?.lowercase()
                                    ?: error("نسخة احتياطية بلا بصمة قاعدة البيانات")
                            )
                        }
                        DATABASE_ENTRY -> {
                            require(!databaseFound) { "ملف النسخة يحتوي قاعدة بيانات مكررة" }
                            FileOutputStream(destinationDatabase).use { out -> zip.copyTo(out) }
                            databaseFound = true
                        }
                    }
                    zip.closeEntry()
                }
            }
            val result = manifest ?: error("الملف ليس نسخة Fush ERP احتياطية صالحة")
            require(result.formatVersion == FORMAT_VERSION) { "إصدار النسخة الاحتياطية غير مدعوم" }
            require(databaseFound && destinationDatabase.isFile && destinationDatabase.length() > 0L) { "قاعدة البيانات غير موجودة داخل النسخة" }
            require(sha256(destinationDatabase).equals(result.databaseSha256, ignoreCase = true)) { "فشل التحقق من بصمة قاعدة البيانات" }
            return result
        } catch (t: Throwable) {
            destinationDatabase.delete()
            throw t
        }
    }

    fun sha256(file: File): String = FileInputStream(file).use { sha256(it) }

    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
