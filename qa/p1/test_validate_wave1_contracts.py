#!/usr/bin/env python3
import importlib.util
import unittest
from pathlib import Path


VALIDATOR_PATH = Path(__file__).with_name("validate_wave1_contracts.py")
SPEC = importlib.util.spec_from_file_location("wave1_validator", VALIDATOR_PATH)
VALIDATOR = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(VALIDATOR)


def guard_fixture(include_on_open: bool = True) -> str:
    on_open = (
        "        override fun onOpen(db: SupportSQLiteDatabase) {\n"
        "            install(db)\n"
        "        }\n"
        if include_on_open
        else ""
    )
    return (
        "internal object AccountingJournalIntegrityGuards {\n"
        "    private val stableSourcesSql = \"'SALE','CUSTOMER_RECEIPT','SALES_RETURN','PURCHASE','PURCHASE_RETURN','SUPPLIER_PAYMENT'\"\n"
        "    internal val triggerSql = listOf(\n"
        "        \"trg_journal_entries_closed_period BEFORE INSERT ON journal_entries FROM accounting_periods ap "
        "NEW.entryDate BETWEEN ap.startDate AND ap.endDate ap.status <> 'OPEN'\",\n"
        "        \"WHEN sourceType IN ($stableSourcesSql) ACCOUNTING_STABLE_SOURCE_ID_REQUIRED\",\n"
        "        \"WHEN sourceType IN ($stableSourcesSql) DUPLICATE_ACCOUNTING_POSTING\",\n"
        "        \"WHEN sourceType IN ($stableSourcesSql) POSTED_JOURNAL_IMMUTABLE_USE_REVERSAL\",\n"
        "        \"POSTED_JOURNAL_LINE_IMMUTABLE_USE_REVERSAL\",\n"
        "        \"INVALID_JOURNAL_LINE\"\n"
        "    )\n"
        "    fun install(db: SupportSQLiteDatabase) {\n"
        "        triggerSql.forEach(db::execSQL)\n"
        "    }\n"
        "    val callback: RoomDatabase.Callback = object : RoomDatabase.Callback() {\n"
        "        override fun onCreate(db: SupportSQLiteDatabase) {\n"
        "            install(db)\n"
        "        }\n"
        + on_open
        + "    }\n"
        "}\n"
    )


MIGRATION_WIRED = (
    "val MIGRATION_34_35_ACCOUNTING_P1 = object : Migration(34, 35) {\n"
    "    override fun migrate(db: SupportSQLiteDatabase) {\n"
    "        AccountingJournalIntegrityGuards.install(db)\n"
    "    }\n"
    "}\n"
)

APP_CONTAINER_WIRED = (
    "val db = Room.databaseBuilder(context, FushDatabase::class.java, \"fush_erp.db\")\n"
    "    .addMigrations(MIGRATION_34_35_ACCOUNTING_P1)\n"
    "    .addCallback(AccountingJournalIntegrityGuards.callback)\n"
    "    .build()\n"
)


class AeAcc011ValidatorRegressionTest(unittest.TestCase):
    def test_guards_may_live_outside_migrations_when_shared_installer_is_fully_wired(self):
        self.assertNotIn("ACCOUNTING_STABLE_SOURCE_ID_REQUIRED", MIGRATION_WIRED)
        self.assertNotIn("'SALE'", MIGRATION_WIRED)

        result = VALIDATOR.evaluate_ae_acc_011_wiring(
            guard_fixture(),
            MIGRATION_WIRED,
            APP_CONTAINER_WIRED,
        )

        self.assertTrue(result["migration_installs_guards"])
        self.assertTrue(result["migration_registered"])
        self.assertTrue(result["callback_wired"])
        self.assertTrue(result["on_create_installs"])
        self.assertTrue(result["on_open_installs"])
        self.assertTrue(result["closed_period_trigger"])
        self.assertTrue(all(result["markers"].values()))
        self.assertTrue(all(result["stable_sources"].values()))

    def test_marker_decoys_in_migrations_do_not_replace_real_wiring(self):
        decoy_migration = (
            "\n".join(VALIDATOR.REQUIRED_MARKERS)
            + "\n"
            + "\n".join(f"'{source}'" for source in VALIDATOR.REQUIRED_STABLE_SOURCES)
            + "\nval MIGRATION_34_35_ACCOUNTING_P1 = object : Migration(34, 35) {\n"
            "    override fun migrate(db: SupportSQLiteDatabase) {\n"
            "        // AccountingJournalIntegrityGuards.install(db)\n"
            "    }\n"
            "}\n"
        )
        app_without_callback = (
            "val db = Room.databaseBuilder(context, FushDatabase::class.java, \"fush_erp.db\")\n"
            "    .addMigrations(MIGRATION_34_35_ACCOUNTING_P1)\n"
            "    .build()\n"
        )

        result = VALIDATOR.evaluate_ae_acc_011_wiring(
            guard_fixture(),
            decoy_migration,
            app_without_callback,
        )

        self.assertFalse(result["migration_installs_guards"])
        self.assertFalse(result["callback_wired"])

    def test_fresh_room35_requires_both_on_create_and_on_open(self):
        result = VALIDATOR.evaluate_ae_acc_011_wiring(
            guard_fixture(include_on_open=False),
            MIGRATION_WIRED,
            APP_CONTAINER_WIRED,
        )

        self.assertTrue(result["on_create_installs"])
        self.assertFalse(result["on_open_installs"])

    def test_embedded_regression_guard_stays_green(self):
        self.assertTrue(VALIDATOR.ae_acc_011_layout_regression_self_test())


if __name__ == "__main__":
    unittest.main()
