package com.fush.erp.backup

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class RecoveryCodeRequiredException : SecurityException(
    "هذه النسخة تحتاج رمز الاسترداد لأن مفتاح الجهاز الأصلي غير متاح"
)

class InvalidRecoveryCodeException : SecurityException("رمز استرداد النسخة الاحتياطية غير صحيح")

data class PortableArchiveWriteResult(val recoveryCode: String)

/**
 * P1 portable backup envelope.
 *
 * The database payload is encrypted with a random per-backup DEK. The DEK is
 * wrapped twice: once by the non-exportable Android Keystore key and once by a
 * random 256-bit recovery secret. Only the wrapped DEKs are stored in the
 * archive. The recovery secret is returned to the caller exactly once and is
 * never persisted by this codec.
 */
object BackupPortableArchiveCodec {
    const val FORMAT_VERSION = 3
    const val ENCRYPTION_ALGORITHM = "AES-256-GCM+DUAL-WRAPPED-DEK"

    private const val ENVELOPE_VERSION = 1
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12
    private const val AES_KEY_BYTES = 32
    private const val WRAPPED_DEK_BYTES = AES_KEY_BYTES + (GCM_TAG_BITS / 8)
    private val MAGIC = byteArrayOf(0x46, 0x55, 0x53, 0x48, 0x42, 0x4B, 0x50, 0x33) // FUSHBKP3
    private val secureRandom = SecureRandom()

    fun isPortableBackup(input: BufferedInputStream): Boolean {
        input.mark(MAGIC.size + 1)
        val prefix = ByteArray(MAGIC.size)
        val read = input.read(prefix)
        input.reset()
        return read == MAGIC.size && prefix.contentEquals(MAGIC)
    }

    fun writeArchive(
        databaseFile: File,
        outputFile: File,
        manifest: BackupManifest,
        deviceWrappingKey: SecretKey
    ): PortableArchiveWriteResult {
        require(databaseFile.isFile) { "ملف قاعدة البيانات غير موجود" }
        require(deviceWrappingKey.algorithm.equals("AES", ignoreCase = true)) { "مفتاح الجهاز يجب أن يكون AES" }
        require(manifest.formatVersion == FORMAT_VERSION) { "إصدار تنسيق النسخة غير مدعوم للإنشاء" }
        require(manifest.encryptionAlgorithm == ENCRYPTION_ALGORITHM) { "بيانات تشفير النسخة غير صحيحة" }

        val dek = generateAesKey()
        val recoveryKeyBytes = ByteArray(AES_KEY_BYTES).also(secureRandom::nextBytes)
        val recoveryKey = SecretKeySpec(recoveryKeyBytes, "AES")
        val recoveryCode = Base64.getUrlEncoder().withoutPadding().encodeToString(recoveryKeyBytes)
        val deviceWrap = encryptKey(dek, deviceWrappingKey, "device-dek")
        val recoveryWrap = encryptKey(dek, recoveryKey, "recovery-dek")
        val payloadIv = randomIv()

        outputFile.parentFile?.mkdirs()
        outputFile.delete()
        try {
            FileOutputStream(outputFile).use { fileOut ->
                DataOutputStream(fileOut).apply {
                    write(MAGIC)
                    writeByte(ENVELOPE_VERSION)
                    writeByte(deviceWrap.iv.size)
                    writeByte(deviceWrap.ciphertext.size)
                    writeByte(recoveryWrap.iv.size)
                    writeByte(recoveryWrap.ciphertext.size)
                    writeByte(payloadIv.size)
                    write(deviceWrap.iv)
                    write(deviceWrap.ciphertext)
                    write(recoveryWrap.iv)
                    write(recoveryWrap.ciphertext)
                    write(payloadIv)
                    flush()
                }

                val cipher = payloadCipher(Cipher.ENCRYPT_MODE, dek, payloadIv)
                CipherOutputStream(fileOut, cipher).use { cipherOut ->
                    writeZipPayload(databaseFile, cipherOut, manifest)
                }
            }
            return PortableArchiveWriteResult(recoveryCode)
        } catch (t: Throwable) {
            outputFile.delete()
            throw t
        } finally {
            recoveryKeyBytes.fill(0)
        }
    }

