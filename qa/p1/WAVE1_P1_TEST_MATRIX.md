# FUSH ERP Mobile — QA P1 Wave-1 Test Matrix

Status: **IN PROGRESS / PREPARED — FINAL CENTRAL APK RETEST REQUIRED**

Branch: `fush/testing-qa`

Preparation baseline: `fush/integration-current@05ec94e097af5aed0e3e138bfee0afea12c9d8f2`

Central source tree recorded after Accounting P1: `6007751001ec0527aa1440016f305382656c6ebc`

Current Room schema: `35`.

Current migration introduced by Central: `MIGRATION_34_35_ACCOUNTING_P1`.

## Scope of this QA slice

This is the next QA preparation slice from the branch plan. It prepares migration/integration/regression evidence around the first P1 business wave:

- Accounting P1 — already integrated in the preparation baseline.
- Treasury P1 — pending Central integration at preparation time.
- Purchases/Suppliers P1 — pending Central integration at preparation time.
- Sales/Customers P1 — pending Central integration at preparation time.

The phase must **not** be marked COMPLETE/TESTED/READY FOR HANDOFF until the merged Central APK containing the intended four P1 changes has been tested again. Branch-level or source-only PASS is pre-acceptance evidence only.

## Compatibility warning

Treasury P1, Purchases P1 and Sales P1 were prepared from the previous exact Central `2cb8da801fc54aec8c1f0d6a83588f097ca85117` / Room 34. The current preparation baseline has advanced to Accounting P1 / Room 35. Therefore the pending P1 patches must be re-applied/revalidated on the new Central rather than treated as already proven compatible.

## Critical/High scenario matrix

| ID | Area | Risk | Scenario | Required assertions | Stage |
|---|---|---:|---|---|---|
| A1 | Accounting migration | C | Upgrade Room 34 -> 35 with historical POSTED journals | Existing rows survive; schema validates; no destructive reset; legacy rows remain readable | Automated MigrationTestHelper now + final Central rerun |
| A2 | Accounting integrity | C | Insert POSTED stable-source journal without `sourceId` | Rejected with stable-source identity guard | Automated migration/integration |
| A3 | Accounting integrity | C | Retry same `sourceType + sourceId` for stable source | Second POSTED journal rejected; first unchanged | Automated migration/integration |
| A4 | Accounting integrity | C | Update/delete POSTED journal | Rejected; correction requires reversal | Automated migration/integration |
| A5 | Accounting line sanity | C | Negative debit/credit or both debit and credit positive | Rejected before persistence | Automated migration/integration |
| A6 | Accounting compatibility | H | Repeatable/manual event uses same/null source identity | Must not be falsely deduplicated | Automated regression |
| S1 | Sales identity | C | Post sale with non-positive/missing customer identity | Rejected before AR/accounting persistence | Final merged source + APK |
| S2 | Sales + Accounting | C | Retry same sale posting | One SALE journal only; no duplicate AR/stock/COGS effect | Final merged integration |
| S3 | Collection | C | Customer receipt allocated only to invoices of same customer | Cross-customer allocation rejected; correct receipt succeeds | Final merged integration |
| S4 | Receipt reversal | C | Reverse receipt whose allocations do not belong to receipt customer | Reversal rejected; original data unchanged | Final merged integration |
| S5 | Sales return | C | Return references real customer and original sale | Customer identity preserved; duplicate posting blocked; stock/AR reversal consistent | Final merged integration |
| T1 | Treasury party rule | C | Voucher to employee payable `2200` | Exactly one valid employee identity required | Final merged integration |
| T2 | Treasury party rule | C | Voucher to sales-representative payable `2300` | Exactly one valid sales-rep identity required | Final merged integration |
| T3 | Treasury control accounts | C | Generic voucher attempts direct post to customer `1300` or supplier `2100` | Remains blocked; dedicated collection/payment workflows required | Final merged integration |
| T4 | Treasury general account | H | General account with orphan/mixed party IDs | Invalid orphan/mixed identity rejected; valid no-party use preserved | Final merged integration |
| P1 | Purchase identity | C | Supplier payment allocation references invoice owned by another supplier | Cross-supplier allocation excluded/rejected from payable results | Final merged integration |
| P2 | Purchase return | C | Return supplier does not match source invoice supplier | Must not affect supplier statement/aging/outstanding | Final merged integration |
| P3 | Supplier statement | C | Credit purchase -> partial payment -> partial supplier-credit return | Statement, aging and outstanding reconcile numerically | Final merged golden/integration |
| P4 | Supplier payment chronology | H | Payment posted after invoice period | Historical/as-of report recognizes payment on payment date, not invoice date | Final merged regression |
| P5 | Supplier return chronology | H | Return posted after invoice period | Historical/as-of report recognizes return on return date | Final merged regression |
| X1 | Cross-module atomicity | C | Accounting duplicate/identity guard rejects operational posting | No partial AR/AP/treasury/inventory state remains | Final merged transactional test |
| X2 | Cross-module reversal | C | Sale/receipt/purchase/payment/return reversal | Reversal traceable; original POSTED journal immutable; balances reconcile | Final merged integration |
| X3 | Update safety | C | Upgrade installed previous Central to merged P1 Central without uninstall | User data preserved; DB opens at final schema; no reset | Final Central APK gate |
| X4 | Identity/package | C | Test exact merged APK | `com.fush.erp.recovery`; APK digest recorded against source SHA; signing identity verified when official signing exists | Final Central APK gate |

## Stable source identities that must remain protected

The Accounting P1 Central migration explicitly protects the operational sources relevant to this wave: `SALE`, `CUSTOMER_RECEIPT`, `SALES_RETURN`, `PURCHASE`, `PURCHASE_RETURN`, and `SUPPLIER_PAYMENT`. QA must verify that identity-hardening in Sales/Purchases/Treasury does not bypass or conflict with these DB-level guards.

## Prepared automated assets

- `AccountingP1Migration34To35Test.kt`: `MigrationTestHelper` coverage for 34 -> 35 and 32 -> 35 chain validation, row preservation, duplicate/source-id/immutability/line-sanity guards.
- `validate_wave1_contracts.py`: source-level gate that reports Accounting/Treasury/Purchases/Sales P1 contracts as PRESENT/PENDING and can switch to strict `--require-all` after integration.
- `.github/workflows/testing-qa-wave1-p1.yml`: independent QA automation that always exports the latest `fush/integration-current`, applies QA tests only in a temporary work tree, runs Unit/Release/Android migration tests and uploads evidence. It does not merge to Central or `fush/main`.
- `FINAL_CENTRAL_APK_GATE.md`: mandatory exact-APK retest checklist after the integration wave is complete.

## Acceptance state

Preparation may report PASS for the current Accounting-P1 Central, but that is **not final acceptance**. Final acceptance requires all intended P1 changes to exist together in one Central source SHA and the corresponding Central APK to be retested and recorded.
