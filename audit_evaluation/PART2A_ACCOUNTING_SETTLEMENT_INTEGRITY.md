# FUSH ERP Mobile — Part 2A Accounting Settlement Integrity Audit

Branch: `fush/audit-evaluation`

Status: **COMPLETE FOR PART 2A STATIC CROSS-LEDGER PROOF — PART 2 FULL E2E REMAINS IN PROGRESS**

Audited baseline: **Phase 14.5.54 Printing Integrated**

- Central branch record: `fush/integration-printing-14.5.54@5095ba46a676fd6a8e048f2325c433a1f336d05d`
- Final integrated source tree: `1b6af7bcaa86138ae75ea3d905db4bdba0fe04ff`
- Application ID: `com.fush.erp.recovery`
- Room schema: `34`
- Audit rule: no application defect is fixed in this branch.

## Objective

Part 2 requires End-to-End accounting validation by matching business documents to journal entries, party subledgers and financial statements. Part 2A focuses on the settlement invariant for returns:

> A cash refund must not be posted against an unpaid credit sale/purchase unless the system also clears the corresponding receivable/payable or can prove that sufficient prior cash settlement exists.

A balanced journal alone is not sufficient. The party balance must also represent the real economic obligation after the return.

## Source evidence identity

SHA-256 of inspected source files from the validated 14.5.54 source package:

- `SalesService.kt`: `f26312b1dbe73c7c90743ea3d164baf4fef6acabe162ee5972cbbeb7ba951fa0`
- `SalesDaos.kt`: `5e289474699fe4c9c77da5e46272ad317e0f6528ad41c11ed5b528e185565b26`
- `SalesScreens.kt`: `0c3ab960d1218c087c9922675431ec986f9279106e9803730af50ea3f5703e0a`
- `PurchaseService.kt`: `6100a5f9395195f166800e60e5e066eda5f09e4ce9b66a6aebc2fcea58dcc1da`
- `PurchaseDaos.kt`: `b511f8dd4e7ff93ee45e397b028e0fc234580465b45c227dbec4c028cfe32eed`
- `PurchaseScreens.kt`: `8c028ef195e245f893e98fc5d49a46c6833a85ac3eac2a9d462b827ae6223fa0`

---

## AE-ACC-009 — Uncollected credit sale can be returned as CASH_REFUND while customer receivable remains open

- **Severity:** HIGH
- **Owner Branch:** `fush/sales-customers`
- **Required Review:** `fush/accounting`
- **Status:** READY_FOR_OWNER / DYNAMIC RETEST REQUIRED
- **Impact:** The application can pay cash to a customer for a credit invoice that has not been collected, while the customer's receivable remains due. A fully returned sale can therefore still appear as customer debt and cash can be reduced without clearing that debt.
- **Expected:** For a credit invoice, `CASH_REFUND` must be limited to the amount previously collected and not already refunded/reversed, or the posting must split settlement so that any unpaid portion credits account `1300` (customer receivable) instead of cash. A full return of a fully unpaid credit invoice must not leave the original receivable outstanding.
- **Actual:**
  1. `SalesService.postReturn()` accepts either `CUSTOMER_CREDIT` or `CASH_REFUND` for any posted sales invoice and does not validate prior collections before a cash refund.
  2. The UI defaults based on invoice payment type but still exposes both settlement choices.
  3. `postSalesReturnJournal()` credits the selected treasury account for `CASH_REFUND`; it credits account `1300` only for `CUSTOMER_CREDIT`.
  4. Customer outstanding calculations subtract only returns whose `settlementType = 'CUSTOMER_CREDIT'`; a cash-refund return does not reduce the credit invoice receivable.
- **Evidence:**
  - `app/src/main/java/com/fush/erp/domain/SalesService.kt:546-567` — accepts both return settlement modes, resolves cash treasury, but has no collected/refundable amount guard.
  - `app/src/main/java/com/fush/erp/ui/screens/SalesScreens.kt:987-1017` — both `CUSTOMER_CREDIT` and `CASH_REFUND` remain selectable.
  - `app/src/main/java/com/fush/erp/domain/SalesService.kt:972-1009` — `CASH_REFUND` credits treasury; otherwise credits AR `1300`.
  - `app/src/main/java/com/fush/erp/data/dao/SalesDaos.kt:183-190` — separate received and return totals; outstanding-reducing return is specifically `CUSTOMER_CREDIT`.
  - `app/src/main/java/com/fush/erp/data/dao/SalesDaos.kt:208-225` — customer outstanding subtracts credit-invoice receipts and `CUSTOMER_CREDIT` returns, not `CASH_REFUND` returns.
