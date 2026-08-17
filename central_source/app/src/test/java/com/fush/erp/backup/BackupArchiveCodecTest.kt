package com.fush.erp.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.KeyGenerator

class BackupArchiveCodecTest {
    private fun newAesKey() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test
    fun encrypted_archive_round_trip_preserves_manifest_and_database_hash() {
        val dir = kotlin.io.path.createTempDirectory("fush-backup-").toFile()
        try {
            val marker = "SQLite format 3\u0000FUSH-SENSITIVE-ACCOUNTING-DATA"
            val db = File(dir, "fush_erp.db").apply { writeBytes(marker.toByteArray() + ByteArray(512) { (it % 251).toByte() }) }
            val hash = BackupArchiveCodec.sha256(db)
            val archive = File(dir, "test.fushbackup")
            val expected = BackupManifest(BackupArchiveCodec.FORMAT_VERSION, "com.fush.erp.recovery", "test", 34, 1234L, hash)
            val key = newAesKey()

            BackupArchiveCodec.writeArchive(db, archive, expected, key)

            val rawArchive = archive.readBytes()
            assertFalse("encrypted backup must not expose SQLite header", rawArchive.toString(Charsets.ISO_8859_1).contains("SQLite format 3"))
            assertFalse("encrypted backup must not expose known database content", rawArchive.toString(Charsets.ISO_8859_1).contains("FUSH-SENSITIVE-ACCOUNTING-DATA"))
            assertFalse("encrypted backup must not be a raw ZIP", rawArchive.size >= 2 && rawArchive[0] == 'P'.code.toByte() && rawArchive[1] == 'K'.code.toByte())

            val restored = File(dir, "restored.db")
            val actual = FileInputStream(archive).use { BackupArchiveCodec.extractAndVerify(it, restored, key) }
            assertEquals(expected, actual)
            assertEquals(hash, BackupArchiveCodec.sha256(restored))
            assertTrue(restored.readBytes().contentEquals(db.readBytes()))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun wrong_key_fails_without_leaving_partial_database() {
        val dir = kotlin.io.path.createTempDirectory("fush-backup-wrong-key-").toFile()
        try {
            val db = File(dir, "fush_erp.db").apply { writeBytes(ByteArray(1024) { 9 }) }
            val archive = File(dir, "test.fushbackup")
            val manifest = BackupManifest(
                BackupArchiveCodec.FORMAT_VERSION,
                "com.fush.erp.recovery",
                "test",
                34,
                1234L,
                BackupArchiveCodec.sha256(db)
            )
            BackupArchiveCodec.writeArchive(db, archive, manifest, newAesKey())

            val destination = File(dir, "partial.db")
            runCatching {
                FileInputStream(archive).use { BackupArchiveCodec.extractAndVerify(it, destination, newAesKey()) }
            }.onSuccess { error("wrong key must fail") }
            assertFalse(destination.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun tampered_ciphertext_fails_without_leaving_partial_database() {
        val dir = kotlin.io.path.createTempDirectory("fush-backup-tamper-").toFile()
        try {
            val db = File(dir, "fush_erp.db").apply { writeBytes(ByteArray(2048) { (it % 199).toByte() }) }
            val archive = File(dir, "test.fushbackup")
            val key = newAesKey()
            val manifest = BackupManifest(
                BackupArchiveCodec.FORMAT_VERSION,
                "com.fush.erp.recovery",
                "test",
                34,
                1234L,
                BackupArchiveCodec.sha256(db)
            )
            BackupArchiveCodec.writeArchive(db, archive, manifest, key)
            val bytes = archive.readBytes()
            val index = bytes.size / 2
            bytes[index] = (bytes[index].toInt() xor 0x01).toByte()
            archive.writeBytes(bytes)

            val destination = File(dir, "partial.db")
            runCatching {
                FileInputStream(archive).use { BackupArchiveCodec.extractAndVerify(it, destination, key) }
            }.onSuccess { error("tampered archive must fail") }
            assertFalse(destination.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun legacy_plaintext_backup_remains_readable_for_upgrade_compatibility() {
        val dir = kotlin.io.path.createTempDirectory("fush-backup-legacy-").toFile()
        try {
            val db = File(dir, "legacy.db").apply { writeBytes(ByteArray(512) { 7 }) }
            val hash = BackupArchiveCodec.sha256(db)
            val archive = File(dir, "legacy.fushbackup")
            ZipOutputStream(FileOutputStream(archive)).use { zip ->
                zip.putNextEntry(ZipEntry(BackupArchiveCodec.MANIFEST_ENTRY))
                Properties().apply {
                    setProperty("formatVersion", BackupArchiveCodec.LEGACY_FORMAT_VERSION.toString())
                    setProperty("packageId", "com.fush.erp.recovery")
                    setProperty("appVersion", "legacy")
                    setProperty("schemaVersion", "34")
                    setProperty("createdAt", "1234")
                    setProperty("databaseSha256", hash)
                }.store(zip, "legacy")
                zip.closeEntry()
                zip.putNextEntry(ZipEntry(BackupArchiveCodec.DATABASE_ENTRY))
                FileInputStream(db).use { it.copyTo(zip) }
                zip.closeEntry()
            }

            val restored = File(dir, "restored.db")
            val actual = FileInputStream(archive).use { BackupArchiveCodec.extractAndVerify(it, restored, newAesKey()) }
            assertEquals(BackupArchiveCodec.LEGACY_FORMAT_VERSION, actual.formatVersion)
            assertEquals("NONE", actual.encryptionAlgorithm)
            assertEquals(hash, BackupArchiveCodec.sha256(restored))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun failed_legacy_extraction_deletes_partial_destination() {
        val dir = kotlin.io.path.createTempDirectory("fush-backup-corrupt-").toFile()
        try {
            val archive = File(dir, "bad.fushbackup")
            ZipOutputStream(FileOutputStream(archive)).use { zip ->
                zip.putNextEntry(ZipEntry(BackupArchiveCodec.MANIFEST_ENTRY))
                Properties().apply {
                    setProperty("formatVersion", BackupArchiveCodec.LEGACY_FORMAT_VERSION.toString())
                    setProperty("packageId", "com.fush.erp.recovery")
                    setProperty("appVersion", "test")
                    setProperty("schemaVersion", "34")
                    setProperty("createdAt", "1234")
                    setProperty("databaseSha256", "00".repeat(32))
                }.store(zip, "bad")
                zip.closeEntry()
                zip.putNextEntry(ZipEntry(BackupArchiveCodec.DATABASE_ENTRY))
                zip.write(ByteArray(512) { 7 })
                zip.closeEntry()
            }
            val destination = File(dir, "partial.db")
            runCatching {
                FileInputStream(archive).use { BackupArchiveCodec.extractAndVerify(it, destination, newAesKey()) }
            }.onSuccess { error("corrupt archive must fail") }
            assertFalse(destination.exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
