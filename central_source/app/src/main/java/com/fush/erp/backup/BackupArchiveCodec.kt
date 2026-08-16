package com.fush.erp.backup

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class BackupManifest(
    val formatVersion: Int,
    val packageId: String,
    val appVersion: String,
    val schemaVersion: Int,
    val createdAt: Long,
    val databaseSha256: String,
    val encryptionAlgorithm: String = BackupArchiveCodec.ENCRYPTION_ALGORITHM
)

object BackupArchiveCodec {
    const val FORMAT_VERSION = 2
    const val LEGACY_FORMAT_VERSION = 1
    const val MANIFEST_ENTRY = "backup-manifest.properties"
    const val DATABASE_ENTRY = "database/fush_erp.db"
    const val ENCRYPTION_ALGORITHM = "AES-256-GCM"

    private const val ENVELOPE_VERSION = 1
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12
    private val MAGIC = byteArrayOf(0x46, 0x55, 0x53, 0x48, 0x42, 0x4B, 0x50, 0x32) // FUSHBKP2

    fun writeArchive(databaseFile: File, outputFile: File, manifest: BackupManifest, encryptionKey: SecretKey) {
        require(databaseFile.isFile) { "ملف قاعدة البيانات غير موجود" }
        require(encryptionKey.algorithm.equals("AES", ignoreCase = true)) { "مفتاح النسخ الاحتياطي يجب أن يكون AES" }
        require(manifest.formatVersion == FORMAT_VERSION) { "إصدار تنسيق النسخة غير مدعوم للإنشاء" }
        outputFile.parentFile?.mkdirs()
        outputFile.delete()
        try {
            FileOutputStream(outputFile).use { fileOut ->
                val iv = ByteArray(GCM_IV_BYTES).also { SecureRandom().nextBytes(it) }
                val header = DataOutputStream(fileOut)
                header.write(MAGIC)
                header.writeByte(ENVELOPE_VERSION)
                header.writeByte(iv.size)
                header.write(iv)
                header.flush()

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, GCMParameterSpec(GCM_TAG_BITS, iv))
                cipher.updateAAD(MAGIC)
                cipher.updateAAD(byteArrayOf(ENVELOPE_VERSION.toByte()))

                CipherOutputStream(fileOut, cipher).use { cipherOut ->
                    writeZipPayload(databaseFile, cipherOut, manifest)
                }
            }
        } catch (t: Throwable) {
            outputFile.delete()
            throw t
        }
    }

    fun extractAndVerify(
        input: InputStream,
        destinationDatabase: File,
        encryptionKey: SecretKey,
        allowLegacyPlaintext: Boolean = true
    ): BackupManifest {
        destinationDatabase.parentFile?.mkdirs()
        destinationDatabase.delete()
        try {
            val buffered = if (input is BufferedInputStream) input else BufferedInputStream(input)
            buffered.mark(MAGIC.size + 2)
            val prefix = ByteArray(MAGIC.size)
            val read = buffered.read(prefix)
            buffered.reset()

            val manifest = if (read == MAGIC.size && prefix.contentEquals(MAGIC)) {
                extractEncrypted(buffered, destinationDatabase, encryptionKey)
            } else {
                require(allowLegacyPlaintext) { "النسخة الاحتياطية غير مشفرة أو بتنسيق غير مدعوم" }
                extractZipPayload(buffered, destinationDatabase, expectedFormatVersions = setOf(LEGACY_FORMAT_VERSION))
            }
            require(sha256(destinationDatabase).equals(manifest.databaseSha256, ignoreCase = true)) {
                "فشل التحقق من بصمة قاعدة البيانات"
            }
            return manifest
        } catch (t: Throwable) {
            destinationDatabase.delete()
            throw t
        }
    }

    private fun extractEncrypted(input: InputStream, destinationDatabase: File, encryptionKey: SecretKey): BackupManifest {
        require(encryptionKey.algorithm.equals("AES", ignoreCase = true)) { "مفتاح النسخ الاحتياطي يجب أن يكون AES" }
        val header = DataInputStream(input)
        val magic = ByteArray(MAGIC.size)
        header.readFully(magic)
        require(magic.contentEquals(MAGIC)) { "ترويسة النسخة الاحتياطية غير صالحة" }
        val envelopeVersion = header.readUnsignedByte()
        require(envelopeVersion == ENVELOPE_VERSION) { "إصدار تشفير النسخة الاحتياطية غير مدعوم" }
        val ivLength = header.readUnsignedByte()
        require(ivLength == GCM_IV_BYTES) { "بيانات تشفير النسخة الاحتياطية غير صالحة" }
        val iv = ByteArray(ivLength)
        header.readFully(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(MAGIC)
        cipher.updateAAD(byteArrayOf(envelopeVersion.toByte()))

        val manifest = CipherInputStream(input, cipher).use { cipherIn ->
            extractZipPayload(cipherIn, destinationDatabase, expectedFormatVersions = setOf(FORMAT_VERSION))
        }
        require(manifest.encryptionAlgorithm == ENCRYPTION_ALGORITHM) { "خوارزمية تشفير النسخة الاحتياطية غير مدعومة" }
        return manifest
    }

    private fun writeZipPayload(databaseFile: File, output: OutputStream, manifest: BackupManifest) {
        ZipOutputStream(output).use { zip ->
            val props = Properties().apply {
                setProperty("formatVersion", manifest.formatVersion.toString())
                setProperty("packageId", manifest.packageId)
                setProperty("appVersion", manifest.appVersion)
                setProperty("schemaVersion", manifest.schemaVersion.toString())
                setProperty("createdAt", manifest.createdAt.toString())
                setProperty("databaseSha256", manifest.databaseSha256.lowercase())
                setProperty("encryptionAlgorithm", manifest.encryptionAlgorithm)
            }
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            props.store(zip, "Fush ERP backup manifest")
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(DATABASE_ENTRY))
            FileInputStream(databaseFile).use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }

    private fun extractZipPayload(
        input: InputStream,
        destinationDatabase: File,
        expectedFormatVersions: Set<Int>
    ): BackupManifest {
        var manifest: BackupManifest? = null
        var databaseFound = false
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.name.contains("..") && !entry.name.startsWith('/')) { "ملف النسخة الاحتياطية غير آمن" }
                when (entry.name) {
                    MANIFEST_ENTRY -> {
                        require(manifest == null) { "ملف النسخة يحتوي بيانات تعريف مكررة" }
                        val props = Properties().apply { load(zip) }
                        val formatVersion = props.getProperty("formatVersion")?.toIntOrNull()
                            ?: error("نسخة احتياطية بلا إصدار تنسيق")
                        manifest = BackupManifest(
                            formatVersion = formatVersion,
                            packageId = props.getProperty("packageId") ?: error("نسخة احتياطية بلا معرف تطبيق"),
                            appVersion = props.getProperty("appVersion") ?: "غير معروف",
                            schemaVersion = props.getProperty("schemaVersion")?.toIntOrNull()
                                ?: error("نسخة احتياطية بلا إصدار قاعدة بيانات"),
                            createdAt = props.getProperty("createdAt")?.toLongOrNull()
                                ?: error("نسخة احتياطية بلا تاريخ"),
                            databaseSha256 = props.getProperty("databaseSha256")?.lowercase()
                                ?: error("نسخة احتياطية بلا بصمة قاعدة البيانات"),
                            encryptionAlgorithm = props.getProperty("encryptionAlgorithm")
                                ?: if (formatVersion == LEGACY_FORMAT_VERSION) "NONE" else error("نسخة احتياطية بلا بيانات تشفير")
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
        require(result.formatVersion in expectedFormatVersions) { "إصدار النسخة الاحتياطية غير مدعوم" }
        require(databaseFound && destinationDatabase.isFile && destinationDatabase.length() > 0L) {
            "قاعدة البيانات غير موجودة داخل النسخة"
        }
        return result
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
