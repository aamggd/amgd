from pathlib import Path
import shutil
import sys

if len(sys.argv) != 4:
    raise SystemExit("usage: merge_combined.py <multi_dir> <party_dir> <final_dir>")

multi = Path(sys.argv[1]).resolve()
party = Path(sys.argv[2]).resolve()
final = Path(sys.argv[3]).resolve()

if final.exists():
    shutil.rmtree(final)
shutil.copytree(multi, final)

party_files = [
    "app/src/main/java/com/fush/erp/data/dao/GovernanceDao.kt",
    "app/src/main/java/com/fush/erp/data/dao/PartyDao.kt",
    "app/src/main/java/com/fush/erp/data/dao/PurchaseDaos.kt",
    "app/src/main/java/com/fush/erp/data/dao/SalesDaos.kt",
    "app/src/main/java/com/fush/erp/data/entity/PartyEntities.kt",
    "app/src/main/java/com/fush/erp/domain/AccountingService.kt",
    "app/src/main/java/com/fush/erp/domain/PurchaseService.kt",
    "app/src/main/java/com/fush/erp/domain/SalesService.kt",
    "app/src/main/java/com/fush/erp/ui/screens/AccountingScreens.kt",
    "app/src/main/java/com/fush/erp/ui/screens/HomeShell.kt",
    "app/src/main/java/com/fush/erp/ui/screens/PartyScreens.kt",
    "app/src/main/java/com/fush/erp/ui/screens/PurchaseScreens.kt",
    "app/src/main/java/com/fush/erp/ui/screens/SalesScreens.kt",
]
for rel in party_files:
    src = party / rel
    dst = final / rel
    if not src.is_file():
        raise RuntimeError(f"missing Party source file: {rel}")
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src, dst)

# Permanent side-by-side recovery build. It can live beside the old signed com.fush.erp app.
p = final / "app/build.gradle.kts"
s = p.read_text(encoding="utf-8")
s = s.replace('applicationId = "com.fush.erp"', 'applicationId = "com.fush.erp.preview"')
s = s.replace('versionCode = 63', 'versionCode = 64')
s = s.replace(
    'versionName = "0.15.4.24-phase14.5-multi-sample-quality"',
    'versionName = "0.15.4.25-combined-recovery-party-quality"',
)
p.write_text(s, encoding="utf-8")

# Union of the two legitimate schema-24 development branches -> schema 25.
p = final / "app/src/main/java/com/fush/erp/data/FushDatabase.kt"
s = p.read_text(encoding="utf-8")
s = s.replace('const val FUSH_DB_SCHEMA_VERSION = 24', 'const val FUSH_DB_SCHEMA_VERSION = 25')
s = s.replace(
    '        ProductionPlanMaterialEntity::class\n',
    '        ProductionPlanMaterialEntity::class,\n'
    '        PartyVoucherEntity::class,\n'
    '        PartyAttachmentEntity::class\n',
)
s = s.replace(
    '    abstract fun planningDao(): PlanningDao\n}',
    '    abstract fun planningDao(): PlanningDao\n'
    '    abstract fun partyDao(): PartyDao\n}',
)
p.write_text(s, encoding="utf-8")

# Keep multi-sample migration 23->24, then converge either known schema-24 shape into schema 25.
party_migrations = (party / "app/src/main/java/com/fush/erp/data/Migrations.kt").read_text(encoding="utf-8")
needle = 'val MIGRATION_23_24 = object : Migration(23, 24)'
idx = party_migrations.index(needle)
party_block = party_migrations[idx:].strip()
body = party_block.split('override fun migrate(db: SupportSQLiteDatabase) {', 1)[1]
body = body.rsplit('\n    }\n}', 1)[0]
quality_prefix = '''val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Schema 24 existed in two compatible branches. IF NOT EXISTS safely converges both:
        // original 14.5.24 already has quality_check_samples; Party preview already has party_*.
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS quality_check_samples (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                checkId INTEGER NOT NULL,
                sequenceNo INTEGER NOT NULL,
                measuredValue REAL NOT NULL,
                FOREIGN KEY(checkId) REFERENCES quality_checks(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_quality_check_samples_checkId ON quality_check_samples(checkId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_quality_check_samples_checkId_sequenceNo ON quality_check_samples(checkId, sequenceNo)")
'''
combined_migration = quality_prefix + body + '\n    }\n}\n'
p = final / "app/src/main/java/com/fush/erp/data/Migrations.kt"
s = p.read_text(encoding="utf-8").rstrip() + "\n\n" + combined_migration
p.write_text(s, encoding="utf-8")

