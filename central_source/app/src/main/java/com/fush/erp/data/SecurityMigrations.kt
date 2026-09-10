package com.fush.erp.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Installs the users/RBAC/MFA schema without assuming the final global Room version.
 * The specialized security branch wraps this as 27 -> 28 for isolated validation only.
 * During central integration, accounting owns 27 -> 28 and this installer must be wrapped as 28 -> 29.
 */
fun installUsersSecuritySchema(db: SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE users ADD COLUMN failedLoginAttempts INTEGER NOT NULL DEFAULT 0")
    db.execSQL("ALTER TABLE users ADD COLUMN lockoutCount INTEGER NOT NULL DEFAULT 0")
    db.execSQL("ALTER TABLE users ADD COLUMN lockedUntil INTEGER")
    db.execSQL("ALTER TABLE users ADD COLUMN lastLoginAt INTEGER")
    db.execSQL("ALTER TABLE users ADD COLUMN passwordChangedAt INTEGER")
    db.execSQL("ALTER TABLE users ADD COLUMN sessionVersion INTEGER NOT NULL DEFAULT 0")
    db.execSQL("ALTER TABLE users ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
    db.execSQL("ALTER TABLE users ADD COLUMN mfaEnabled INTEGER NOT NULL DEFAULT 0")
    db.execSQL("ALTER TABLE users ADD COLUMN mfaSecretCiphertext TEXT")
    db.execSQL("ALTER TABLE users ADD COLUMN mfaConfirmedAt INTEGER")
    db.execSQL("ALTER TABLE users ADD COLUMN mfaVerifiedSessionVersion INTEGER NOT NULL DEFAULT -1")
    db.execSQL("UPDATE users SET updatedAt = createdAt WHERE updatedAt = 0")
    // Preserve existing passwords without forcing an immediate reset solely because the age field is new.
    // Password age starts at the safe migration time for pre-security accounts.
    db.execSQL("UPDATE users SET passwordChangedAt = CAST(strftime('%s','now') AS INTEGER) * 1000 WHERE passwordChangedAt IS NULL")

    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS roles (
            code TEXT NOT NULL PRIMARY KEY,
            nameAr TEXT NOT NULL,
            nameEn TEXT NOT NULL,
            description TEXT NOT NULL,
            isSystem INTEGER NOT NULL,
            isActive INTEGER NOT NULL,
            createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL
        )
        """.trimIndent()
    )

    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS permissions (
            code TEXT NOT NULL PRIMARY KEY,
            moduleKey TEXT NOT NULL,
            actionKey TEXT NOT NULL,
            nameAr TEXT NOT NULL,
            nameEn TEXT NOT NULL,
            description TEXT NOT NULL,
            sortOrder INTEGER NOT NULL
        )
        """.trimIndent()
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS index_permissions_moduleKey ON permissions(moduleKey)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_permissions_sortOrder ON permissions(sortOrder)")

    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS role_permissions (
            roleCode TEXT NOT NULL,
            permissionCode TEXT NOT NULL,
            PRIMARY KEY(roleCode, permissionCode),
            FOREIGN KEY(roleCode) REFERENCES roles(code) ON UPDATE NO ACTION ON DELETE CASCADE,
            FOREIGN KEY(permissionCode) REFERENCES permissions(code) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent()
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS index_role_permissions_permissionCode ON role_permissions(permissionCode)")

    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS user_password_history (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            userId INTEGER NOT NULL,
            passwordHash TEXT NOT NULL,
            salt TEXT NOT NULL,
            createdAt INTEGER NOT NULL,
            FOREIGN KEY(userId) REFERENCES users(id) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent()
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS index_user_password_history_userId ON user_password_history(userId)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_user_password_history_createdAt ON user_password_history(createdAt)")

    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS user_mfa_recovery_codes (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            userId INTEGER NOT NULL,
            codeHash TEXT NOT NULL,
            salt TEXT NOT NULL,
            usedAt INTEGER,
            createdAt INTEGER NOT NULL,
            FOREIGN KEY(userId) REFERENCES users(id) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent()
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS index_user_mfa_recovery_codes_userId ON user_mfa_recovery_codes(userId)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_user_mfa_recovery_codes_usedAt ON user_mfa_recovery_codes(usedAt)")
}

val MIGRATION_32_33_SECURITY = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) = installUsersSecuritySchema(db)
}
