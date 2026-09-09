# Accounting Handoff — AE-ACC-025

- Defect ID: AE-ACC-025
- Root Cause: Inventory valuation and COGS used `stock_movements.quantityBase * unitCostBase`, but there was no immutable cost-layer evidence with a versioned costing method and no grouped trace to the inventory GL. Audit could not reconstruct Movement → Cost Evidence → Valuation/COGS → GL independently.
- Accounting-side Fix: Added immutable 1:1 `inventory_cost_layers` evidence for every stock movement using the existing `MOVEMENT_ACTUAL_V1` costing basis. Positive and negative movement values are signed, so summing layers reconstructs the current inventory valuation model. Known accounting movement types are mapped to stable journal source keys, and `inventory_cost_gl_trace` groups signed layer value against account 1200 GL movement.
- Ownership Boundary: No inventory allocation/FIFO/lot-selection business logic was changed. Transfers and legacy lot reclassification are explicitly `NON_GL_INTERNAL`; unsupported correction/source mappings are `OWNER_MAPPING_REQUIRED` instead of guessed. Inventory owner must close any remaining mapping classes discovered by QA.
- Database Impact: Room 37 -> 38 via non-destructive AutoMigration creating `inventory_cost_layers`; post-migration backfill preserves every stock movement and installs immutable/autocreate triggers plus the reconciliation view. Fresh Room 38 installs the same support from the accounting cold-open initializer.
- Tests: layer-vs-stock valuation reconstruction; mapped GL reconciliation; mismatch rejection; local SQLite trace harness for purchase/sale mapping and valuation reconstruction.
- Status: FIXED IN ACCOUNTING TRACEABILITY SCOPE / READY FOR PRE-INTEGRATION QA; inventory-owner regression required for allocation/costing algorithms.