p = final / "app/src/main/java/com/fush/erp/data/AppContainer.kt"
s = p.read_text(encoding="utf-8")
s = s.replace(
    'MIGRATION_22_23, MIGRATION_23_24).build()',
    'MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25).build()',
)
p.write_text(s, encoding="utf-8")

# Recovery package may import original FUSH backups, but normal package matching remains strict.
p = final / "app/src/main/java/com/fush/erp/backup/BackupRestoreManager.kt"
s = p.read_text(encoding="utf-8")
s = s.replace(
    'require(manifest.packageId == BuildConfig.APPLICATION_ID) { "هذه النسخة لا تخص تطبيق Fush ERP الحالي" }',
    'require(isCompatibleBackupPackage(manifest.packageId)) { "هذه النسخة لا تخص تطبيق Fush ERP الحالي" }',
)
s = s.replace(
    'require(manifest.packageId == BuildConfig.APPLICATION_ID) { "هذه النسخة لا تخص Fush ERP" }',
    'require(isCompatibleBackupPackage(manifest.packageId)) { "هذه النسخة لا تخص Fush ERP" }',
)
marker = '    fun consumeRestoreError(context: Context): String? {'
helper = '''    internal fun isCompatibleBackupPackage(packageId: String): Boolean =
        packageId == BuildConfig.APPLICATION_ID ||
            (BuildConfig.APPLICATION_ID == "com.fush.erp.preview" && packageId == "com.fush.erp")

'''
if marker not in s:
    raise RuntimeError("BackupRestoreManager insertion point not found")
s = s.replace(marker, helper + marker, 1)
p.write_text(s, encoding="utf-8")

(final / "PHASE14_5_25_SCOPE.md").write_text(
    """# Phase 14.5.25 — Combined Recovery + Party Subledger + Multi-sample Quality\n\n"
    "- Combines multi-sample quantitative quality checks with Party Subledger vouchers.\n"
    "- Room schema 25 non-destructively converges both known schema-24 branches.\n"
    "- Package com.fush.erp.preview accepts verified backups created by original com.fush.erp.\n"
    "- Restore still checks archive format, SHA-256, SQLite integrity and schema before staging.\n"
    "- The original signed FUSH app may remain installed while recovery is verified.\n",
    encoding="utf-8",
)

checks = {
    final / "app/build.gradle.kts": ["com.fush.erp.preview", "versionCode = 64", "0.15.4.25-combined-recovery-party-quality"],
    final / "app/src/main/java/com/fush/erp/data/FushDatabase.kt": ["FUSH_DB_SCHEMA_VERSION = 25", "QualityCheckSampleEntity::class", "PartyVoucherEntity::class", "partyDao()"],
    final / "app/src/main/java/com/fush/erp/data/Migrations.kt": ["MIGRATION_23_24", "MIGRATION_24_25", "quality_check_samples", "party_vouchers", "party_attachments"],
    final / "app/src/main/java/com/fush/erp/backup/BackupRestoreManager.kt": ["isCompatibleBackupPackage", 'packageId == "com.fush.erp"'],
}
for path, tokens in checks.items():
    text = path.read_text(encoding="utf-8")
    for token in tokens:
        if token not in text:
            raise RuntimeError(f"merge verification failed: {token} missing from {path}")

print("Combined Phase 14.5.25 recovery source prepared")
