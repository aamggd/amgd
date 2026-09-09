# Phase 13.2 — Material Availability UX Fix

- Detailed material availability dialog for planned production orders.
- Shows Arabic item name, code, required quantity, available quantity, base unit, and shortage.
- No partial reservation when any material is short.
- Automatically reserves all materials only when every component is available.
- Direct button to open Inventory when shortages exist.
- Improved service error text uses item name/code instead of numeric item id.
- No database schema change; existing data is preserved.
