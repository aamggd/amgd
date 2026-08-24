# v137 Validation Report — Safe Support Repair

## Identity

- applicationId: `com.fush.erp.recovery`
- versionCode: `137`
- versionName: `0.15.4.88-safe-support-repair1`
- Room schema: `42`
- Migration: `41 -> 42`, additive/non-destructive

## Release gates executed

- Kotlin / KSP / Room generation: PASS
- `:app:testDebugUnitTest`: PASS
- JUnit result: **432 tests / 0 failures / 0 errors / 0 skipped**
- `:app:assembleRelease` (lint tasks excluded from this build command): PASS
- `:app:lintVitalRelease`: PASS
- `zipalign -c -v 4`: PASS
- APK signature v2: PASS
- APK signature v3: PASS
- signer SHA-256: `22d5e2a8bd48ddd2339abdc4748648b509e02d046524d6e118fbe05088155586`
- signed APK package: `com.fush.erp.recovery`
- signed APK versionCode: `137`
- signed APK versionName: `0.15.4.88-safe-support-repair1`

## Safe Repair checks

- semantic source reconstruction runs before `STAGING -> POSTED`: PASS
- exact account/debit/credit line comparison: PASS by implementation + contract tests
- entry date/currency/exchange-rate comparison: PASS by implementation + contract tests
- SALE expected journal includes source-derived COGS and free-quantity cost through sales allocations: PASS
- CUSTOMER_RECEIPT expected journal includes settlement discount and realized FX: PASS
- SUPPLIER_PAYMENT expected journal includes realized FX: PASS
- sales/purchase returns are reconstructed from source: PASS
- unknown/unsupported source fails closed: PASS
- historical cash source without treasury provenance fails closed: PASS
- TOCTOU guard rechecks source/journal immediately before POST: PASS
- failed repair business mutation rolls back: PASS
- failed Support attempt is persisted separately after rollback: PASS
- Support snapshots/audit/validation remain immutable through existing DB triggers: PASS

## Session / identity checks

- recent reauthentication required in backend before Support Session activation: PASS
- only 1h / 6h / 24h sessions accepted: PASS
- legacy indefinite (`Long.MAX_VALUE`) Support Session treated as inactive: PASS
- normal local user creation cannot create FUSH_SUPPORT: PASS
- normal local role assignment cannot assign/remove FUSH_SUPPORT: PASS
- local password reset / active-state management for FUSH_SUPPORT blocked: PASS
- FUSH_SUPPORT permissions cannot be edited from company role management: PASS

## Migration smoke

The 41->42 SQL additions were executed against SQLite smoke tables and all expected nullable treasury provenance columns were created successfully. Historical data is not rewritten and NULL provenance is intentionally preserved.

## Known non-blocking warnings

Build reports existing deprecation/index advisory warnings. No warning observed in this gate caused compilation, test, lint-vital, or release-build failure.

## Explicit non-claims

- Instrumented Android tests were not used as a v137 release gate.
- v137 is not remote support.
- v137 does not provide cryptographic Vendor Support provisioning/verification; it prevents local company-admin provisioning and preserves existing Support identities.
- future shared-database multi-company row-level isolation is not implemented by this release.
