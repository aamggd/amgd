# FUSH ERP Mobile v191 — Admin Temporary Test Cleanup

- Base: v190 (`0.15.4.141-accountant-closed-period-cloud-hydration`).
- App ID remains `com.fush.erp.recovery`.
- Room schema remains 49; no destructive migration and no database recreation.
- Adds a one-hour ADMIN-only temporary cleanup session after fresh password re-authentication.
- The local ADMIN session does **not** require FSP2 and is accepted only by the two typed test-data cleanup commands:
  - delete a deliberately-created test sales invoice bundle and its directly related effects;
  - delete a deliberately-created test shipment expense when it is no longer actively allocated.
- The reason must explicitly state that the data is test/dummy data.
- General diagnose/rebuild/repost/correct-data tools remain restricted to provisioned `FUSH_SUPPORT` sessions.
- Existing FSP2 Vendor Support lifecycle remains available and unchanged for full vendor support access.
- Immutable support/governance audit is retained.
- v190 closed-period cloud hydration fix and prior sync/security fixes are preserved.
