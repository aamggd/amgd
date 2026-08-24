# v140 Validation Report

## Confirmed
- Exact uploaded v139 source used as baseline: PASS.
- v139 43→44 index-loss startup defect reproduced independently with SQLite: PASS.
- Broken ordering loses four Room-required vendor identity indexes after dropping renamed v138 table: REPRODUCED.
- Corrected 43→44 ordering recreates all six required indexes on the v44 lifecycle table: PASS.
- Historical v138 Vendor Identity row is preserved as `PROVISION`, credentialVersion=1: PASS.
- `SupportMigrations.kt` Kotlin syntax check with minimal AndroidX stubs: PASS.
- Regression contract test added for migration index ordering.
- Existing Support Session duration separation remains present.
- Room schema remains 44; no new DB migration added.
- No `fallbackToDestructiveMigration` introduced.

## Full Gradle gate
A build attempt was made with the project Gradle Wrapper, but this runtime cannot resolve `services.gradle.org`; Gradle 9.4.1 therefore could not be downloaded (`UnknownHostException`). This report does not claim the full 457 unit tests executed and does not claim a v140 APK was built in this runtime.
