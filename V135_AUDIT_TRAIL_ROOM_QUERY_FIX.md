# FUSH ERP Mobile v135 — Audit Trail Room Query Fix

Baseline: `v134 AuditTrail-FreeQuantity FINAL`.

## Preserved
- All v128 Release Hardening fixes already integrated in the v134 baseline.
- v129–v133 fixes.
- v134 Audit Trail and Free Quantity functionality.
- App ID `com.fush.erp.recovery`.
- Room schema 40 (unchanged; no migration).

## Fix
Room/KSP rejected the Audit Trail CTE because `action` was used as an unquoted SQL alias. The read projection now exposes the SQL column as `actionCode`, while the Kotlin model keeps the public property name `action` through `@ColumnInfo(name = "actionCode")`. Audit filters/searches use `actionCode`, eliminating the parser conflict without changing audit data or schema.

## Identity
- versionCode: 135
- versionName: `0.15.4.86-audit-trail-room-query-fix1`
- Room: 40
