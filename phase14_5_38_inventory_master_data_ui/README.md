# FUSH ERP Mobile — Phase 14.5.38 Inventory & Master Data UI

Baseline: `0.15.4.37-ui-accounting-treasury`
Target: `0.15.4.38-ui-inventory-master-data`
Branch: `fush/ui-professional-redesign`

## Purpose
Fifth UI professionalization package focused on Inventory, Warehouses, Items, Units and Unit Conversions.

## Patch order
1. `01_scope.patch`
2. `02_build_gradle.patch`
3. `03_inventory_workspace.patch`
4. `04_balances_alerts.patch`
5. `05_counts_lots_movements_transfers.patch`
6. `06_master_data_workspace.patch`
7. `07_master_data_lists.patch`

Apply from the root of a source tree that already contains Phase 14.5.37:

```bash
cat phase14_5_38_inventory_master_data_ui/patches/*.patch > /tmp/fush-ui-14.5.38.patch
git apply --check /tmp/fush-ui-14.5.38.patch
git apply /tmp/fush-ui-14.5.38.patch
```

## UI scope
- Inventory KPIs and clearer quick actions.
- Searchable/filterable warehouse balances with sellable, expired and controlled quantities.
- Risk-aware reorder and expiry alerts.
- Professional inventory count, lot, movement and transfer cards.
- Master-data KPIs, section hierarchy and search.
- Professional item, unit, warehouse and unit-conversion status cards.

## Safety boundary
Presentation only. No stock quantity, lot allocation, inventory costing, count posting, transfer posting, opening-stock posting, reorder calculations, master-data validation or database schema are intentionally changed.

## Validation
All seven patches were applied sequentially to Phase 14.5.37 with `git apply --check`. The resulting changed files exactly match the Phase 14.5.38 working source. A whitespace/conflict-marker diff check on the resulting changed files reports no errors. Android build validation remains separate because the supplied source package does not include the project Gradle wrapper/Android SDK toolchain in this environment.
