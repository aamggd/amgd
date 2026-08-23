# FUSH ERP Mobile v133 — Temporal Integrity & Portable Settings

Base: user-supplied v132 IntegratedReleaseHardening-TrustedTime.
Branch: `fush/v133-temporal-integrity-portable-settings`.
No merge to master.

Identity:
- applicationId: `com.fush.erp.recovery`
- versionCode: 133
- versionName: `0.15.4.84-temporal-integrity-portable-settings1`
- Room schema: 39 (unchanged; no migration)

Closed in v133 source:
- HTTPS UTC best-effort validation added to TrustedTimeService; elapsedRealtime remains the offline monotonic clock.
- First-launch/reboot sensitive operations fail closed when no trusted anchor exists and Android automatic time is disabled, until trusted time becomes available.
- Raw no-arg Date() usages removed from app main source; no-arg Calendar.getInstance() removed; System.currentTimeMillis() isolated to TrustedTimeService raw wall-clock read.
- Future-date guards added for customer receipts, sales returns, supplier payments, purchase returns and receipt/payment reversals.
- Explicit editable return dates added to Sales Return and Purchase Return UI.
- Searchable selection stale-object mismatch is globally fail-safe; Expense selections explicitly clear backing objects.
- `near_expiry_days` is carried by portable backup manifest and restored after database restore.
- Stale general source/build docs updated for v133.

Static/source validation performed:
- exact Date() count in app/src/main/java: 0
- exact no-arg Calendar.getInstance() count: 0
- System.currentTimeMillis() count: 1, only TrustedTimeService
- fallbackToDestructiveMigration count: 0
- Room remains 39
- AndroidManifest parses successfully
- clean patch dry-run against the exact supplied v132 source: PASS
- test annotations in source: 405; full Gradle suite was not rerun in this source-only workflow, so no new v133 pass-count is claimed.

Known limitation:
HTTPS trusted UTC uses normal TLS validation. If a device clock is so incorrect that TLS certificate validation cannot succeed, sensitive operations remain fail-closed rather than accepting the untrusted wall clock.

Explicitly NOT closed in v133:
Manufacturing Overhead Allocation. This requires a separate accounting/valuation design because it changes WIP, finished-goods inventory valuation, COGS, reversals and historical reporting.

Local delivery SHA-256:
- Source ZIP: `4cccdd8886e2e5bb5f35f59e3e8851ea6befc9d1c55dd9d26da9dc52e6ef7a7b`
- Patch: `29c65b4e2d1c2f258fdc40303e6581d9bf0fbe6a052690bababc1ff668a0c75d`

The compressed/base64-encoded patch is stored next to this handoff as `V133_TEMPORAL_INTEGRITY_PORTABLE_SETTINGS.patch.gz.b64`.