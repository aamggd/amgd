# FUSH ERP Mobile — Accounting Branch P0 Handoff

## المرحلة
**P0 — تثبيت عقد التكامل المحاسبي**

حسب خطة `fush/accounting`: تعريف الأحداث التي تنشئ قيودًا، ومفاتيح المصدر والمرجع، قبل الانتقال إلى سلامة القيود/منع التكرار في P1.

## Baseline
- Central Baseline: **Phase 14.5.54 Printing Integrated**
- Source branch used for validation: `fush/integration-printing-14.5.54`
- Central validation run: `31909754750`
- Central source ZIP SHA-256: `8adc047bc38703fcb460344b4ddd2c7f18f0c47c3ca1a6c62a51d3ef590450a1`
- Application ID: `com.fush.erp.recovery`
- Central Room schema: `34`

## Commit SHAs
- Validated P0 workflow/code head: `09764d6b74b2d0cb4e122694f91ac500c5202fbb`
- Validation record commit: `b4b1138498c7cc1f7593fc6b646dd7574585e556`
- The branch head after adding this handoff document is the commit returned by this handoff commit.

## الملفات المعدلة / المضافة في كود المرحلة
Exactly three new files were applied to the Central 14.5.54 source during validation:
1. `ACCOUNTING_P0_INTEGRATION_CONTRACT_14_5_54.md`
2. `app/src/main/java/com/fush/erp/domain/AccountingIntegrationContract.kt`
3. `app/src/test/java/com/fush/erp/domain/AccountingIntegrationContractTest.kt`

Branch delivery/control files:
- `fush_accounting/rebase/ACCOUNTING_P0_INTEGRATION_CONTRACT_14_5_54.patch`
- `fush_accounting/ACCOUNTING_P0_14_5_54_SCOPE.md`
- `fush_accounting/ACCOUNTING_P0_14_5_54_VALIDATION.md`
- `fush_accounting/ACCOUNTING_P0_HANDOFF.md`
- `.github/workflows/build-accounting-p0-contract-14.5.54-v2.yml`

## Business Logic
P0 does **not** alter posting amounts or operational workflows. It establishes an executable accounting integration contract that:
- registers 36 journal source types;
- covers accounting, treasury, sales, purchases, inventory, production and fixed-assets domains;
- documents the source-reference semantics for each event;
- classifies replay safety as `STABLE_SOURCE`, `STATE_GUARDED`, `NEEDS_STABLE_EVENT_ID`, or `MANUAL_ONLY`;
- records reversal policy for each accounting event;
- exposes deterministic canonical event-key construction for registered source types;
- identifies unsafe current references that must receive immutable event IDs before P1 idempotency can be enforced safely.

Important P0 finding: the historical branch-local Phase 14.5.43 idempotency patch must **not** be copied wholesale onto Central 14.5.54. Commission events, production corrections, generated treasury-voucher references, fiscal-year close/reclose cycles, and selected fixed-asset flows need safer immutable event identities first.

## Room / Migrations
- Room schema changed: **NO**
- Migration added: **NO**
- Central schema remains: **34**
- `FushDatabase.kt`, `Migrations.kt`, and `AppContainer.kt` were not changed by P0.
- No `fallbackToDestructiveMigration` or destructive migration was added.

## أثر المحاسبة
- Accounting posting amounts: **no change**.
- Debit/credit logic: **no change**.
- Customer/supplier balances: **no change**.
- Existing reversal behavior: **no change**.
- Effect of P0 is governance/contract safety only; runtime posting behavior is intentionally preserved.

## أثر المخزون
- Inventory quantities: **no change**.
- Inventory valuation logic: **no change**.
- P0 only documents inventory-origin accounting events such as opening stock and inventory count.

## أثر الإنتاج
- Production quantities: **no change**.
- BOM / issue / receipt / reject calculations: **no change**.
- P0 only documents production-origin accounting event identity and identifies correction-event identity gaps for P1.

