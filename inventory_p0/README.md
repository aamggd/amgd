# FUSH Inventory — P0 Ledger Invariant

Branch: `fush/inventory`

Baseline: validated central Phase 14.5.54 Printing Integrated source, artifact run `31909754750`, source tree `1b6af7bcaa86138ae75ea3d905db4bdba0fe04ff`.

## Scope

This patch implements **P0 only** from the inventory branch plan:

> Inventory balance at a cutoff = the sum of persisted stock movements for the item and warehouse whose `movementDate <= asOf`.

It adds a canonical historical `StockDao.balanceAt(...)` query, exposes a read-only `InventoryService.balanceAt(...)` path, and adds deterministic unit tests for cutoff semantics and invalid movement values.

## Explicit non-scope

- No stock movement type/reference unification (P1).
- No UOM changes (P2).
- No lot/genealogy changes (P3).
- No count workflow changes (P4).
- No historical cost suite expansion (P5).
- No accounting logic changes.
- No Room schema/entity/migration changes.
- No versionCode/versionName changes.
- No signing material.

## Known existing issue carried forward

`AppContainer.repairLegacyFinishedGoods()` still performs silent historical-data repair during startup in the 14.5.54 baseline. The branch plan explicitly requires moving this to an explicit reviewed repair/migration path. P0 does not modify that behavior; it remains a tracked issue for a later scoped phase and must not be forgotten.

## Patch integrity

`inventory-p0.patch` SHA-256:

`e169dc89ba6d6598c30f4937f4e6f0013e2e8498137381b8569eb9e3f08b8f73`

The CI workflow reconstructs the exact validated central 14.5.54 source artifact, verifies the source tree, applies the patch, runs all unit tests, builds release, and verifies the fixed Application ID and absence of destructive migration.
