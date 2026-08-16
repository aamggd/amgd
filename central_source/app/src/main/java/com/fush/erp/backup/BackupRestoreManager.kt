package com.fush.erp.backup

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.fush.erp.BuildConfig
import com.fush.erp.data.FUSH_DB_SCHEMA_VERSION
import com.fush.erp.data.FushDatabase
import com.fush.erp.data.entity.AuditEventEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


data class BackupResult(
    val uri: Uri,
    val displayName: String,
    val createdAt: Long,
    val schemaVersion: Int,
    val databaseSha256: String,
    val sizeBytes: Long
)

data class RestoreInspection(
    val sourceUri: Uri,
    val appVersion: String,
    val createdAt: Long,
    val schemaVersion: Int,
    val databaseSha256: String,
    val sizeBytes: Long
)

object BackupRestoreManager {
    const val DATABASE_NAME = "fush_erp.db"
    const val CURRENT_SCHEMA_VERSION = FUSH_DB_SCHEMA_VERSION
    private const val PREFS = "backup_restore_state"
    private const val PENDING_DB = "pending_restore.db"
    private const val PENDING_USER_ID = "pending_user_id"
    private const val PENDING_SCHEMA = "pending_schema"
    private const val APPLIED_USER_ID = "applied_user_id"
    private const val APPLIED_SCHEMA = "applied_schema"
    private const val APPLIED_AT = "applied_at"
    private const val LAST_BACKUP_URI = "last_backup_uri"
    private const val RESTORE_ERROR = "restore_error"

