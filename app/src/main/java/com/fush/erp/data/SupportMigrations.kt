package com.fush.erp.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_40_41_SUPPORT_MAINTENANCE_MODE = object : Migration(40, 41) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS support_tickets (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                companyId TEXT NOT NULL,
                branchId TEXT,
                requestedBy INTEGER NOT NULL,
                problemDescription TEXT NOT NULL,
                status TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                closedAt INTEGER,
                FOREIGN KEY(requestedBy) REFERENCES users(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_support_tickets_companyId ON support_tickets(companyId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_support_tickets_branchId ON support_tickets(branchId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_support_tickets_requestedBy ON support_tickets(requestedBy)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_support_tickets_status ON support_tickets(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_support_tickets_createdAt ON support_tickets(createdAt)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS support_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                ticketId INTEGER NOT NULL,
                companyId TEXT NOT NULL,
                branchId TEXT,
                supportUserId INTEGER NOT NULL,
                activatedBy INTEGER NOT NULL,
                status TEXT NOT NULL,
                startedAt INTEGER NOT NULL,
                expiresAt INTEGER NOT NULL,
                revokedAt INTEGER,
                closedAt INTEGER,
                closeReason TEXT NOT NULL,
                FOREIGN KEY(ticketId) REFERENCES support_tickets(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(supportUserId) REFERENCES users(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(activatedBy) REFERENCES users(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_support_sessions_ticketId ON support_sessions(ticketId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_support_sessions_companyId ON support_sessions(companyId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_support_sessions_supportUserId ON support_sessions(supportUserId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_support_sessions_status ON support_sessions(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_support_sessions_expiresAt ON support_sessions(expiresAt)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS support_snapshots (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                supportUserId INTEGER NOT NULL,
                companyId TEXT NOT NULL,
                ticketId INTEGER NOT NULL,
                sessionId INTEGER NOT NULL,
                module TEXT NOT NULL,
                recordTable TEXT NOT NULL,
                recordId TEXT NOT NULL,
                beforeValue TEXT NOT NULL,
                reason TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(ticketId) REFERENCES support_tickets(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(sessionId) REFERENCES support_sessions(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(supportUserId) REFERENCES users(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_support_snapshots_ticketId ON support_snapshots(ticketId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_support_snapshots_sessionId ON support_snapshots(sessionId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_support_snapshots_recordTable_recordId ON support_snapshots(recordTable,recordId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_support_snapshots_createdAt ON support_snapshots(createdAt)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS support_audit_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                supportUserId INTEGER NOT NULL,
                companyId TEXT NOT NULL,
                ticketId INTEGER NOT NULL,
                sessionId INTEGER NOT NULL,
                snapshotId INTEGER,
                action TEXT NOT NULL,
                module TEXT NOT NULL,
                recordTable TEXT NOT NULL,
                recordId TEXT NOT NULL,
                beforeValue TEXT NOT NULL,
                afterValue TEXT NOT NULL,
                reason TEXT NOT NULL,
                validationSummary TEXT NOT NULL,
                eventAt INTEGER NOT NULL,
                ipAddress TEXT NOT NULL,
                deviceInfo TEXT NOT NULL,
                FOREIGN KEY(ticketId) REFERENCES support_tickets(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(sessionId) REFERENCES support_sessions(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(supportUserId) REFERENCES users(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_support_audit_log_supportUserId ON support_audit_log(supportUserId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_support_audit_log_companyId ON support_audit_log(companyId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_support_audit_log_ticketId ON support_audit_log(ticketId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_support_audit_log_sessionId ON support_audit_log(sessionId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_support_audit_log_eventAt ON support_audit_log(eventAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_support_audit_log_recordTable_recordId ON support_audit_log(recordTable,recordId)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS support_validation_results (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                supportUserId INTEGER NOT NULL,
                companyId TEXT NOT NULL,
                ticketId INTEGER NOT NULL,
                sessionId INTEGER NOT NULL,
                command TEXT NOT NULL,
                status TEXT NOT NULL,
                summary TEXT NOT NULL,
                eventAt INTEGER NOT NULL,
                FOREIGN KEY(ticketId) REFERENCES support_tickets(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(sessionId) REFERENCES support_sessions(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(supportUserId) REFERENCES users(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_support_validation_results_ticketId ON support_validation_results(ticketId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_support_validation_results_sessionId ON support_validation_results(sessionId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_support_validation_results_eventAt ON support_validation_results(eventAt)")
        installSupportImmutableGuards(db)
    }
}

fun installSupportImmutableGuards(db: SupportSQLiteDatabase) {
    listOf("support_snapshots", "support_audit_log", "support_validation_results", "vendor_support_identities", "vendor_support_keys").forEach { table ->
        val exists = db.query("SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1", arrayOf(table)).use { it.moveToFirst() }
        if (!exists) return@forEach
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS trg_${table}_no_update
            BEFORE UPDATE ON $table
            BEGIN SELECT RAISE(ABORT, '$table is immutable'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS trg_${table}_no_delete
            BEFORE DELETE ON $table
            BEGIN SELECT RAISE(ABORT, '$table is immutable'); END
        """.trimIndent())
    }
}


val MIGRATION_41_42_SUPPORT_JOURNAL_PROVENANCE = object : Migration(41, 42) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v137 stores the exact treasury selected by the source document so Support Repair
        // can reconstruct the expected journal without trusting the STAGING journal itself.
        db.execSQL("ALTER TABLE sales_invoices ADD COLUMN treasuryAccountId INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE customer_receipts ADD COLUMN treasuryAccountId INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE sales_returns ADD COLUMN treasuryAccountId INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE purchase_invoices ADD COLUMN treasuryAccountId INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE purchase_returns ADD COLUMN treasuryAccountId INTEGER DEFAULT NULL")
        // Historical rows stay NULL. Safe Support Repair fails closed for a cash source when
        // provenance cannot be proven; it never guesses a treasury account from the journal.
    }
}


val MIGRATION_42_43_VENDOR_SUPPORT_PROVISIONING = object : Migration(42, 43) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS vendor_support_identities (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                supportUserId INTEGER NOT NULL,
                installationBinding TEXT NOT NULL,
                keyId TEXT NOT NULL,
                packageNonce TEXT NOT NULL,
                packageFingerprint TEXT NOT NULL,
                provisionedBy INTEGER NOT NULL,
                provisionedAt INTEGER NOT NULL,
                FOREIGN KEY(supportUserId) REFERENCES users(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(provisionedBy) REFERENCES users(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_vendor_support_identities_supportUserId ON vendor_support_identities(supportUserId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_vendor_support_identities_installationBinding ON vendor_support_identities(installationBinding)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_vendor_support_identities_packageNonce ON vendor_support_identities(packageNonce)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_vendor_support_identities_provisionedBy ON vendor_support_identities(provisionedBy)")
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS trg_vendor_support_identities_no_update
            BEFORE UPDATE ON vendor_support_identities
            BEGIN SELECT RAISE(ABORT, 'vendor_support_identities are immutable'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS trg_vendor_support_identities_no_delete
            BEFORE DELETE ON vendor_support_identities
            BEGIN SELECT RAISE(ABORT, 'vendor_support_identities are immutable'); END
        """.trimIndent())
    }
}


val MIGRATION_43_44_VENDOR_SUPPORT_LIFECYCLE = object : Migration(43, 44) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v139 converts Vendor Support identity into append-only lifecycle history.
        // Historical v138 rows are preserved verbatim and become credentialVersion=1 PROVISION events.
        db.execSQL("DROP TRIGGER IF EXISTS trg_vendor_support_identities_no_update")
        db.execSQL("DROP TRIGGER IF EXISTS trg_vendor_support_identities_no_delete")
        db.execSQL("ALTER TABLE vendor_support_identities RENAME TO vendor_support_identities_v138")
        db.execSQL("""
            CREATE TABLE vendor_support_identities (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                supportUserId INTEGER NOT NULL,
                installationBinding TEXT NOT NULL,
                keyId TEXT NOT NULL,
                packageNonce TEXT NOT NULL,
                packageFingerprint TEXT NOT NULL,
                provisionedBy INTEGER NOT NULL,
                provisionedAt INTEGER NOT NULL,
                lifecycleAction TEXT NOT NULL,
                credentialVersion INTEGER NOT NULL,
                supersedesIdentityId INTEGER,
                previousPackageFingerprint TEXT NOT NULL,
                FOREIGN KEY(supportUserId) REFERENCES users(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(provisionedBy) REFERENCES users(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO vendor_support_identities(
                id,supportUserId,installationBinding,keyId,packageNonce,packageFingerprint,
                provisionedBy,provisionedAt,lifecycleAction,credentialVersion,supersedesIdentityId,previousPackageFingerprint
            )
            SELECT id,supportUserId,installationBinding,keyId,packageNonce,packageFingerprint,
                   provisionedBy,provisionedAt,'PROVISION',1,NULL,''
            FROM vendor_support_identities_v138
        """.trimIndent())
        // IMPORTANT: SQLite index names are global. After RENAME, v138 indexes still exist
        // under their original names but point at vendor_support_identities_v138. Creating the
        // v139 indexes before dropping the old table makes IF NOT EXISTS silently skip them,
        // then DROP TABLE removes the old indexes and Room fails schema validation at startup.
        // Drop the v138 table first, then create every index on the new lifecycle table.
        db.execSQL("DROP TABLE vendor_support_identities_v138")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_vendor_support_identities_supportUserId ON vendor_support_identities(supportUserId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_vendor_support_identities_installationBinding ON vendor_support_identities(installationBinding)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_vendor_support_identities_packageNonce ON vendor_support_identities(packageNonce)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_vendor_support_identities_provisionedBy ON vendor_support_identities(provisionedBy)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_vendor_support_identities_credentialVersion ON vendor_support_identities(credentialVersion)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_vendor_support_identities_supersedesIdentityId ON vendor_support_identities(supersedesIdentityId)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS vendor_support_keys (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                keyId TEXT NOT NULL,
                publicKeyDerBase64 TEXT NOT NULL,
                supersedesKeyId TEXT NOT NULL,
                packageFingerprint TEXT NOT NULL,
                rotatedBy INTEGER NOT NULL,
                activatedAt INTEGER NOT NULL,
                FOREIGN KEY(rotatedBy) REFERENCES users(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_vendor_support_keys_keyId ON vendor_support_keys(keyId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_vendor_support_keys_rotatedBy ON vendor_support_keys(rotatedBy)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_vendor_support_keys_activatedAt ON vendor_support_keys(activatedAt)")
        installSupportImmutableGuards(db)
    }
}
