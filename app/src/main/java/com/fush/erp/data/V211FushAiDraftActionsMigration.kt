package com.fush.erp.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v211: isolated, non-posting FUSH AI draft store. */
val MIGRATION_52_53_FUSH_AI_DRAFT_ACTIONS = object : Migration(52, 53) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `fush_ai_drafts` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `draftType` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `summary` TEXT NOT NULL,
                `payloadJson` TEXT NOT NULL,
                `requestedBy` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `approvedBy` INTEGER,
                `approvedAt` INTEGER,
                `cancelledBy` INTEGER,
                `cancelledAt` INTEGER,
                FOREIGN KEY(`requestedBy`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_fush_ai_drafts_requestedBy` ON `fush_ai_drafts` (`requestedBy`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_fush_ai_drafts_status` ON `fush_ai_drafts` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_fush_ai_drafts_draftType` ON `fush_ai_drafts` (`draftType`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_fush_ai_drafts_createdAt` ON `fush_ai_drafts` (`createdAt`)")
    }
}
