from pathlib import Path

root = Path('FushERP_Mobile_Phase5')

p = root / 'app/src/main/java/com/fush/erp/data/FushDatabase.kt'
s = p.read_text()
s = s.replace('        SafetyInspectionEntity::class\n    ],\n    version = 5,', '        SafetyInspectionEntity::class,\n        ControlledDocumentEntity::class,\n        ChangeRequestEntity::class,\n        ApprovalRequestEntity::class,\n        AuditEventEntity::class\n    ],\n    version = 6,')
s = s.replace('    abstract fun maintenanceDao(): MaintenanceDao\n}', '    abstract fun maintenanceDao(): MaintenanceDao\n    abstract fun governanceDao(): GovernanceDao\n}')
p.write_text(s)

p = root / 'app/src/main/java/com/fush/erp/data/AppContainer.kt'
s = p.read_text().replace('.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build()', '.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build()')
p.write_text(s)

p = root / 'app/src/main/java/com/fush/erp/ui/screens/HomeShell.kt'
s = p.read_text()
s = s.replace('            ModuleCard("الأصول والصيانة والسلامة", "الأصول، الأعطال، الوقائي، المعايرة والحوادث", "المرحلة 5 جاهزة"),\n            ModuleCard("لوحة الإدارة",', '            ModuleCard("الأصول والصيانة والسلامة", "الأصول، الأعطال، الوقائي، المعايرة والحوادث", "المرحلة 5 جاهزة"),\n            ModuleCard("الحوكمة والتدقيق", "SOP، النماذج، إدارة التغيير، الموافقات وسجل التدقيق", "المرحلة 6 جاهزة"),\n            ModuleCard("لوحة الإدارة",')
s = s.replace('listOf("الرئيسية", "المبيعات", "الإنتاج", "الصيانة", "المشتريات", "المخزون", "الحسابات", "البيانات")', 'listOf("الرئيسية", "المبيعات", "الإنتاج", "الصيانة", "المشتريات", "المخزون", "الحوكمة", "الحسابات", "البيانات")')
s = s.replace('            "المخزون" -> InventoryScreen(container, user, Modifier.padding(pad))\n            "الحسابات"', '            "المخزون" -> InventoryScreen(container, user, Modifier.padding(pad))\n            "الحوكمة" -> GovernanceScreen(container, user, Modifier.padding(pad))\n            "الحسابات"')
s = s.replace('Metric("الإصدار", "0.5", Modifier.weight(1f))', 'Metric("الإصدار", "0.6", Modifier.weight(1f))')
p.write_text(s)

p = root / 'app/src/main/java/com/fush/erp/data/Migrations.kt'
s = p.read_text()
if 'MIGRATION_5_6' not in s:
    s += r'''

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS controlled_documents (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                documentCode TEXT NOT NULL,
                titleAr TEXT NOT NULL,
                category TEXT NOT NULL,
                versionNo INTEGER NOT NULL,
                status TEXT NOT NULL,
                effectiveAt INTEGER,
                reviewDueAt INTEGER,
                ownerRole TEXT NOT NULL,
                contentSummary TEXT NOT NULL,
                createdBy INTEGER NOT NULL,
                approvedBy INTEGER,
                approvedAt INTEGER,
                createdAt INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_controlled_documents_documentCode ON controlled_documents(documentCode)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_controlled_documents_status ON controlled_documents(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_controlled_documents_category ON controlled_documents(category)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS change_requests (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                requestNo TEXT NOT NULL,
                changeType TEXT NOT NULL,
                subject TEXT NOT NULL,
                reason TEXT NOT NULL,
                qualityImpact TEXT NOT NULL,
                financialImpact TEXT NOT NULL,
                inventoryImpact TEXT NOT NULL,
                status TEXT NOT NULL,
                requestedBy INTEGER NOT NULL,
                approvedBy INTEGER,
                approvedAt INTEGER,
                implementedAt INTEGER,
                createdAt INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_change_requests_requestNo ON change_requests(requestNo)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_change_requests_status ON change_requests(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_change_requests_changeType ON change_requests(changeType)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS approval_requests (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                referenceType TEXT NOT NULL,
                referenceId TEXT NOT NULL,
                title TEXT NOT NULL,
                requestedRole TEXT NOT NULL,
                requestedBy INTEGER NOT NULL,
                status TEXT NOT NULL,
                decisionBy INTEGER,
                decisionAt INTEGER,
                decisionNote TEXT NOT NULL,
                requestedAt INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_approval_requests_status ON approval_requests(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_approval_requests_referenceType ON approval_requests(referenceType)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_approval_requests_requestedAt ON approval_requests(requestedAt)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS audit_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                eventAt INTEGER NOT NULL,
                userId INTEGER NOT NULL,
                action TEXT NOT NULL,
                entityType TEXT NOT NULL,
                entityId TEXT NOT NULL,
                oldValue TEXT NOT NULL,
                newValue TEXT NOT NULL,
                reason TEXT NOT NULL,
                deviceInfo TEXT NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_events_eventAt ON audit_events(eventAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_events_entityType ON audit_events(entityType)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_events_userId ON audit_events(userId)")
    }
}
'''
p.write_text(s)

(root / 'PHASE6_SCOPE.md').write_text('''# Fush ERP Mobile — Phase 6\n\nGovernance and controlled records:\n- Controlled SOP / form / policy documents with version/status.\n- Change requests for critical controlled changes.\n- Approval queue and decisions.\n- Append-only audit event log in the mobile UI.\n- Database migration 5 -> 6.\n''')
