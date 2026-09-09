package com.fush.erp.backup

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
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
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class BackupManifest(
    val formatVersion: Int,
    val packageId: String,
    val appVersion: String,
    val schemaVersion: Int,
    val createdAt: Long,
    val databaseSha256: String,
    val encryptionAlgorithm: String = BackupArchiveCodec.PORTABLE_ENCRYPTION_ALGORITHM
)

object BackupArchiveCodec {
    /** Current cross-device portable format. */
    const val FORMAT_VERSION = 4
    const val PORTABLE_DB_ONLY_FORMAT_VERSION = 3
    /** Previous encrypted format tied to AndroidKeyStore on the creating device. */
    const val DEVICE_BOUND_FORMAT_VERSION = 2
    const val LEGACY_FORMAT_VERSION = 1
    const val MANIFEST_ENTRY = "backup-manifest.properties"
    const val DATABASE_ENTRY = "database/fush_erp.db"
    /** Optional portable UI/domain settings. Kept inside format v4 for backward compatibility; older v4 readers ignore it. */
    const val SETTINGS_ENTRY = "settings/app.properties"
    const val PORTABLE_ENCRYPTION_ALGORITHM = "AES-256-GCM/PBKDF2-HMAC-SHA256"
    const val DEVICE_ENCRYPTION_ALGORITHM = "AES-256-GCM"
    const val PBKDF2_ITERATIONS = 310_000

    private const val ENVELOPE_VERSION = 1
    private const val KDF_PBKDF2_SHA256 = 1
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256
    private const val MIN_ACCEPTED_ITERATIONS = 100_000
    private const val MAX_ACCEPTED_ITERATIONS = 1_000_000
    private val MAGIC_V2 = byteArrayOf(0x46, 0x55, 0x53, 0x48, 0x42, 0x4B, 0x50, 0x32) // FUSHBKP2
    private val MAGIC_V3 = byteArrayOf(0x46, 0x55, 0x53, 0x48, 0x42, 0x4B, 0x50, 0x33) // FUSHBKP3

    /**
     * Creates a portable backup. The password is never stored in the archive.
     * A random salt + PBKDF2 derive a unique AES-256 key for each archive.
     */
    fun writePortableArchive(
        databaseFile: File,
        outputFile: File,
        manifest: BackupManifest,
        password: CharArray,
        attachmentsRoot: File? = null,
        portableSettings: Properties? = null
    ) {
        require(databaseFile.isFile) { "ملف قاعدة البيانات غير موجود" }
        require(password.isNotEmpty()) { "كلمة مرور النسخة الاحتياطية مطلوبة" }
        require(manifest.formatVersion == FORMAT_VERSION) { "إصدار تنسيق النسخة غير مدعوم للإنشاء" }
        outputFile.parentFile?.mkdirs()
        outputFile.delete()
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val iv = ByteArray(GCM_IV_BYTES).also(SecureRandom()::nextBytes)
        val key = derivePortableKey(password, salt, PBKDF2_ITERATIONS)
        try {
            FileOutputStream(outputFile).use { fileOut ->
                val headerBytes = portableHeader(salt, iv, PBKDF2_ITERATIONS)
                fileOut.write(headerBytes)

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                cipher.updateAAD(headerBytes)
                CipherOutputStream(fileOut, cipher).use { cipherOut ->
                    writeZipPayload(databaseFile, cipherOut, manifest, attachmentsRoot, portableSettings)
                }
            }
        } catch (t: Throwable) {
            outputFile.delete()
            throw t
        }
    }

