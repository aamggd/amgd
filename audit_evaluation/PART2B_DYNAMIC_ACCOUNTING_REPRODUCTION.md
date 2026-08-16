# FUSH ERP Mobile — Part 2B Dynamic Accounting Reproduction

Branch: `fush/audit-evaluation`

Audited baseline: **Phase 14.5.54 Printing Integrated**

- Central branch record: `fush/integration-printing-14.5.54@5095ba46a676fd6a8e048f2325c433a1f336d05d`
- Validated Central source tree: `1b6af7bcaa86138ae75ea3d905db4bdba0fe04ff`
- Application ID: `com.fush.erp.recovery`
- Room schema: `34`
- Audit workflow: `.github/workflows/audit-evaluation-accounting-e2e-14.5.54.yml`
- Workflow run: `31919065033`
- Audit workflow head: `85640a9859609f8418cc715ae925910f83503e4e`

Status: **COMPLETE / DYNAMIC REPRODUCTION PASS / FULL UNIT PASS / RELEASE PASS / INVARIANTS PASS**

This file is audit evidence only. The workflow restores and verifies the exact validated 14.5.54 source tree before injecting a temporary test harness inside the CI workspace. The test harness is not copied into the production source artifact and does not repair any finding.

## Dynamic scenarios executed

The targeted test step completed successfully and therefore reproduced the current behavior asserted by all three audit scenarios:

1. `AE-ACC-009` — uncollected CREDIT sale can be fully returned as `CASH_REFUND` while the customer receivable remains open.
2. `AE-ACC-010` — unpaid CREDIT purchase can be fully returned as `CASH_REFUND` while the supplier payable remains open.
3. Closed-period purchase scenario — a purchase dated inside a `CLOSED` accounting period is accepted and its operational journal is posted.

The complete baseline unit-test suite with the audit harness, `assembleRelease`, and the final Application ID / Room / destructive-migration invariants all completed successfully in workflow run `31919065033`.

---

## AE-ACC-011 — Closed accounting periods are not consistently enforced for operational postings

- **Severity:** HIGH
- **Owner Branch:** `fush/accounting`
- **Required Reviews:** `fush/sales-customers`, `fush/purchases-suppliers`, `fush/inventory`, `fush/production-quality`, `fush/treasury-banking`, `fush/expenses` for their posting entry points.
- **Status:** READY_FOR_OWNER / DYNAMICALLY REPRODUCED FOR PURCHASE POSTING
- **Impact:** A period marked `CLOSED` can still be changed by operational documents that create journals without calling the accounting-period guard. That undermines period close, reconciliations, audit evidence, trial balance and financial statements because historical closed-period balances can change after closure.
- **Expected:** Every business operation that creates or reverses a journal must pass one centralized posting-period control before any business document, stock movement, treasury movement or journal is committed. A `CLOSED` period rejects posting unless an explicit, permission-controlled reopen workflow changes the period status first.
- **Actual:**
  1. `AccountingService.requirePostingPeriodOpen(entryDate)` rejects a non-OPEN accounting period.
  2. Manual/accounting paths call this guard, and fixed-asset posting paths call it.
  3. `PurchaseService.postPurchase()` does not call the guard before inserting the purchase and posting its journal.
  4. A dynamic audit scenario inserted a `CLOSED` period and successfully posted a purchase dated inside it; the resulting `PURCHASE` journal existed with the closed-period date.
  5. Static call-site inspection also shows only reversal-specific calls in `SalesService` and `PurchaseService`, and no `requirePostingPeriodOpen` call in `InventoryService`, `ProductionService`, or `AdvancedInventoryService`, despite those services containing journal-posting paths.
- **Evidence:**
  - `app/src/main/java/com/fush/erp/domain/AccountingService.kt:1325-1330` — centralized period guard.
  - `app/src/main/java/com/fush/erp/domain/PurchaseService.kt:69+` — `postPurchase()` starts without a period guard.
  - `app/src/main/java/com/fush/erp/domain/PurchaseService.kt:473` — the only direct PurchaseService guard call is on supplier-payment reversal.
  - `app/src/main/java/com/fush/erp/domain/SalesService.kt:193+` and `546+` — normal sale and return posting entry points do not call the guard; the direct SalesService guard call is at receipt reversal (`:468`).
  - `app/src/main/java/com/fush/erp/domain/InventoryService.kt` — journal posting exists, no guard call.
  - `app/src/main/java/com/fush/erp/domain/ProductionService.kt` — multiple production journal paths exist, no guard call.
  - `app/src/main/java/com/fush/erp/domain/AdvancedInventoryService.kt` — inventory-transfer/adjustment journal paths exist, no guard call.
  - GitHub Actions run `31919065033`, targeted dynamic accounting audit step — PASS for `reproduces_closed_period_bypass_for_operational_purchase_posting`.
- **Reproduction:**
  1. Create an accounting period covering the target document date and set status to `CLOSED`.
  2. Using a user who can post purchases, create a valid CREDIT purchase dated inside that closed period.
  3. Post the purchase.
  4. Query the purchase and its source journal.
  5. Current 14.5.54 behavior reproduced by the audit test: purchase succeeds and a `PURCHASE` journal is created with the closed-period date instead of rejecting the transaction.
- **Acceptance Criteria:**
  - A centralized accounting-period posting guard is enforced for every journal-generating business operation, including sales, collections, returns, purchases, supplier payments, inventory adjustments/transfers, production/WIP/finished-goods journals, expenses, treasury operations, fixed assets and all reversal paths as applicable.
  - Rejected closed-period operations are atomic: no orphan business document, stock movement, treasury movement, allocation, commission or journal remains.
  - The permitted reopen workflow is permission-controlled and audit logged; business services do not bypass it.
  - Tests cover OPEN/CLOSED periods for representative posting and reversal flows across all affected owner branches.
  - Financial statements and reconciliations remain unchanged after a rejected closed-period operation.
  - No Room destructive migration is introduced to implement the control.
- **Retest:** Audit branch must rerun representative owner-branch posting scenarios after the fix is integrated. Build PASS alone cannot close the finding.

## Part 2B test gate — final

Workflow run `31919065033` completed with conclusion **SUCCESS**.

- Exact Central 14.5.54 source-tree verification: **PASS**.
- Targeted dynamic accounting reproduction tests: **PASS**.
- Complete unit-test suite with audit harness: **PASS**.
- Release `assembleRelease`: **PASS**.
- Final release APK presence check: **PASS**.
- Application ID `com.fush.erp.recovery`: **PASS**.
- Room schema remains `34`: **PASS**.
- `fallbackToDestructiveMigration` absence check: **PASS**.

A successful audit workflow means the reproduction harness and safety gates succeeded; it does **not** mean `AE-ACC-009`, `AE-ACC-010`, or `AE-ACC-011` are fixed. They remain open until owner-branch fixes are integrated and independently retested.

## Impact introduced by audit branch

- Production application Business Logic changed: **No**
- Room Schema changed: **No**
- Migration added: **No**
- Accounting implementation changed: **No**
- Inventory implementation changed: **No**
- Production implementation changed: **No**
- Audit/CI evidence changed: **Yes — audit-only workflow and documentation**
