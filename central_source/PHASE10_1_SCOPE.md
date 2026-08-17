# Phase 10.1 Hotfix — Master Data Visibility and Automatic Numbering

This hotfix keeps Phase 10 inventory features and adds:

- A clearly visible `الأصناف` navigation destination with sections for materials/items, units and warehouses.
- A shortcut from advanced inventory to item/unit management.
- Add-unit flow on Android.
- Add-item flow with automatic code generation and base-unit conversion creation.
- Automatic supplier codes: `SUP-000001`.
- Automatic customer codes: `CUS-000001`.
- Automatic unit codes: `UNT-001`.
- Automatic new item codes by category: `RM-000001`, `PK-000001`, `FG-000001`.
- Automatic purchase invoice numbers: `PINV-YYYYMMDD-####`.
- Automatic purchase return numbers: `PRET-YYYYMMDD-####`.
- Automatic sales invoice numbers: `SINV-YYYYMMDD-####`.
- Automatic sales return numbers: `SRET-YYYYMMDD-####`.
- Automatic production order numbers: `PROD-YYYYMMDD-####`.
- Database-backed sequence table and Room migration 10 -> 11.
- Existing legacy codes remain unchanged.
- Numbers are generated inside the same database transaction as the saved record, so a failed save rolls back the sequence increment.