    /**
     * Kept only to read/test format v2. New backups MUST use writePortableArchive.
     */
    fun writeArchive(databaseFile: File, outputFile: File, manifest: BackupManifest, encryptionKey: SecretKey) {
        require(databaseFile.isFile) { "ملف قاعدة البيانات غير موجود" }
        require(encryptionKey.algorithm.equals("AES", ignoreCase = true)) { "مفتاح النسخ الاحتياطي يجب أن يكون AES" }
        require(manifest.formatVersion == DEVICE_BOUND_FORMAT_VERSION) { "إصدار تنسيق النسخة القديمة غير مدعوم للإنشاء" }
        outputFile.parentFile?.mkdirs()
        outputFile.delete()
        try {
            FileOutputStream(outputFile).use { fileOut ->
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, encryptionKey)
                val iv = cipher.iv ?: error("تعذر إنشاء IV آمن للنسخة الاحتياطية")
                require(iv.size == GCM_IV_BYTES) { "طول IV للنسخة الاحتياطية غير صالح" }

                val header = DataOutputStream(fileOut)
                header.write(MAGIC_V2)
                header.writeByte(ENVELOPE_VERSION)
                header.writeByte(iv.size)
                header.write(iv)
                header.flush()

                cipher.updateAAD(MAGIC_V2)
                cipher.updateAAD(byteArrayOf(ENVELOPE_VERSION.toByte()))
                CipherOutputStream(fileOut, cipher).use { cipherOut ->
                    writeZipPayload(databaseFile, cipherOut, manifest, attachmentsRoot = null, portableSettings = null)
                }
            }
        } catch (t: Throwable) {
            outputFile.delete()
            throw t
        }
    }

    /**
     * Reads v3 portable, v2 device-bound, and v1 plaintext backups.
     * For v3 [password] is required. For v2 [legacyDeviceKey] is required and works
     * only on the device where that AndroidKeyStore key was originally created.
     */
    fun extractAndVerifyPortable(
        input: InputStream,
        destinationDatabase: File,
        password: CharArray?,
        legacyDeviceKey: SecretKey?,
        allowLegacyPlaintext: Boolean = true,
        destinationAttachments: File? = null,
        destinationSettings: File? = null
    ): BackupManifest {
        destinationDatabase.parentFile?.mkdirs()
        destinationDatabase.delete()
        try {
            val buffered = if (input is BufferedInputStream) input else BufferedInputStream(input)
            buffered.mark(MAGIC_V3.size + 2)
            val prefix = ByteArray(MAGIC_V3.size)
            val read = buffered.read(prefix)
            buffered.reset()

            val manifest = when {
                read == MAGIC_V3.size && prefix.contentEquals(MAGIC_V3) -> {
                    val portablePassword = password ?: error("أدخل كلمة مرور النسخة الاحتياطية")
                    require(portablePassword.isNotEmpty()) { "أدخل كلمة مرور النسخة الاحتياطية" }
                    try {
                        extractPortableV3(buffered, destinationDatabase, portablePassword, destinationAttachments, destinationSettings)
                    } catch (t: Throwable) {
                        if (containsAeadFailure(t)) {
                            error("كلمة مرور النسخة غير صحيحة أو أن ملف النسخة تالف")
                        }
                        throw t
                    }
                }
                read == MAGIC_V2.size && prefix.contentEquals(MAGIC_V2) -> {
                    val key = legacyDeviceKey ?: error("هذه نسخة احتياطية قديمة مرتبطة بالجهاز الذي أنشأها")
                    try {
                        extractEncryptedV2(buffered, destinationDatabase, key)
                    } catch (t: Throwable) {
                        if (containsAeadFailure(t)) {
                            error("هذه نسخة احتياطية قديمة v2 مرتبطة بالجهاز الذي أنشأها. افتحها على الجهاز الأصلي، ثم أنشئ نسخة Portable جديدة لنقلها إلى جهاز آخر.")
                        }
                        throw t
                    }
                }
                else -> {
                    require(allowLegacyPlaintext) { "النسخة الاحتياطية غير مشفرة أو بتنسيق غير مدعوم" }
                    extractZipPayload(buffered, destinationDatabase, expectedFormatVersions = setOf(LEGACY_FORMAT_VERSION), destinationAttachments = null, destinationSettings = null)
                }
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

    /** Existing v1/v2 API retained for binary/backward compatibility. */
    fun extractAndVerify(
        input: InputStream,
        destinationDatabase: File,
        encryptionKey: SecretKey,
        allowLegacyPlaintext: Boolean = true
    ): BackupManifest = extractAndVerifyPortable(
        input = input,
        destinationDatabase = destinationDatabase,
        password = null,
        legacyDeviceKey = encryptionKey,
        allowLegacyPlaintext = allowLegacyPlaintext
    )

    private fun extractPortableV3(
        input: InputStream,
        destinationDatabase: File,
        password: CharArray,
        destinationAttachments: File?,
        destinationSettings: File?
    ): BackupManifest {
        val header = DataInputStream(input)
        val magic = ByteArray(MAGIC_V3.size)
        header.readFully(magic)
        require(magic.contentEquals(MAGIC_V3)) { "ترويسة النسخة الاحتياطية غير صالحة" }
        val envelopeVersion = header.readUnsignedByte()
        require(envelopeVersion == ENVELOPE_VERSION) { "إصدار تشفير النسخة الاحتياطية غير مدعوم" }
        val kdf = header.readUnsignedByte()
        require(kdf == KDF_PBKDF2_SHA256) { "خوارزمية اشتقاق مفتاح النسخة غير مدعومة" }
        val saltLength = header.readUnsignedByte()
        val ivLength = header.readUnsignedByte()
        val iterations = header.readInt()
        require(saltLength == SALT_BYTES && ivLength == GCM_IV_BYTES) { "بيانات تشفير النسخة الاحتياطية غير صالحة" }
        require(iterations in MIN_ACCEPTED_ITERATIONS..MAX_ACCEPTED_ITERATIONS) { "عدد دورات حماية النسخة غير صالح" }
        val salt = ByteArray(saltLength).also(header::readFully)
        val iv = ByteArray(ivLength).also(header::readFully)
        val headerBytes = portableHeader(salt, iv, iterations)
        val key = derivePortableKey(password, salt, iterations)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(headerBytes)
        val manifest = CipherInputStream(input, cipher).use { cipherIn ->
            extractZipPayload(
                cipherIn,
                destinationDatabase,
                expectedFormatVersions = setOf(PORTABLE_DB_ONLY_FORMAT_VERSION, FORMAT_VERSION),
                destinationAttachments = destinationAttachments,
                destinationSettings = destinationSettings
            )
        }
        require(manifest.encryptionAlgorithm == PORTABLE_ENCRYPTION_ALGORITHM) {
            "خوارزمية تشفير النسخة الاحتياطية غير مدعومة"
        }
        return manifest
    }

    private fun extractEncryptedV2(input: InputStream, destinationDatabase: File, encryptionKey: SecretKey): BackupManifest {
        require(encryptionKey.algorithm.equals("AES", ignoreCase = true)) { "مفتاح النسخ الاحتياطي يجب أن يكون AES" }
        val header = DataInputStream(input)
        val magic = ByteArray(MAGIC_V2.size)
        header.readFully(magic)
        require(magic.contentEquals(MAGIC_V2)) { "ترويسة النسخة الاحتياطية غير صالحة" }
        val envelopeVersion = header.readUnsignedByte()
        require(envelopeVersion == ENVELOPE_VERSION) { "إصدار تشفير النسخة الاحتياطية غير مدعوم" }
        val ivLength = header.readUnsignedByte()
        require(ivLength == GCM_IV_BYTES) { "بيانات تشفير النسخة الاحتياطية غير صالحة" }
        val iv = ByteArray(ivLength)
        header.readFully(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(MAGIC_V2)
        cipher.updateAAD(byteArrayOf(envelopeVersion.toByte()))

        val manifest = CipherInputStream(input, cipher).use { cipherIn ->
            extractZipPayload(cipherIn, destinationDatabase, expectedFormatVersions = setOf(DEVICE_BOUND_FORMAT_VERSION), destinationAttachments = null, destinationSettings = null)
        }
        require(manifest.encryptionAlgorithm == DEVICE_ENCRYPTION_ALGORITHM) {
            "خوارزمية تشفير النسخة الاحتياطية القديمة غير مدعومة"
        }
        return manifest
    }

    private fun portableHeader(salt: ByteArray, iv: ByteArray, iterations: Int): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.write(MAGIC_V3)
            out.writeByte(ENVELOPE_VERSION)
            out.writeByte(KDF_PBKDF2_SHA256)
            out.writeByte(salt.size)
            out.writeByte(iv.size)
            out.writeInt(iterations)
            out.write(salt)
            out.write(iv)
        }
        return bytes.toByteArray()
    }

    private fun derivePortableKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun containsAeadFailure(t: Throwable): Boolean {
        var current: Throwable? = t
        repeat(8) {
            if (current is AEADBadTagException) return true
            current = current?.cause
            if (current == null) return false
        }
        return false
    }

    private fun writeZipPayload(
        databaseFile: File,
        output: OutputStream,
        manifest: BackupManifest,
        attachmentsRoot: File?,
        portableSettings: Properties?
    ) {
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

            if (manifest.formatVersion >= FORMAT_VERSION && portableSettings != null) {
                zip.putNextEntry(ZipEntry(SETTINGS_ENTRY))
                portableSettings.store(zip, "Fush ERP portable app settings")
                zip.closeEntry()
            }

            if (manifest.formatVersion >= FORMAT_VERSION && attachmentsRoot?.isDirectory == true) {
                val root = attachmentsRoot.canonicalFile
                root.walkTopDown().filter { it.isFile }.forEach { file ->
                    val relative = file.canonicalFile.relativeTo(root).invariantSeparatorsPath
                    require(relative.isNotBlank() && !relative.contains("..")) { "مسار مرفق غير آمن" }
                    zip.putNextEntry(ZipEntry("attachments/$relative"))
                    FileInputStream(file).use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    private fun extractZipPayload(
        input: InputStream,
        destinationDatabase: File,
        expectedFormatVersions: Set<Int>,
        destinationAttachments: File?,
        destinationSettings: File?
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
                    SETTINGS_ENTRY -> if (destinationSettings != null) {
                        destinationSettings.parentFile?.mkdirs()
                        destinationSettings.delete()
                        FileOutputStream(destinationSettings).use { out -> zip.copyTo(out) }
                    }
                    else -> if (entry.name.startsWith("attachments/") && destinationAttachments != null && !entry.isDirectory) {
                        val relative = entry.name.removePrefix("attachments/")
                        require(relative.isNotBlank() && !relative.contains("..") && !relative.startsWith('/')) { "مسار مرفق غير آمن داخل النسخة" }
                        val root = destinationAttachments.canonicalFile.apply { mkdirs() }
                        val outFile = File(root, relative).canonicalFile
                        require(outFile.path.startsWith(root.path + File.separator)) { "مسار مرفق خارج مجلد الاستعادة" }
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { out -> zip.copyTo(out) }
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
