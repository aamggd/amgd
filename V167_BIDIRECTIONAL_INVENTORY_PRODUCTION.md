# v167 — Bidirectional Inventory & Production

## Goal
Allow authorized operational phones to send inventory changes and newly closed production orders back to the company cloud without silently overwriting newer cloud state.

## Production safety
- OWNER / ADMIN / PRODUCTION can publish a **newly closed** production order when that order number does not yet exist in the cloud.
- Employee devices do not update/overwrite an existing cloud production order.
- Existing differences continue to be surfaced by the v166 field-level conflict inspector.

## Inventory safety
- OWNER / ADMIN / PRODUCTION / INVENTORY may publish a lot-level inventory balance through `fush_publish_inventory_snapshot_cas`.
- The phone only attempts outbound publication for lots touched by non-cloud stock movements since its prior outbound cursor.
- If Supabase `updated_at` is newer than the phone cursor, the app treats the lot as a conflict instead of overwriting it.
- If a lot only changed in the cloud, the phone reconciles its Room balance with explicit `CLOUD_SYNC_*` movements.
- Cloud-generated adjustment movements are never re-published, preventing feedback loops.

## Accounting boundary
This release synchronizes operational production documents and physical inventory state. It does **not** replay general-ledger journals on other phones. General accounting remains a separate cloud-sync wave.

## Database
Android Room remains schema 46. Supabase requires `cloud/supabase/009_bidirectional_inventory_production.sql` after v165 SQL.
