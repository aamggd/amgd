# FUSH ERP Mobile — Accounting P1 Handoff

## Status
**VALIDATED / READY FOR INTEGRATION REVIEW — NOT MERGED**

## Branch / Phase
- Branch: `fush/accounting`
- Phase: **Accounting P1 — Journal Integrity**
- Plan scope: double-entry integrity, duplicate-posting prevention, and reversal instead of deletion for posted operations.

## Exact Central Baseline
- Source branch: `fush/integration-current`
- Exact Central HEAD: `2cb8da801fc54aec8c1f0d6a83588f097ca85117`
- Central source tree before P1: `7733c6570357eb813f7e05e5093752ea26788749`
- Application ID: `com.fush.erp.recovery`
- Central Room schema before P1: `34`

P1 was validated by checking out this exact Central commit separately and applying only the P1 patch to `central_source`. No old accounting source tree was used as the build base.

## Payload
- Patch: `fush_accounting/handoffs/P1_journal_integrity/ACCOUNTING_P1_JOURNAL_INTEGRITY.patch`
- Patch SHA-256: `6ef030b26c2ae27ca181bb7b78ddebf9de1ea1e5d0a4ae14e6dbf5c0c88b85ec`
- Changed Central application files: **5**

### Exact changed files
1. `app/src/main/java/com/fush/erp/data/AppContainer.kt`
2. `app/src/main/java/com/fush/erp/data/FushDatabase.kt`
3. `app/src/main/java/com/fush/erp/data/Migrations.kt`
4. `app/src/main/java/com/fush/erp/domain/AccountingP1IntegrityPolicy.kt` — new
5. `app/src/test/java/com/fush/erp/domain/AccountingP1IntegrityPolicyTest.kt` — new

## Business Logic
1. Existing `AccountingValidator` remains the application-level double-entry gate; P1 regression confirms balanced journals pass and unbalanced journals fail.
2. Duplicate-posting protection is enforced prospectively only for the 13 P0 events classified `STABLE_SOURCE`:
   - `CASH_COUNT_ADJUSTMENT`
   - `FX_REVALUATION`
   - `SALE`
   - `CUSTOMER_RECEIPT`
   - `SALES_RETURN`
   - `PURCHASE`
   - `PURCHASE_RETURN`
   - `SUPPLIER_PAYMENT`
   - `INVENTORY_COUNT`
   - `PRODUCTION_ISSUE`
   - `PRODUCTION_LABOR`
   - `PRODUCTION_RECEIPT`
   - `PRODUCTION_REJECT`
3. New POSTED journals for these stable events require a nonblank `sourceId`.
4. A second POSTED journal with the same stable `sourceType + sourceId` is rejected with `DUPLICATE_ACCOUNTING_POSTING`.
5. Existing historical duplicate rows are preserved; the migration does not delete or rewrite historical journals.
6. A POSTED journal header cannot be updated or deleted; correction must use reversal/new journal.
7. Lines belonging to a POSTED journal cannot be updated or deleted.
8. DB-level line sanity rejects negative debit/credit and a line that is both debit and credit.
9. Reversal remains a new independent balanced `REVERSAL` journal; the original POSTED journal remains intact.
10. Repeatable/manual/state-guarded events are intentionally excluded from the stable-source duplicate trigger so legitimate repeated events are not falsely collapsed.

## Room / Migration
- Central schema before phase: `34`
- Branch-local validation schema after phase: `35`
- Migration: `MIGRATION_34_35_ACCOUNTING_P1`
- Status of number: **PROVISIONAL / BRANCH ONLY** — integration branch owns final migration numbering.
- Migration behavior: trigger creation only; no table drop, no row deletion, no database recreation, no destructive fallback.
- `fallbackToDestructiveMigration`: **NOT PRESENT**.

## Impact Review
### Accounting
- Strengthens journal integrity and posted-record immutability.
- Prevents duplicate posting only where P0 proved source identity stable.
- Does not change account selection, debit/credit amounts, FX calculations, subledger balances, or posting profiles.

### Inventory
- Inventory quantity logic changed: **NO**.
- Stock movement calculation/valuation logic changed: **NO**.
- `INVENTORY_COUNT` accounting journal replay is protected; inventory quantities themselves are not mutated by P1.

