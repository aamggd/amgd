# FAZ v1.0.23 Supplier Item Movement & Sell-Through Report

## Goal
Build an auditable supplier-item movement report without guessing which supplier supplied a sold unit when the same SKU is purchased from multiple suppliers.

## Compatibility rules
- Upgrade Room 52 -> 53 only; no destructive migration.
- Preserve every existing Item / ProductVariant identity and all historical transaction IDs.
- Normal supplier AP remains purchase-based; sales do not change supplier debt.
- Historical stock that cannot be tied to a supplier with evidence is `UNATTRIBUTED`, never silently assigned.
- New movements after schema 53 must retain supplier provenance end-to-end.

## Data design
Create two immutable provenance tables:
1. `supplier_stock_sources`: stock source layers. New PURCHASE creates an EXACT source linked to supplier and purchase line. Migration creates LEGACY_OPENING / UNATTRIBUTED sources for pre-v53 on-hand lot balances.
2. `supplier_stock_allocations`: signed allocations from a source to consuming/restoring stock movements. SALE/PURCHASE_RETURN are negative; SALES_RETURN restores the original source with positive quantity. Generic business reference fields retain the original allocation identity.

A SALE stock movement may have multiple supplier allocation rows, so a single SKU/lot sold quantity can be split correctly between suppliers.

## Gate 1 — Provenance core + math (TDD)
Files:
- Create `app/src/main/java/com/fush/erp/data/entity/SupplierStockProvenanceEntities.kt`
- Create `app/src/main/java/com/fush/erp/data/dao/SupplierStockProvenanceDao.kt`
- Create `app/src/main/java/com/fush/erp/data/SupplierStockProvenanceMigration.kt`
- Create `app/src/main/java/com/fush/erp/domain/SupplierStockProvenanceService.kt`
- Create `app/src/main/java/com/fush/erp/domain/SupplierItemMovementMath.kt`
- Modify `FushDatabase.kt`, `AppContainer.kt`, `PurchaseService.kt`, `SalesService.kt`
- Create tests `SupplierItemMovementMathTest.kt`, `SupplierStockProvenanceContractTest.kt`

TDD acceptance:
- RED first: same SKU sourced A=10, B=20; selling 6 from A must not mark B as sold.
- GREEN: split one sale across A/B sources while preserving exact quantities.
- Purchase return consumes only its original purchase source.
- Sales return restores the same original supplier source allocation.
- Legacy opening remains UNATTRIBUTED.
- Sum of provenance allocations for each new stock movement equals movement quantity.

## Gate 2 — Report query + Product Master hierarchy
Files:
- Modify `ReportEntities.kt`, `ReportDao.kt`.
- Add supplier/item rows with Family -> Brand -> Variant/SKU display data.
- Add opening, received, purchase return, net received, sold, sales return, net sold, closing exact supplier stock, cost, revenue, COGS, gross profit, sell-through, last sale, days idle, movement class, attribution coverage.
- Keep unattributed quantity visible as a separate KPI/row, never merged into a supplier.

## Gate 3 — Transfers / damage / adjustments provenance
- Preserve supplier source across warehouse transfer out/in and reversals.
- Allocate damage/issues/negative adjustments against source layers using the existing lot/FEFO order.
- Positive unsupported adjustments become UNATTRIBUTED unless a source is explicitly known.

## Gate 4 — UI + export
- Add `حركة أصناف الموردين` inside inventory reports.
- Filters: supplier/all, date, warehouse, Family, Brand, Variant/SKU, movement class.
- KPI cards: received, net sold, closing, remaining cost, sales revenue, gross profit, sell-through, unattributed.
- Reuse existing ReportExportDocument/ReportExportTable for print/PDF/Excel/preview.

## Final verification
- `:app:testDebugUnitTest`
- `:app:assembleRelease`
- Room schema 53 generated and parsed.
- Upgrade migration 52->53 test/contract passes; no destructive migration.
- ProductVariant.id == Item.id contract remains intact.
- Signed APK uses the same permanent FAZ certificate; verify v2/v3, package `com.faz.solar`, versionCode increment, versionName v1.0.23.
