# FUSH ERP Mobile v128 — Validation Report

## Release identity
- AppID: `com.fush.erp.recovery`
- versionCode: `128`
- versionName: `0.15.4.79-release-hardening1`
- compileSdk/targetSdk: 36
- minSdk: 26
- Room schema: 39 (unchanged from v127)
- Business time zone: `Asia/Aden`

## Implemented scope
- Printing hardening and safer Android print launch.
- Sharing hardening with explicit URI grant/ClipData.
- Managed attachments: open/share/export and portable attachment backup/restore support.
- Legacy external attachment migration before backup when accessible.
- CASH_REFUND statement/report correction.
- Positive shelf life required for finished goods.
- Removed 60/200 ml report hardcoding; reports use actual product data.
- Accepted Production KPI uses accepted/production-receipt timing.
- Configurable near-expiry threshold (default 60 days, valid 1..3650).
- ACCOUNTANT default permission separation from treasury/collection/payment-sensitive operations.
- Business time zone pinned to Asia/Aden.
- Release documentation updated.

## Explicit exclusions
- Historical production difference 3,669.10 was intentionally excluded by user request.
- Android instrumented test execution on emulator/device was waived by user for this release.

## Verification
- Unit tests: 389 tests, 0 failures, 0 errors, 0 skipped.
- Release build: PASS (`assembleRelease` including `lintVitalAnalyzeRelease`, `lintVitalReportRelease`, `lintVitalRelease`, `packageRelease`).
- APK signature: PASS, v2=true, v3=true.
- Signing certificate SHA-256: `22d5e2a8bd48ddd2339abdc4748648b509e02d046524d6e118fbe05088155586`.
- zipalign: PASS.
- No `fallbackToDestructiveMigration`, `deleteDatabase`, or `clearAllTables` introduced in executable source.
- No local `/mnt/data/...` repository path remains in final Gradle settings.

## Final artifact SHA-256
- APK `FUSH_ERP_Mobile_v128-ReleaseHardening-FINAL-SIGNED.apk`: `7e9cb7f3c3655aeec22f83389e07576e4a9770efae956b18c10170283903ccbb`
- Source `FushERP-Mobile-v128-ReleaseHardening-FINAL-Source.zip`: `0d567621b83a55890c2088a8b66f1bfec5d62aa67976897efaf758080ca73e33`
- Validation report: `a014063fecaab9d383ea1e9d68cda13adf6d73f3234177b79e746ad7b078cd11`
- v127→v128 patch: `0e62d961e6f986a828f1e035f9bfdc7a1b235ec69ff3451dfaa2944e81c58d75`

## Note
Android instrumentation test source files for printing/sharing/attachments remain in the source tree. They were not executed on an emulator/device because that gate was explicitly waived by the user.
