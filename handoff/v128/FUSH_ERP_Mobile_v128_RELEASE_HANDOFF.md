# FUSH ERP Mobile v128 — Release Hardening Handoff

## Authoritative baseline
This handoff represents the completed v128 release built from:
`FushERP-Mobile-v127-SearchableDropdownKeyboardFix-FINAL-Source`

Do not rebuild from older sources.

## Release identity
- applicationId: `com.fush.erp.recovery`
- versionCode: `128`
- versionName: `0.15.4.79-release-hardening1`
- compileSdk: `36`
- targetSdk: `36`
- minSdk: `26`
- Room schema: `39` (unchanged from v127; no migration)
- Business time zone: `Asia/Aden`
- Permanent signer certificate SHA-256: `22d5e2a8bd48ddd2339abdc4748648b509e02d046524d6e118fbe05088155586`

## Implemented scope
- Printing hardening: require a valid Activity before launching Android Print UI.
- Sharing hardening: explicit URI read grant plus ClipData.
- Attachments redesign: managed internal FUSH storage, open/share/export actions, and portability through backup/restore.
- Legacy external attachment migration before backup when accessible.
- Portable backup format updated to carry attachment files while retaining prior-format restore compatibility.
- CASH_REFUND corrected so customer AR is not affected twice in statement/report logic.
- Positive shelf life required for finished goods.
- Removed hardcoded 60/200 product-volume logic from the report layer.
- Accepted Production KPI uses PRODUCTION_RECEIPT / accepted timing rather than manufacture-date-only semantics.
- Near-expiry threshold is configurable; default 60 days, normalized to 1..3650.
- ACCOUNTANT defaults separated from treasury posting, cash count, customer collection/collection-discount, supplier payment, and geography-management permissions.
- Business-date/time-zone semantics pinned to Asia/Aden instead of device system default.
- Release documentation updated.
- Android instrumentation test source was added for printing/sharing/attachments.

## Explicit exclusions / waived gate
- Historical production difference `3,669.10` was intentionally excluded by user request.
- Execution of Android instrumented tests on emulator/device was explicitly waived by user for this release. Do not claim those tests were run.

## Verified final gates
- JVM unit tests: `389` tests, `0` failures, `0` errors, `0` skipped.
- `assembleRelease`: PASS, including `lintVitalAnalyzeRelease`, `lintVitalReportRelease`, `lintVitalRelease`, `packageRelease`.
- APK zipalign: PASS.
- APK signature verification: PASS; APK Signature Scheme v2=true, v3=true.
- App package/version verified: `com.fush.erp.recovery`, versionCode `128`.
- No `fallbackToDestructiveMigration`, `deleteDatabase`, or `clearAllTables` introduced in executable source.
- No local `/mnt/data/...` Maven repository remains in final Gradle settings.

## Final local artifact names and SHA-256
- APK: `FUSH_ERP_Mobile_v128-ReleaseHardening-FINAL-SIGNED.apk`
  - SHA-256: `7e9cb7f3c3655aeec22f83389e07576e4a9770efae956b18c10170283903ccbb`
- Source: `FushERP-Mobile-v128-ReleaseHardening-FINAL-Source.zip`
  - SHA-256: `0d567621b83a55890c2088a8b66f1bfec5d62aa67976897efaf758080ca73e33`
- Validation report: `FUSH_ERP_Mobile_v128-ReleaseHardening-ValidationReport.md`
  - SHA-256: `a014063fecaab9d383ea1e9d68cda13adf6d73f3234177b79e746ad7b078cd11`
- v127→v128 patch SHA-256: `0e62d961e6f986a828f1e035f9bfdc7a1b235ec69ff3451dfaa2944e81c58d75`

## Continuation rules
1. Treat v128 as the only baseline for v129+ work.
2. Keep `applicationId = com.fush.erp.recovery` and the same permanent signer.
3. Preserve Room 39 unless a real schema change requires an explicit non-destructive migration.
4. Never use `fallbackToDestructiveMigration` and never delete/recreate the user database as a migration strategy.
5. Preserve all v124-v128 fixes, especially transaction chronology, historical multi-lot allocation, collection settlement discount, global searchable autocomplete, and searchable-dropdown keyboard fix.
6. No merge to master/Central is authorized by this handoff. This branch is a continuation snapshot only.

## GitHub location
- Repository: `aamggd/amgd`
- Branch: `fush/v128-release-hardening-handoff`
- Handoff file: `handoff/v128/FUSH_ERP_Mobile_v128_RELEASE_HANDOFF.md`
