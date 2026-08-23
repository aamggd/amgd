# FUSH ERP Mobile v132 — Release Handoff

## Status
Final local release package completed and verified. This branch is documentation-only and must **not** be merged to `master` or Central unless the user explicitly requests it.

## Release identity
- applicationId: `com.fush.erp.recovery`
- versionCode: `132`
- versionName: `0.15.4.83-integrated-release-hardening-trusted-time1`
- Room schema: `39`
- compileSdk / targetSdk: `36`
- minSdk: `26`

## Preserved lineage
- v128 Release Hardening: printing/sharing/portable attachments, shelf-life enforcement, product-driven reports, accepted-production KPI semantics, configurable near-expiry, ACCOUNTANT permission separation, fixed Asia/Aden business time zone.
- v129 Cash Refund Actual-Cash Guard.
- v130 Generic Production Batch Number.
- v131 Trusted Time / Clock Tamper Protection.
- v132 Integrated Release Hardening + Trusted Time.

## Verified release gates
- Unit tests: **402**, failures: **0**, errors: **0**.
- Portable-source unit rerun after removing machine-local paths: PASS.
- `lintVitalRelease`: PASS.
- `assembleRelease`: PASS.
- `zipalign`: PASS.
- APK Signature Scheme v2: PASS.
- APK Signature Scheme v3: PASS.
- Signing certificate SHA-256: `22d5e2a8bd48ddd2339abdc4748648b509e02d046524d6e118fbe05088155586`.

## Final artifacts
- APK: `FUSH_ERP_Mobile_v132-IntegratedReleaseHardening-TrustedTime-FINAL-SIGNED.apk`
  - SHA-256: `28f32506cef083a3a2c1d09ba6c5d6d5a7d8e225ed5c40c1aba2c7ad7a4c0601`
- Source: `FushERP-Mobile-v132-IntegratedReleaseHardening-TrustedTime-FINAL-Source.zip`
  - SHA-256: `3fb54afeebb5e5c2093ebc2cd90306696c9ea385587799bc0c62df38637988f1`
- Validation report: `FUSH_ERP_Mobile_v132-IntegratedReleaseHardening-TrustedTime-ValidationReport.md`
  - SHA-256: `f0d6c0bcc1a5519936f153b53c2440386abf6ed3b71d1243e46afb69ca4edc09`

## Source portability
The final source has one top-level folder, includes the Gradle Wrapper, and excludes build outputs, `.gradle`, `local.properties`, APKs, signing keys, JKS/PKCS12 files, and machine-local `/mnt/data`/`/workspaces` repository paths. Repositories are Google, Maven Central, and Gradle Plugin Portal only.

## Release-gate repair
The earlier Release gate failed only because the offline environment was missing `androidx.compose.material:material-icons-core-desktop:1.7.8` for Lint Vital. After supplying that dependency to the local build environment, `lintVitalRelease` and the full `assembleRelease` both passed. The temporary machine-local repository path was removed before packaging final source.

## Explicit exclusions
- Historical production-data difference `3,669.10` remains intentionally excluded by user instruction.
- Emulator/device instrumented execution is not a v132 release gate by user instruction.

## Merge policy
**Do not merge this handoff branch to master/Central.** No application source or signing material is uploaded by this handoff.
