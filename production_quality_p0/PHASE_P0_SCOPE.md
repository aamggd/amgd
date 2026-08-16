# FUSH ERP Mobile — Production & Quality P0

Branch: `fush/production-quality`
Baseline: Phase 14.5.54 Printing Integrated (`fush/integration-printing-14.5.54@5095ba46a676fd6a8e048f2325c433a1f336d05d`)
Status: BRANCH ONLY / TEST

## Objective
Freeze the exact recipe/version used by a production order when the order is created, without changing Room schema or accounting logic.

## Baseline findings
- A production order stores an exact `recipeId` and material rows preserve `recipeComponentId`, `itemId`, and standard quantity.
- Recipe version creation creates a new row/version and marks the source as `SUPERSEDED`.
- The DAO previously exposed a generic `@Update RecipeEntity`, which could mutate metadata of an already-used historical recipe if called by future code.
- BOM integrity was validated later in the production lifecycle, but not immediately after creating the frozen order-material snapshot.

## P0 implementation
1. Replace generic whole-row recipe update with a status-only SQL update (`updateRecipeStatus`). This preserves the historical recipe code/product/version/output metadata and components once created; version lifecycle can still mark the old version `SUPERSEDED`.
2. Build the production order material rows as `frozenMaterials` and immediately validate that their `(recipeComponentId,itemId)` mapping exactly matches the selected recipe before order creation completes.
3. Keep all existing fixed-batch quantities in the order material snapshot; later recipe versions do not rewrite those order rows.

## Database / accounting / inventory impact
- Room schema: unchanged at 34. No migration.
- Destructive migration: none.
- Accounting logic: unchanged.
- Inventory movement logic: unchanged in P0.
- Production logic: strengthened only at recipe-version/order snapshot boundary.

## Validation gate
The branch workflow reconstructs the accepted 14.5.54 source artifact, verifies the exact baseline source tree `1b6af7bcaa86138ae75ea3d905db4bdba0fe04ff`, applies only `production-quality-p0.patch`, runs `:app:testDebugUnitTest`, runs `:app:assembleRelease`, confirms Application ID `com.fush.erp.recovery`, confirms schema 34, and rejects any `fallbackToDestructiveMigration` occurrence.
