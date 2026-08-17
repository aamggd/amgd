#!/usr/bin/env python3
import argparse
import json
import re
import sys
from pathlib import Path


REQUIRED_MARKERS = [
    "ACCOUNTING_STABLE_SOURCE_ID_REQUIRED",
    "DUPLICATE_ACCOUNTING_POSTING",
    "POSTED_JOURNAL_IMMUTABLE_USE_REVERSAL",
    "POSTED_JOURNAL_LINE_IMMUTABLE_USE_REVERSAL",
    "INVALID_JOURNAL_LINE",
]

REQUIRED_STABLE_SOURCES = [
    "SALE",
    "CUSTOMER_RECEIPT",
    "SALES_RETURN",
    "PURCHASE",
    "PURCHASE_RETURN",
    "SUPPLIER_PAYMENT",
]


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8") if path.exists() else ""


def extract_braced_block(text: str, anchor: str) -> str:
    '''Return the anchor plus its first balanced {...} block, or an empty string.'''
    start = text.find(anchor)
    if start < 0:
        return ""
    open_brace = text.find("{", start)
    if open_brace < 0:
        return ""

    depth = 0
    for index in range(open_brace, len(text)):
        char = text[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return text[start:index + 1]
    return ""


def slice_between(text: str, start_anchor: str, end_anchor: str) -> str:
    start = text.find(start_anchor)
    if start < 0:
        return ""
    end = text.find(end_anchor, start + len(start_anchor))
    if end < 0:
        return ""
    return text[start:end]


def strip_kotlin_comments(text: str) -> str:
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.DOTALL)
    return re.sub(r"//[^\n]*", "", text)


def evaluate_ae_acc_011_wiring(guards: str, migrations: str, app_container: str) -> dict:
    '''Validate AE-ACC-011 by following the shared installer and actual Room wiring.'''
    stable_sources_section = slice_between(
        guards,
        "private val stableSourcesSql",
        "internal val triggerSql",
    )
    trigger_sql_section = slice_between(
        guards,
        "internal val triggerSql",
        "\n    fun install",
    )
    install_block = extract_braced_block(guards, "fun install(")
    callback_block = extract_braced_block(guards, "val callback: RoomDatabase.Callback")
    on_create_block = extract_braced_block(callback_block, "override fun onCreate")
    on_open_block = extract_braced_block(callback_block, "override fun onOpen")

    install_code = strip_kotlin_comments(install_block)
    migration_block = extract_braced_block(
        migrations,
        "val MIGRATION_34_35_ACCOUNTING_P1",
    )
    migration_code = strip_kotlin_comments(migration_block)

    builder_start = app_container.find("Room.databaseBuilder(")
    builder_end = app_container.find(".build()", builder_start) if builder_start >= 0 else -1
    room_builder_section = (
        app_container[builder_start:builder_end + len(".build()")]
        if builder_start >= 0 and builder_end >= 0
        else ""
    )
    room_builder_code = strip_kotlin_comments(room_builder_section)

    on_create_code = strip_kotlin_comments(on_create_block)
    on_open_code = strip_kotlin_comments(on_open_block)

    marker_results = {
        marker: marker in trigger_sql_section
        for marker in REQUIRED_MARKERS
    }
    stable_source_results = {
        source_type: f"'{source_type}'" in stable_sources_section
        for source_type in REQUIRED_STABLE_SOURCES
    }

    return {
        "guard_object": "internal object AccountingJournalIntegrityGuards" in guards,
        "install_executes_trigger_sql": (
            bool(install_block)
            and re.search(
                r"\btriggerSql\.forEach\s*\(\s*db::execSQL\s*\)",
                install_code,
            )
            is not None
        ),
        "migration_installs_guards": (
            bool(migration_block)
            and "object : Migration(34, 35)" in migration_code
            and re.search(
                r"\bAccountingJournalIntegrityGuards\.install\s*\(\s*db\s*\)",
                migration_code,
            )
            is not None
        ),
        "migration_registered": (
            re.search(r"\bMIGRATION_34_35_ACCOUNTING_P1\b", room_builder_code)
            is not None
        ),
        "callback_wired": (
            re.search(
                r"\.addCallback\s*\(\s*AccountingJournalIntegrityGuards\.callback\s*\)",
                room_builder_code,
            )
            is not None
        ),
        "on_create_installs": (
            bool(on_create_block)
            and re.search(r"\binstall\s*\(\s*db\s*\)", on_create_code) is not None
        ),
        "on_open_installs": (
            bool(on_open_block)
            and re.search(r"\binstall\s*\(\s*db\s*\)", on_open_code) is not None
        ),
        "markers": marker_results,
        "stable_sources": stable_source_results,
        "stable_sources_wired_into_triggers": (
            bool(stable_sources_section)
            and bool(trigger_sql_section)
            and trigger_sql_section.count("$stableSourcesSql") >= 3
        ),
        "closed_period_trigger": all(
            token in trigger_sql_section
            for token in [
                "trg_journal_entries_closed_period",
                "BEFORE INSERT ON journal_entries",
                "FROM accounting_periods ap",
                "NEW.entryDate BETWEEN ap.startDate AND ap.endDate",
                "ap.status <> 'OPEN'",
            ]
        ),
    }


