package com.fush.erp.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
    private fun password() = "Fush-Portable-2026!".toCharArray()

    @Test
    fun portable_archive_round_trip_preserves_manifest_and_database_hash() {
        val dir = kotlin.io.path.createTempDirectory("fush-backup-v3-").toFile()
        try {
            val marker = "SQLite format 3\u0000FUSH-SENSITIVE-ACCOUNTING-DATA"
            val db = File(dir, "fush_erp.db").apply { writeBytes(marker.toByteArray() + ByteArray(512) { (it % 251).toByte() }) }
            val hash = BackupArchiveCodec.sha256(db)
            val archive = File(dir, "test.fushbackup")
            val expected = BackupManifest(
                BackupArchiveCodec.FORMAT_VERSION,
                "com.fush.erp.recovery",
                "test",
                38,
                1234L,
                hash,
                BackupArchiveCodec.PORTABLE_ENCRYPTION_ALGORITHM
            )
            val password = password()
            try {
                BackupArchiveCodec.writePortableArchive(db, archive, expected, password)
                val rawArchive = archive.readBytes()
                assertTrue(String(rawArchive.copyOfRange(0, 8), Charsets.US_ASCII) == "FUSHBKP3")
                assertFalse(rawArchive.toString(Charsets.ISO_8859_1).contains("SQLite format 3"))
                assertFalse(rawArchive.toString(Charsets.ISO_8859_1).contains("FUSH-SENSITIVE-ACCOUNTING-DATA"))
                assertFalse(rawArchive.size >= 2 && rawArchive[0] == 'P'.code.toByte() && rawArchive[1] == 'K'.code.toByte())

                val restored = File(dir, "restored.db")
                val actual = FileInputStream(archive).use {
                    BackupArchiveCodec.extractAndVerifyPortable(it, restored, password, legacyDeviceKey = null)
                }
                assertEquals(expected, actual)
                assertEquals(hash, BackupArchiveCodec.sha256(restored))
                assertTrue(restored.readBytes().contentEquals(db.readBytes()))
            } finally {
                password.fill('\u0000')
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun same_password_creates_different_ciphertext_because_salt_and_iv_are_random() {
        val dir = kotlin.io.path.createTempDirectory("fush-backup-random-").toFile()
        try {
            val db = File(dir, "fush_erp.db").apply { writeBytes(ByteArray(1024) { (it % 211).toByte() }) }
            val manifest = BackupManifest(
                BackupArchiveCodec.FORMAT_VERSION,
                "com.fush.erp.recovery",
                "test",
                38,
                1234L,
                BackupArchiveCodec.sha256(db),
                BackupArchiveCodec.PORTABLE_ENCRYPTION_ALGORITHM
            )
            val a = File(dir, "a.fushbackup")
            val b = File(dir, "b.fushbackup")
            val password = password()
            try {
                BackupArchiveCodec.writePortableArchive(db, a, manifest, password)
                BackupArchiveCodec.writePortableArchive(db, b, manifest, password)
            } finally {
                password.fill('\u0000')
            }
            assertNotEquals(BackupArchiveCodec.sha256(a), BackupArchiveCodec.sha256(b))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun wrong_portable_password_fails_without_leaving_partial_database() {
        val dir = kotlin.io.path.createTempDirectory("fush-backup-wrong-password-").toFile()
        try {
            val db = File(dir, "fush_erp.db").apply { writeBytes(ByteArray(1024) { 9 }) }
            val archive = File(dir, "test.fushbackup")
            val manifest = BackupManifest(
                BackupArchiveCodec.FORMAT_VERSION,
                "com.fush.erp.recovery",
                "test",
                38,
                1234L,
                BackupArchiveCodec.sha256(db),
                BackupArchiveCodec.PORTABLE_ENCRYPTION_ALGORITHM
            )
            val password = password()
            try { BackupArchiveCodec.writePortableArchive(db, archive, manifest, password) } finally { password.fill('\u0000') }

            val destination = File(dir, "partial.db")
            val wrong = "Definitely-Wrong-Password".toCharArray()
            val failure = runCatching {
                FileInputStream(archive).use { BackupArchiveCodec.extractAndVerifyPortable(it, destination, wrong, null) }
            }.exceptionOrNull()
            wrong.fill('\u0000')
            assertTrue(failure != null)
            assertTrue(failure!!.message.orEmpty().contains("كلمة مرور النسخة"))
            assertFalse(destination.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun tampered_portable_ciphertext_fails_without_leaving_partial_database() {
        val dir = kotlin.io.path.createTempDirectory("fush-backup-tamper-").toFile()
        try {
            val db = File(dir, "fush_erp.db").apply { writeBytes(ByteArray(2048) { (it % 199).toByte() }) }
            val archive = File(dir, "test.fushbackup")
            val manifest = BackupManifest(
                BackupArchiveCodec.FORMAT_VERSION,
                "com.fush.erp.recovery",
                "test",
                38,
                1234L,
                BackupArchiveCodec.sha256(db),
                BackupArchiveCodec.PORTABLE_ENCRYPTION_ALGORITHM
            )
            val password = password()
            try { BackupArchiveCodec.writePortableArchive(db, archive, manifest, password) } finally { password.fill('\u0000') }
            val bytes = archive.readBytes()
            val index = bytes.size / 2
            bytes[index] = (bytes[index].toInt() xor 0x01).toByte()
            archive.writeBytes(bytes)

            val destination = File(dir, "partial.db")
            val restorePassword = password()
            runCatching {
                FileInputStream(archive).use { BackupArchiveCodec.extractAndVerifyPortable(it, destination, restorePassword, null) }
            }.onSuccess { error("tampered archive must fail") }
            restorePassword.fill('\u0000')
            assertFalse(destination.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun device_bound_v2_backup_remains_readable_with_original_key() {
        val dir = kotlin.io.path.createTempDirectory("fush-backup-v2-").toFile()
        try {
            val db = File(dir, "fush_erp.db").apply { writeBytes(ByteArray(1024) { 3 }) }
            val archive = File(dir, "v2.fushbackup")
            val key = newAesKey()
            val manifest = BackupManifest(
                BackupArchiveCodec.DEVICE_BOUND_FORMAT_VERSION,
                "com.fush.erp.recovery",
                "v105",
                38,
                1234L,
                BackupArchiveCodec.sha256(db),
                BackupArchiveCodec.DEVICE_ENCRYPTION_ALGORITHM
            )
            BackupArchiveCodec.writeArchive(db, archive, manifest, key)
            val restored = File(dir, "restored.db")
            val actual = FileInputStream(archive).use {
                BackupArchiveCodec.extractAndVerifyPortable(it, restored, password = null, legacyDeviceKey = key)
            }
            assertEquals(BackupArchiveCodec.DEVICE_BOUND_FORMAT_VERSION, actual.formatVersion)
            assertTrue(restored.readBytes().contentEquals(db.readBytes()))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun device_bound_v2_wrong_key_returns_clear_cross_device_message() {
        val dir = kotlin.io.path.createTempDirectory("fush-backup-v2-wrong-").toFile()
        try {
            val db = File(dir, "fush_erp.db").apply { writeBytes(ByteArray(1024) { 4 }) }
            val archive = File(dir, "v2.fushbackup")
            val manifest = BackupManifest(
                BackupArchiveCodec.DEVICE_BOUND_FORMAT_VERSION,
                "com.fush.erp.recovery",
                "v105",
                38,
                1234L,
                BackupArchiveCodec.sha256(db),
                BackupArchiveCodec.DEVICE_ENCRYPTION_ALGORITHM
            )
            BackupArchiveCodec.writeArchive(db, archive, manifest, newAesKey())
            val destination = File(dir, "partial.db")
            val failure = runCatching {
                FileInputStream(archive).use {
                    BackupArchiveCodec.extractAndVerifyPortable(it, destination, password = null, legacyDeviceKey = newAesKey())
                }
            }.exceptionOrNull()
            assertTrue(failure != null)
            assertTrue(failure!!.message.orEmpty().contains("مرتبطة بالجهاز"))
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
                    setProperty("schemaVersion", "38")
                    setProperty("createdAt", "1234")
                    setProperty("databaseSha256", hash)
                }.store(zip, "legacy")
                zip.closeEntry()
                zip.putNextEntry(ZipEntry(BackupArchiveCodec.DATABASE_ENTRY))
                FileInputStream(db).use { it.copyTo(zip) }
                zip.closeEntry()
            }

            val restored = File(dir, "restored.db")
            val actual = FileInputStream(archive).use {
                BackupArchiveCodec.extractAndVerifyPortable(it, restored, password = null, legacyDeviceKey = null)
            }
            assertEquals(BackupArchiveCodec.LEGACY_FORMAT_VERSION, actual.formatVersion)
            assertEquals("NONE", actual.encryptionAlgorithm)
            assertEquals(hash, BackupArchiveCodec.sha256(restored))
        } finally {
            dir.deleteRecursively()
        }
    }
    @Test
    fun portable_v4_backup_round_trip_preserves_managed_attachments() {
        val dir = kotlin.io.path.createTempDirectory("fush-backup-v4-attachments-").toFile()
        try {
            val db = File(dir, "fush_erp.db").apply { writeBytes(ByteArray(768) { (it % 173).toByte() }) }
            val sourceAttachments = File(dir, "attachments").apply { mkdirs() }
            val invoice = File(sourceAttachments, "expense/receipt.pdf").apply { parentFile!!.mkdirs(); writeBytes("PDF-ATTACHMENT-128".toByteArray()) }
            val photo = File(sourceAttachments, "party/photo.jpg").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(255) { (it % 97).toByte() }) }
            val archive = File(dir, "portable-v4.fushbackup")
            val manifest = BackupManifest(
                BackupArchiveCodec.FORMAT_VERSION,
                "com.fush.erp.recovery",
                "0.15.4.79-release-hardening1",
                39,
                5678L,
                BackupArchiveCodec.sha256(db),
                BackupArchiveCodec.PORTABLE_ENCRYPTION_ALGORITHM
            )
            val password = password()
            try {
                BackupArchiveCodec.writePortableArchive(db, archive, manifest, password, sourceAttachments)
                val restoredDb = File(dir, "restored/fush_erp.db")
                val restoredAttachments = File(dir, "restored/attachments")
                val actual = FileInputStream(archive).use {
                    BackupArchiveCodec.extractAndVerifyPortable(it, restoredDb, password, null, destinationAttachments = restoredAttachments)
                }
                assertEquals(BackupArchiveCodec.FORMAT_VERSION, actual.formatVersion)
                assertTrue(restoredDb.readBytes().contentEquals(db.readBytes()))
                assertTrue(File(restoredAttachments, "expense/receipt.pdf").readBytes().contentEquals(invoice.readBytes()))
                assertTrue(File(restoredAttachments, "party/photo.jpg").readBytes().contentEquals(photo.readBytes()))
            } finally {
                password.fill('\u0000')
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun portable_v4_backup_round_trip_preserves_portable_settings() {
        val dir = kotlin.io.path.createTempDirectory("fush-backup-v4-settings-").toFile()
        try {
            val db = File(dir, "fush_erp.db").apply { writeBytes(ByteArray(512) { (it % 149).toByte() }) }
            val archive = File(dir, "portable-v4-settings.fushbackup")
            val manifest = BackupManifest(
                BackupArchiveCodec.FORMAT_VERSION,
                "com.fush.erp.recovery",
                "test-v133",
                39,
                6789L,
                BackupArchiveCodec.sha256(db),
                BackupArchiveCodec.PORTABLE_ENCRYPTION_ALGORITHM
            )
            val settings = Properties().apply { setProperty("near_expiry_days", "90") }
            val password = password()
            try {
                BackupArchiveCodec.writePortableArchive(
                    db, archive, manifest, password,
                    attachmentsRoot = null,
                    portableSettings = settings
                )
                val restoredDb = File(dir, "restored/fush_erp.db")
                val restoredSettings = File(dir, "restored/app.properties")
                FileInputStream(archive).use {
                    BackupArchiveCodec.extractAndVerifyPortable(
                        it, restoredDb, password, null,
                        destinationSettings = restoredSettings
                    )
                }
                val actualSettings = Properties().apply {
                    restoredSettings.inputStream().use { input -> load(input) }
                }
                assertEquals("90", actualSettings.getProperty("near_expiry_days"))
            } finally {
                password.fill('\u0000')
            }
        } finally {
            dir.deleteRecursively()
        }
    }

}
