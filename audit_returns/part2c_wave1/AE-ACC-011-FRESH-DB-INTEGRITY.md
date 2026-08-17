# Final Part 2C Wave1 Audit Return — AE-ACC-011 / Fresh DB Integrity Parity

Owner branch: `fush/accounting`

Status: **OPEN / INTEGRITY GAP CONFIRMED BY INTEGRATED SOURCE + ROOM SCHEMA AUDIT / FINAL APK RETEST REQUIRED**

Tested Central:
- commit: `ddae764a236cb1916a46e194ccb2d3c24ca6b919`
- `central_source` tree: `30b028b75d6463c07afd0419429f53c7937fabb1`
- Room: `35`

## Finding

The migration chain contains database-level journal integrity guards, including the accounting-period insert trigger, POSTED journal/line immutability triggers, and Accounting P1 stable-source/duplicate-posting triggers. This protects databases that actually traverse the relevant migrations.

However, a fresh Room 35 database is created directly at schema 35. The exported Room 35 schema `setupQueries` contains only the Room master-table setup, and `AppContainer` builds the database with migrations but installs only the audit-events immutability triggers in `seedIfNeeded`; it does not install the journal closed-period, POSTED immutability, stable-source-id, or duplicate-posting triggers for a fresh database.

Several Wave1 operational posting paths in Sales/Purchases also do not independently call `requirePostingPeriodOpen` before their writes. Therefore an upgraded database and a fresh Room 35 database do not have equivalent journal-integrity enforcement. This is directly relevant to `AE-ACC-011` and also creates a fresh-install parity risk for POSTED immutability and duplicate prevention.

## Scope distinctions

- **Upgraded DB:** the migration-created closed-period / immutability / Accounting P1 stable-source guards are present.
- **Fresh Room 35 DB:** those custom journal guards are not established by the Room schema creation path or the current AppContainer initialization path.
- Reversal operations that explicitly call `requirePostingPeriodOpen` retain their service-level check; this finding concerns the broader Wave1 posting paths and fresh-install DB parity.

## Required owner retest

1. Create a genuinely fresh Room 35 database, not an upgraded database.
2. Close an accounting period and attempt: sale, collection, sales return, purchase, supplier payment, purchase return, and generic treasury voucher; all must reject with no persistent side effects.
3. Attempt UPDATE/DELETE of POSTED journal headers and UPDATE/DELETE of POSTED journal lines on the fresh database; all must be rejected and correction must use reversal.
4. Attempt duplicate POSTED stable-source journals and blank source IDs for protected source types on the fresh database; all must be rejected.
5. Repeat the same matrix on an upgraded DB and prove equivalent behavior.
6. Re-run on the exact final integrated Central APK after the owner fix is merged.

## Audit boundary

The audit branch did **not** modify Accounting production code, migrations, Room schema, or database callbacks. This file only returns the defect to the responsible branch.
