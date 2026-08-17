package com.fush.erp.backup

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Filesystem operations used by restore are kept here so they can be unit-tested
 * without an Android Context. All replacement files live on the same app volume.
 */
internal object RestoreFileSafety {
    fun sync(file: File) {
        require(file.isFile) { "الملف المطلوب تثبيته غير موجود" }
        RandomAccessFile(file, "rw").use { it.fd.sync() }
    }

    fun copyAndSync(source: File, destination: File) {
        require(source.isFile) { "ملف المصدر غير موجود" }
        destination.parentFile?.mkdirs()
        source.copyTo(destination, overwrite = true)
        sync(destination)
    }

    fun atomicReplace(source: File, destination: File) {
        require(source.isFile) { "ملف الاستبدال غير موجود" }
        destination.parentFile?.mkdirs()
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (_: UnsupportedOperationException) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun cleanupPendingTemps(pendingDir: File, pendingName: String) {
        pendingDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.startsWith("$pendingName.tmp")) file.delete()
        }
    }

    fun cleanupDatabaseRestoreTemps(databaseFile: File) {
        databaseFile.parentFile?.listFiles()?.forEach { file ->
            if (file.isFile && (
                    file.name == "${databaseFile.name}.restore" ||
                        file.name.startsWith("${databaseFile.name}.restore.tmp")
                    )
            ) {
                file.delete()
            }
        }
    }

    fun copyDatabaseSet(databaseFile: File, safetyDir: File) {
        safetyDir.mkdirs()
        listOf(databaseFile, File(databaseFile.path + "-wal"), File(databaseFile.path + "-shm")).forEach { source ->
            if (source.isFile) copyAndSync(source, File(safetyDir, source.name))
        }
    }

    fun restoreDatabaseSet(safetyDir: File, databaseFile: File) {
        val safetyDb = File(safetyDir, databaseFile.name)
        require(safetyDb.isFile) { "نسخة الأمان الداخلية لقاعدة البيانات غير موجودة" }

        File(databaseFile.path + "-wal").delete()
        File(databaseFile.path + "-shm").delete()

        val rollbackTemp = File(databaseFile.parentFile, "${databaseFile.name}.rollback.tmp")
        rollbackTemp.delete()
        copyAndSync(safetyDb, rollbackTemp)
        atomicReplace(rollbackTemp, databaseFile)

        listOf("-wal", "-shm").forEach { suffix ->
            val source = File(safetyDir, databaseFile.name + suffix)
            val destination = File(databaseFile.path + suffix)
            if (source.isFile) copyAndSync(source, destination) else destination.delete()
        }
    }
}
