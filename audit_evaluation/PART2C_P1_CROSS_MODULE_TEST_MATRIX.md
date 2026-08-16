# FUSH ERP Mobile — Part 2C P1 Cross-Module Integration Audit Matrix

Branch: `fush/audit-evaluation`

Status: **PRE-MERGE TEST PREPARATION / NOT FINAL / POST-MERGE CENTRAL APK RETEST REQUIRED**

This phase is audit/test work only. It does not repair application defects and it does not merge specialist branches into Central.

## 1. Current Central anchor

The preparation baseline was re-pinned after Treasury P1 entered Central while the first pre-merge automation was being prepared:

- Central branch: `fush/integration-current`
- Central HEAD: `bd39c9fdce444da865460539ea80058f02770d4a`
- Central repository tree: `8b44fe7f02ae146673e11637cfa9f9169738a452`
- `central_source` tree: `2c8f39d515e627d9d7d6ba1eac3e065a1d17f245`
- Application ID: `com.fush.erp.recovery`
- Room schema: `35`
- Accounting P1 is integrated in this Central.
- Treasury P1 is integrated in this Central.

### Specialist P1 handoffs under test

| Module | Branch | P1 handoff/head SHA | Central status at current preparation anchor |
|---|---|---|---|
| Accounting P1 — Journal Integrity | `fush/accounting` | `7510ff3dc03d28ebf751ebe32624e36ef175b9a7` | **Integrated** |
| Treasury P1 — Party Requirement | `fush/treasury-banking` | `7a1f1aec9f0315a23b62205aa5e4b049449203cc` | **Integrated** at Central commit `bd39c9fdce444da865460539ea80058f02770d4a` |
| Purchases P1 — Supplier Profile / AP Reconciliation | `fush/purchases-suppliers` | `b6a154a908898a25fcb40bd55dddfedce596c643` | Pending integration |
| Sales P1 — Customer Movement Identity | `fush/sales-customers` | `f7e6263f2c675704913fb6c1fb675f7ade36fc37` | Pending integration |

Purchases P1 and Sales P1 were prepared against the older Central `2cb8da801fc54aec8c1f0d6a83588f097ca85117` / schema 34. The audit therefore applies **only their functional P1 patches** to a temporary copy of the current Central after Accounting P1 + Treasury P1/schema 35. It must never replace current Central files with old branch source trees.

The original pre-merge run was also intentionally invalidated when Central advanced from Accounting-only commit `05ec94e097af5aed0e3e138bfee0afea12c9d8f2` to the Treasury-integrated commit above. This demonstrates the baseline pinning gate: a compatibility result from a superseded Central is not carried forward automatically.

## 2. Hard finalization rule

A successful pre-merge workflow means only:

`PRE-MERGE COMPATIBILITY / CONTRACT TESTED`

It **must not** be interpreted as `COMPLETE`, `TESTED FINAL`, `READY FOR HANDOFF`, or a production-readiness decision.

Part 2C can be finalized only after the integration conversation has merged the accepted Accounting/Treasury/Purchases/Sales P1 work into a new Central baseline and produced the corresponding Central APK. The audit branch must then rerun the final matrix against both:

1. the exact final integrated Central source tree; and
2. the exact Central APK artifact built from that source.

If either identity is different from the tested identity, the final result remains pending.

---

## 3. Automated pre-merge compatibility matrix

