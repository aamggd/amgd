# v195 — Accounting Cloud Source Identity Hotfix

- Baseline: v194.
- App ID unchanged: `com.fush.erp.recovery`.
- `versionCode=195`.
- Room schema remains 49; no destructive migration.
- Fixes cloud accounting hydration failing with `ACCOUNTING_SOURCE_ID_REQUIRED`.
- Every non-MANUAL cloud journal now receives a non-empty deterministic source identity before SQLite insert.
- Local business-document source IDs are preferred whenever they can be resolved.
- Unresolved portable references use a deterministic `cloud-sync:<sourceType>:<reference>` fallback instead of null.
- Historical TREASURY_* hydration keeps the dedicated `cloud-legacy:` compatibility identity and STAGING alias.
- Unknown remote source types become explicit sync conflicts rather than aborting the entire accounting sync.
- No accounting database guard is disabled or weakened.
