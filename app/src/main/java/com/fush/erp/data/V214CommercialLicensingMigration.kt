package com.fush.erp.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v214: add the local cache for the server-authoritative commercial license snapshot.
 * Existing v213 databases migrate with no license row. Business data is never touched.
 */
val MIGRATION_54_55_COMMERCIAL_LICENSING = object : Migration(54, 55) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `commercial_license_snapshot` (
                `id` INTEGER NOT NULL,
                `organization_id` TEXT NOT NULL,
                `license_id` TEXT NOT NULL,
                `device_entitlement_id` TEXT,
                `plan_code` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `is_trial` INTEGER NOT NULL,
                `starts_at` INTEGER NOT NULL,
                `expires_at` INTEGER NOT NULL,
                `offline_grace_until` INTEGER NOT NULL,
                `max_devices` INTEGER NOT NULL,
                `verified_at` INTEGER NOT NULL,
                `server_time` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_commercial_license_snapshot_organization_id` " +
                "ON `commercial_license_snapshot` (`organization_id`)"
        )
    }
}