| ID | Area | Scenario | Required result before merge | Final APK gate |
|---|---|---|---|---|
| P2C-PRE-001 | Baseline | Export exact current Central and verify HEAD/tree/App ID/Room 35 plus integrated Accounting P1 + Treasury P1 | PASS | Re-pin to final Central |
| P2C-PRE-002 | Handoff identity | Fetch exact Purchases/Sales P1 SHAs and exact Git blob identities for their functional patches | PASS | Re-pin accepted integration SHAs |
| P2C-PRE-003 | Selective integration | Apply only pending Purchases/Sales P1 functional patches over current Central; no historical branch tree replacement | PASS | Must match actual integrated diff |
| P2C-PRE-004 | Room safety | Pending P1 patches must not alter `FushDatabase.kt`, `Migrations.kt`, schema JSON or allocate a new migration | PASS; remain schema 35 | Verify final assigned schema/migration chain |
| P2C-PRE-005 | Destructive safety | No `fallbackToDestructiveMigration`, DB deletion/reset, or `clearAllTables` introduced | PASS | PASS on final source/APK build record |
| P2C-PRE-006 | Accounting stable keys | `SALE`, `CUSTOMER_RECEIPT`, `SALES_RETURN`, `PURCHASE`, `PURCHASE_RETURN`, `SUPPLIER_PAYMENT` remain duplicate-protected stable source types | PASS | E2E replay test on integrated build |
| P2C-PRE-007 | Accounting source ID | Stable POSTED sources require nonblank source identity | Contract/unit PASS | E2E duplicate/replay rejection |
| P2C-PRE-008 | Journal immutability | POSTED journal/header lines remain immutable; correction uses reversal | Unit/source contract PASS | E2E reversal on final APK |
| P2C-PRE-009 | Customer identity | Customer movement guard accepts only a positive real `customerId` | PASS | Sale/receipt/return/reversal UI/API path |
| P2C-PRE-010 | Customer reversal integrity | Receipt reversal keeps one customer identity and rejects invoice allocation from another customer | Source/unit contract PASS | Final integrated E2E |
| P2C-PRE-011 | Supplier identity | Supplier-linked AP movement retains real positive supplier identity | PASS | Purchase/payment/return final E2E |
| P2C-PRE-012 | Supplier isolation | AP invoice/payment/return queries count only rows whose payment/return supplier matches invoice supplier | Source + unit PASS | Seeded final DB reconciliation |
| P2C-PRE-013 | Supplier profile | Aging + open invoice + direct voucher adjustments reconcile to supplier statement; differences are surfaced, not hidden | PASS | Final supplier profile / statement validation |
| P2C-PRE-014 | Treasury customer control | Account `1300` requires exactly one customer identity; generic treasury path must not bypass dedicated collection workflow | PASS | Final collection + generic-voucher negative test |
| P2C-PRE-015 | Treasury supplier control | Account `2100` requires exactly one supplier identity; generic treasury path must not bypass dedicated supplier-payment workflow | PASS | Final payment + generic-voucher negative test |
| P2C-PRE-016 | Employee payable | Account `2200` accepts exactly one employee and rejects missing/mixed party identity | PASS | Final voucher test |
| P2C-PRE-017 | Sales-rep payable | Account `2300` accepts exactly one sales representative and rejects other party identities | PASS | Final voucher/commission test |
| P2C-PRE-018 | General account | Non-party account rejects orphan customer/supplier/employee/sales-rep linkage | PASS | Final voucher negative test |
| P2C-PRE-019 | Regression | Full Unit suite after applying all pending P1 patches on current Central | PASS required | Re-run on final Central source |
| P2C-PRE-020 | Build | `assembleRelease`, App ID, schema and zipalign safety on pre-merge candidate | PASS required but **not final** | Must build/test actual final Central APK |

---

## 4. Final integrated business E2E matrix

These are the scenarios that determine Part 2C closure. They are deliberately not marked PASS during preparation.

### P2C-E2E-001 — Credit sale → collection → receipt reversal

Evidence must reconcile all of the following for one real customer:

- sales document and customer ID;
- AR posting source identity;
- treasury movement;
- customer receipt allocation;
- receipt reversal as a new movement;
- original receipt remains auditable;
- customer subledger balance;
- GL/AR control balance;
- no duplicate stable-source journal.

Run once with the same exchange rate and once with a different valid settlement exchange rate if the workflow supports it.

### P2C-E2E-002 — Credit sale → partial return → remaining receivable

Verify return quantity/amount, AR reduction, inventory return movement, COGS reversal, customer statement, journal balance, and unique `SALES_RETURN` source identity.

### P2C-E2E-003 — Credit sale → full return settlement safety

Mandatory retest of `AE-ACC-009`.

A full return of an **uncollected credit sale** must not issue a cash refund while leaving the original receivable open. The final accepted behavior must reconcile cash, AR, return settlement type, customer statement and journal entries atomically.

### P2C-E2E-004 — Credit purchase → supplier payment → payment reversal

Evidence must reconcile:

- purchase document and supplier ID;
- AP posting source identity;
- supplier-payment allocation;
- treasury movement;
- reversal as a new movement;
- supplier statement/aging;
- GL/AP control balance;
- no duplicate stable-source journal.

### P2C-E2E-005 — Credit purchase → partial return → remaining payable

Verify supplier-credit return reduces the correct invoice and the same supplier only, AP balance is correct, inventory is reduced correctly, and supplier aging equals statement liability.

