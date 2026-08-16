# Fush ERP Phase 14.5.7 — Warehouse-Specific Reorder Alerts

This phase closes the highest-priority inventory alert gap identified after Phase 14.5.6.

## Implemented
- Reorder policy is now Item + Warehouse, not one aggregate item balance across all warehouses.
- Existing positive item-level reorder thresholds are migrated to the operational RM/FG warehouse only.
- Current alert quantity is reconstructed only from movements in the selected warehouse up to the alert timestamp.
- Expired lots and lots marked QUARANTINE, BLOCKED or RETURNED are excluded from usable quantity.
- Quarantine and returns warehouses are not automatically given reorder policies.
- New UI to create, edit and delete warehouse reorder policies.
- Audit trail for policy create/update/delete.
- Room schema migration 19 -> 20 preserves existing transaction data.

## Explicitly not included in this phase
- Backdated transfer hardening.
- Reverse of a posted warehouse transfer.
- In-transit / receiving variance.
- Partial purchase return redesign.

Those remain separate phase-gated fixes.
