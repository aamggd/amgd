# FUSH ERP Mobile v162 — Purchase Conflict Inspector

- Baseline: v161 Purchase Documents Cloud Mirror Production Fixes.
- App ID remains `com.fush.erp.recovery`.
- versionCode: 162.
- Room schema remains 46; no migration was added.
- Adds non-destructive purchase conflict details for purchase invoices and purchase returns.
- Persists the last conflict list per local user and shows it in Cloud Sync.
- Compares header fields and line-level item/unit/quantity/cost/lot/expiry data.
- Does not overwrite either copy and does not replay inventory, supplier payment, treasury, or journal side effects.
- No Supabase SQL migration is required beyond v161.
