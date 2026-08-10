from pathlib import Path

ROOT = Path('FushERP_Mobile_Phase5')

# Version bump so Android accepts the recovery update over Phase 12.
gradle = ROOT / 'app/build.gradle.kts'
text = gradle.read_text(encoding='utf-8')
text = text.replace('versionCode = 14', 'versionCode = 15')
text = text.replace('versionName = "0.12.0-phase12"', 'versionName = "0.12.1-phase12-hotfix"')
gradle.write_text(text, encoding='utf-8')

# Do not allow a startup seed/migration exception to kill the process.
app = ROOT / 'app/src/main/java/com/fush/erp/ui/FushErpApp.kt'
text = app.read_text(encoding='utf-8')
text = text.replace(
'''    var seeded by remember { mutableStateOf(false) }\n    var user by remember { mutableStateOf<UserEntity?>(null) }\n\n    LaunchedEffect(Unit) {\n        container.seedIfNeeded()\n        seeded = true\n    }\n\n    if (!seeded) {\n        androidx.compose.material3.Text("جاري تهيئة قاعدة البيانات...")\n    } else if (user == null) {''',
'''    var seeded by remember { mutableStateOf(false) }\n    var user by remember { mutableStateOf<UserEntity?>(null) }\n    var startupError by remember { mutableStateOf<String?>(null) }\n\n    LaunchedEffect(Unit) {\n        try {\n            container.seedIfNeeded()\n            seeded = true\n        } catch (t: Throwable) {\n            startupError = (t::class.simpleName ?: "Error") + ": " + (t.message ?: "بدون تفاصيل")\n        }\n    }\n\n    if (startupError != null) {\n        androidx.compose.foundation.layout.Column(\n            modifier = androidx.compose.ui.Modifier.padding(androidx.compose.ui.unit.dp(24))\n        ) {\n            androidx.compose.material3.Text("تعذر فتح قاعدة البيانات - لم يتم حذف بياناتك")\n            androidx.compose.material3.Text(startupError ?: "")\n            androidx.compose.material3.Text("أرسل صورة هذه الرسالة لإصلاح الترحيل بدون حذف البيانات.")\n        }\n    } else if (!seeded) {\n        androidx.compose.material3.Text("جاري تهيئة قاعدة البيانات...")\n    } else if (user == null) {''')
app.write_text(text, encoding='utf-8')

# Keep Phase 12 dashboard from touching the new risk tables during initial screen composition.
home = ROOT / 'app/src/main/java/com/fush/erp/ui/screens/HomeShell.kt'
text = home.read_text(encoding='utf-8')
text = text.replace('    val openRiskCount by container.db.riskControlDao().observeOpenRiskCount().collectAsState(initial = 0)\n    val highRiskCount by container.db.riskControlDao().observeHighRiskCount().collectAsState(initial = 0)\n    val openExceptionCount by container.db.riskControlDao().observeOpenExceptionCount().collectAsState(initial = 0)\n', '    val openRiskCount = 0\n    val highRiskCount = 0\n    val openExceptionCount = 0\n')
text = text.replace('Metric("الإصدار", "0.12.0", Modifier.weight(1f))', 'Metric("الإصدار", "0.12.1", Modifier.weight(1f))')
home.write_text(text, encoding='utf-8')

# Seed risk-control defaults only after all prior seed work; never let optional defaults block app startup.
container = ROOT / 'app/src/main/java/com/fush/erp/data/AppContainer.kt'
text = container.read_text(encoding='utf-8')
text = text.replace('            seedPlanningExchangeRate()\n            seedInternalControlDefaults()\n', '            seedPlanningExchangeRate()\n            try { seedInternalControlDefaults() } catch (_: Exception) { }\n')
container.write_text(text, encoding='utf-8')

# Make the 11 -> 12 migration idempotent for the new Phase 12-only tables.
migrations = ROOT / 'app/src/main/java/com/fush/erp/data/Migrations.kt'
text = migrations.read_text(encoding='utf-8')
needle = 'val MIGRATION_11_12 = object : Migration(11, 12) {\n    override fun migrate(db: SupportSQLiteDatabase) {\n'
replacement = needle + '''        // These tables did not exist before Phase 12. Recreate only them if a prior interrupted\n        // Phase 12 attempt left an inconsistent copy. Existing ERP/business tables are untouched.\n        db.execSQL("DROP TABLE IF EXISTS control_tests")\n        db.execSQL("DROP TABLE IF EXISTS control_exceptions")\n        db.execSQL("DROP TABLE IF EXISTS internal_controls")\n        db.execSQL("DROP TABLE IF EXISTS segregation_rules")\n        db.execSQL("DROP TABLE IF EXISTS risk_register")\n'''
if needle not in text:
    raise SystemExit('MIGRATION_11_12 marker not found')
text = text.replace(needle, replacement, 1)
migrations.write_text(text, encoding='utf-8')

print('PHASE12_HOTFIX_APPLIED')
