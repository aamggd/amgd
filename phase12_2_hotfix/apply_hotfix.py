from pathlib import Path

ROOT = Path('FushERP_Mobile_Phase5')

# Phase 12.2: preserve existing currency parent rows referenced by business data.
# SQLite INSERT OR REPLACE deletes the conflicting parent row before inserting it again,
# which trips RESTRICT foreign keys once invoices/transactions reference currencies.

gradle = ROOT / 'app/build.gradle.kts'
text = gradle.read_text(encoding='utf-8')
text = text.replace('versionCode = 15', 'versionCode = 16')
text = text.replace('versionName = "0.12.1-phase12-hotfix"', 'versionName = "0.12.2-phase12-fk-hotfix"')
if 'versionCode = 16' not in text or '0.12.2-phase12-fk-hotfix' not in text:
    raise SystemExit('Phase 12.2 version bump failed')
gradle.write_text(text, encoding='utf-8')

# Currency defaults are seed data, not an update operation. Never REPLACE an existing
# currency row because many ERP tables reference currencies(code) with FK RESTRICT.
dao = ROOT / 'app/src/main/java/com/fush/erp/data/dao/Daos.kt'
text = dao.read_text(encoding='utf-8')
old = '''    @Insert(onConflict = OnConflictStrategy.REPLACE)\n    suspend fun upsertAll(rows: List<CurrencyEntity>)\n'''
new = '''    @Insert(onConflict = OnConflictStrategy.IGNORE)\n    suspend fun insertDefaultsIgnore(rows: List<CurrencyEntity>)\n'''
if old not in text:
    raise SystemExit('CurrencyDao REPLACE seed marker not found')
text = text.replace(old, new, 1)
dao.write_text(text, encoding='utf-8')

app = ROOT / 'app/src/main/java/com/fush/erp/data/AppContainer.kt'
text = app.read_text(encoding='utf-8')
old = '            db.currencyDao().upsertAll(\n'
new = '            db.currencyDao().insertDefaultsIgnore(\n'
if old not in text:
    raise SystemExit('AppContainer currency seed call not found')
text = text.replace(old, new, 1)
app.write_text(text, encoding='utf-8')

# Keep the visible version marker aligned with the installed build.
home = ROOT / 'app/src/main/java/com/fush/erp/ui/screens/HomeShell.kt'
text = home.read_text(encoding='utf-8')
text = text.replace('Metric("الإصدار", "0.12.1", Modifier.weight(1f))', 'Metric("الإصدار", "0.12.2", Modifier.weight(1f))')
home.write_text(text, encoding='utf-8')

# Static regression guards: seed path must not REPLACE currencies anymore.
check = dao.read_text(encoding='utf-8')
if 'suspend fun insertDefaultsIgnore(rows: List<CurrencyEntity>)' not in check:
    raise SystemExit('Currency safe seed method missing')
if 'suspend fun upsertAll(rows: List<CurrencyEntity>)' in check:
    raise SystemExit('Unsafe currency REPLACE seed method still present')

print('PHASE12_2_FK_HOTFIX_APPLIED')
