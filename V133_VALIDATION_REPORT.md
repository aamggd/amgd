# v133 Validation Report — merged final

## Merge basis
- Baseline: `v132 Integrated Release Hardening + Trusted Time`.
- v133 was reviewed as a feature/update source, not accepted by filename alone.
- Merge was selective: 4 added files + 34 changed files were applied over v132; no v132 functional file was deleted.
- The uploaded v133 candidate initially failed `compileDebugKotlin` with six nullable-selection callback type mismatches in Expense, Maintenance, and Reports UI. Those merge blockers were corrected before acceptance.

## Identity and data safety
- `applicationId = com.fush.erp.recovery`
- `versionCode = 133`
- `versionName = 0.15.4.84-time-selection-backup-hardening1`
- Room schema = `39` (unchanged from v132).
- Room schema 39 JSON is byte-for-byte identical to v132.
- No new schema migration.
- No `fallbackToDestructiveMigration`.
- No database recreation/deletion fallback added.

## v133 functional review
- HTTPS trusted UTC uses consensus from at least two independent origins before re-anchoring.
- Offline trusted time continues from `SystemClock.elapsedRealtime()` after a trusted anchor.
- Sensitive operations fail closed when time cannot be trusted.
- Future business-date guards are present for customer receipt, sales return, supplier payment, and purchase return.
- Sales-return and purchase-return UI expose editable return dates.
- Searchable selection invalidates stale backing selections when visible text is edited.
- Portable backup/restore carries `near_expiry_days` transactionally with rollback protection.
- Main Kotlin source contains no no-argument `Date()` and no default `Calendar.getInstance()` clock read.

## Executed gates on the merged source
- `:app:testDebugUnitTest`: PASS.
- Unit tests: **408 tests, 0 failures, 0 errors, 0 skipped**.
- `:app:assembleRelease`: PASS.
- `lintVitalAnalyzeRelease`: PASS.
- `lintVitalReportRelease`: PASS.
- `lintVitalRelease`: PASS.
- APK package/assemble: PASS.
- `zipalign -c -v 4`: PASS.
- `apksigner verify --verbose --print-certs`: PASS.
- APK Signature Scheme v2: PASS.
- APK Signature Scheme v3: PASS.
- Signer certificate SHA-256: `22d5e2a8bd48ddd2339abdc4748648b509e02d046524d6e118fbe05088155586`.

## Build environment note
The normal portable source repositories remain `google()`, `mavenCentral()`, and `gradlePluginPortal()`. During the offline release build only, a temporary local repository was used to supply the already-verified `material-icons-core-desktop:1.7.8` lint dependency; `settings.gradle.kts` was restored afterward and the final source contains no `<local-build-path>` or local-maven path.

## Android instrumentation
Android emulator/device execution is not claimed for this v133 merge gate. The user previously waived actual Android-device instrumentation as a release blocker.