### Production
- Production quantity/BOM/yield logic changed: **NO**.
- Original issue/labor/receipt/reject accounting events with stable source references gain replay protection.
- Repeatable production correction events remain outside this P1 stable-source trigger until they receive immutable event IDs.

## Validation
- GitHub Actions workflow: `.github/workflows/validate-accounting-p1-central-2cb8da-v2.yml`
- Successful Run ID: `31977295344`
- Validated workflow head: `aa8d96fcc402dbc37d38a78a3c1eac3a46bdf59a`
- Artifact ID: `9271482612`
- Artifact: `FushERP-Accounting-P1-Journal-Integrity-Validated-Handoff`
- Artifact digest: `sha256:4a11eaf2a9452ed4d16d8a03c1aa61b6adfe3ca2e56a43976b68dad982a45e9b`

### Gates
- Exact Central HEAD / source-tree guard: PASS
- Patch SHA / exact five-file allowlist: PASS
- P0 → P1 stable-source contract regression: PASS
- SQLite migration/data-preservation smoke: PASS
- Historical duplicate preservation: PASS
- New stable-source duplicate prevention: PASS
- Posted journal immutability: PASS
- Posted line immutability: PASS
- Reversal-as-new-entry smoke: PASS
- Double-entry validator regression: PASS
- Full Unit Tests (`:app:testDebugUnitTest`): **PASS**
- Release Build (`:app:assembleRelease`): **PASS**
- Room Schema 35 generation: **PASS**
- No destructive migration/fallback: **PASS**
- Zipalign: **PASS**
- Signing: **NOT PERFORMED**

## Artifact file SHA-256
- Patch: `6ef030b26c2ae27ca181bb7b78ddebf9de1ea1e5d0a4ae14e6dbf5c0c88b85ec`
- Exact Central diff: `88f148836fb02893af4f38e1cbfab7a20576d62f3bac61ecfc2482457ee26912`
- Aligned unsigned APK: `84c70db0f8813e37ca6da2bf4967762183f12f9245ca27189e62d82bfcab738e`
- Full P1-on-Central source ZIP: `9292f402d8344eec6a12f7a9c47c8935e955f6a8a78eab2ea48b96661cc20c9a`

## Known issues / intentional limits
1. P1 does **not** claim universal idempotency for every accounting source type. Events P0 classified `NEEDS_STABLE_EVENT_ID` or `STATE_GUARDED` are intentionally not included in the duplicate trigger.
2. Historical duplicate journals are preserved for audit/data safety; P1 prevents new duplicates prospectively.
3. Migration number `34 -> 35` is not final and must be remapped by integration if another accepted handoff consumes schema 35 first.
4. No device signing or real-device upgrade test was performed in this branch workflow; the produced APK is aligned unsigned.
5. P2 has **not** been started.

## Manual test steps
1. Start from a test database on Central schema 34 containing historical journal data and upgrade with the P1 migration; confirm all prior journals remain present.
2. Post one stable event such as a `SALE` with a fixed source ID; confirm one balanced journal is created.
3. Attempt to create another POSTED `SALE` journal with the same source ID; expect `DUPLICATE_ACCOUNTING_POSTING` and no second journal.
4. Attempt to post a stable event with blank source ID; expect `ACCOUNTING_STABLE_SOURCE_ID_REQUIRED`.
5. Attempt to update or delete the original POSTED journal; expect `POSTED_JOURNAL_IMMUTABLE_USE_REVERSAL`.
6. Attempt to update/delete one of its lines; expect `POSTED_JOURNAL_LINE_IMMUTABLE_USE_REVERSAL`.
7. Reverse the operation through the supported reversal flow; verify a new balanced reversal journal is created and the original journal remains unchanged.
8. Verify a repeatable/manual source is not falsely deduplicated by the stable-source trigger.
9. Re-run full Unit Tests and `assembleRelease` after integration applies the handoff.

## Integration instruction
Apply only the exact P1 patch to the then-current accepted Central baseline after dependency review. Reassign the migration number centrally if needed. Do not merge this specialized branch history wholesale and do not merge directly to `fush/main`.

`ACCOUNTING_P1_JOURNAL_INTEGRITY_VALIDATED_OK`
