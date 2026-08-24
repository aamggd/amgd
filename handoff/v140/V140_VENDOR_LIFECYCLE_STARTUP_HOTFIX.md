# v140 — Vendor Lifecycle Startup Hotfix

Baseline: uploaded `FushERP-Mobile-v139-VendorSupportLifecycle-FINAL-Source(2).zip` only.

## Root cause
`MIGRATION_43_44_VENDOR_SUPPORT_LIFECYCLE` renamed `vendor_support_identities` to `vendor_support_identities_v138`. SQLite keeps the old index names attached to the renamed table. The migration then attempted `CREATE INDEX IF NOT EXISTS` using those same global index names before dropping the renamed table. SQLite skipped recreation; dropping the old table then removed those indexes, and Room schema validation failed during startup.

## Fix
The migration now copies historical rows, drops `vendor_support_identities_v138`, and only then creates all six Room-required indexes on the new lifecycle table. Historical Vendor Identity rows remain preserved as append-only `PROVISION`, credential version 1.

## Identity
- applicationId: `com.fush.erp.recovery`
- versionCode: `140`
- versionName: `0.15.4.91-vendor-lifecycle-startup-hotfix1`
- Room schema: `44` (unchanged)
- no destructive migration / no `fallbackToDestructiveMigration`

## Data safety
Do not uninstall the failing v139 or clear app data. v140 is intended as an update over v139. If v139's 43→44 upgrade failed, the migration transaction is retried with the corrected ordering. A database already at schema 44 opens without a new migration.