    suspend fun createBackup(context: Context, db: FushDatabase, userId: Long): BackupResult = withContext(Dispatchers.IO) {
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = userId,
                action = "CREATE_BACKUP",
                entityType = "DATABASE_BACKUP",
                entityId = DATABASE_NAME,
                newValue = "schema=$CURRENT_SCHEMA_VERSION app=${BuildConfig.VERSION_NAME}",
                reason = "نسخة احتياطية يدوية"
            )
        )
        checkpoint(db)

        val source = context.getDatabasePath(DATABASE_NAME)
        require(source.isFile) { "قاعدة البيانات غير موجودة" }
        val workDir = File(context.cacheDir, "backup_work").apply { mkdirs() }
        val snapshot = File(workDir, "snapshot-${System.nanoTime()}.db")
        val archive = File(workDir, "FushERP-${stamp(System.currentTimeMillis())}-${System.nanoTime()}.fushbackup")
        try {
            RestoreFileSafety.copyAndSync(source, snapshot)
            val actualSchema = validateSqlite(snapshot, expectedMaxSchema = CURRENT_SCHEMA_VERSION)
            require(actualSchema == CURRENT_SCHEMA_VERSION) {
                "إصدار قاعدة البيانات الفعلي $actualSchema لا يطابق إصدار التطبيق $CURRENT_SCHEMA_VERSION"
            }

            val now = System.currentTimeMillis()
            val dbHash = BackupArchiveCodec.sha256(snapshot)
            val backupKey = BackupEncryptionKeyProvider.getOrCreate()
            BackupArchiveCodec.writeArchive(
                snapshot,
                archive,
                BackupManifest(
                    formatVersion = BackupArchiveCodec.FORMAT_VERSION,
                    packageId = BuildConfig.APPLICATION_ID,
                    appVersion = BuildConfig.VERSION_NAME,
                    schemaVersion = actualSchema,
                    createdAt = now,
                    databaseSha256 = dbHash
                ),
                backupKey
            )
            val archiveSize = archive.length()
            val displayName = archive.name.substringBeforeLast('-') + ".fushbackup"
            val saved = saveArchive(context, archive, displayName)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(LAST_BACKUP_URI, saved.toString())
                .apply()
            BackupResult(saved, displayName, now, actualSchema, dbHash, archiveSize)
        } finally {
            snapshot.delete()
            archive.delete()
        }
    }

    suspend fun inspectBackup(context: Context, uri: Uri): RestoreInspection = withContext(Dispatchers.IO) {
        val stageDir = File(context.cacheDir, "restore_inspect").apply { mkdirs() }
        val candidate = File(stageDir, "candidate-${System.nanoTime()}.db")
        try {
            val manifest = context.contentResolver.openInputStream(uri)?.use {
                BackupArchiveCodec.extractAndVerify(it, candidate, BackupEncryptionKeyProvider.getOrCreate())
            } ?: error("تعذر فتح ملف النسخة الاحتياطية")
            require(isCompatibleBackupPackage(manifest.packageId)) { "هذه النسخة لا تخص تطبيق Fush ERP الحالي" }
            require(manifest.schemaVersion in 1..CURRENT_SCHEMA_VERSION) {
                "إصدار قاعدة البيانات ${manifest.schemaVersion} أحدث من الإصدار الذي يدعمه التطبيق"
            }
            val actualSchema = validateSqlite(candidate, expectedMaxSchema = CURRENT_SCHEMA_VERSION)
            require(actualSchema == manifest.schemaVersion) { "إصدار قاعدة البيانات داخل الملف لا يطابق بيانات النسخة" }
            RestoreInspection(uri, manifest.appVersion, manifest.createdAt, actualSchema, manifest.databaseSha256, candidate.length())
        } finally {
            candidate.delete()
        }
    }

    suspend fun stageRestore(context: Context, inspection: RestoreInspection, userId: Long) = withContext(Dispatchers.IO) {
        val pendingDir = File(context.filesDir, "pending_restore").apply { mkdirs() }
        RestoreFileSafety.cleanupPendingTemps(pendingDir, PENDING_DB)
        val temp = File(pendingDir, "$PENDING_DB.tmp-${System.nanoTime()}")
        val pending = File(pendingDir, PENDING_DB)
        try {
            val manifest = context.contentResolver.openInputStream(inspection.sourceUri)?.use {
                BackupArchiveCodec.extractAndVerify(it, temp, BackupEncryptionKeyProvider.getOrCreate())
            } ?: error("تعذر إعادة فتح ملف النسخة الاحتياطية")
            require(isCompatibleBackupPackage(manifest.packageId)) { "هذه النسخة لا تخص Fush ERP" }
            require(manifest.schemaVersion == inspection.schemaVersion) { "تغير إصدار النسخة المحددة أثناء عملية الاستعادة" }
            require(manifest.databaseSha256.equals(inspection.databaseSha256, ignoreCase = true)) {
                "تغيرت بصمة النسخة المحددة أثناء عملية الاستعادة"
            }
            val actualSchema = validateSqlite(temp, expectedMaxSchema = CURRENT_SCHEMA_VERSION)
            require(actualSchema == inspection.schemaVersion) { "تغيرت النسخة المحددة أثناء عملية الاستعادة" }
            require(BackupArchiveCodec.sha256(temp).equals(inspection.databaseSha256, ignoreCase = true)) {
                "بصمة ملف الاستعادة غير مطابقة"
            }
            RestoreFileSafety.sync(temp)
            RestoreFileSafety.atomicReplace(temp, pending)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(PENDING_USER_ID, userId)
                .putInt(PENDING_SCHEMA, actualSchema)
                .remove(RESTORE_ERROR)
                .commit()
        } finally {
            temp.delete()
            RestoreFileSafety.cleanupPendingTemps(pendingDir, PENDING_DB)
        }
    }

    fun applyPendingRestore(context: Context): Boolean {
        val pendingDir = File(context.filesDir, "pending_restore").apply { mkdirs() }
        RestoreFileSafety.cleanupPendingTemps(pendingDir, PENDING_DB)
        val pending = File(pendingDir, PENDING_DB)
        if (!pending.isFile) return false

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val dbFile = context.getDatabasePath(DATABASE_NAME)
        dbFile.parentFile?.mkdirs()
        RestoreFileSafety.cleanupDatabaseRestoreTemps(dbFile)
        val replacement = File(dbFile.parentFile, "$DATABASE_NAME.restore.tmp-${System.nanoTime()}")
        var safetyDir: File? = null
        var replacementInstalled = false

        return try {
            val sourceSchema = validateSqlite(pending, expectedMaxSchema = CURRENT_SCHEMA_VERSION)
            val pendingHash = BackupArchiveCodec.sha256(pending)
            val userId = prefs.getLong(PENDING_USER_ID, 0L)

            RestoreFileSafety.copyAndSync(pending, replacement)
            require(validateSqlite(replacement, expectedMaxSchema = CURRENT_SCHEMA_VERSION) == sourceSchema) {
                "إصدار ملف الاستعادة تغير أثناء التجهيز"
            }
            require(BackupArchiveCodec.sha256(replacement).equals(pendingHash, ignoreCase = true)) {
                "فشل التحقق من نسخة الاستعادة المؤقتة"
            }

            if (dbFile.isFile) {
                // Make the current database self-contained before sidecars are removed.
                checkpointClosedDatabase(dbFile)
                safetyDir = File(context.filesDir, "pre_restore_safety/${System.currentTimeMillis()}").apply { mkdirs() }
                RestoreFileSafety.copyDatabaseSet(dbFile, safetyDir!!)
            }

            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()
            RestoreFileSafety.atomicReplace(replacement, dbFile)
            replacementInstalled = true
            require(validateSqlite(dbFile, expectedMaxSchema = CURRENT_SCHEMA_VERSION) == sourceSchema) {
                "فشل التحقق من قاعدة البيانات بعد تطبيق الاستعادة"
            }
            require(BackupArchiveCodec.sha256(dbFile).equals(pendingHash, ignoreCase = true)) {
                "بصمة قاعدة البيانات بعد الاستعادة غير مطابقة"
            }

            val appliedAt = System.currentTimeMillis()
            prefs.edit()
                .remove(PENDING_USER_ID)
                .remove(PENDING_SCHEMA)
                .remove(RESTORE_ERROR)
                .putLong(APPLIED_USER_ID, userId)
                .putInt(APPLIED_SCHEMA, sourceSchema)
                .putLong(APPLIED_AT, appliedAt)
                .commit()

            // Move the consumed restore source out of the pending location so a crash
            // after a successful swap cannot cause an unintended second restore.
            val consumedDir = safetyDir ?: File(context.filesDir, "pre_restore_safety/$appliedAt").apply { mkdirs() }
            val consumed = File(consumedDir, "applied-$PENDING_DB")
            runCatching { RestoreFileSafety.atomicReplace(pending, consumed) }
                .onFailure { pending.delete() }
            pruneSafetyDirectories(context, keep = 3)
            true
        } catch (t: Throwable) {
            val rollbackError = if (replacementInstalled) {
                runCatching {
                    val dir = safetyDir
                    if (dir != null && File(dir, dbFile.name).isFile) {
                        RestoreFileSafety.restoreDatabaseSet(dir, dbFile)
                    } else {
                        dbFile.delete()
                        File(dbFile.path + "-wal").delete()
                        File(dbFile.path + "-shm").delete()
                    }
                }.exceptionOrNull()
            } else null
            val reason = buildString {
                append(t.message ?: t::class.java.simpleName)
                if (rollbackError != null) append(" | فشل الرجوع لنسخة الأمان: ${rollbackError.message}")
            }
            prefs.edit().putString(RESTORE_ERROR, reason).commit()
            false
        } finally {
            replacement.delete()
            RestoreFileSafety.cleanupDatabaseRestoreTemps(dbFile)
            RestoreFileSafety.cleanupPendingTemps(pendingDir, PENDING_DB)
        }
    }

    internal fun isCompatibleBackupPackage(packageId: String): Boolean =
        packageId == BuildConfig.APPLICATION_ID ||
            (BuildConfig.APPLICATION_ID == "com.fush.erp.recovery" && packageId == "com.fush.erp")

    fun consumeRestoreError(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val value = prefs.getString(RESTORE_ERROR, null)
        if (value != null) prefs.edit().remove(RESTORE_ERROR).apply()
        return value
    }

    suspend fun recordAppliedRestoreAudit(context: Context, db: FushDatabase) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val appliedAt = prefs.getLong(APPLIED_AT, 0L)
        if (appliedAt <= 0L) return@withContext
        val userId = prefs.getLong(APPLIED_USER_ID, 0L)
        val schema = prefs.getInt(APPLIED_SCHEMA, 0)
        db.governanceDao().insertAudit(
            AuditEventEntity(
                eventAt = appliedAt,
                userId = userId,
                action = "RESTORE_BACKUP",
                entityType = "DATABASE_BACKUP",
                entityId = DATABASE_NAME,
                oldValue = "database replaced atomically",
                newValue = "restored schema=$schema migrated_to=$CURRENT_SCHEMA_VERSION",
                reason = "استعادة نسخة احتياطية بعد التحقق من السلامة والرجوع الآمن"
            )
        )
        prefs.edit().remove(APPLIED_USER_ID).remove(APPLIED_SCHEMA).remove(APPLIED_AT).apply()
    }

    fun lastBackupUri(context: Context): Uri? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(LAST_BACKUP_URI, null)?.let(Uri::parse)

    fun shareBackup(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة نسخة Fush ERP الاحتياطية").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun checkpoint(db: FushDatabase) {
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
            if (cursor.moveToFirst() && cursor.columnCount >= 1) {
                require(cursor.getInt(0) == 0) { "تعذر تثبيت معاملات قاعدة البيانات قبل النسخ" }
            }
        }
    }

    private fun checkpointClosedDatabase(dbFile: File) {
        val current = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            current.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { cursor ->
                if (cursor.moveToFirst() && cursor.columnCount >= 1) {
                    require(cursor.getInt(0) == 0) { "تعذر تثبيت معاملات قاعدة البيانات الحالية قبل الاستعادة" }
                }
            }
        } finally {
            current.close()
        }
    }

    private fun validateSqlite(file: File, expectedMaxSchema: Int): Int {
        require(file.length() >= 100L) { "ملف قاعدة البيانات غير صالح" }
        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val integrity = db.rawQuery("PRAGMA quick_check(1)", null).use { c -> if (c.moveToFirst()) c.getString(0) else "failed" }
            require(integrity.equals("ok", ignoreCase = true)) { "فشل فحص سلامة قاعدة البيانات: $integrity" }
            val version = db.rawQuery("PRAGMA user_version", null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
            require(version in 1..expectedMaxSchema) { "إصدار قاعدة البيانات غير مدعوم: $version" }
            return version
        } finally {
            db.close()
        }
    }

    private fun saveArchive(context: Context, archive: File, displayName: String): Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/FushERP/Backups")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)) { "تعذر إنشاء ملف النسخة الاحتياطية" }
            try {
                resolver.openOutputStream(uri)?.use { out -> archive.inputStream().use { it.copyTo(out) } }
                    ?: error("تعذر كتابة النسخة الاحتياطية")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return uri
            } catch (t: Throwable) {
                resolver.delete(uri, null, null)
                throw t
            }
        }
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "FushERP/Backups").apply { mkdirs() }
        val destination = File(dir, displayName)
        archive.copyTo(destination, overwrite = true)
        return FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".files", destination)
    }

    private fun pruneSafetyDirectories(context: Context, keep: Int) {
        val root = File(context.filesDir, "pre_restore_safety")
        val dirs = root.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.lastModified() }.orEmpty()
        dirs.drop(keep.coerceAtLeast(1)).forEach { it.deleteRecursively() }
    }

    private fun stamp(time: Long): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(time))
}