### P2C-E2E-006 — Credit purchase → full return settlement safety

Mandatory retest of `AE-ACC-010`.

A full return of an **unpaid credit purchase** must not create a cash-refund movement while leaving the supplier payable open. Cash/AP/return settlement/supplier statement must reconcile atomically.

### P2C-E2E-007 — Duplicate posting replay protection

For each stable source below, attempt a replay with the same `sourceType + sourceId`:

- `SALE`
- `CUSTOMER_RECEIPT`
- `SALES_RETURN`
- `PURCHASE`
- `PURCHASE_RETURN`
- `SUPPLIER_PAYMENT`

Expected: one POSTED journal only; replay rejected; no extra treasury, allocation, stock, customer/supplier subledger or business document side effect remains.

### P2C-E2E-008 — Closed accounting period enforcement

Mandatory retest of `AE-ACC-011` for at least:

- sale;
- collection;
- sales return;
- purchase;
- supplier payment;
- purchase return;
- generic treasury voucher.

Expected: CLOSED period rejects the operation before any document/journal/treasury/stock/allocation side effect is committed, unless the authorized reopen workflow explicitly changes the period first.

### P2C-E2E-009 — Treasury party-control isolation

Negative cases:

- generic treasury voucher directly to customer control `1300`;
- generic treasury voucher directly to supplier control `2100`;
- employee payable `2200` without employee;
- employee payable with mixed employee/customer identity;
- sales-rep payable `2300` without sales rep;
- general account with orphan party identity.

All must reject atomically according to the approved dedicated-workflow contract.

### P2C-E2E-010 — Cross-customer receipt corruption guard

Construct/restore a test fixture where a receipt allocation references an invoice belonging to a different customer than the receipt owner. Attempt reversal.

Expected: rejection before the reversal movement is persisted; no treasury/journal/subledger mutation.

### P2C-E2E-011 — Cross-supplier AP ghost-activity guard

Construct a controlled fixture containing a payment allocation/return relation whose supplier identity does not match the target invoice supplier.

Expected after Purchases P1: the wrong-supplier row cannot contaminate open invoice amount, supplier payments list, supplier returns list, aging or supplier statement. Any reconciliation difference must be explicit rather than silently folded into another supplier.

### P2C-E2E-012 — Supplier profile reconciliation

For a supplier with:

- multiple credit invoices;
- partial payment;
- supplier-credit return;
- direct supplier payment/receipt voucher;
- at least two aging buckets;

verify:

`statement balance == open invoice liability + non-invoice supplier adjustment`

within the system rounding tolerance. The UI/report must show a mismatch if the equality does not hold.

### P2C-E2E-013 — Posted journal immutability and reversal

For a journal generated by one of the cross-module flows:

- update original header → reject;
- delete original header → reject;
- update/delete original lines → reject;
- approved reversal → create a new balanced reversal journal;
- original remains unchanged and auditable.

### P2C-E2E-014 — Financial reconciliation after scenario pack

After executing the full scenario pack, reconcile:

- sum debit = sum credit for every POSTED journal;
- Trial Balance total debit = total credit;
- customer AR subledger = customer control balance for the fixture scope;
- supplier AP subledger = supplier control balance for the fixture scope;
- treasury movements = affected treasury-account ledger movement;
- no unexpected stable-source duplicate journal exists.

---

## 5. Final Central APK gate

The final automation/manual evidence package must pin:

- final `fush/integration-current` commit SHA;
- final `central_source` tree SHA;
- final Room schema and full migration path from the previous installed Central schema;
- exact Central build workflow run ID;
- exact APK artifact ID/name/digest;
- package name `com.fush.erp.recovery`;
- signing certificate fingerprint when the official signed build is available;
- install/upgrade result on Android Emulator or physical device;
- launch/smoke result;
- final E2E evidence IDs above.

A pre-merge unsigned APK produced by this audit workflow is only a **candidate compatibility build** and can never satisfy this final gate.

## 6. Phase status

- Matrix prepared: **YES**
- Pre-merge automation prepared: **YES / running against re-pinned current Central**
- Accounting P1 on current Central: **PRESENT**
- Treasury P1 on current Central: **PRESENT**
- Purchases P1 integrated into Central: **NOT YET at current anchor**
- Sales P1 integrated into Central: **NOT YET at current anchor**
- Final integrated Central APK tested: **NO**
- Part 2C final status: **IN PROGRESS / NOT FINAL**
