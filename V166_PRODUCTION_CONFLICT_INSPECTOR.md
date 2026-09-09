# FUSH ERP Mobile v166 — Production Conflict Inspector

- Baseline: v165 Inventory & Production Cloud Mirror.
- App ID unchanged: `com.fush.erp.recovery`.
- versionCode: 166.
- Room schema remains 46; no migration and no destructive fallback.
- Supabase schema remains the v165 inventory/production mirror; **no new SQL is required**.

## Change

v165 protected conflicting production documents but displayed the complete canonical document as one long line. v166 replaces that presentation source with a field-level comparison.

Production-order differences are now broken down into:
- order header fields,
- each production material,
- production batch quantities/dates/status,
- each material issue including quantity, unit cost, total cost, lot and expiry.

Differences are classified as:
- `BUSINESS`: quantity, cost, item, warehouse, lot, expiry, status and other business-impacting fields.
- `TIMING`: planned/closed/issue timestamps.
- `DESCRIPTIVE`: notes/reasons.

Issue rows are paired by material/item/issue-kind before comparison so a timestamp-only difference does not shift the whole issue list and create a cascade of false differences.

## Safety

This release is an inspector only. It does not overwrite either production copy, replay production posting, or change the inventory reconciliation policy.
