# FUSH ERP Mobile — Phase 14.5.37 Accounting & Treasury UI

Baseline: `0.15.4.36-ui-purchases-suppliers`
Target: `0.15.4.37-ui-accounting-treasury`
Branch: `fush/ui-professional-redesign`

## Purpose
Fourth UI professionalization package focused on Accounting, Treasury, Manual Journals and Financial Reports.

## Patch order
1. `01_scope.patch`
2. `02_build_gradle.patch`
3. `03_accounting_journal.patch`
4. `04_treasury_manual.patch`
5. `05_reports_tables.patch`

Apply from the root of a source tree that already contains Phase 14.5.36:

```bash
cat phase14_5_37_accounting_treasury_ui/patches/*.patch > /tmp/fush-ui-14.5.37.patch
git apply --check /tmp/fush-ui-14.5.37.patch
git apply /tmp/fush-ui-14.5.37.patch
```

## UI scope
- Accounting workspace hierarchy and navigation.
- Journal KPIs, search, entry status and clearer detail presentation.
- Treasury liquidity KPIs and cash/bank cards.
- Quick receipt/payment/income/transfer actions.
- Manual-journal safety guidance.
- Improved ledger, trial balance, income statement, balance sheet and cash-flow presentation.
- Shared professional accounting table and summary components.

## Safety boundary
Presentation only. No journal posting, reversal rules, voucher posting, account balances, DAO queries, report formulas, exchange-rate calculations or database schema are intentionally changed.

## Validation
All five patches were applied sequentially to Phase 14.5.36 with `git apply --check`. The resulting changed source files exactly match the Phase 14.5.37 working source, and `git diff --check` reports no errors in this change set. Android build validation remains separate because the supplied source package does not include the Gradle wrapper/Android SDK toolchain in this environment.
