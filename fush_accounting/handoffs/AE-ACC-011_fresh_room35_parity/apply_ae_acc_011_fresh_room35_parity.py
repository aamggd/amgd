from pathlib import Path
import sys

ROOT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("central_source")
DATA = ROOT / "app/src/main/java/com/fush/erp/data"
DOMAIN = ROOT / "app/src/main/java/com/fush/erp/domain"
TEST = ROOT / "app/src/test/java/com/fush/erp/data/AccountingJournalIntegrityGuardsTest.kt"

GUARDS = DATA / "AccountingJournalIntegrityGuards.kt"
APP = DATA / "AppContainer.kt"
MIGRATIONS = DATA / "Migrations.kt"
SALES = DOMAIN / "SalesService.kt"
PURCHASE = DOMAIN / "PurchaseService.kt"

if GUARDS.exists():
    raise SystemExit(f"Guard file already exists: {GUARDS}")

GUARDS.write_text(r'''package com.fush.erp.data

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * AE-ACC-011: one idempotent journal-integrity guard set for both fresh Room 35
 * databases and databases upgraded through 34 -> 35.
 *
 * This installer is DDL-only: it creates triggers and never mutates user rows.
 */
internal object AccountingJournalIntegrityGuards {
    private val stableSourcesSql = """
        'CASH_COUNT_ADJUSTMENT',
        'FX_REVALUATION',
        'SALE',
        'CUSTOMER_RECEIPT',
        'SALES_RETURN',
        'PURCHASE',
        'PURCHASE_RETURN',
        'SUPPLIER_PAYMENT',
        'INVENTORY_COUNT',
        'PRODUCTION_ISSUE',
        'PRODUCTION_LABOR',
        'PRODUCTION_RECEIPT',
        'PRODUCTION_REJECT'
    """.trimIndent()

    internal val triggerSql: List<String>
        get() = listOf(
            """
                CREATE TRIGGER IF NOT EXISTS trg_journal_entries_closed_period
                BEFORE INSERT ON journal_entries
                WHEN EXISTS (
                    SELECT 1
                    FROM accounting_periods ap
                    WHERE NEW.entryDate BETWEEN ap.startDate AND ap.endDate
                      AND ap.status <> 'OPEN'
                )
                BEGIN
                    SELECT RAISE(ABORT, 'الفترة المحاسبية مقفلة');
                END
            """.trimIndent(),
            """
                CREATE TRIGGER IF NOT EXISTS trg_journal_stable_source_id_required_insert
                BEFORE INSERT ON journal_entries
                WHEN NEW.status = 'POSTED'
                  AND NEW.sourceType IN ($stableSourcesSql)
                  AND (NEW.sourceId IS NULL OR TRIM(NEW.sourceId) = '')
                BEGIN
                    SELECT RAISE(ABORT, 'ACCOUNTING_STABLE_SOURCE_ID_REQUIRED');
                END
            """.trimIndent(),
            """
                CREATE TRIGGER IF NOT EXISTS trg_journal_no_duplicate_stable_source_insert
                BEFORE INSERT ON journal_entries
                WHEN NEW.status = 'POSTED'
                  AND NEW.sourceType IN ($stableSourcesSql)
                  AND NEW.sourceId IS NOT NULL
                  AND TRIM(NEW.sourceId) <> ''
                  AND EXISTS (
                      SELECT 1 FROM journal_entries existing
                      WHERE existing.status = 'POSTED'
                        AND existing.sourceType = NEW.sourceType
                        AND existing.sourceId = NEW.sourceId
                  )
                BEGIN
                    SELECT RAISE(ABORT, 'DUPLICATE_ACCOUNTING_POSTING');
                END
            """.trimIndent(),
            """
                CREATE TRIGGER IF NOT EXISTS trg_journal_no_duplicate_stable_source_update
                BEFORE UPDATE OF status, sourceType, sourceId ON journal_entries
                WHEN OLD.status <> 'POSTED'
                  AND NEW.status = 'POSTED'
                  AND NEW.sourceType IN ($stableSourcesSql)
                  AND NEW.sourceId IS NOT NULL
                  AND TRIM(NEW.sourceId) <> ''
                  AND EXISTS (
                      SELECT 1 FROM journal_entries existing
                      WHERE existing.id <> NEW.id
                        AND existing.status = 'POSTED'
                        AND existing.sourceType = NEW.sourceType
                        AND existing.sourceId = NEW.sourceId
                  )
                BEGIN
                    SELECT RAISE(ABORT, 'DUPLICATE_ACCOUNTING_POSTING');
                END
            """.trimIndent(),
            """
                CREATE TRIGGER IF NOT EXISTS trg_posted_journal_no_update
                BEFORE UPDATE ON journal_entries
                WHEN OLD.status = 'POSTED'
                BEGIN
                    SELECT RAISE(ABORT, 'POSTED_JOURNAL_IMMUTABLE_USE_REVERSAL');
                END
            """.trimIndent(),
            """
                CREATE TRIGGER IF NOT EXISTS trg_posted_journal_no_delete
                BEFORE DELETE ON journal_entries
                WHEN OLD.status = 'POSTED'
                BEGIN
                    SELECT RAISE(ABORT, 'POSTED_JOURNAL_IMMUTABLE_USE_REVERSAL');
                END
            """.trimIndent(),
            """
                CREATE TRIGGER IF NOT EXISTS trg_journal_line_sanity_insert
                BEFORE INSERT ON journal_lines
                WHEN NEW.debit < 0 OR NEW.credit < 0 OR (NEW.debit > 0 AND NEW.credit > 0)
                BEGIN
                    SELECT RAISE(ABORT, 'INVALID_JOURNAL_LINE');
                END
            """.trimIndent(),
            """
                CREATE TRIGGER IF NOT EXISTS trg_posted_journal_line_no_update
                BEFORE UPDATE ON journal_lines
                WHEN EXISTS (
                    SELECT 1 FROM journal_entries je
                    WHERE je.id = OLD.entryId AND je.status = 'POSTED'
                )
                BEGIN
                    SELECT RAISE(ABORT, 'POSTED_JOURNAL_LINE_IMMUTABLE_USE_REVERSAL');
                END
            """.trimIndent(),
            """
                CREATE TRIGGER IF NOT EXISTS trg_posted_journal_line_no_delete
                BEFORE DELETE ON journal_lines
                WHEN EXISTS (
                    SELECT 1 FROM journal_entries je
                    WHERE je.id = OLD.entryId AND je.status = 'POSTED'
                )
                BEGIN
                    SELECT RAISE(ABORT, 'POSTED_JOURNAL_LINE_IMMUTABLE_USE_REVERSAL');
                END
            """.trimIndent()
        )

    fun install(db: SupportSQLiteDatabase) {
        triggerSql.forEach(db::execSQL)
    }

    val callback: RoomDatabase.Callback = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            install(db)
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            install(db)
        }
    }
}
''', encoding="utf-8")

