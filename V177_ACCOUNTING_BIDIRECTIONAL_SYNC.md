# FUSH ERP Mobile v177 — Accounting Bidirectional Cloud Sync

- Baseline: v176 Integrated Production Report.
- App ID unchanged: `com.fush.erp.recovery`.
- versionCode: 177.
- versionName: `0.15.4.128-accounting-bidirectional-sync`.
- Room schema remains 46; no destructive migration.

## Scope
- Canonical posted General Ledger journal sync, header + all lines as one atomic cloud document.
- Generic party/treasury voucher sync linked to the canonical journal and treasury account code.
- Multi-writer safe publication: same natural key with different content becomes a conflict; no silent last-write-wins.
- Explicit KEEP_LOCAL / USE_CLOUD conflict resolution, with cloud-side conflict audit rows.
- Local/cloud treasury balance reconciliation after sync.
- Foreground automatic sync runs accounting after operational domains.

## Backend requirement
Run `supabase/V177_ACCOUNTING_BIDIRECTIONAL_SYNC.sql` once in the FUSH Supabase project before using the v177 accounting sync button. The Android Room schema does not change.
