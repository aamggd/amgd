# FUSH ERP Mobile — Final Part2C Wave1 AE-ACC-011

## Status
**READY FOR AUDIT RE-INTEGRATION**

**Final integrated audit status: NOT FINAL PASS**

## Official Defect Scope
This handoff addresses the Final Part2C Wave1 definition of `AE-ACC-011` only:

1. Fresh Room35 journal-integrity protection parity with databases upgraded through `34 -> 35`.
2. Closed-period enforcement parity.
3. POSTED journal header/line immutability parity.
4. Stable-source-id and duplicate-posting protection parity.
5. Independent service-level posting-period guards before persistent writes for:
   - `SALE`
   - `CUSTOMER_RECEIPT`
   - `SALES_RETURN`
   - `PURCHASE`
   - `SUPPLIER_PAYMENT`
   - `PURCHASE_RETURN`

The earlier journal-posting atomicity work is retained separately and is **not** part of this official `AE-ACC-011` patch.

## Exact Central Baseline
- Repository: `aamggd/amgd`
- Central branch: `fush/integration-current`
- Exact Central HEAD: `ddae764a236cb1916a46e194ccb2d3c24ca6b919`
- Exact Central source tree: `30b028b75d6463c07afd0419429f53c7937fabb1`
- Room schema: `35`
- Application ID: `com.fush.erp.recovery`

## Functional Fix
### Shared integrity guard installer
A single idempotent DDL-only `AccountingJournalIntegrityGuards` installer contains the journal guards required by Wave1:

- `trg_journal_entries_closed_period`
- `trg_journal_stable_source_id_required_insert`
- `trg_journal_no_duplicate_stable_source_insert`
- `trg_journal_no_duplicate_stable_source_update`
- `trg_posted_journal_no_update`
- `trg_posted_journal_no_delete`
- `trg_journal_line_sanity_insert`
- `trg_posted_journal_line_no_update`
- `trg_posted_journal_line_no_delete`

The same installer is used by both paths:

- Fresh Room35: `RoomDatabase.Callback.onCreate/onOpen` from `AppContainer`.
- Upgrade to Room35: `MIGRATION_34_35_ACCOUNTING_P1` calls the same installer.

The installer executes `CREATE TRIGGER IF NOT EXISTS` statements only. It does not delete, update, rewrite, reset, recreate, or transform existing user rows.

### Independent closed-period guards
Before the first persistent write in each operational posting path:

- `SalesService.postSale()` validates `invoiceDate`.
- `SalesService.postReceiptAllocationsInternal()` validates `receiptDate`.
- `SalesService.postReturn()` validates `returnDate`.
- `PurchaseService.postPurchase()` validates `invoiceDate`.
- `PurchaseService.postSupplierPaymentAllocationsInternal()` validates `paymentDate`.
- `PurchaseService.postPurchaseReturn()` validates `returnDate`.

All use the existing `AccountingService.requirePostingPeriodOpen(...)` rule.

## Exact Functional Changed-File Allowlist
1. `central_source/app/src/main/java/com/fush/erp/data/AccountingJournalIntegrityGuards.kt` — new
2. `central_source/app/src/main/java/com/fush/erp/data/AppContainer.kt`
3. `central_source/app/src/main/java/com/fush/erp/data/Migrations.kt`
4. `central_source/app/src/main/java/com/fush/erp/domain/SalesService.kt`
5. `central_source/app/src/main/java/com/fush/erp/domain/PurchaseService.kt`
6. `central_source/app/src/test/java/com/fush/erp/data/AccountingJournalIntegrityGuardsTest.kt` — new

No `FushDatabase.kt` change is included. No Room version increase is included.

## Full Room34/Room35 Parity Validation
The final v2 validation constructs complete SQLite databases from the exact Room exported schemas:

- `app/schemas/com.fush.erp.data.FushDatabase/35.json` for the direct Fresh Room35 path.
- `app/schemas/com.fush.erp.data.FushDatabase/34.json` for the pre-upgrade path.