# Fresh Room35: install the exact guard set during create/open, before normal use.
app = APP.read_text(encoding="utf-8")
needle = "MIGRATION_34_35_ACCOUNTING_P1).build()"
replacement = "MIGRATION_34_35_ACCOUNTING_P1)\n    .addCallback(AccountingJournalIntegrityGuards.callback)\n    .build()"
if app.count(needle) != 1:
    raise SystemExit("AppContainer Room builder anchor mismatch")
APP.write_text(app.replace(needle, replacement, 1), encoding="utf-8")

# 34->35 upgraded DB: use the same exact idempotent guard installer. No row DML.
migrations = MIGRATIONS.read_text(encoding="utf-8")
marker = "val MIGRATION_34_35_ACCOUNTING_P1 = object : Migration(34, 35) {"
pos = migrations.find(marker)
if pos < 0 or migrations.find(marker, pos + 1) >= 0:
    raise SystemExit("MIGRATION_34_35 marker mismatch")
new_tail = '''val MIGRATION_34_35_ACCOUNTING_P1 = object : Migration(34, 35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // AE-ACC-011: use the same DDL-only guards as a fresh Room 35 database.
        // Existing user rows are untouched; only idempotent triggers are created.
        AccountingJournalIntegrityGuards.install(db)
    }
}
'''
MIGRATIONS.write_text(migrations[:pos] + new_tail, encoding="utf-8")

# Independent service-level closed-period guards for the six Wave1 operational paths.
sales = SALES.read_text(encoding="utf-8")
repls = [
    (
        "        db.requireUserPermission(request.createdBy, SecurityPermissions.SALES_POST)\n        require(request.paymentType in setOf(\"CASH\", \"CREDIT\"))",
        "        db.requireUserPermission(request.createdBy, SecurityPermissions.SALES_POST)\n        AccountingService(db).requirePostingPeriodOpen(request.invoiceDate)\n        require(request.paymentType in setOf(\"CASH\", \"CREDIT\"))",
    ),
    (
        "        db.requireUserPermission(createdBy, SecurityPermissions.COLLECTION_POST)\n        SalesMath.validateExchangeRate(exchangeRate)",
        "        db.requireUserPermission(createdBy, SecurityPermissions.COLLECTION_POST)\n        AccountingService(db).requirePostingPeriodOpen(receiptDate)\n        SalesMath.validateExchangeRate(exchangeRate)",
    ),
    (
        "        db.requireUserPermission(createdBy, SecurityPermissions.SALES_RETURN)\n        require(settlementType in setOf(\"CUSTOMER_CREDIT\", \"CASH_REFUND\"))",
        "        db.requireUserPermission(createdBy, SecurityPermissions.SALES_RETURN)\n        AccountingService(db).requirePostingPeriodOpen(returnDate)\n        require(settlementType in setOf(\"CUSTOMER_CREDIT\", \"CASH_REFUND\"))",
    ),
]
for old, new in repls:
    if sales.count(old) != 1:
        raise SystemExit(f"SalesService anchor mismatch: {old[:80]}")
    sales = sales.replace(old, new, 1)
