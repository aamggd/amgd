# FUSH Inventory — P1 stock movement/source identity

Branch: `fush/inventory`

## Mandatory baseline

- Central ref: `fush/integration-current@ddae764a236cb1916a46e194ccb2d3c24ca6b919`
- Central source tree: `30b028b75d6463c07afd0419429f53c7937fabb1`
- Application ID: `com.fush.erp.recovery`
- Central Room: `35`

This P1 work does **not** reuse the old Inventory P0 source as a base. The branch commit is constructed with the exact current Central tree and keeps the old branch history only as ancestry so the ref can move by normal fast-forward without force-push.

## Official P1 scope only

Unify stock movement types and source references and prevent orphan/duplicate source movements.

P1 introduces a canonical `StockMovementPolicy`, one application writer, concrete persistent source references for each movement, and a durable unique `sourceKey`. Existing historical rows are preserved and receive only a `LEGACY:<id>` key during migration.

## Room

P1 requires a branch-only provisional `35 -> 36` migration because duplicate prevention must survive process restarts and concurrent application calls.

`MIGRATION_35_36_INVENTORY_P1_PROVISIONAL` is **PROVISIONAL / BRANCH ONLY**. Central integration owns the final schema number.

The migration is non-destructive: no table/data deletion and no historical quantity/cost/date/reference rewrite.

## Explicit non-scope

- P2 UOM/base-unit factor history: not started.
- P3 lot/expiry genealogy redesign: not started.
- P4 inventory-count workflow redesign: not started.
- P5 historical costing expansion: not started.
- Accounting posting logic/formulas: unchanged.
- Production BOM/costing formulas: unchanged.
- Signing/version finalization: not performed.

## Known carried-forward issue

The current Central baseline still calls `repairLegacyFinishedGoods()` during startup and silently edits historical data. The official inventory plan requires an explicit reviewed repair/migration path. P1 does not expand scope to repair that separate issue; it remains known and must be addressed in its proper later phase.

## Validation

The P1 workflow reconstructs an exact Central worktree, applies the deterministic P1 transformation, verifies changed-file scope, performs migration/data-preservation smoke tests, targeted tests, full unit tests, `assembleRelease`, Room schema generation, Application ID/destructive guards, zipalign, and emits an exact patch candidate plus SHA-256.
