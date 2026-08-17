# FUSH Inventory P1 — Handoff

Status: **COMPLETE / TESTED / READY FOR CENTRAL REVIEW**

## Phase

`Inventory P1 — canonical stock movement types, source references, orphan/duplicate prevention`

Official P1 scope only: unify stock movement types and source references and prevent orphan stock movements. P2 UOM work and later phases were not started.

## Exact starting baseline

- Branch/ref supplied by Central: `fush/integration-current@ddae764a236cb1916a46e194ccb2d3c24ca6b919`
- Central source tree: `30b028b75d6463c07afd0419429f53c7937fabb1`
- Application ID: `com.fush.erp.recovery`
- Central Room schema: `35`

The P1 application worktree was reconstructed from this exact Central tree. Old Inventory P0 application source was not used as the P1 base. The old inventory branch commit remains ancestry only so `fush/inventory` could advance without a force push.

## Validated candidate

- Candidate validation branch SHA: `ee5667513db7d3088d85cb964872852fec740872`
- GitHub Actions workflow: `FUSH Inventory P1 Candidate Validation`
- Workflow run: `31982480355`
- Job: `95251616933`
- Result: **SUCCESS**
- Artifact: `FushERP-Inventory-P1-Candidate`
- Artifact ID: `9272870832`
- Artifact ZIP SHA-256: `1ab3b63dd12846c093ac8b0cad1bcb4880034b16575be8865e368e0c82a24bf7`
- Exact application patch SHA-256: `4f78ee7c37b439efd9427554433433822416fd669474224cdec00fbb35b6bdc0`
- Unsigned test APK SHA-256: `e314648845189537de75b2aa77af1b031491327954465dfa639dd4cc4bc0679c`

## Application/test files changed by the exact patch

1. `app/schemas/com.fush.erp.data.FushDatabase/36.json`
2. `app/src/main/java/com/fush/erp/data/AppContainer.kt`
3. `app/src/main/java/com/fush/erp/data/FushDatabase.kt`
4. `app/src/main/java/com/fush/erp/data/Migrations.kt`
5. `app/src/main/java/com/fush/erp/data/dao/PurchaseDaos.kt`
6. `app/src/main/java/com/fush/erp/data/entity/PurchaseEntities.kt`
7. `app/src/main/java/com/fush/erp/domain/AdvancedInventoryService.kt`
8. `app/src/main/java/com/fush/erp/domain/InventoryService.kt`
9. `app/src/main/java/com/fush/erp/domain/ProductionService.kt`
10. `app/src/main/java/com/fush/erp/domain/PurchaseService.kt`
11. `app/src/main/java/com/fush/erp/domain/SalesService.kt`
12. `app/src/main/java/com/fush/erp/domain/StockMovementPolicy.kt` (new)
13. `app/src/main/java/com/fush/erp/domain/StockMovementWriter.kt` (new)
14. `app/src/test/java/com/fush/erp/domain/StockMovementPolicyTest.kt` (new)

Branch tooling/docs used to reconstruct and validate the patch are under `inventory_p1/` and `.github/workflows/` and are not Central application source changes.

## Business Logic

P1 establishes one canonical source identity contract for every new stock movement:

- `OPENING -> JOURNAL_ENTRY`
- `PURCHASE -> PURCHASE_LINE`
- `PURCHASE_RETURN -> PURCHASE_RETURN_LINE`
- `SALE -> SALES_ALLOCATION`
- `SALES_RETURN -> SALES_RETURN_ALLOCATION`
- production issue/issue return/issue correction -> `PRODUCTION_ISSUE`
- production receipt/receipt correction/cost revaluation -> `PRODUCTION_BATCH`
- count adjustment -> `INVENTORY_COUNT_LINE`
- legacy lot reclassification -> `AUDIT_EVENT`
- transfer out/in/reversal out/reversal in -> `WAREHOUSE_TRANSFER_LINE`

New writes pass through `StockMovementWriter`, which validates movement type, quantity direction, reference contract and existence of the persisted source record before inserting.

Every new movement receives a durable unique source key, normally:

`P1:<referenceType>:<referenceId>:<movementType>`

This makes the same source leg idempotent at database level. Transfer OUT and IN can coexist because their movement types differ while both reference the same persisted transfer line. Repeatable production correction/revaluation events use an explicit event discriminator while retaining the same parent source lineage.

No stored stock balance is directly modified by P1.

## Room / Migration

P1 uses branch-only provisional Room `36` because source-leg idempotency must be durable across restarts/concurrent calls.

Migration: `MIGRATION_35_36_INVENTORY_P1_PROVISIONAL`

Classification: **PROVISIONAL / BRANCH ONLY**. Central integration owns final schema numbering and may renumber/rebase this migration when applying the exact patch.

