# FUSH ERP Mobile — Final Part 2C Wave1 Integrated Source Audit

Status: **FINAL APK RETEST REQUIRED / NOT FINAL — OPEN DEFECTS RETURNED TO OWNER BRANCHES**

Audit scope only. No production application defect was fixed by this branch.

## Exact integrated Central under audit

- Central branch: `fush/integration-current`
- Central commit: `ddae764a236cb1916a46e194ccb2d3c24ca6b919`
- Central repository tree: `3859a7186ac837ea434129a73d9056a25c29f1f3`
- `central_source` tree: `30b028b75d6463c07afd0419429f53c7937fabb1`
- Application ID: `com.fush.erp.recovery`
- Room schema: `35`
- Accounting P1: integrated
- Treasury P1: integrated
- Purchases P1: integrated
- Sales P1: integrated

## Automated integrated-source run

Workflow: `Audit P2C Wave1 Final Integrated Source`
Run: `31981507405`
Audit workflow head: `1540f9ac580ef21617ef83b27dec198dcfc72c71`
Result: **SUCCESS for the executable Source/Unit/Release gates**

Passed gates:
- exact Central commit/repository-tree/source-tree pin;
- exact integrated source export and tree identity;
- App ID / Room 35 / migration registration / no destructive reset checks;
- focused Accounting + Treasury + Purchases + Sales integrity tests;
- full Unit regression suite;
- `assembleRelease` from the exact integrated source;
- `aapt` package verification;
- zipalign verification.

Source-built unsigned APK SHA-256:
`7de768ee98c722fd81c28d155a2afe5b33be019d57cb565770d27c6d24e260f8`

Audit artifact:
- ID: `9272591666`
- Name: `FushERP-P2C-Wave1-FINAL-SOURCE-AUDIT-NOT-FINAL`
- Artifact digest: `sha256:b738973fa9f31c7ce73f552c5b9f864d72998a672a22432343c31a5355427fa4`

The generated APK is audit evidence only: unsigned and not the exact final signed Central APK.

## Wave1 control results

### PASS at integrated source / Unit level

- Customer identity policy and cross-customer allocation rejection contracts.
- Supplier identity policy and cross-supplier AP isolation contracts.
- Treasury party requirements for protected party accounts.
- Customer receipt reversal source/unit contracts.
- Supplier payment reversal source/unit contracts.
- Accounting P1 stable-source policy tests.
- Supplier profile/AP reconciliation math tests.
- Trial Balance / accounting report math and reconciliation tests included in the focused suite.
- Treasury reconciliation/report math tests included in the focused suite.

These are not a substitute for final Android business-E2E validation on the exact Central APK.

### AE-ACC-009 — FAIL

Integrated `SalesService.postReturn` accepts `CASH_REFUND` after resolving treasury but does not prove that the returned amount was actually collected / cash-refundable before selecting that settlement. `invoiceOutstandingBase` exists separately but is not used to constrain `CASH_REFUND` in `postReturn`.

Risk: a fully or partially uncollected CREDIT sale can be returned through a cash-refund path while receivable settlement remains inconsistent.

Returned to: `fush/sales-customers`
Audit-return commit: `c7f6a7679c7b8da594d6b7d6173e0cea1aa653d2`

### AE-ACC-010 — FAIL

Integrated `PurchaseService.postPurchaseReturn` accepts `CASH_REFUND` after resolving treasury but does not prove that the invoice was paid / that the refund is bounded by actual cash paid before choosing that settlement.

Risk: an unpaid or partially paid CREDIT purchase can use a cash-refund path while supplier AP settlement remains inconsistent.

Returned to: `fush/purchases-suppliers`
Audit-return commit: `86df2e1d357b8c3e1fa3d9833b1687000ed2f518`

### AE-ACC-011 — FAIL for fresh Room 35 integrity parity

A second automated workflow (`Audit P2C Wave1 DB Integrity Parity`, run `31981745907`) compared migration-created guards with the fresh Room 35 creation path.

Observed on the exact integrated source:
- closed-period trigger exists in migrations: YES;
- closed-period trigger exists in fresh DB creation path: NO;
- POSTED journal header update/delete guards exist in migrations: YES; fresh creation path: NO;
- POSTED journal-line update/delete guards exist in migrations: YES; fresh creation path: NO;
- stable-source-ID guard exists in migrations: YES; fresh creation path: NO;
- duplicate stable-source guard exists in migrations: YES; fresh creation path: NO.

The operational service paths `SALE`, `CUSTOMER_RECEIPT`, `SALES_RETURN`, `PURCHASE`, `SUPPLIER_PAYMENT`, and `PURCHASE_RETURN` do not independently contain the `requirePostingPeriodOpen` service guard. Generic treasury voucher does.

Therefore:
- upgraded databases traversing the migrations have the DB closed-period guard;
- a fresh database created directly at Room 35 does not have equivalent custom journal guard installation through Room setup / current AppContainer initialization;
- `AE-ACC-011` remains open for fresh-install parity;
- POSTED immutability and duplicate-prevention also have a fresh-install parity defect even though the migration path contains the protections.

Returned to: `fush/accounting`
Audit-return commit: `cb42f5e9119dde3929416c700b25c1247a9d9cbf`

DB-parity run artifact:
- ID: `9272537094`
- Name: `FushERP-P2C-Wave1-DB-Integrity-Parity`
- Digest: `sha256:78a02fe704e607bb997ffa1e4331ef7f9b993c61f4ea23e6fc7e36a0a4f6d650`

## Exact Central APK status

A Central v102 candidate exists and its build workflow pins the same functional Central source (`ddae764...` / `30b028...`), but it is an **unsigned candidate** and its workflow intentionally changes test version metadata. It is not the final signed Central APK required by this audit gate.

No `FushERP-Central-v102-Final` artifact was available at the time of this audit.

Therefore Android install/launch and final operational business-E2E tests were **not run as final evidence**. The audit branch did not sign the candidate, create a signing key, or substitute a locally signed APK for the required Central artifact.

## Required final retest after owner fixes and new Central APK

The exact final Central APK must be identity-pinned to its integrated source and then tested on Android for:
- AE-ACC-009 sale return settlement;
- AE-ACC-010 purchase return settlement;
- AE-ACC-011 closed-period rejection on a genuinely fresh database and an upgraded database;
- POSTED header/line immutability on fresh + upgraded DB;
- duplicate stable-source prevention on fresh + upgraded DB;
- customer/supplier party identity and cross-party negative cases;
- customer receipt and supplier payment reversals;
- Trial Balance / AR / AP / Treasury reconciliation after the complete Wave1 E2E sequence.

Until those defects are fixed by their owner branches, merged into Central, and the exact final Central APK passes the Android retest, Part 2C must remain:

`FINAL APK RETEST REQUIRED / NOT FINAL`
