# FUSH ERP Mobile v131 — Trusted Time / Clock Tamper Validation

## Identity
- applicationId: `com.fush.erp.recovery`
- versionCode: `131`
- versionName: `0.15.4.82-trusted-time-clock-tamper1`
- Room schema: `39`
- Database migration: none
- destructive migration: none

## Clock hardening
- v130 direct `System.currentTimeMillis()` uses in main source: 243
- v131 direct `System.currentTimeMillis()` uses in main source: 1
- The only remaining direct wall-clock read is private raw input inside `TrustedTimeService` and is checked against the monotonic anchor.
- `SystemClock.elapsedRealtime()` backs monotonic elapsed time.
- Sessions and recent reauthentication windows use monotonic elapsed time.
- Same-boot process restarts reuse a persisted monotonic anchor (BOOT_COUNT with boot-epoch fallback).
- Runtime wall-clock drift beyond 2 minutes is detected.
- Backward clock rollback across reboot is detected.
- If a previous trusted anchor exists and the device reboots with Android automatic time disabled, sensitive security use fails closed until time is trustworthy again.

## Protected future-date guards
- Production future dates
- Sales future dates
- Fixed-asset depreciation period end
- FX revaluation date
- Accounting period close
- Accounting year close

## Verification performed
- `TrustedTimeService.kt` + `SecurityPolicy.kt` compiled with Kotlin compiler against minimal Android API stubs: PASS.
- Existing source-contract tests that intentionally asserted the old raw clock were updated to the centralized trusted clock.
- Static scan confirms no `fallbackToDestructiveMigration` in main source.

## Build status
Per the user's current instruction, no APK build was performed for this delivery; source and repair patch only.