Each schema contains **97 Room entities**. The test creates all exported tables and indices and executes the exported Room setup queries before testing the guard installation path.

### Fresh Room35
Before the new callback installer, the direct Room35 exported schema contains no target journal-integrity triggers. After the shared installer is invoked, all nine required triggers exist and the behavioral matrix passes.

### 34 -> 35 upgraded database
The complete Room34 schema is created, representative existing POSTED journal header/line data is inserted, then the exact shared `34 -> 35` guard installer is executed. The pre-existing journal header and line are unchanged after installation, and the same protection matrix passes.

## Acceptance Gates
- Exact Central HEAD guard: **PASS**
- Exact Central source-tree guard: **PASS**
- Fresh Room35 full exported-schema protection parity: **PASS**
- 34→35 full exported-schema upgraded DB parity: **PASS**
- Existing user journal data preserved: **PASS**
- Fresh Room35 closed-period enforcement for all six operational source types: **PASS**
- Upgraded Room35 closed-period enforcement for all six operational source types: **PASS**
- Blank stable-source-id protection: **PASS**
- Duplicate stable-source protection: **PASS**
- POSTED journal header UPDATE blocked: **PASS**
- POSTED journal header DELETE blocked: **PASS**
- POSTED journal line UPDATE blocked: **PASS**
- POSTED journal line DELETE blocked: **PASS**
- POSTED immutability Fresh/Upgrade parity: **PASS**
- Six service-level closed-period guards occur before first persistent write: **PASS**
- Targeted accounting/integrity unit tests: **PASS**
- Full Unit (`:app:testDebugUnitTest`): **PASS**
- `assembleRelease`: **PASS**
- Release APK zipalign: **PASS**
- Room schema remains `35`: **PASS**
- Application ID remains `com.fush.erp.recovery`: **PASS**
- New 35→x migration introduced: **NO**
- Destructive migration: **NONE**
- `fallbackToDestructiveMigration`: **NONE**
- P2 started: **NO**

## Exact Functional Patch
- Generated against the exact Central baseline above.
- SHA-256: `317053a67f092caef5dbcbe3646e805a887fc9158d6a12f5eaaeb59980cef169`
- Patch name in validation artifact: `ACCOUNTING_AE_ACC_011_FRESH_ROOM35_PARITY.patch`
- Deterministic applicator: `fush_accounting/handoffs/AE-ACC-011_fresh_room35_parity/apply_ae_acc_011_fresh_room35_parity.py`

The same SHA was independently recalculated from the downloaded v2 artifact after the successful run.

## Validation Evidence
- Final validation workflow: `.github/workflows/validate-accounting-ae-acc-011-fresh-room35-parity-v2.yml`
- Successful Run ID: `31984673212`
- Validated workflow HEAD: `5983fb5ca5509dd4a6021866afd4dd63fe67d5a5`
- Artifact: `FushERP-Accounting-AE-ACC-011-Full-Room35-Ready-For-Audit-Reintegration-v2`
- Artifact ID: `9273481665`
- Artifact ZIP digest: `sha256:ebb6b01734f37ffe1d226f46a3ffada0fe6809e2eabf767d50cd6242b152688f`

## Atomicity Work Classification
The prior atomicity patch is deliberately excluded from the official patch above.

- Atomicity patch SHA-256: `353a1a82c1221e9bb9cc6b9767848cae4bd4e5654719c8108c7b68c5df0a7d44`
- Classification: **SEPARATE IMPROVEMENT — NOT AE-ACC-011 CLOSURE**

No atomicity work was deleted.

## Integration Boundary
This is an **Exact functional patch handoff only**.

- Do not Full Merge `fush/accounting`.
- Do not push this branch to `fush/integration-current`.
- Do not push this branch to `fush/main`.
- The Central/audit owner must apply only the official exact functional patch to the accepted Central baseline, rerun the Final Part2C Wave1 integrated audit, and decide Final PASS/FAIL.

**READY FOR AUDIT RE-INTEGRATION**
