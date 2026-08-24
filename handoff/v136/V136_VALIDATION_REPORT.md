# v136 Validation Report — Support / Maintenance Mode

## Identity
- applicationId: `com.fush.erp.recovery`
- versionCode: `136`
- versionName: `0.15.4.87-support-maintenance-mode1`
- Room schema: `41`
- Migration: `40 -> 41`, additive/non-destructive

## Checks completed in this environment
- Exact baseline verified as uploaded v135 source.
- Static support security-contract checks: PASS.
- XML resource parse: PASS.
- Migration 40->41 SQLite smoke: PASS.
- Immutable Support snapshot/audit/validation UPDATE/DELETE guards: PASS.
- Direct Kotlin `SupportPolicy` duration/expiry checks: PASS.
- No `fallbackToDestructiveMigration`: PASS.
- No generic `execSQL`/DELETE/DROP mutation in `SupportService`: PASS.
- `FUSH_SUPPORT` permission separation: PASS by source contract.
- Backend active Ticket/Session/user/role/permission/time re-check present: PASS by source contract.
- Snapshot before typed repair + validation + immutable Support Audit: PASS by source contract.
- Cross-module validation for SALE/PURCHASE/RECEIPT/PAYMENT/RETURNS: PASS by source contract.
- Unit test annotations present in `app/src/test`: 427.

## Full Gradle build/test
A local Gradle build attempt was made. It did not start because Gradle 9.4.1 was not locally cached and the environment could not resolve `services.gradle.org` (`UnknownHostException`). Therefore this report does **not** claim 427 tests executed and does **not** claim a locally built APK.

A GitHub Actions build may be used as the fallback release gate; signing material must remain local and must not be uploaded to GitHub.
