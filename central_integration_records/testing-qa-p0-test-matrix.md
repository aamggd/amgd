# FUSH ERP Mobile — QA P0 Test Matrix

Status: **COMPLETE / READY FOR REVIEW**

Branch: `fush/testing-qa`

Baseline: `fush/integration-printing-14.5.54@5095ba46a676fd6a8e048f2325c433a1f336d05d`

Central source tree recorded by the baseline: `1b6af7bcaa86138ae75ea3d905db4bdba0fe04ff`

Scope source: `21_fush_testing_qa.pdf`, P0 = Matrix Test linking every module to risks and scenarios.

## QA gate policy

A Candidate Release is not accepted from build success alone. Critical/High findings require a regression test whenever technically feasible. Test evidence must be tied to the exact source/build SHA. APK and source SHA must refer to the same candidate. Unit, integration/database, regression, UI/permission, visual/report, and real-device update/restore coverage are tracked separately.

## Baseline integrity checks

| Check | Baseline finding | P0 result |
|---|---|---|
| Application ID | `com.fush.erp.recovery` | PASS |
| Room schema | `34` | PASS — unchanged by P0 |
| Destructive migration | None recorded in 14.5.54 build record | PASS |
| Unit tests | Baseline build record = PASS | PASS (baseline evidence) |
| Release build | Baseline build record = PASS | PASS (baseline evidence) |
| Zipalign | Baseline build record = PASS | PASS (baseline evidence) |
| Official certificate fingerprint | `22:D5:E2:A8:BD:48:DD:D2:33:9A:BD:C4:74:86:48:B5:09:E0:2D:04:65:24:D6:E1:18:FB:E0:50:88:15:55:86` | Recorded only; no key added to branch |
| Existing JVM unit test files | 39 files in integrated source | INVENTORIED |
| Android instrumentation test files | 0 in integrated source | GAP — addressed starting P3/P5 |

P0 does not modify application code, Room entities, migrations, accounting logic, inventory logic, production logic, versionCode/versionName, signing configuration, or runtime behavior.

## Risk scale

- **C — Critical:** possible financial/data corruption, unauthorized access, destructive update/restore, or broken core transaction flow.
- **H — High:** incorrect balances/stock/production results, broken navigation/permissions, severe report/export mismatch.
- **M — Medium:** calculation edge case, usability regression, localization/layout problem without data corruption.
- **L — Low:** cosmetic/non-blocking issue.

## Module-to-risk test matrix

