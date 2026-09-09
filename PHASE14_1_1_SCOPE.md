# Phase 14.1.1 — Production Material Issue Correction

- Correct excess material issue on old or current production orders without deleting historical issue records.
- User enters the corrected final issued quantity and a mandatory reason.
- Excess quantity is returned against the original issue lots in reverse issue order, using the original historical unit cost.
- A negative production-issue correction record is retained for traceability; raw-material stock is restored to the same lot/expiry.
- Production material issued quantity and material cost are recalculated from net issue history.
- Open production: reverses material value from WIP to inventory.
- Rejected batch: reduces production/quality loss and restores raw inventory.
- Accepted closed batch: revalues the remaining finished-goods lot and reduces COGS for the net-sold share.
- Historical sales allocations and sales-return cost records for the production batch are updated to the corrected batch unit cost.
- Finished-goods revaluation uses quantity-neutral stock movements, preserving physical quantity while correcting inventory value.
- Correction is blocked if the accepted batch lot contains unsupported inventory movements such as count adjustments, to avoid silently misclassifying cost.
- Audit event records user, old/new issued quantities, values, and reason.
- Production material-usage report is tied to production/manufacture date so corrections to old production restate the original production period.
- Database migration 13 → 14 adds correction metadata to production_issues without deleting existing data.
