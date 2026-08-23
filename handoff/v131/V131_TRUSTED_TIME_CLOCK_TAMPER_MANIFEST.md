# v131 Trusted Time / Clock Tamper

Baseline: v130 Generic Production Batch Number

Identity:
- applicationId: `com.fush.erp.recovery`
- versionCode: `131`
- versionName: `0.15.4.82-trusted-time-clock-tamper1`
- Room schema: `39`

Repair:
- Adds `TrustedTimeService` backed by Android `SystemClock.elapsedRealtime()`.
- Persists a same-boot monotonic anchor using BOOT_COUNT with a boot-epoch fallback.
- Detects runtime Date/Time drift and backward rollback.
- Security authentication and reauthentication fail closed on detected time tampering.
- Sessions and recent reauthentication windows use elapsed realtime.
- Production, sales, fixed-asset, FX revaluation, period-close and year-close future guards require trusted time.
- Direct `System.currentTimeMillis()` references in main source reduced from 243 to 1 raw monitored sample inside TrustedTimeService.
- No database migration and no `fallbackToDestructiveMigration`.

Local final artifacts:
- `FushERP-Mobile-v131-TrustedTimeClockTamper-FINAL-Source.zip`
  SHA-256: `6d9c6f63bd713ad00e36160140bda8b749c9a6f17e7dcfd767495d22ae47097d`
- `V131_TRUSTED_TIME_CLOCK_TAMPER.patch`
  SHA-256: `c65fbedccc02140e25334cacf93101d02e874ee44a7f5a0a382e7386bad648b3`

Build status: intentionally not built in this delivery because the user requested repair/source files only.