| Module / area | Primary risks | Risk | Required scenarios | Required layers | Existing direct unit evidence / current gap |
|---|---|---:|---|---|---|
| Authentication / session / MFA | Unauthorized access, stale session, bypassed MFA/reauth | C | Valid/invalid login; lockout policy; MFA challenge; privileged reauth; session expiry; logout/restart | Unit + Integration + UI permission + Real device | `PasswordHasherTest`, `MfaSecurityTest`, `SecurityPolicyTest`; instrumentation gap |
| Users / roles / permissions | Role leakage, forbidden screen/action accessible | C | Role matrix; permission revoke; disabled user; navigation visibility; direct action guard | Unit + Integration + UI permission + Regression | `PermissionCatalogTest`; UI permission tests required P3 |
| Audit / governance / risk controls | Missing audit trail, mutable/incorrect control event | H | Critical action audit; reversal/approval event; control calculation; unauthorized control access | Unit + Integration + Regression | `RiskControlMathTest`; end-to-end audit coverage gap |
| Master data | Broken references, duplicate numbering, invalid edits | H | Create/edit/inactivate; auto numbering; referenced master data cannot corrupt transaction history | Unit + Integration + Regression | `MasterDataMathTest`, `AutoNumberFormatTest` |
| Customers / suppliers / statements | Wrong AR/AP, aging/statement mismatch | C | Credit sale/purchase; payment/collection; return; partial settlement; aging; statement chronology | Unit + Integration + Golden + Regression | `CustomerArMathTest`, `SupplierApMathTest`, `AgingReportMathTest`, `PartyStatementMathTest`, `SettlementAllocationMathTest` |
| Sales | Wrong totals/returns/COGS/stock posting | C | Cash/credit sale; partial/full return; discount/tax edge; representative linkage; collection state; stock reduction | Unit + Integration + Golden accounting/inventory + Regression | `SalesMathTest`; transactional end-to-end gap |
| Purchases | Wrong AP/stock/cost/returns | C | Cash/credit purchase; partial/full return; supplier payment; quantity/cost update; chronological reporting | Unit + Integration + Golden accounting/inventory + Regression | `PurchaseMathTest`; transactional end-to-end gap |
| Treasury / cash / bank / FX | Incorrect cash/bank balances, reconciliation, FX valuation | C | Receipt/payment; cash count; bank reconciliation; FX conversion; reversal; period crossing | Unit + Integration + Golden + Regression | `TreasuryFxMathTest`, `TreasuryReconciliationTest`, `TreasuryReportMathTest` |
| Accounting / GL / periods / reconciliation | Unbalanced journals, wrong ledger/period close/reopen | C | Balanced entry; rejected unbalanced entry; posting; reversal; control accounts; close/reopen; reconciliation; comparative reports | Unit + Integration + Golden + Regression | `AccountingValidatorTest`, `AccountingReportMathTest`, `AccountingReconciliationMathTest`, `ControlAccountPolicyTest`, `PeriodComparisonMathTest` |
| Expenses | Wrong expense totals/classification/payment effect | H | Expense create/edit/reversal; category/dimension; paid/unpaid; report totals; long descriptions in print | Unit + Integration + Golden + Visual report | `ExpenseReportAnalyticsTest`; UI/report regression gap |
| Inventory / warehouses | Negative/wrong stock, bad transfer/count/reorder | C | Receipt/issue; transfer; count variance; reorder; historical quantity; return coupling; restart persistence | Unit + Integration + Golden inventory + Regression | `InventoryMathTest`, `InventoryCountMathTest`, `InventoryReportMathTest`, `WarehouseTransferMathTest`, `WarehouseReorderMathTest` |
| Production / quality | Wrong consumption/output/cost/genealogy | C | Issue materials; complete batch; yield variance; rejected/partial output; stock/accounting coupling; restart interruption | Unit + Integration + Golden inventory/accounting + Regression | `ProductionMathTest`; full E2E coupling gap |
| Employees / HR | Incorrect compensation/rules, unauthorized HR access | H | Employee state; payroll/compensation rule edges; sales-rep linkage; role visibility | Unit + Integration + UI permission | `HrRulesTest`; full compensation integration gap |
| Sales representatives / commissions | Wrong commission on return/collection, orphan linkage | H | Sale linkage; previous sales assignment; commission accrual; return cancellation; collection eligibility | Integration + Golden + Regression | Service exists; dedicated direct test coverage gap |
| Fixed assets | Wrong depreciation/book value/history | H | Acquisition; depreciation; disposal; as-of report; accounting posting | Unit + Integration + Golden accounting + Regression | `FixedAssetMathTest`; service integration gap |
| Maintenance | Incorrect maintenance metrics/status/cost | M | Schedule/status; cost total; overdue calculation; linked asset continuity | Unit + Integration + Regression | `MaintenanceMathTest` |
| Planning / geography | Wrong forecast/region calculation or navigation | M | Planning math edge cases; geography assignment; persistence; navigation | Unit + Integration + UI smoke | `PlanningMathTest`, `GeographyMathTest` |
| Reports / exports / printing | Totals differ from source data; clipped RTL/long text/tables | H | Accounting/treasury/expense reports; PDF/print; spreadsheet cell values; Arabic RTL; long text; wide tables; empty/large datasets | Unit + Integration + Golden + Visual regression | `ReportMathTest`, `SpreadsheetCellValueTest`, `AccountingSectionExportTest`; PDF visual golden gap P4 |
| Backup / restore | Data loss, unsafe archive/path, restore incompatibility | C | Create backup; tamper/corrupt archive rejection; restore; update then restore; restart; no destructive reset | Unit + Integration/DB + Real device | `BackupArchiveCodecTest`, `RestoreFileSafetyTest`; device update/restore gap P5 |
| Database / Room migrations | Data loss, migration path missing, schema mismatch | C | Historical schema fixture → current; row preservation; constraints/indexes; open after migration | Migration + Integration + Regression | Current P0 inventory finds no instrumentation/migration-test suite; P1 target |
| App shell / navigation | Dead destination, crash, inaccessible module | H | Every module route; back navigation; state restore; permission-dependent destinations | UI smoke + Regression | No Android instrumentation tests; P3 target |
| Localization / RTL / theme | Broken Arabic layout, clipped content, wrong direction | M/H for reports | Arabic/English; RTL/LTR; long text; theme switch; tables/forms/reports | UI + Visual regression | No screenshot/visual baseline suite; P4 target |
| Installation / update / offline / restart | Update loses data, app fails offline/restart, interrupted operation corrupts state | C | Install candidate; update without uninstall; offline CRUD where supported; force-stop/restart; interrupted write; backup/restore | Real device release test | P5 target |

