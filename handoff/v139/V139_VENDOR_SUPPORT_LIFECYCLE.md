# v139 — Vendor Support Lifecycle + Support Session Duration Hardening

Baseline for code changes: uploaded `v138 VendorSupportProvisioning FINAL` source only.

Release identity:
- applicationId `com.fush.erp.recovery`
- versionCode `139`
- versionName `0.15.4.90-vendor-support-lifecycle1`
- Room `44`
- Migration `43 -> 44`

Closed lifecycle items:
1. Signed append-only `REBIND` after Portable Restore to another installation; historical Vendor Identity rows are preserved.
2. Legacy `FUSH_SUPPORT` without Vendor Identity is fail-closed for login, permissions and Support Session activation until signed `LEGACY_CLAIM`.
3. Signed credential `ROTATE` creates a superseding immutable identity row and invalidates stale sessions via `sessionVersion`.
4. Signed Vendor public-key supersession (`KEY_ROTATE` / `FSK1`) stores append-only trust history; only the latest trusted key verifies new packages.
5. Rejected provisioning writes immutable `VENDOR_SUPPORT_PROVISION_FAILED` with sanitized failure code + lifecycle purpose only; no package/token/hash/salt secrets are logged.
6. Vendor lifecycle Audit events are classified under Support and have management-friendly Arabic labels.

Support Session duration fix:
- 1h/6h/24h company grants are authoritative for FUSH_SUPPORT maintenance access.
- Generic app idle/absolute timers no longer truncate an active Support Session.
- Backend commands still fail closed exactly at trusted Support Session expiry.
- When the grant ends, maintenance access is removed immediately; the shell starts a fresh normal idle window instead of retroactively logging out because the generic absolute timer elapsed during maintenance.

Repair Commands were not expanded.

Remote limitation: v139 is still Local Support Mode. A local-only rebind cannot remotely revoke a separate old physical device copy; that requires the future Remote Support Backend/revocation architecture.
