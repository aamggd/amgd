# FUSH ERP Mobile — Accounting Rebase on Professional UI 14.5.38

## Governing baseline
- Source: official successful GitHub Actions artifact `FushERP-Phase14.5.38-Professional-UI-Build`.
- Application ID: `com.fush.erp.recovery` — unchanged.
- Baseline versionCode: `77` — unchanged in this accounting branch.
- Baseline versionName: `0.15.4.38-ui-inventory-master-data` — unchanged in this accounting branch.
- Baseline Room schema: `27`.

## Accounting functionality rebased
The functional accounting changes from the previous specialized accounting lineage were three-way merged onto the Professional UI baseline instead of replacing modern UI files:
1. Accounting Integrity: control-account guards, invoice-aware settlements, FX-correct customer receipts, treasury selection.
2. Period Control & Reconciliation: accounting periods and GL/subledger reconciliation.
3. Operational Reversals: auditable reversals for customer receipts and supplier payments.
4. Fiscal Year Closing: P&L closing to retained earnings with reopen/reversal audit trail.
5. Treasury & Bank Reconciliation: cash counts, cash variances, bank statements and reconciliations.

## UI conflict resolution
Only three merge conflicts existed: `app/build.gradle.kts`, `AccountingScreens.kt`, and `PartyScreens.kt`.
- `build.gradle.kts`: Professional UI identity/version retained.
- `AccountingScreens.kt`: Professional UI composition retained; cash-count and bank-reconciliation controls inserted into the modern treasury UI.
- `PartyScreens.kt`: Professional customer/supplier profiles retained; obsolete generic trade-control vouchers replaced with invoice-aware customer/supplier settlement actions; operational reversal actions retained.

## Room migration numbering — BRANCH ONLY / PROVISIONAL
This specialized accounting branch currently expresses its standalone migration chain as:
- `27 -> 28`: accounting periods and reconciliation.
- `28 -> 29`: operational reversals.
- `29 -> 30`: fiscal-year closing.
- `30 -> 31`: treasury/bank reconciliation.

These numbers are NOT the final integrated project schema numbers. The main integration branch already has other migrations (including users/permissions) that may occupy the same version numbers. During integration, the main conversation must renumber accounting migrations sequentially after the current integrated schema while preserving SQL semantics and all existing data.

## Safety rules
- No destructive migration or database recreation.
- No Application ID change.
- No official versionCode assignment in this specialized branch.
- Signing material/password is not stored in source, GitHub, Gradle, workflow, README, or commits.
