# FUSH ERP Mobile v108 — Accounting Authorization Hardening

Baseline: v107 PortableBackup / UsersPermissions / SessionControl / NoMFA.

## Fixed
- Added service-level authorization guards to sensitive accounting mutations.
- Added specialized permissions for cash counts, bank reconciliation, fixed assets, FX revaluation, accounting periods, and fiscal-year closing.
- FixedAssetService mutations are now protected by `FIXED_ASSET_POST`.
- Accounting period create/close/reopen is protected by `ACCOUNTING_PERIOD_MANAGE`.
- Fiscal-year close/reopen is protected by `ACCOUNTING_YEAR_CLOSE`.
- Foreign-treasury revaluation is protected by `FX_REVALUATION_POST`.
- Bank-statement creation/editing/matching/finalization is protected by `BANK_RECONCILIATION_POST`.
- Cash-count recording is protected by `CASH_COUNT_POST`; posting a variance adjustment additionally requires `ACCOUNTING_POST`.
- Accounting UI now hides/disables mutation controls when the matching permission is absent. Service guards remain authoritative.

## Compatibility
- No Room schema change.
- Existing roles that already had `ACCOUNTING_POST` inherit the new accounting-specialist permissions on seed; read-only roles do not.
- Existing roles with `TREASURY_POST` inherit `CASH_COUNT_POST` to preserve cash-count workflow.
- ADMIN remains an explicit bypass in the central authorization guard.

## Version
- versionCode: 108
- versionName: 0.15.4.59-accounting-authorization-hardening
