# v147 Financial Date Policy Hardening

- versionCode 147 / versionName 0.15.4.98-financial-date-policy-hardening
- Room schema remains 44; no migration and no destructive database change.
- Blocks future effective dates for manual journals, treasury vouchers (including expenses), journal reversals, cash counts, bank statement end dates, and fixed-asset posting/reversal dates.
- Adds an explicit selectable effective date to both opening-stock entry points.
- Removes the meaningless nullable DAO return type for matched bank-reconciliation journal ids.
