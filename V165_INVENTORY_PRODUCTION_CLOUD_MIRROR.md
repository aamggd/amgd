# FUSH ERP Mobile v165 — Inventory & Production Cloud Mirror

Baseline: v164 Cloud Document Number Guard.

## Scope
- Keeps `applicationId = com.fush.erp.recovery`.
- Raises `versionCode` to 165.
- Keeps Room schema 46; no destructive migration.
- Adds OWNER/ADMIN-authored cloud mirror for recipes, production orders, production materials, production batches, and production material issues/corrections.
- Adds current lot-level inventory snapshot by company / warehouse / item / lot / expiry.
- Employee phones reconcile Room stock balances to the authoritative company snapshot through explicit `CLOUD_SYNC_*` stock movements. Sales, purchase, and production posting services are not replayed.
- Existing local production documents are compared; differences are reported as conflicts and neither copy is overwritten.
- Adds manual sync UI plus automatic foreground synchronization after existing v158/v159/v161/v163 waves.

## Safety boundary
- v165 is not yet a write-enabled production/inventory collaboration wave for employee phones. OWNER/ADMIN remains the publisher of the company production and stock state.
- General journal entries and generic treasury vouchers are not synchronized in this wave.
- Quality specifications/checks and non-conformance records remain outside this wave.

## Supabase migration
Run `cloud/supabase/008_inventory_production_mirror.sql` before installing/using v165 sync.
