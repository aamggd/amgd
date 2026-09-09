# v140 — Vendor Lifecycle Startup Hotfix

Baseline: uploaded `FushERP-Mobile-v139-VendorSupportLifecycle-FINAL-Source(2).zip` only.

## Root cause
`MIGRATION_43_44_VENDOR_SUPPORT_LIFECYCLE` renamed `vendor_support_identities` to
`vendor_support_identities_v138`. SQLite keeps the old index names attached to the renamed table.
The migration then attempted `CREATE INDEX IF NOT EXISTS` using those same global index names
*before* dropping the renamed table. SQLite therefore skipped recreation. Dropping the v138 table
then removed the old indexes, leaving the new lifecycle table without four Room-required indexes.
Room schema validation failed on database open, causing the application to terminate during startup.

## Fix
The migration now copies the historical rows, drops `vendor_support_identities_v138`, and only then
creates all six v44 indexes on the new `vendor_support_identities` table. Historical Vendor Identity
rows remain append-only and are preserved as `PROVISION`, credential version 1.

## Release identity
- applicationId: `com.fush.erp.recovery`
- versionCode: `140`
- versionName: `0.15.4.91-vendor-lifecycle-startup-hotfix1`
- Room schema: `44` (unchanged)
- No destructive migration and no `fallbackToDestructiveMigration`.

## Data safety
Do not uninstall the failing v139 or clear app data. The corrected v140 code can retry the 43->44
migration when v139's startup migration was rolled back. A database already at schema 44 is opened
without running a new migration.
