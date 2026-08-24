# FUSH ERP Mobile v136 — Release Handoff

## Baseline
Built strictly over the uploaded `v135 AuditTrailRoomQueryFix FINAL` source. No merge to master/Central is implied by this package.

## Identity
- `applicationId = com.fush.erp.recovery`
- `versionCode = 136`
- `versionName = 0.15.4.87-support-maintenance-mode1`
- Room schema `41`
- Migration `40 -> 41` additive/non-destructive

## Scope
See `V136_SUPPORT_MAINTENANCE_MODE.md` and `V136_VALIDATION_REPORT.md`.

## Data safety
- Historical business records are not deleted or rewritten by migration.
- No `fallbackToDestructiveMigration`.
- Support repair operations are typed commands; no generic DB editor is exposed.
- Immutable Support snapshots, audit logs and validation results are protected from UPDATE/DELETE.
- Financial repair favors validation/repost/correction patterns and preserves original source transactions.

## Final verification
- KSP/Room: PASS.
- `compileDebugKotlin`: PASS.
- **427 JVM unit tests passed / 0 failed / 0 errors / 0 skipped.**
- `assembleRelease`: PASS.
- `zipalign`: PASS.
- APK v2/v3 signature verification: PASS.
- Signer SHA-256: `22d5e2a8bd48ddd2339abdc4748648b509e02d046524d6e118fbe05088155586`.

## Instrumentation
23 Android instrumented test annotations are present in source; actual device/emulator instrumentation was not executed in this environment.

Signing material and passwords are not included in the source archive.
