# Purchases / Suppliers — AE-ACC-010 Supplier Payment Atomicity Handoff

Status: **FIXED / TESTED / READY FOR RE-INTEGRATION AUDIT**

Scope is intentionally limited to the current owner request for `AE-ACC-010`: supplier payment persistence and its accounting journal must behave atomically, with no persisted payment/allocation side effect when journal preparation or journal posting fails.

## Exact Central baseline

- Central branch: `fush/integration-current`
- Central commit: `ddae764a236cb1916a46e194ccb2d3c24ca6b919`
- Central repository tree: `3859a7186ac837ea434129a73d9056a25c29f1f3`
- Central source tree: `30b028b75d6463c07afd0419429f53c7937fabb1`
- Application ID: `com.fush.erp.recovery`
- Room schema: `35`

No branch reset, force push, full merge, schema change, database migration change, package change, or destructive migration was used.

## Patch identity

- Patch: `purchases_suppliers_ae_acc_010/ae_acc_010_atomic_supplier_payment.patch`
- Exact patch SHA-256: `f37b4cc317e3c672fb87628bd8dc6e5e84ce32230426d5adf02349fe524f3294`
- Patch commit: `a0a3d4dbf1c8dbdc91bb63246f513bf9c93f0af6`
- Validated workflow head: `3811b01bf8813d29262fa8710b419ad5f1df5e47`

## Functional change

Only `PurchaseService` plus the dedicated regression test are changed when the patch is applied to the exact Central source.

1. The single-invoice `postSupplierPayment` entry point now owns a Room `withTransaction` boundary and calls the internal payment operation directly inside that transaction.
2. The internal supplier-payment mutation path asserts that a Room transaction is active.
3. Supplier-payment journal preparation now resolves required GL accounts, creates the draft journal lines, validates double entry, and produces the journal label **before** document numbering, supplier-payment persistence, or allocation persistence.
4. After the payment and allocations are written, the prepared journal is inserted inside the same Room transaction. Any exception during journal insertion propagates through `withTransaction`, so payment/allocation/number-sequence writes roll back with the journal.
5. No asynchronous `launch`/`async` boundary exists in the mutation path.

The debit/credit model is unchanged: AP 2100 is debited, treasury is credited, and FX gain/loss handling remains the existing logic.

## Changed application files after patch application

- `app/src/main/java/com/fush/erp/domain/PurchaseService.kt`
- `app/src/test/java/com/fush/erp/domain/AeAcc010SupplierPaymentAtomicityTest.kt`

No changes to:

- `FushDatabase.kt`
- `Migrations.kt`
- `AppContainer.kt`
- Room schemas
- `app/build.gradle.kts`
- Application ID / package

## Validation

Workflow: `Build Purchases Suppliers AE-ACC-010 Atomic Payment`

- Run ID: `31983236244`
- Result: **SUCCESS**
- Artifact ID: `9273090592`
- Artifact: `FushERP-Purchases-Suppliers-AE-ACC-010-Atomic-Payment`
- Artifact digest: `sha256:acd50c993803d40d90802566763b66223c0f4dea1f19c761fdb1ab6065670414`

Passed gates:

- exact Central commit / repository tree / source tree pin;
- exact patch SHA-256 validation;
- patch apply + `git diff --check`;
- source-order atomicity contract;
- dedicated `AeAcc010SupplierPaymentAtomicityTest`;
- Purchases targeted tests: `PurchaseMathTest`, `SettlementAllocationMathTest`, `SupplierMovementIdentityTest`, `SupplierProfileMathTest`;
- cross accounting/AP tests: `SupplierApMathTest`, `PartyStatementMathTest`, `AccountingIntegrationContractTest`, `AccountingP1IntegrityPolicyTest`, `AccountingReconciliationMathTest`, `AccountingReportMathTest`, `AccountingValidatorTest`, `TreasuryReconciliationTest`, `TreasuryReportMathTest`;
- full Unit regression suite;
- Release build;
- Room 35 / App ID / no-destructive checks;
- `aapt` package verification;
- zipalign verification.

Source archive SHA-256:
`548c86f7f5d7b6137cfbed8d3ab4f63eaeaceeedd4fdcddc8bc1eb0654bd8026`

Aligned unsigned APK SHA-256:
`931ca4597a42e849d19c60fb2cc7e4963f38db891ce34a423d0abff386410db7`

The APK is an unsigned test artifact only; no signing key or certificate was created or changed.

## Re-integration / audit instruction

Apply only the exact patch above to the current integration candidate, or carry the two resulting application-file changes forward if Central has advanced. Re-run the owner audit for supplier-payment atomicity with a forced journal failure and verify that no supplier payment, payment allocation, number-sequence mutation, or cash/AP journal side effect survives the failed transaction.

Do not treat this handoff as authorization to start P2 or to fix any other Wave1 finding.