- **Deterministic ledger proof (100 base currency, no prior collection):**
  - Original credit sale: `Dr AR 100 / Cr Sales 100`.
  - Full return selected as cash refund: `Dr Sales Returns 100 / Cr Cash 100`.
  - Net revenue is reversed, but AR remains **Dr 100** and Cash is **Cr 100**. The customer still owes 100 after a full return and the business has also paid out 100 cash.
- **Reproduction:**
  1. Create/post a CREDIT sales invoice for 100 and do not collect it.
  2. Confirm customer outstanding = 100.
  3. Return the full line and manually select `CASH_REFUND`; select an active treasury.
  4. Post the return.
  5. Compare the sales return document, journal entry, customer statement/outstanding and treasury movement.
  6. Expected defect state from current logic: revenue/COGS reverse, treasury decreases, but the customer receivable remains 100 because the return is not `CUSTOMER_CREDIT`.
- **Acceptance Criteria:**
  - Cash-refund amount cannot exceed net cash actually collected for that invoice after previous refunds/reversals.
  - Unpaid portion of a credit-sale return clears AR `1300` rather than cash.
  - Partial collection + partial/full return is correctly split or explicitly constrained.
  - Customer statement, invoice outstanding, AR control account and treasury all agree after the transaction.
  - Regression tests cover: zero collection, partial collection, full collection, prior refund, receipt reversal, partial return and full return.
  - Existing valid cash-sale refund behavior remains unchanged.
- **Retest:** Must be executed by this audit branch after the owner branch reports a fix. Do not close on build PASS alone.

---

## AE-ACC-010 — Unpaid credit purchase can be returned as CASH_REFUND while supplier payable remains open

- **Severity:** HIGH
- **Owner Branch:** `fush/purchases-suppliers`
- **Required Review:** `fush/accounting`
- **Status:** READY_FOR_OWNER / DYNAMIC RETEST REQUIRED
- **Impact:** The application can record cash received from a supplier for an unpaid credit purchase return while the original supplier payable remains outstanding. A fully returned purchase can therefore leave the business owing the supplier the original amount despite returning all goods and receiving cash.
- **Expected:** For a credit purchase, `CASH_REFUND` must be limited to amounts previously paid/settled and not already refunded/reversed, or the posting must split settlement so the unpaid portion debits account `2100` (supplier payable). A full return of a fully unpaid credit purchase must clear the payable, not create a cash receipt while preserving the liability.
- **Actual:**
  1. `PurchaseService.postPurchaseReturn()` accepts `SUPPLIER_CREDIT` or `CASH_REFUND` for any posted purchase invoice and does not compare a requested cash refund with prior supplier payments.
  2. The UI defaults according to invoice payment type but still allows both settlement choices.
  3. `postReturnJournal()` debits treasury for `CASH_REFUND`; it debits account `2100` only for `SUPPLIER_CREDIT`.
  4. Supplier outstanding calculations subtract only `SUPPLIER_CREDIT` returns and supplier payment allocations; a cash-refund return does not reduce AP.
- **Evidence:**
  - `app/src/main/java/com/fush/erp/domain/PurchaseService.kt:154-175` — both settlement modes accepted with no prior-paid/refundable amount guard.
  - `app/src/main/java/com/fush/erp/ui/screens/PurchaseScreens.kt:717-723` and `782-813` — both settlement modes remain selectable and cash refund only requires treasury selection.
  - `app/src/main/java/com/fush/erp/domain/PurchaseService.kt:707-742` — `CASH_REFUND` debits treasury; otherwise debits AP `2100`.
  - `app/src/main/java/com/fush/erp/data/dao/PurchaseDaos.kt:138-160` — payable/outstanding subtracts supplier payment allocations and only `SUPPLIER_CREDIT` returns.
- **Deterministic ledger proof (100 base currency, no prior supplier payment):**
  - Original credit purchase: `Dr Inventory 100 / Cr AP 100`.
  - Full purchase return selected as cash refund: `Dr Cash 100 / Cr Inventory 100`.
  - Inventory is fully reversed, but AP remains **Cr 100** and Cash increases **Dr 100**. The business still appears to owe the supplier 100 after all goods were returned, while also recording 100 cash received.
- **Reproduction:**
  1. Create/post a CREDIT purchase invoice for 100 and do not pay it.
  2. Confirm supplier outstanding = 100.
  3. Return all purchased quantity and select `CASH_REFUND`; select a treasury.
  4. Post the return.
  5. Compare purchase return, journal entry, supplier statement/outstanding and treasury movement.
  6. Expected defect state from current logic: inventory reverses and treasury increases, but AP remains 100 because the return is not `SUPPLIER_CREDIT`.
