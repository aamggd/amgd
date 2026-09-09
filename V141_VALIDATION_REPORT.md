# FUSH ERP Mobile v141 Validation Report

## Identity
- applicationId: `com.fush.erp.recovery`
- versionCode: `141`
- versionName: `0.15.4.92-vendor-lifecycle-startup-hotfix2`
- Room schema: `44`

## Startup crash fix
The early Room bootstrap now registers `MIGRATION_43_44_VENDOR_SUPPORT_LIFECYCLE` after `MIGRATION_42_43_VENDOR_SUPPORT_PROVISIONING`. This keeps the pre-container database open path aligned with the Room schema used by `AppContainer` and prevents the missing 43->44 migration failure during application startup on an existing database.

A regression contract test was added: `AccountingWaveBRoomBootstrapContractTest`.

## Verified build gates
- `:app:testDebugUnitTest`: PASS — 458 tests, 0 failures, 0 errors, 0 skipped.
- `:app:lintVitalRelease`: PASS.
- `:app:assembleRelease`: PASS.
- APK zipalign verification: PASS.
- APK Signature Scheme v2: PASS.
- APK Signature Scheme v3: PASS.
- Signing certificate SHA-256: `22d5e2a8bd48ddd2339abdc4748648b509e02d046524d6e118fbe05088155586`.

No destructive database fallback was added. The fix is an update over v140 and preserves the existing application ID and signing identity.