## Critical golden scenarios to be implemented in P2

1. **Sale → collection → return:** verify customer balance, cash/bank, revenue/COGS/stock, and return effects stay numerically consistent.
2. **Purchase → supplier payment → partial return:** verify supplier balance, stock quantity/cost, cash/bank, and chronological reporting.
3. **Inventory transfer → count variance:** verify source/destination quantities, variance posting, and report totals.
4. **Production issue → completion:** verify raw-material consumption, finished output, production cost, inventory, and accounting coupling.
5. **Expense payment:** verify expense analytics, treasury effect, accounting posting, dimensions, and printed totals.
6. **Period close / reversal / reconciliation:** verify closed-period guards, balanced entries, reversal traceability, and reconciled balances.

All numerical expected values must be explicit constants in the P2 tests rather than recalculated by the same production functions under test.

## P1 migration fixture priorities

Historical fixture coverage will prioritize schema versions that materially changed accounting, inventory, production, security, HR, and printing-supporting data. P1 must use `MigrationTestHelper` (or equivalent Android Room migration harness), preserve representative rows across upgrade, and verify constraints/indexes. Schema numbers used only by this branch are **BRANCH ONLY / PROVISIONAL**; P0 introduces no schema change.

## Release acceptance checklist skeleton

A candidate is blocked unless all applicable items are evidenced against the exact candidate SHA:

- [ ] Source SHA and tested APK/artifact digest recorded together.
- [ ] Unit tests PASS.
- [ ] Integration tests PASS.
- [ ] Migration tests PASS for all supported upgrade paths, or explicit proof schema is unchanged.
- [ ] Critical/High golden regression scenarios PASS.
- [ ] UI navigation and permission matrix PASS.
- [ ] PDF/RTL/long-text/table visual regressions PASS.
- [ ] Update without uninstall PASS on real device/emulator target.
- [ ] Backup/restore PASS.
- [ ] Offline/restart/interruption scenarios PASS.
- [ ] `assembleRelease` PASS.
- [ ] Application ID = `com.fush.erp.recovery`.
- [ ] No `fallbackToDestructiveMigration` / destructive migration.
- [ ] Signing identity verified when official signing is available; otherwise artifact marked unsigned test-only.
- [ ] Known issues classified and release-blocking findings resolved or explicitly rejected by release authority.

## P0 test/validation evidence

The 14.5.54 central candidate build record reports Unit Tests PASS, Release Build PASS, Zipalign PASS, Application ID `com.fush.erp.recovery`, Room schema 34 unchanged, and no destructive migration. The integrated source artifact was inspected for P0: it contains 39 JVM unit-test files and no `androidTest` test files. P0 itself changes documentation only, so no runtime or schema delta is introduced.

## P0 handoff

- **Branch:** `fush/testing-qa`
- **Phase:** P0 — Module/Risk/Scenario Test Matrix
- **Baseline:** `fush/integration-printing-14.5.54@5095ba46a676fd6a8e048f2325c433a1f336d05d`
- **Files changed:** `qa/P0_TEST_MATRIX.md`
- **Functionality added/corrected:** QA coverage map and release-gate skeleton only; no application runtime code.
- **Business Logic changed:** No.
- **Room Schema changed:** No.
- **Migrations added:** None.
- **Accounting affected:** No runtime change; accounting scenarios are now explicitly gated in QA matrix.
- **Inventory affected:** No runtime change; inventory scenarios are now explicitly gated in QA matrix.
- **Production affected:** No runtime change; production scenarios are now explicitly gated in QA matrix.
- **Unit Tests:** PASS on exact starting central candidate according to its build record; no application code changed by P0.
- **Release Build:** PASS on exact starting central candidate according to its build record; no application code changed by P0.
- **Known issues/gaps:** No Android instrumentation tests in inspected integrated source; migration fixture suite not yet implemented; golden numeric E2E scenarios not yet implemented; PDF visual regression suite not yet implemented; real-device update/restore suite not yet implemented. These map directly to P1–P5.
- **Manual P0 verification:** Review each application module against the matrix and ensure every Critical/High area has at least one defined scenario and a target test layer.
- **Expected result:** No core module is omitted; Critical financial/data/security paths are assigned to integration/golden/migration/device tests rather than Unit-only coverage.
- **Merge instruction:** Cherry-pick the P0 commit from `fush/testing-qa`; do not merge directly to `fush/main` from this branch.
