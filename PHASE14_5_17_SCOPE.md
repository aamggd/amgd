# Phase 14.5.17 — Warehouse and Master Data Controls

- Adds creation of additional warehouses with automatic `WH-###` codes.
- Shows active and inactive items, units, warehouses and item-unit conversions in master data.
- Adds audited activate/deactivate controls instead of destructive deletion.
- Prevents deactivating a warehouse or item while inventory remains.
- Prevents deactivating an item used by an active production recipe.
- Prevents deactivating a unit used as an active base unit or active conversion.
- Adds full item-unit conversion maintenance: factor, purchase/sale permissions, barcode, active status.
- Keeps the base-unit conversion factor fixed at 1 and prevents deactivating the base conversion.
- Prevents duplicate nonblank barcodes across unit conversions.
- New purchases and sales reject inactive items at service level.
- Creation and master-data status changes are recorded in the audit trail.
- Room schema remains 23; no migration is required.
- Android version: 0.15.4.17-phase14.5-master-data-controls (versionCode 56).
