package com.fush.erp.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupArchiveCodecTest {
    @Test
    fun archive_round_trip_preserves_manifest_and_database_hash() {
        val dir = kotlin.io.path.createTempDirectory("fush-backup-").toFile()
        try {
            val db = File(dir, "fush_erp.db").apply { writeBytes(ByteArray(512) { (it % 251).toByte() }) }
            val hash = BackupArchiveCodec.sha256(db)
            val archive = File(dir, "test.fushbackup")
            val expected = BackupManifest(1, "com.fush.erp", "test", 16, 1234L, hash)
            BackupArchiveCodec.writeArchive(db, archive, expected)
            val restored = File(dir, "restored.db")
            val actual = FileInputStream(archive).use { BackupArchiveCodec.extractAndVerify(it, restored) }
            assertEquals(expected, actual)
            assertEquals(hash, BackupArchiveCodec.sha256(restored))
            assertTrue(restored.readBytes().contentEquals(db.readBytes()))
        } finally {
            dir.deleteRecursively()
        }
    }
    @Test
    fun failedExtractionDeletesPartialDestination() {
        val dir = kotlin.io.path.createTempDirectory("fush-backup-corrupt-").toFile()
        try {
            val archive = File(dir, "bad.fushbackup")
            ZipOutputStream(FileOutputStream(archive)).use { zip ->
                zip.putNextEntry(ZipEntry(BackupArchiveCodec.MANIFEST_ENTRY))
                Properties().apply {
                    setProperty("formatVersion", "1")
                    setProperty("packageId", "com.fush.erp")
                    setProperty("appVersion", "test")
                    setProperty("schemaVersion", "23")
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
                FileInputStream(archive).use { BackupArchiveCodec.extractAndVerify(it, destination) }
            }.onSuccess { error("corrupt archive must fail") }
            assertFalse(destination.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

}
