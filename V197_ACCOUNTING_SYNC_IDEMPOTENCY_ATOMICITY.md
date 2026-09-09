# v197 — Accounting Sync Idempotency + Atomicity

- Fixes `DUPLICATE_ACCOUNTING_SOURCE` during cloud hydration of historical production journals.
- Preserves all historical journals; no destructive cleanup or migration.
- Historical `PRODUCTION_ISSUE` is excluded from the source uniqueness trigger because older releases legitimately used the same order number for additional material-issue corrections.
- New additional material-issue corrections use `PROD_ISSUE_CORR` instead of creating another `PRODUCTION_ISSUE`.
- Cloud publishing is dependency ordered: **all journal batches first, then all treasury-voucher batches**. This prevents `Voucher ... references missing journal ...` when a voucher's journal is in a later batch.
- Replay-safe source collisions are returned as explicit conflicts instead of aborting the entire sync through SQLite trigger exception.
- AppId unchanged; Room schema remains 49; no destructive migration.
