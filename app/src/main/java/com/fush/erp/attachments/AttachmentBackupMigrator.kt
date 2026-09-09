package com.fush.erp.attachments

import androidx.room.withTransaction
import com.fush.erp.data.FushDatabase
import com.fush.erp.data.entity.AuditEventEntity

object AttachmentBackupMigrator {
    suspend fun makePortable(db: FushDatabase, context: android.content.Context, userId: Long): Int = db.withTransaction {
        var migrated = 0
        db.partyDao().allAttachments().forEach { row ->
            if (!AttachmentStorage.isManaged(row.uri)) {
                val ref = AttachmentStorage.migrateLegacyReference(context, row.uri, "party", row.fileName)
                require(db.partyDao().updateAttachmentUri(row.id, ref) == 1)
                migrated++
            }
        }
        db.expenseDao().allAttachments().forEach { row ->
            if (!AttachmentStorage.isManaged(row.uri)) {
                val ref = AttachmentStorage.migrateLegacyReference(context, row.uri, "expense", row.fileName)
                require(db.expenseDao().updateAttachmentUri(row.id, ref) == 1)
                migrated++
            }
        }
        if (migrated > 0) {
            db.governanceDao().insertAudit(AuditEventEntity(
                userId = userId, action = "MIGRATE_ATTACHMENTS", entityType = "ATTACHMENT_STORAGE", entityId = "portable",
                newValue = "migrated=$migrated", reason = "تحويل المرفقات القديمة إلى تخزين داخلي محمول قبل النسخ الاحتياطي"
            ))
        }
        migrated
    }
}
