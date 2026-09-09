# FUSH ERP Mobile v212 — Shipment Number Collision Hotfix

- Fixes `UNIQUE constraint failed: sales_shipments.shipmentNo` on secondary/cloud-synced phones.
- SHP numbering now reconciles the local sequence with persisted hydrated shipment numbers for the business date.
- Connected devices reserve `SALE_SHIPMENT` numbers in the company-wide Supabase document-number registry before insert.
- Existing cloud shipment numbers are backfilled to the registry and future shipment sync writes register the number automatically.
- No Room schema change: schema remains 53. No destructive migration.