- **Acceptance Criteria:**
  - Cash refund from supplier cannot exceed net cash previously paid for the returned invoice after prior refunds/reversals.
  - Unpaid portion of a credit-purchase return reduces AP `2100` rather than increasing cash.
  - Partial payment + partial/full return is correctly split or explicitly constrained.
  - Supplier statement, invoice outstanding, AP control account and treasury all agree after the transaction.
  - Regression tests cover: zero payment, partial payment, full payment, prior refund, payment reversal, partial return and full return.
  - Existing valid cash-purchase refund behavior remains unchanged.
- **Retest:** Must be executed by this audit branch after the owner branch reports a fix. Do not close on build PASS alone.

---

## Part 2 accounting E2E matrix

The following matrix is the required continuation of Part 2. `STATIC PROOF` means source/journal/subledger path has been traced but is not a substitute for the dynamic database scenario required before final audit completion.

| Scenario ID | Scenario | Required reconciliation | Current audit state |
|---|---|---|---|
| ACC-E2E-01 | Cash sale | Invoice ↔ treasury ↔ revenue ↔ COGS ↔ inventory | PENDING DYNAMIC |
| ACC-E2E-02 | Credit sale | Invoice ↔ AR ↔ revenue ↔ COGS ↔ inventory | PENDING DYNAMIC |
| ACC-E2E-03 | Credit sale receipt, same FX | Receipt ↔ allocation ↔ AR ↔ treasury | PENDING DYNAMIC |
| ACC-E2E-04 | Credit sale receipt, changed FX | Receipt ↔ AR ↔ treasury ↔ FX gain/loss | PENDING DYNAMIC |
| ACC-E2E-05 | Receipt reversal | Reversal doc ↔ original allocation ↔ reversal journal ↔ commission | PENDING DYNAMIC |
| ACC-E2E-06 | Credit-sale return as customer credit | Return ↔ AR ↔ sales returns ↔ inventory/COGS | PENDING DYNAMIC |
| ACC-E2E-07 | Uncollected credit-sale return as cash refund | Return ↔ treasury ↔ AR outstanding | **STATIC PROOF FAIL — AE-ACC-009** |
| ACC-E2E-08 | Cash-sale return as cash refund | Return ↔ treasury ↔ sales returns ↔ inventory/COGS | PENDING DYNAMIC |
| ACC-E2E-09 | Credit purchase | Invoice ↔ AP ↔ inventory | PENDING DYNAMIC |
| ACC-E2E-10 | Supplier payment, same/changed FX | Payment ↔ AP ↔ treasury ↔ FX gain/loss | PENDING DYNAMIC |
| ACC-E2E-11 | Supplier payment reversal | Reversal doc ↔ allocation ↔ journal ↔ AP/treasury | PENDING DYNAMIC |
| ACC-E2E-12 | Credit-purchase return as supplier credit | Return ↔ AP ↔ inventory | PENDING DYNAMIC |
| ACC-E2E-13 | Unpaid credit-purchase return as cash refund | Return ↔ treasury ↔ AP outstanding | **STATIC PROOF FAIL — AE-ACC-010** |
| ACC-E2E-14 | Cash-purchase return as cash refund | Return ↔ treasury ↔ inventory | PENDING DYNAMIC |
| ACC-E2E-15 | Customer/supplier vouchers | Voucher ↔ party ID ↔ control account ↔ statement | PENDING DYNAMIC |
| ACC-E2E-16 | Manual journal + period lock | Journal ↔ ledger ↔ trial balance ↔ period controls | PENDING DYNAMIC |
| ACC-E2E-17 | Financial statements | Ledger ↔ trial balance ↔ P&L ↔ balance sheet ↔ cash flow | PENDING DYNAMIC |

## Part 2A impact statement

- Application source changed: **No**
- Business Logic changed by audit branch: **No**
- Room schema changed: **No**
- Migration added: **No**
- Accounting implementation changed: **No**
- Inventory implementation changed: **No**
- Production implementation changed: **No**
- Findings created: **2 HIGH accounting-integrity findings**

## Validation / gate

Part 2A adds audit documentation only. The accepted 14.5.54 application source remains unchanged, so its already validated Unit Tests PASS / Release Build PASS / Zipalign PASS remain the build baseline for this documentation-only phase. These build results do not close `AE-ACC-009` or `AE-ACC-010` and are not treated as proof of accounting correctness.

Part 2 remains **IN PROGRESS** until the dynamic E2E matrix is executed and reconciled against documents, journals, party subledgers and financial statements.
