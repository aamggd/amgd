package com.fush.erp.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v213: persist the cloud tenant identity in the Room file itself.
 * Existing v212 databases migrate with an empty marker and are bound on the first successful
 * authenticated cloud membership check. No business row is modified or deleted.
 */
val MIGRATION_53_54_COMMERCIAL_TENANT_BINDING = object : Migration(53, 54) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cloud_tenant_binding` (
                `id` INTEGER NOT NULL,
                `organization_id` TEXT NOT NULL,
                `bound_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_cloud_tenant_binding_organization_id` " +
                "ON `cloud_tenant_binding` (`organization_id`)"
        )
    }
}
