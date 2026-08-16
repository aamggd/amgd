# Phase 14.5.21 — Production Order Delete + Fixed Batch Formula

- Production orders may be hard-deleted only while PLANNED or MATERIALS_RESERVED.
- Deleting a MATERIALS_RESERVED order releases its reservation because no stock issue has happened yet.
- Orders with material issues, batches, stock/accounting effects cannot be hard-deleted.
- Deletion is audited with user, order number, status and reason.
- Recipe component quantities are fixed inputs for one batch.
- The recipe output quantity is a reference/expected yield, not a scaler for materials.
- Planned output may be above or below the reference without changing recipe material quantities.
- Actual output remains entered after filling and may differ from planned/reference yield.
- Existing production orders keep their snapshotted material quantities; no data migration changes them.
- Room schema remains 23.