    fun extractAndVerify(
        input: InputStream,
        destinationDatabase: File,
        deviceWrappingKey: SecretKey,
        recoveryCode: String? = null
    ): BackupManifest {
        destinationDatabase.parentFile?.mkdirs()
        destinationDatabase.delete()
        try {
            val header = DataInputStream(input)
            val magic = ByteArray(MAGIC.size)
            header.readFully(magic)
            require(magic.contentEquals(MAGIC)) { "ترويسة النسخة الاحتياطية المحمولة غير صالحة" }
            val envelopeVersion = header.readUnsignedByte()
            require(envelopeVersion == ENVELOPE_VERSION) { "إصدار تشفير النسخة المحمولة غير مدعوم" }

            val deviceIvLength = header.readUnsignedByte()
            val deviceWrapLength = header.readUnsignedByte()
            val recoveryIvLength = header.readUnsignedByte()
            val recoveryWrapLength = header.readUnsignedByte()
            val payloadIvLength = header.readUnsignedByte()
            require(deviceIvLength == GCM_IV_BYTES && recoveryIvLength == GCM_IV_BYTES && payloadIvLength == GCM_IV_BYTES) {
                "بيانات IV للنسخة المحمولة غير صالحة"
            }
            require(deviceWrapLength == WRAPPED_DEK_BYTES && recoveryWrapLength == WRAPPED_DEK_BYTES) {
                "بيانات مفتاح النسخة المحمولة غير صالحة"
            }

            val deviceIv = ByteArray(deviceIvLength).also(header::readFully)
            val deviceWrappedDek = ByteArray(deviceWrapLength).also(header::readFully)
            val recoveryIv = ByteArray(recoveryIvLength).also(header::readFully)
            val recoveryWrappedDek = ByteArray(recoveryWrapLength).also(header::readFully)
            val payloadIv = ByteArray(payloadIvLength).also(header::readFully)

            val dek = tryUnwrap(deviceWrappedDek, deviceIv, deviceWrappingKey, "device-dek")
                ?: unwrapWithRecoveryCode(recoveryWrappedDek, recoveryIv, recoveryCode)

            val cipher = payloadCipher(Cipher.DECRYPT_MODE, dek, payloadIv)
            val manifest = CipherInputStream(input, cipher).use { cipherIn ->
                extractZipPayload(cipherIn, destinationDatabase)
            }
            require(manifest.formatVersion == FORMAT_VERSION) { "إصدار النسخة المحمولة غير مدعوم" }
            require(manifest.encryptionAlgorithm == ENCRYPTION_ALGORITHM) { "خوارزمية تشفير النسخة المحمولة غير مدعومة" }
            require(BackupArchiveCodec.sha256(destinationDatabase).equals(manifest.databaseSha256, ignoreCase = true)) {
                "فشل التحقق من بصمة قاعدة البيانات"
            }
            return manifest
        } catch (t: Throwable) {
            destinationDatabase.delete()
            throw t
        }
    }

    private fun unwrapWithRecoveryCode(
        wrappedDek: ByteArray,
        iv: ByteArray,
        recoveryCode: String?
    ): SecretKey {
        if (recoveryCode.isNullOrBlank()) throw RecoveryCodeRequiredException()
        val bytes = try {
            Base64.getUrlDecoder().decode(recoveryCode.trim())
        } catch (_: IllegalArgumentException) {
            throw InvalidRecoveryCodeException()
        }
        try {
            if (bytes.size != AES_KEY_BYTES) throw InvalidRecoveryCodeException()
            val key = SecretKeySpec(bytes, "AES")
            return tryUnwrap(wrappedDek, iv, key, "recovery-dek") ?: throw InvalidRecoveryCodeException()
        } finally {
            bytes.fill(0)
        }
    }

    private fun tryUnwrap(wrapped: ByteArray, iv: ByteArray, wrappingKey: SecretKey, purpose: String): SecretKey? = try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        addAad(cipher, purpose)
        val raw = cipher.doFinal(wrapped)
        if (raw.size != AES_KEY_BYTES) null else SecretKeySpec(raw, "AES")
    } catch (_: GeneralSecurityException) {
        null
    }

    private fun encryptKey(dek: SecretKey, wrappingKey: SecretKey, purpose: String): WrappedKey {
        val iv = randomIv()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        addAad(cipher, purpose)
        val ciphertext = cipher.doFinal(dek.encoded ?: error("مفتاح بيانات النسخة غير قابل للتغليف"))
        require(ciphertext.size == WRAPPED_DEK_BYTES) { "حجم مفتاح البيانات المغلف غير صالح" }
        return WrappedKey(iv, ciphertext)
    }

    private fun payloadCipher(mode: Int, dek: SecretKey, iv: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, dek, GCMParameterSpec(GCM_TAG_BITS, iv))
            addAad(this, "payload")
        }

    private fun addAad(cipher: Cipher, purpose: String) {
        cipher.updateAAD(MAGIC)
        cipher.updateAAD(byteArrayOf(ENVELOPE_VERSION.toByte()))
        cipher.updateAAD(purpose.toByteArray(Charsets.UTF_8))
    }

    private fun generateAesKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private fun randomIv(): ByteArray = ByteArray(GCM_IV_BYTES).also(secureRandom::nextBytes)

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
            zip.putNextEntry(ZipEntry(BackupArchiveCodec.MANIFEST_ENTRY))
            props.store(zip, "Fush ERP portable backup manifest")
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(BackupArchiveCodec.DATABASE_ENTRY))
            FileInputStream(databaseFile).use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }

    private fun extractZipPayload(input: InputStream, destinationDatabase: File): BackupManifest {
        var manifest: BackupManifest? = null
        var databaseFound = false
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.name.contains("..") && !entry.name.startsWith('/')) { "ملف النسخة الاحتياطية غير آمن" }
                when (entry.name) {
                    BackupArchiveCodec.MANIFEST_ENTRY -> {
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
                                ?: error("نسخة احتياطية بلا بصمة قاعدة البيانات"),
                            encryptionAlgorithm = props.getProperty("encryptionAlgorithm")
                                ?: error("نسخة احتياطية بلا بيانات تشفير")
                        )
                    }
                    BackupArchiveCodec.DATABASE_ENTRY -> {
                        require(!databaseFound) { "ملف النسخة يحتوي قاعدة بيانات مكررة" }
                        FileOutputStream(destinationDatabase).use { out -> zip.copyTo(out) }
                        databaseFound = true
                    }
                }
                zip.closeEntry()
            }
        }
        val result = manifest ?: error("الملف ليس نسخة Fush ERP احتياطية صالحة")
        require(databaseFound && destinationDatabase.isFile && destinationDatabase.length() > 0L) {
            "قاعدة البيانات غير موجودة داخل النسخة"
        }
        return result
    }

    private data class WrappedKey(val iv: ByteArray, val ciphertext: ByteArray)
}
