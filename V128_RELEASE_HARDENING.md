# FUSH ERP Mobile v128 — Release Hardening

Baseline: v127 SearchableDropdownKeyboardFix.

## Identity
- applicationId: `com.fush.erp.recovery`
- versionCode: `128`
- versionName: `0.15.4.79-release-hardening1`
- Room schema: `39` (unchanged; no schema migration)
- Business time zone: `Asia/Aden`

## Scope implemented
- Printing hardening: explicit Activity requirement before launching Android Print UI.
- Sharing hardening: stream URI is also placed in ClipData and read permission is granted.
- Attachments redesign: new attachments are copied into managed FUSH storage; they can be opened, shared, and exported.
- Backup portability: managed attachment files are included in portable encrypted backup format v4; legacy external references are migrated into managed storage before backup when accessible.
- CASH_REFUND statement/report handling corrected to avoid double AR effect.
- Finished goods require a positive shelf life.
- 60/200 ml report hardcoding removed; reports are product-driven.
- Accepted Production KPI uses production-receipt/accepted timing instead of manufacture-date-only semantics.
- Near-expiry warning window is configurable (default 60 days, valid 1..3650).
- ACCOUNTANT default permissions are separated from treasury vouchers, cash counts, customer collections/discounts, supplier payments, and geography management.
- Business-date policy is pinned to `Asia/Aden`, independent of device time-zone changes.
- Printing/sharing/attachment Android instrumentation tests are present in source.

## Explicit exclusions
- Historical production data difference 3,669.10 was intentionally excluded at user request.
- Running Android instrumented tests on an emulator/device is not a release gate for this build, per user request.

## Release verification gate
- Full JVM unit tests must pass on the exact final source.
- Android test APK compilation should be attempted when cached dependencies are available.
- Release APK must compile/package, be zipaligned, and signed with the permanent FUSH certificate.
- No `fallbackToDestructiveMigration` and no database deletion is introduced.
