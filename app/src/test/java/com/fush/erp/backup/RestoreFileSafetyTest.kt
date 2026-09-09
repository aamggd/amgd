package com.fush.erp.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RestoreFileSafetyTest {
    @Test
    fun atomicReplace_replacesDestinationWithoutLeavingSource() {
        val dir = kotlin.io.path.createTempDirectory("restore-atomic-").toFile()
        try {
            val source = File(dir, "source.tmp").apply { writeText("new") }
            val destination = File(dir, "database.db").apply { writeText("old") }
            RestoreFileSafety.sync(source)
            RestoreFileSafety.atomicReplace(source, destination)
            assertEquals("new", destination.readText())
            assertFalse(source.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun cleanupOnlyDeletesRestoreTemporaryFiles() {
        val dir = kotlin.io.path.createTempDirectory("restore-clean-").toFile()
        try {
            val pending = File(dir, "pending_restore.db").apply { writeText("keep") }
            val stale1 = File(dir, "pending_restore.db.tmp-1").apply { writeText("x") }
            val stale2 = File(dir, "pending_restore.db.tmp-old").apply { writeText("x") }
            RestoreFileSafety.cleanupPendingTemps(dir, "pending_restore.db")
            assertTrue(pending.isFile)
            assertFalse(stale1.exists())
            assertFalse(stale2.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun restoreDatabaseSetRestoresMainWalAndShm() {
        val dir = kotlin.io.path.createTempDirectory("restore-rollback-").toFile()
        try {
            val db = File(dir, "fush_erp.db").apply { writeText("new-db") }
            File(db.path + "-wal").writeText("new-wal")
            File(db.path + "-shm").writeText("new-shm")
            val safety = File(dir, "safety").apply { mkdirs() }
            File(safety, db.name).writeText("old-db")
            File(safety, db.name + "-wal").writeText("old-wal")
            File(safety, db.name + "-shm").writeText("old-shm")

            RestoreFileSafety.restoreDatabaseSet(safety, db)

            assertEquals("old-db", db.readText())
            assertEquals("old-wal", File(db.path + "-wal").readText())
            assertEquals("old-shm", File(db.path + "-shm").readText())
        } finally {
            dir.deleteRecursively()
        }
    }
}
