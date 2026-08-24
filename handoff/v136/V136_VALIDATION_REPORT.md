# v136 Validation Report — Support / Maintenance Mode

## Identity
- applicationId: `com.fush.erp.recovery`
- versionCode: `136`
- versionName: `0.15.4.87-support-maintenance-mode1`
- Room schema: `41`
- Migration: `40 -> 41`, additive/non-destructive

## Support security / data safety checks
- Exact baseline verified as uploaded v135 source.
- Static support security-contract checks: PASS.
- XML resource parse: PASS.
- Migration 40->41 SQLite smoke: PASS.
- Immutable Support snapshot/audit/validation UPDATE/DELETE guards: PASS.
- Direct Kotlin `SupportPolicy` duration/expiry checks: PASS.
- No `fallbackToDestructiveMigration`: PASS.
- No generic DB/table editor or unrestricted direct mutation in `SupportService`: PASS.
- `FUSH_SUPPORT` permission separation: PASS.
- Backend active Ticket/Session/user/role/permission/time re-check: PASS.
- Snapshot before typed repair + validation + immutable Support Audit: PASS.
- Cross-module validation contracts for SALE/PURCHASE/RECEIPT/PAYMENT/RETURNS: PASS.

## Gradle / Room / compiler gate
- JDK: Temurin 17.0.20+8.
- Gradle: 9.4.1 standalone.
- Android SDK: API 36 / Build Tools 36.0.0.
- KSP/Room generation: PASS.
- `compileDebugKotlin`: PASS.
- A stale historical contract test that hard-coded `FUSH_DB_SCHEMA_VERSION = 40` was corrected to assert schema >= 40; this was a test-maintenance fix only and did not change accounting/discount behavior.

## Unit tests
- Test result XML suites: 104.
- JVM tests executed: **427**.
- Passed: **427**.
- Failed: **0**.
- Errors: **0**.
- Skipped: **0**.

## Release build / signing
- `assembleRelease` (lintVital tasks excluded per release build procedure): PASS.
- `zipalign -c -v 4`: PASS.
- APK Signature Scheme v2: PASS.
- APK Signature Scheme v3: PASS.
- Signer certificate SHA-256: `22d5e2a8bd48ddd2339abdc4748648b509e02d046524d6e118fbe05088155586`.
- Package identity verified from APK: `com.fush.erp.recovery`, versionCode `136`, versionName `0.15.4.87-support-maintenance-mode1`, targetSdk 36.

## Instrumentation
- Android instrumented tests present in source: 23.
- They were not executed in this environment and are not claimed as passed.

## Architecture boundary
Support/Maintenance Mode provides controlled, ticketed access **inside the customer database on the device where the support account is authenticated**. It does not create remote connectivity to a customer's phone. Remote FUSH access would require a separate secure backend/device-pairing transport and is outside v136.