Non-destructive migration actions only:

- adds `sourceKey TEXT NOT NULL DEFAULT ''` to `stock_movements`;
- assigns existing historical rows only a unique `LEGACY:<id>` source key;
- adds unique index `index_stock_movements_sourceKey`;
- adds insert triggers that reject an empty source key, invalid movement/reference/direction contracts, and source references that do not exist.

The migration does **not** delete/recreate the DB/table and does not rewrite historical `id`, movement date, warehouse, item, movement type, quantity, unit cost, reference fields, lot, expiry or creation time. No `fallbackToDestructiveMigration` is used.

## Impact

### Inventory

Intentional P1 change. New stock movements are normalized to concrete persisted source records, orphan movements are rejected, and duplicate source legs are blocked durably.

### Accounting

No journal formulas, account mappings, accounting-period logic, GL posting rules, AR/AP logic or accounting calculations were changed. Existing business events continue to drive their existing journals; P1 normalizes only the stock-movement source identity created by those events.

### Production

No BOM, production costing formula, production state machine or quantity formula was changed. Production stock movements now reference persisted production issue/batch sources consistently.

### UOM / Lots / Counts / Costing

P2 UOM factor/history work was not started. P3 genealogy redesign was not started. P4 inventory-count workflow redesign was not started; P1 only gives existing count-adjustment movements a concrete count-line source identity. P5 historical cost expansion was not started.

## Validation results

- Exact Central baseline/tree gate: **PASS**
- P1 changed-file scope guard: **PASS**
- Migration/data-preservation SQLite smoke: **PASS** — `INVENTORY_P1_MIGRATION_DATA_PRESERVATION_OK`
- Targeted tests (`StockMovementPolicyTest` + carried-forward `StockLedgerInvariantTest`): **PASS**, `BUILD SUCCESSFUL in 2m 38s`
- Full Unit (`:app:testDebugUnitTest`): **PASS**, `BUILD SUCCESSFUL in 39s`
- Release (`:app:assembleRelease`): **PASS**, `BUILD SUCCESSFUL in 4m 19s`
- Generated Room schema 36: **PASS**
- Application ID `com.fush.erp.recovery`: **PASS**
- destructive migration guard: **PASS**
- zipalign check: **PASS**
- Signing: unsigned test APK only; no signing material was created or uploaded.

Existing Central KSP/deprecation warnings remain warnings and were not changed under Inventory P1.

## Manual acceptance steps

1. Upgrade a representative Room-35 database containing historical stock movements. Confirm the DB opens without reset, the historical movement facts remain unchanged, and each old row has a unique `LEGACY:<id>` source key.
2. Post a purchase. Confirm the positive stock movement references the persisted purchase line.
3. Post a purchase return. Confirm the negative movement references the persisted purchase-return line.
4. Post a sale allocated across lots. Confirm SALE movements reference their persisted sales allocations, not an ambiguous text/document-only source.
5. Post a sales return. Confirm the positive movement references its persisted return allocation.
6. Transfer one line between two warehouses. Confirm OUT and IN both reference the same persisted transfer line, have distinct source keys, and their quantities sum to zero.
7. Reverse the transfer and confirm the same paired-source behavior for reversal OUT/IN.
8. Post a count adjustment. Confirm it references the persisted inventory-count line.
9. Attempt a canonical new movement with a nonexistent source row. Expected: rejected as `ORPHAN_STOCK_MOVEMENT_SOURCE`.
10. Attempt the same source leg twice. Expected: the unique source-key constraint rejects the duplicate.
11. Attempt a movement type with the wrong quantity direction or wrong reference type. Expected: rejected as `INVALID_STOCK_MOVEMENT_CONTRACT`.
12. Recheck inventory balances and corresponding existing accounting journals. Expected: no direct balance mutation and no accounting formula change.

## Known issues

`repairLegacyFinishedGoods()` is still called by the accepted Central baseline during startup and silently repairs historical data. The official inventory plan requires moving that behavior to an explicit reviewed repair/migration path. It was intentionally not expanded into P1 to avoid crossing the official P1 scope.

## Exact integration instruction

Do not merge this branch wholesale into Central. Use the validated artifact/patch from workflow run `31982480355`, artifact ID `9272870832`, and verify patch SHA-256 `4f78ee7c37b439efd9427554433433822416fd669474224cdec00fbb35b6bdc0` before applying to the integration branch's accepted Central source. If Central's Room number has advanced, re-number/rebase the **provisional** migration without changing its data-preservation semantics, then rerun migration, targeted, full-unit and release gates.

No merge to `fush/main` or `fush/integration-current` was performed by the inventory branch. P2 has not started.
