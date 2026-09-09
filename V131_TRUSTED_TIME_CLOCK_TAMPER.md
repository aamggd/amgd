# v131 Trusted Time / Clock Tamper Protection

- Business time zone remains fixed to `Asia/Aden`.
- Added `TrustedTimeService` backed by `SystemClock.elapsedRealtime()` for monotonic time.
- Device wall-clock changes during a boot no longer move ERP/security time.
- The monotonic anchor is persisted and reused across app process restarts in the same boot.
- Backward wall-clock changes across reboot are detected.
- After a reboot, if a previous trusted anchor exists and Android automatic time is disabled, security-sensitive use is blocked until automatic time is restored.
- Sessions and recent reauthentication windows use elapsed realtime, not wall-clock time.
- Security authentication/reauthentication fail closed when clock tampering is detected.
- Future-date guards in production, sales, fixed assets, FX revaluation, period close and year close require trusted time.
- Direct `System.currentTimeMillis()` usage in application main source is reduced to the single raw wall sample inside `TrustedTimeService`.
- Room schema remains 39; no database migration and no destructive migration.