## Unit / Integration / Regression
Successful validation workflow:
- Workflow: `.github/workflows/build-accounting-p0-contract-14.5.54-v2.yml`
- Run ID: `31918423520`
- Artifact ID: `9255691655`

Results:
- Exact Central source digest guard: PASS
- Exact P0 patch digest guard: PASS
- Patch apply check: PASS
- `git diff --check`: PASS
- Application ID guard: PASS
- Room schema unchanged guard: PASS
- Exact 3-file application scope guard: PASS
- Accounting source-contract regression: PASS
- Unit Tests `:app:testDebugUnitTest`: **PASS**
- Release `:app:assembleRelease`: **PASS**
- Existing Room schema 34 output: PASS
- No destructive migration guard: PASS
- Zipalign: PASS
- Artifact upload: PASS

## Release Build
**PASS** — `assembleRelease` completed successfully in run `31918423520`.

Validated artifact:
`FushERP-Accounting-P0-14.5.54-Validated-Handoff`

Artifact digest:
`sha256:71a543b430b232ff2a998f2503a32e4ec5c065bb2b7a53064495ebf4ebed2efd`

Key files inside artifact:
- Patch SHA-256: `c16ae280137aa3dc8777011e36d93af00e2ed0c34f9178ddcc0c99dc9fbc100c`
- Aligned unsigned APK SHA-256: `1683a66a87a5d108d7e36969fff4b7f7e7fb1059a69efc06d99cabb02ff51603`
- Full P0-on-Central source ZIP SHA-256: `f416317232733d1dde0dacd97070ad8abdb873a7f075fcaa687295af5c7ad048`

## المشاكل المعروفة
1. P0 is a contract stage; it does not yet enforce duplicate-posting protection at runtime. That belongs to P1 and must not be started before P0 handoff acceptance.
2. Several current source references are not replay-safe and need immutable event IDs before idempotency can be enforced safely.
3. The APK is aligned unsigned; no new signing key was created and no signing secret was stored in GitHub.
4. No direct merge to `fush/main` or `fush/integration-current` is part of this handoff. Integration is the responsibility of the central integration/main conversation.

## خطوات الاختبار اليدوي
P0 has no user-visible feature change, so manual testing is regression-oriented:
1. Install/run the validated P0 APK in a test environment or run the validated source build.
2. Confirm the app starts normally and the package remains `com.fush.erp.recovery`.
3. Create one test sales invoice and inspect its journal linkage; it should still use the existing `SALE` source semantics with no amount change caused by P0.
4. Record one customer receipt and confirm the existing `CUSTOMER_RECEIPT` journal behavior and customer balance are unchanged.
5. Create one purchase and one supplier payment and confirm the existing `PURCHASE` / `SUPPLIER_PAYMENT` linkage and balances remain unchanged.
6. Execute an inventory count adjustment in test data and confirm inventory quantity/value behavior is unchanged; P0 should not introduce an extra journal or quantity mutation.
7. Execute a normal production flow in test data (issue/receipt where already supported) and confirm production quantities are unchanged by P0.
8. Reopen the same records and confirm there is no new UI field or unexpected behavioral change introduced by the contract-only stage.
9. For developer verification, run `:app:testDebugUnitTest` and confirm `AccountingIntegrationContractTest` passes, then run `:app:assembleRelease`.

## تسليم المرحلة
Handoff patch to be reviewed/applied by the integration owner only:
`fush_accounting/rebase/ACCOUNTING_P0_INTEGRATION_CONTRACT_14_5_54.patch`

Do not copy the older full accounting source onto the Central Baseline. Apply only the reviewed P0 patch after reconfirming the Central Baseline.

**STOP CONDITION:** P0 is complete and handed off. Do not start P1 until P0 has been reviewed/accepted by the integration owner.

`ACCOUNTING_P0_HANDOFF_COMPLETE`
