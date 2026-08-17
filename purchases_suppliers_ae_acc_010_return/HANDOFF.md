# Purchases / Suppliers — Official AE-ACC-010 Purchase Return CASH_REFUND Handoff

Status: **FIXED / TESTED / READY FOR RE-INTEGRATION AUDIT**

This handoff closes the owner-branch implementation work for the **official `AE-ACC-010`** finding only: `PurchaseService.postPurchaseReturn` must not allow a supplier `CASH_REFUND` unless the purchase is actually cash-backed by money paid to that supplier, and the refund must remain bounded by the amount still cash-refundable after earlier refunds.

The earlier Supplier Payment Atomicity work and run `31983236244` remain a **separate improvement**. They are not `AE-ACC-010` and are not part of the exact patch below.

## Exact Central baseline

- Central branch: `fush/integration-current`
- Central commit: `ddae764a236cb1916a46e194ccb2d3c24ca6b919`
- Central source tree: `30b028b75d6463c07afd0419429f53c7937fabb1`
- Room schema: `35`
- Application ID: `com.fush.erp.recovery`

No Full Merge, Central push, `fush/main` push, schema change, database migration change, package change, destructive migration, or `fallbackToDestructiveMigration` was performed.

## Exact patch identity

- Patch: `purchases_suppliers_ae_acc_010_return/ae_acc_010_cash_refund.patch`
- Exact SHA-256: `85e5272c43886018c6c57774992382609dc748a54015c72d19f2e3d6ead028e6`

When applied to the exact Central source, the patch changes only:

- `app/src/main/java/com/fush/erp/domain/PurchaseService.kt`
- `app/src/main/java/com/fush/erp/domain/SupplierApMath.kt`
- `app/src/test/java/com/fush/erp/domain/AeAcc010PurchaseReturnCashRefundTest.kt` — new dedicated regression test

It does not change `FushDatabase.kt`, `Migrations.kt`, `AppContainer.kt`, Room schemas, `app/build.gradle.kts`, package/Application ID, or database structure.

## Official AE-ACC-010 behavior

### Unpaid CREDIT purchase

A `CREDIT` purchase whose net `paidBaseForInvoice` is zero has zero `CASH_REFUND` capacity. A caller cannot turn an unpaid payable into cash received from the supplier.

### Partially paid purchase

For `CREDIT`, cash-refundable capacity is bounded by the current net paid amount, not by the invoice face value. Example: invoice base `1000`, net paid `400` => at most `400` total cash refunds can be backed before considering earlier cash refunds.

### Fully paid purchase

A fully paid credit invoice may be cash-refunded only up to its cash-backed invoice amount. The calculation also caps credit-payment backing at the invoice base amount.

### Refund bounding / duplicate refund prevention

Before any return number, return row, stock movement, or return journal is persisted, `postPurchaseReturn` computes:

- current net paid base for a credit invoice using `paidBaseForInvoice`;
- prior POSTED `CASH_REFUND` purchase returns for the same invoice;
- remaining cash-refundable capacity = cash backing minus prior cash refunds.

The new return total must be less than or equal to that remaining capacity. Existing `returnedQuantityForLine` protection remains unchanged and continues to prevent over-returning purchase quantities. Together these prevent repeated cash refunds from exceeding both actual payment backing and returnable quantity.

### Return / supplier-payment reversal integrity

Before a supplier-payment reversal is persisted, the service checks each affected invoice. The reversal is rejected if it would reduce the invoice's net paid amount below cash refunds already issued against that invoice. This preserves the invariant that an existing supplier cash refund always remains backed by actual net supplier payment.

## Accounting / Treasury / Journal effect

No journal formula was changed.

- `CASH_REFUND` purchase return continues to post Treasury debit / Inventory credit.
- `SUPPLIER_CREDIT` purchase return continues to post AP 2100 debit / Inventory credit.
- Supplier payment accounting formulas are unchanged by this official patch.

The fix changes **eligibility and bounding**, not debit/credit calculations. Therefore a cash refund is allowed only to the extent it represents recovery of cash actually paid; unpaid liability continues to remain AP rather than being incorrectly converted into treasury cash.

## Inventory effect

No stock quantity, unit conversion, inventory cost, average cost, or COGS formula was changed. The existing purchase-return stock movement remains the source of the inventory reversal; this patch only adds the cash-backing gate before persistence.

## Validation

Final validation workflow:

- Workflow: `Build Purchases Suppliers Official AE-ACC-010 Cash Refund V4`
- Run ID: `31984539721`
- Validated workflow head: `76308b1ff074094442a26ee0d8b6da6a7200cfd8`
- Result: **SUCCESS**
- Artifact ID: `9273464355`
- Artifact: `FushERP-Purchases-Official-AE-ACC-010-Cash-Refund-V4`
- Artifact digest: `sha256:92b9007c2ed4a22ee288ee5b98600273a36fe7cf51fcb198095e5440827a98de`

Passed gates:

- exact Central commit / source-tree pin;
- exact patch SHA-256 verification;
- patch apply and `git diff --check`;
- exact changed-file boundary check;
- official AE-ACC-010 source contract;
- dedicated `AeAcc010PurchaseReturnCashRefundTest`;
- Purchases targeted tests: `PurchaseMathTest`, `SettlementAllocationMathTest`, `SupplierApMathTest`, `SupplierMovementIdentityTest`, `SupplierProfileMathTest`;
- cross Accounting/AP/Treasury tests: `PartyStatementMathTest`, `AccountingIntegrationContractTest`, `AccountingP1IntegrityPolicyTest`, `AccountingReconciliationMathTest`, `AccountingReportMathTest`, `AccountingValidatorTest`, `TreasuryReconciliationTest`, `TreasuryReportMathTest`;
- full Unit regression suite;
- Release build;
- Room `35` verification;
- App ID `com.fush.erp.recovery` verification;
- no destructive migration / no fallback / no database-clear path verification;
- `aapt` package verification;
- zipalign verification.

Validation artifact evidence:

- Exact patch SHA-256: `85e5272c43886018c6c57774992382609dc748a54015c72d19f2e3d6ead028e6`
- Aligned unsigned APK SHA-256: `19caf36c432a99fe05d42b336cacc7275f4790952f7dc6d6cfc4d4289bdf0ef4`
- Patched source archive SHA-256: `5b1e60a2c4ab545e13f15f1b7f911e8cfb9a71cf172d41f0b88ecb11b9ce8be0`

The APK is unsigned validation evidence only. No signing key or certificate was created or changed.

## Re-integration / audit instruction

For official `AE-ACC-010`, re-integrate **only** the exact patch SHA above against the pinned Central, or selectively carry the same three application-file changes if Central has advanced and then re-run the complete owner audit.

The audit should explicitly exercise:

1. unpaid CREDIT purchase + `CASH_REFUND` => rejected;
2. partially paid purchase => refund accepted only up to current net paid/cash-refundable amount;
3. fully paid purchase => refund bounded by invoice cash backing;
4. repeated refunds => cumulative cash refunds cannot exceed remaining cash-backed capacity, and quantity over-return remains rejected;
5. supplier-payment reversal after cash refund => rejected when reversal would leave the refund unbacked;
6. AP / Treasury / Journal / returned inventory reconciliation after the sequence.

Do not implicitly bundle the Supplier Payment Atomicity patch/run `31983236244` into this official `AE-ACC-010` integration unless Central separately chooses that improvement.

`P2 = NOT STARTED`.
