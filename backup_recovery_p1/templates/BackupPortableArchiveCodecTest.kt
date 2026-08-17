package com.fush.erp.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class BackupPortableArchiveCodecTest {
    @Test
    fun portable_archive_round_trip_works_with_local_device_key() = withFiles { database, archive, restored ->
        database.writeText("SQLite format 3\u0000FUSH-P1-PORTABLE-SENSITIVE-DATA")
        val deviceKey = aesKey()
        val write = BackupPortableArchiveCodec.writeArchive(database, archive, manifest(database), deviceKey)
        val decoded = FileInputStream(archive).use { BackupPortableArchiveCodec.extractAndVerify(it, restored, deviceKey) }
        assertArrayEquals(database.readBytes(), restored.readBytes())
        assertTrue(write.recoveryCode.isNotBlank())
        assertTrue(decoded.formatVersion == BackupPortableArchiveCodec.FORMAT_VERSION)
        assertTrue(decoded.schemaVersion == 35)
    }

    @Test
    fun recovery_code_restores_when_original_device_key_is_unavailable() = withFiles { database, archive, restored ->
        database.writeText("portable-cross-device")
        val write = BackupPortableArchiveCodec.writeArchive(database, archive, manifest(database), aesKey())
        FileInputStream(archive).use { BackupPortableArchiveCodec.extractAndVerify(it, restored, aesKey(), write.recoveryCode) }
        assertArrayEquals(database.readBytes(), restored.readBytes())
    }

    @Test
    fun missing_or_wrong_recovery_code_is_rejected_and_partial_file_is_removed() = withFiles { database, archive, restored ->
        database.writeText("portable-secret")
        val write = BackupPortableArchiveCodec.writeArchive(database, archive, manifest(database), aesKey())
        val unrelatedDeviceKey = aesKey()
        val missing = runCatching { FileInputStream(archive).use { BackupPortableArchiveCodec.extractAndVerify(it, restored, unrelatedDeviceKey) } }.exceptionOrNull()
        assertTrue(missing is RecoveryCodeRequiredException)
        assertFalse(restored.exists())
        val wrongCode = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))
        assertNotEquals(write.recoveryCode, wrongCode)
        val wrong = runCatching { FileInputStream(archive).use { BackupPortableArchiveCodec.extractAndVerify(it, restored, unrelatedDeviceKey, wrongCode) } }.exceptionOrNull()
        assertTrue(wrong is InvalidRecoveryCodeException)
        assertFalse(restored.exists())
    }

    @Test
    fun tampered_portable_backup_is_rejected_and_partial_file_is_removed() = withFiles { database, archive, restored ->
        database.writeText("portable-tamper-sensitive-data")
        val deviceKey = aesKey()
        BackupPortableArchiveCodec.writeArchive(database, archive, manifest(database), deviceKey)
        val bytes = archive.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x01).toByte()
        archive.writeBytes(bytes)

        val failure = runCatching {
            FileInputStream(archive).use { BackupPortableArchiveCodec.extractAndVerify(it, restored, deviceKey) }
        }.exceptionOrNull()

        assertTrue(failure != null)
        assertFalse(restored.exists())
    }

    @Test
    fun archive_contains_neither_plain_database_nor_recovery_secret() = withFiles { database, archive, _ ->
        val marker = "FUSH-P1-RECOVERY-SECRET-MARKER"
        database.writeText("SQLite format 3\u0000$marker")
        val write = BackupPortableArchiveCodec.writeArchive(database, archive, manifest(database), aesKey())
        val raw = archive.readBytes()
        val beginsWithZipMagic = raw.size >= 2 && raw[0] == 'P'.code.toByte() && raw[1] == 'K'.code.toByte()
        assertFalse(beginsWithZipMagic)
        assertFalse(raw.containsBytes(marker.toByteArray()))
        assertFalse(raw.containsBytes(write.recoveryCode.toByteArray()))
        assertTrue(BufferedInputStream(FileInputStream(archive)).use(BackupPortableArchiveCodec::isPortableBackup))
    }

    @Test
    fun recovery_codes_are_unique_per_backup() {
        val db = File.createTempFile("fush-p1-db", ".bin")
        val one = File.createTempFile("fush-p1-one", ".backup")
        val two = File.createTempFile("fush-p1-two", ".backup")
        try {
            db.writeText("same-db")
            val key = aesKey()
            val first = BackupPortableArchiveCodec.writeArchive(db, one, manifest(db), key)
            val second = BackupPortableArchiveCodec.writeArchive(db, two, manifest(db), key)
            assertNotEquals(first.recoveryCode, second.recoveryCode)
        } finally { db.delete(); one.delete(); two.delete() }
    }

    private fun manifest(db: File) = BackupManifest(
        formatVersion = BackupPortableArchiveCodec.FORMAT_VERSION,
        packageId = "com.fush.erp.recovery",
        appVersion = "test",
        schemaVersion = 35,
        createdAt = 1L,
        databaseSha256 = BackupArchiveCodec.sha256(db),
        encryptionAlgorithm = BackupPortableArchiveCodec.ENCRYPTION_ALGORITHM
    )

    private fun aesKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private inline fun withFiles(block: (File, File, File) -> Unit) {
        val database = File.createTempFile("fush-p1-db", ".bin")
        val archive = File.createTempFile("fush-p1-archive", ".fushbackup")
        val restored = File.createTempFile("fush-p1-restored", ".bin").apply { delete() }
        try { block(database, archive, restored) } finally { database.delete(); archive.delete(); restored.delete() }
    }

    private fun ByteArray.containsBytes(needle: ByteArray): Boolean {
        if (needle.isEmpty() || needle.size > size) return false
        for (start in 0..size - needle.size) {
            var match = true
            for (offset in needle.indices) if (this[start + offset] != needle[offset]) { match = false; break }
            if (match) return true
        }
        return false
    }
}
