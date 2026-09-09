# v140 Validation Report

## Confirmed
- Exact uploaded v139 source used as baseline: PASS.
- Root cause reproduced with SQLite: PASS.
- Broken v139 migration loses 4 required indexes after dropping renamed v138 table: REPRODUCED.
- Corrected 43->44 migration creates all 6 Room-required vendor identity indexes: PASS.
- Historical v138 Vendor Identity row survives migration as PROVISION / credentialVersion=1: PASS.
- `SupportMigrations.kt` Kotlin syntax compile with minimal AndroidX stubs: PASS.
- Regression contract test added for index recreation ordering: PASS by source contract.
- Existing Support Session separation (`hasActiveSupportSession` / `shouldExpireSupportShell`) retained.
- Room schema remains 44; no new database migration required.
- No `fallbackToDestructiveMigration` introduced.

## Full Gradle gate
Attempted with the project's Gradle Wrapper. The environment cannot resolve `services.gradle.org`,
so Gradle 9.4.1 could not be downloaded (`UnknownHostException`) and the complete Android Gradle
unit-test / assembleRelease gate could not execute in this runtime.

Accordingly this report does NOT claim the full 457 tests passed and does NOT claim a v140 APK was
built in this runtime. The source-level migration defect itself was reproduced and corrected with an
independent SQLite migration smoke test.
