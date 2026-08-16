#!/usr/bin/env python3
import argparse
import json
import re
import sys
from pathlib import Path


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8") if path.exists() else ""


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

    build = read(app / "build.gradle.kts")
    record("application_id", 'applicationId = "com.fush.erp.recovery"' in build,
           "Application ID must remain com.fush.erp.recovery")

    database = read(app / "src/main/java/com/fush/erp/data/FushDatabase.kt")
    m = re.search(r"FUSH_DB_SCHEMA_VERSION\s*=\s*(\d+)", database)
    schema = int(m.group(1)) if m else -1
    record("room_schema_at_least_35", schema >= 35, f"Detected Room schema {schema}")
    record("accounting_migration_registered",
           "MIGRATION_34_35_ACCOUNTING_P1" in database,
           "Accounting P1 migration must exist in current/future Central")

    for marker in [
        "ACCOUNTING_STABLE_SOURCE_ID_REQUIRED",
        "DUPLICATE_ACCOUNTING_POSTING",
        "POSTED_JOURNAL_IMMUTABLE_USE_REVERSAL",
        "POSTED_JOURNAL_LINE_IMMUTABLE_USE_REVERSAL",
        "INVALID_JOURNAL_LINE",
    ]:
        record(f"accounting_guard_{marker}", marker in database, marker)

    for source_type in [
        "SALE", "CUSTOMER_RECEIPT", "SALES_RETURN",
        "PURCHASE", "PURCHASE_RETURN", "SUPPLIER_PAYMENT",
    ]:
        record(f"stable_source_{source_type}", f"'{source_type}'" in database,
               f"{source_type} remains protected by Accounting P1")

    main_tree = app / "src/main/java"
    destructive = []
    for p in main_tree.rglob("*.kt") if main_tree.exists() else []:
        text = read(p)
        if "fallbackToDestructiveMigration" in text:
            destructive.append(str(p.relative_to(root)))
    record("no_destructive_migration", not destructive,
           "No fallbackToDestructiveMigration in application source" if not destructive else ", ".join(destructive))

    # Treasury P1: party identity policy + service boundary. It is allowed to be pending in preparation mode.
    treasury_policy = app / "src/main/java/com/fush/erp/domain/TreasuryPartyRequirementPolicy.kt"
    accounting_service = read(app / "src/main/java/com/fush/erp/domain/AccountingService.kt")
    treasury_ok = treasury_policy.exists() and "TreasuryPartyRequirementPolicy" in accounting_service
    record("treasury_p1_contract", treasury_ok,
           "party requirement policy wired into AccountingService",
           required=args.require_all)

    # Sales P1: persisted customer movements use real customer identity at service boundaries.
    customer_identity = app / "src/main/java/com/fush/erp/domain/CustomerMovementIdentity.kt"
    sales_service = read(app / "src/main/java/com/fush/erp/domain/SalesService.kt")
    sales_ok = customer_identity.exists() and sales_service.count("CustomerMovementIdentity.requireId") >= 2
    record("sales_p1_customer_identity", sales_ok,
           "CustomerMovementIdentity guard present at multiple SalesService posting/reversal boundaries",
           required=args.require_all)

    # Purchases P1: supplier-profile reconciliation plus supplier-consistent joins for payments/returns.
    supplier_math = app / "src/main/java/com/fush/erp/domain/SupplierProfileMath.kt"
    purchase_daos = read(app / "src/main/java/com/fush/erp/data/dao/PurchaseDaos.kt")
    purchase_ok = (
        supplier_math.exists()
        and "JOIN supplier_payments" in purchase_daos
        and purchase_daos.count("supplierId") > 20
        and "supplierVoucherAdjustmentAt" in purchase_daos
    )
    record("purchases_p1_supplier_integrity", purchase_ok,
           "supplier reconciliation math and supplier-consistent payment/return queries present",
           required=args.require_all)

    if args.require_all:
        record("all_wave1_p1_present", treasury_ok and sales_ok and purchase_ok,
               "Accounting + Treasury + Sales + Purchases P1 must coexist in final Central")

    failures = [r for r in results if r["status"] == "FAIL"]
    summary = {
        "mode": "FINAL_SOURCE_PREACCEPTANCE" if args.require_all else "PREPARATION",
        "roomSchema": schema,
        "results": results,
        "failed": len(failures),
        "note": "Even strict source PASS is not final QA acceptance until the exact merged Central APK is retested."
    }

    if args.json_out:
        out = Path(args.json_out)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(summary, indent=2), encoding="utf-8")

    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
