# FUSH ERP Mobile — Accounting Rebase on Professional UI 14.5.38

## Governing baseline
- Official successful GitHub Actions artifact: `FushERP-Phase14.5.38-Professional-UI-Build`.
- Application ID: `com.fush.erp.recovery` — unchanged.
- Baseline versionCode: `77` — retained for branch testing only.
- Baseline versionName: `0.15.4.38-ui-inventory-master-data` — retained for branch testing only.
- Baseline Room schema: `27`.

## Rebased accounting functionality
1. Accounting Integrity: control-account guards, invoice-aware settlements, FX-correct customer receipts, treasury selection.
2. Period Control & Reconciliation: accounting periods and GL/subledger reconciliation.
3. Operational Reversals: auditable reversals for customer receipts and supplier payments.
4. Fiscal Year Closing: P&L closing to retained earnings with reopen/reversal audit trail.
5. Treasury & Bank Reconciliation: cash counts, cash variances, bank statements and reconciliations.

## UI conflict policy
The rebase must preserve the current Professional UI presentation. Only accounting actions are inserted into it. The CI workflow expects exactly three merge conflicts from the historical accounting lineage: `app/build.gradle.kts`, `AccountingScreens.kt`, and `PartyScreens.kt`. Any additional or changed conflict makes the workflow fail rather than silently guessing.

## Room migration numbering — BRANCH ONLY / PROVISIONAL
The standalone accounting lineage currently expresses:
- `27 -> 28`: accounting periods/reconciliation.
- `28 -> 29`: operational reversals.
- `29 -> 30`: fiscal-year closing.
- `30 -> 31`: treasury/bank reconciliation.

These are not final integrated project schema numbers. The main integration branch must renumber accounting migrations that collide with users/permissions or any other already integrated migration, preserving SQL semantics and all existing data.

## Safety
- No destructive migration or database recreation.
- No Application ID change.
- No official versionCode assignment by this specialized branch.
- No signing key or password stored in source, GitHub, Gradle, Workflow, README, or commits.
- Final signing remains the responsibility of the integration/release process using the permanent FUSH certificate.
