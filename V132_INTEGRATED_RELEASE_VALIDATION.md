# FUSH ERP Mobile v132 — Integrated Release Hardening + Trusted Time

Baseline: v131 TrustedTimeClockTamper.

## Preserved release lineage
- v128 Release Hardening: printing/sharing/portable attachments, shelf-life enforcement, product-driven reports, accepted-production KPI semantics, configurable near-expiry, ACCOUNTANT permission separation, fixed Asia/Aden business time zone.
- v129 Cash Refund Actual-Cash Guard.
- v130 Generic Production Batch Number.
- v131 Trusted Time / Clock Tamper Protection.

## Identity
- applicationId: `com.fush.erp.recovery`
- versionCode: `132`
- versionName: `0.15.4.83-integrated-release-hardening-trusted-time1`
- Room schema: `39` (unchanged)
- Room schema 39 SHA-256: `93d6043acf757369a6913816b3b93fe64d1b50e4ce6915c739c9a4601274a438`
- No database migration.
- No destructive migration.

## Final local release gates
- JDK: Temurin 17
- Android SDK / compileSdk: 36
- `:app:testDebugUnitTest`: PASS
- Unit test XML aggregate: **402 tests, 0 failures, 0 errors**
- Portable-source unit rerun after removing `local.properties` and machine-local Maven path: PASS
- `:app:lintVitalRelease`: PASS
- `:app:assembleRelease`: PASS
- APK zipalign verification: PASS
- APK signature verification: PASS (v2=true, v3=true)
- Signing certificate SHA-256: `22d5e2a8bd48ddd2339abdc4748648b509e02d046524d6e118fbe05088155586`
- Final APK SHA-256: `28f32506cef083a3a2c1d09ba6c5d6d5a7d8e225ed5c40c1aba2c7ad7a4c0601`

## Release-gate repair
The earlier release failure was environmental: Android Lint Vital could not resolve `androidx.compose.material:material-icons-core-desktop:1.7.8` from the offline cache. The dependency was supplied to the local build environment, then `lintVitalRelease` and the full `assembleRelease` were rerun successfully. The final source does **not** retain a `<local-build-path>` Maven repository or `local.properties`; repositories are only Google, Maven Central and Gradle Plugin Portal.

## Merge policy
No v129 source files were overlaid onto v131 because v131 already contains the v129 lineage. This avoids reverting v130/v131 changes. v132 is the integrated superseding update.

## Explicit exclusions
- Historical production data difference 3,669.10 remains excluded by user instruction.
- Running instrumented tests on an emulator/device is not a release gate by user instruction.
- No merge to `master` or Central is authorized by this handoff.
