# Fush ERP Phase 14.5.7 — Warehouse Reorder + Backdated Transfer Safety

This phase continues the non-security correction plan from Phase 14.5.6.

## Changes
- Moves reorder policy from one item-level threshold to a warehouse + item policy table.
- Migrates existing item reorder levels into the normal stocking warehouse(s) without rewriting stock history.
- Seeds the same policy defaults on fresh installations.
- Adds an Arabic UI to view and edit reorder thresholds per warehouse/item.
- Reorder alerts use available stock in the selected warehouse, excluding expired and non-accepted lots and future-dated movements.
- Dashboard low-stock counts are warehouse/item cases.
- Removes the misleading item-global reorder status from the aggregated inventory valuation report.
- Prevents posting a backdated warehouse transfer when that transfer would make any later historical lot balance negative.
- Revalidates that rule both when adding a transfer line and at final posting.

## Database
- Room schema 19 -> 20.
- New table: `warehouse_item_policies`.

## Version
- versionCode 46
- versionName `0.15.4.7-phase14.5-warehouse-reorder-backdate`