def ae_acc_011_layout_regression_self_test() -> bool:
    '''Prevent regression to the obsolete assumption that guard DDL lives in Migrations.kt.'''
    guards = (
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
        "        override fun onOpen(db: SupportSQLiteDatabase) {\n"
        "            install(db)\n"
        "        }\n"
        "    }\n"
        "}\n"
    )
    migration = (
        "val MIGRATION_34_35_ACCOUNTING_P1 = object : Migration(34, 35) {\n"
        "    override fun migrate(db: SupportSQLiteDatabase) {\n"
        "        AccountingJournalIntegrityGuards.install(db)\n"
        "    }\n"
        "}\n"
    )
    app_container = (
        "val db = Room.databaseBuilder(context, FushDatabase::class.java, \"fush_erp.db\")\n"
        "    .addMigrations(MIGRATION_34_35_ACCOUNTING_P1)\n"
        "    .addCallback(AccountingJournalIntegrityGuards.callback)\n"
        "    .build()\n"
    )

    if any(marker in migration for marker in REQUIRED_MARKERS):
        return False
    if any(f"'{source}'" in migration for source in REQUIRED_STABLE_SOURCES):
        return False

    wired = evaluate_ae_acc_011_wiring(guards, migration, app_container)
    if not all(
        [
            wired["guard_object"],
            wired["install_executes_trigger_sql"],
            wired["migration_installs_guards"],
            wired["migration_registered"],
            wired["callback_wired"],
            wired["on_create_installs"],
            wired["on_open_installs"],
            all(wired["markers"].values()),
            all(wired["stable_sources"].values()),
            wired["stable_sources_wired_into_triggers"],
            wired["closed_period_trigger"],
        ]
    ):
        return False

    decoy_migration = (
        "\n".join(REQUIRED_MARKERS)
        + "\n"
        + "\n".join(f"'{source}'" for source in REQUIRED_STABLE_SOURCES)
        + "\nval MIGRATION_34_35_ACCOUNTING_P1 = object : Migration(34, 35) {\n"
        "    override fun migrate(db: SupportSQLiteDatabase) {\n"
        "        // AccountingJournalIntegrityGuards.install(db)\n"
        "    }\n"
        "}\n"
    )
    no_callback = (
        "val db = Room.databaseBuilder(context, FushDatabase::class.java, \"fush_erp.db\")\n"
        "    .addMigrations(MIGRATION_34_35_ACCOUNTING_P1)\n"
        "    .build()\n"
    )
    decoy = evaluate_ae_acc_011_wiring(guards, decoy_migration, no_callback)
    return not decoy["migration_installs_guards"] and not decoy["callback_wired"]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", required=True, help="Exported Central source root")
    parser.add_argument("--require-all", action="store_true", help="Fail unless all four P1 contracts are present")
    parser.add_argument("--json-out")
    args = parser.parse_args()

    root = Path(args.source)
    app = root / "app"
    results = []

    def record(name: str, ok: bool, detail: str, required: bool = True):
        status = "PASS" if ok else ("FAIL" if required else "PENDING")
        results.append({"name": name, "status": status, "detail": detail})
        print(f"{status:7} {name}: {detail}")
        return ok

    record(
        "qa_validator_ae_acc_011_layout_regression",
        ae_acc_011_layout_regression_self_test(),
        "Validator accepts guards outside Migrations.kt only when migration/callback/install wiring is real",
    )

    build = read(app / "build.gradle.kts")
    record(
        "application_id",
        'applicationId = "com.fush.erp.recovery"' in build,
        "Application ID must remain com.fush.erp.recovery",
    )

    data_dir = app / "src/main/java/com/fush/erp/data"
    database = read(data_dir / "FushDatabase.kt")
    migrations = read(data_dir / "Migrations.kt")
    app_container = read(data_dir / "AppContainer.kt")
    guards = read(data_dir / "AccountingJournalIntegrityGuards.kt")

    schema_match = re.search(r"FUSH_DB_SCHEMA_VERSION\s*=\s*(\d+)", database)
    schema = int(schema_match.group(1)) if schema_match else -1
    record("room_schema_at_least_35", schema >= 35, f"Detected Room schema {schema}")

    wiring = evaluate_ae_acc_011_wiring(guards, migrations, app_container)
    record(
        "accounting_guard_object",
        wiring["guard_object"],
        "Shared AccountingJournalIntegrityGuards object exists",
    )
    record(
        "accounting_install_executes_trigger_sql",
        wiring["install_executes_trigger_sql"],
        "Shared installer executes triggerSql against the Room database",
    )
    record(
        "accounting_migration_34_35_installs_guard_set",
        wiring["migration_installs_guards"],
        "MIGRATION_34_35_ACCOUNTING_P1 directly invokes AccountingJournalIntegrityGuards.install(db)",
    )
    record(
        "accounting_migration_registered",
        wiring["migration_registered"],
        "34→35 migration is registered on the actual Room.databaseBuilder chain",
    )
    record(
        "accounting_callback_wired_to_room_builder",
        wiring["callback_wired"],
        "AccountingJournalIntegrityGuards.callback is attached to the actual Room.databaseBuilder chain",
    )
    record(
        "accounting_callback_on_create_installs_guard_set",
        wiring["on_create_installs"],
        "Room callback onCreate installs the shared guard set for fresh Room35 databases",
    )
    record(
        "accounting_callback_on_open_installs_guard_set",
        wiring["on_open_installs"],
        "Room callback onOpen re-installs the idempotent shared guard set",
    )

    for marker in REQUIRED_MARKERS:
        record(
            f"accounting_guard_{marker}",
            wiring["markers"][marker],
            f"{marker} is emitted by AccountingJournalIntegrityGuards.triggerSql",
        )

    for source_type in REQUIRED_STABLE_SOURCES:
        record(
            f"stable_source_{source_type}",
            wiring["stable_sources"][source_type],
            f"{source_type} is listed in AccountingJournalIntegrityGuards.stableSourcesSql",
        )

    record(
        "stable_sources_wired_into_trigger_sql",
        wiring["stable_sources_wired_into_triggers"],
        "Shared stableSourcesSql is referenced by the stable-source trigger definitions",
    )
    record(
        "accounting_closed_period_trigger",
        wiring["closed_period_trigger"],
        "Shared triggerSql contains the closed-period POSTING guard tied to accounting_periods",
    )

    main_tree = app / "src/main/java"
    destructive = []
    for path in main_tree.rglob("*.kt") if main_tree.exists() else []:
        text = read(path)
        if "fallbackToDestructiveMigration" in text:
            destructive.append(str(path.relative_to(root)))
    record(
        "no_destructive_migration",
        not destructive,
        "No fallbackToDestructiveMigration in application source"
        if not destructive
        else ", ".join(destructive),
    )

    accounting_ok = all(
        [
            wiring["guard_object"],
            wiring["install_executes_trigger_sql"],
            wiring["migration_installs_guards"],
            wiring["migration_registered"],
            wiring["callback_wired"],
            wiring["on_create_installs"],
            wiring["on_open_installs"],
            all(wiring["markers"].values()),
            all(wiring["stable_sources"].values()),
            wiring["stable_sources_wired_into_triggers"],
            wiring["closed_period_trigger"],
        ]
    )

    treasury_policy = app / "src/main/java/com/fush/erp/domain/TreasuryPartyRequirementPolicy.kt"
    accounting_service = read(app / "src/main/java/com/fush/erp/domain/AccountingService.kt")
    treasury_ok = treasury_policy.exists() and "TreasuryPartyRequirementPolicy" in accounting_service
    record(
        "treasury_p1_contract",
        treasury_ok,
        "party requirement policy wired into AccountingService",
        required=args.require_all,
    )

    customer_identity = app / "src/main/java/com/fush/erp/domain/CustomerMovementIdentity.kt"
    sales_service = read(app / "src/main/java/com/fush/erp/domain/SalesService.kt")
    sales_ok = customer_identity.exists() and sales_service.count("CustomerMovementIdentity.requireId") >= 2
    record(
        "sales_p1_customer_identity",
        sales_ok,
        "CustomerMovementIdentity guard present at multiple SalesService posting/reversal boundaries",
        required=args.require_all,
    )

    supplier_math = app / "src/main/java/com/fush/erp/domain/SupplierProfileMath.kt"
    purchase_daos = read(app / "src/main/java/com/fush/erp/data/dao/PurchaseDaos.kt")
    purchase_ok = (
        supplier_math.exists()
        and "JOIN supplier_payments" in purchase_daos
        and purchase_daos.count("supplierId") > 20
        and "supplierVoucherAdjustmentAt" in purchase_daos
    )
    record(
        "purchases_p1_supplier_integrity",
        purchase_ok,
        "supplier reconciliation math and supplier-consistent payment/return queries present",
        required=args.require_all,
    )

    if args.require_all:
        record(
            "all_wave1_p1_present",
            accounting_ok and treasury_ok and sales_ok and purchase_ok,
            "Accounting + Treasury + Sales + Purchases P1 must coexist and be wired in final Central",
        )

    failures = [result for result in results if result["status"] == "FAIL"]
    summary = {
        "mode": "FINAL_SOURCE_PREACCEPTANCE" if args.require_all else "PREPARATION",
        "roomSchema": schema,
        "results": results,
        "failed": len(failures),
        "note": (
            "AE-ACC-011 is validated through the shared guard installer and its migration/fresh-DB wiring. "
            "Strict source PASS is still not final QA acceptance until exact Central APK/device gates pass."
        ),
    }

    if args.json_out:
        out = Path(args.json_out)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(summary, indent=2), encoding="utf-8")

    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