SALES.write_text(sales, encoding="utf-8")

purchase = PURCHASE.read_text(encoding="utf-8")
repls = [
    (
        "        db.requireUserPermission(request.createdBy, SecurityPermissions.PURCHASE_POST)\n        require(request.paymentType in setOf(\"CASH\", \"CREDIT\"))",
        "        db.requireUserPermission(request.createdBy, SecurityPermissions.PURCHASE_POST)\n        AccountingService(db).requirePostingPeriodOpen(request.invoiceDate)\n        require(request.paymentType in setOf(\"CASH\", \"CREDIT\"))",
    ),
    (
        "        db.requireUserPermission(request.createdBy, SecurityPermissions.PURCHASE_RETURN)\n        require(request.settlementType in setOf(\"SUPPLIER_CREDIT\", \"CASH_REFUND\"))",
        "        db.requireUserPermission(request.createdBy, SecurityPermissions.PURCHASE_RETURN)\n        AccountingService(db).requirePostingPeriodOpen(request.returnDate)\n        require(request.settlementType in setOf(\"SUPPLIER_CREDIT\", \"CASH_REFUND\"))",
    ),
    (
        "        db.requireUserPermission(createdBy, SecurityPermissions.SUPPLIER_PAYMENT_POST)\n        require(allocations.isNotEmpty())",
        "        db.requireUserPermission(createdBy, SecurityPermissions.SUPPLIER_PAYMENT_POST)\n        AccountingService(db).requirePostingPeriodOpen(paymentDate)\n        require(allocations.isNotEmpty())",
    ),
]
for old, new in repls:
    if purchase.count(old) != 1:
        raise SystemExit(f"PurchaseService anchor mismatch: {old[:80]}")
    purchase = purchase.replace(old, new, 1)
PURCHASE.write_text(purchase, encoding="utf-8")

TEST.parent.mkdir(parents=True, exist_ok=True)
if TEST.exists():
    raise SystemExit(f"Test already exists: {TEST}")
TEST.write_text(r'''package com.fush.erp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountingJournalIntegrityGuardsTest {
    @Test
    fun freshAndUpgradeInstallerContainsCompleteWave1ProtectionSet() {
        val sql = AccountingJournalIntegrityGuards.triggerSql.joinToString("\n")
        val requiredTriggers = setOf(
            "trg_journal_entries_closed_period",
            "trg_journal_stable_source_id_required_insert",
            "trg_journal_no_duplicate_stable_source_insert",
            "trg_journal_no_duplicate_stable_source_update",
            "trg_posted_journal_no_update",
            "trg_posted_journal_no_delete",
            "trg_journal_line_sanity_insert",
            "trg_posted_journal_line_no_update",
            "trg_posted_journal_line_no_delete"
        )
        requiredTriggers.forEach { assertTrue("Missing $it", sql.contains(it)) }
        assertEquals(9, AccountingJournalIntegrityGuards.triggerSql.size)
    }

    @Test
    fun stableOperationalSourcesAreProtected() {
        val sql = AccountingJournalIntegrityGuards.triggerSql.joinToString("\n")
        setOf("SALE", "CUSTOMER_RECEIPT", "SALES_RETURN", "PURCHASE", "SUPPLIER_PAYMENT", "PURCHASE_RETURN")
            .forEach { assertTrue("Missing stable source $it", sql.contains("'$it'")) }
    }

    @Test
    fun installerIsDdlOnlyAndCannotDeleteExistingUserData() {
        AccountingJournalIntegrityGuards.triggerSql.forEach { statement ->
            val normalized = statement.trimStart().uppercase()
            assertTrue(normalized.startsWith("CREATE TRIGGER IF NOT EXISTS"))
            assertFalse(normalized.startsWith("DELETE "))
            assertFalse(normalized.startsWith("UPDATE "))
            assertFalse(normalized.startsWith("DROP "))
            assertFalse(normalized.startsWith("ALTER "))
        }
    }
}
''', encoding="utf-8")

print("AE_ACC_011_FRESH_ROOM35_PARITY_APPLIED")
