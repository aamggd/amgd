# Phase 13.5 validation — Master data editing

## Scope validated in source
- Item/material editing keeps `code`, `category`, and `baseUnitId` immutable by copying the stored row and changing only editable fields.
- Finished goods keep mandatory lot and expiry tracking; missing finished-good shelf life falls back to 730 days.
- Unit editing changes Arabic/English names only and preserves the unit code.
- Warehouse editing changes Arabic/English names and location only and preserves the warehouse code.
- Customer editing preserves customer code and allows changing names, phone, address, province, channel, classification, currency, sales representative, credit permission, credit limit, and credit days.
- Customer credit days are capped by the existing 30-day sales rule.
- Existing receivables are not rewritten when customer credit settings change; the new settings are applied by future sales validation.
- Every edit writes an audit event with old/new values.
- No Room schema change is introduced; database version remains unchanged.
- Android version is incremented from versionCode 22 / Phase 13.4 to versionCode 23 / Phase 13.5.

## Build verification in this execution environment
- Source-diff validation: PASS.
- Required service/DAO/UI wiring present: PASS.
- Full Android Gradle compilation: NOT RUN here because this runtime does not currently expose an Android SDK/Gradle toolchain or dependency cache.
- Release APK generation/signing: NOT RUN here for the same reason.

## Required release gate before installation
1. Run Android unit tests.
2. Build `assembleRelease`.
3. Sign with the existing Fush ERP update signing certificate (same certificate used by Phase 13.4).
4. Verify APK signature and certificate match Phase 13.4.
5. Install over Phase 13.4 using Android Update; do not uninstall first.
6. Verify existing database data remains present.
