# Phase 14.5.23 — Legacy Inventory Lot/Expiry Assignment

- Adds a controlled inventory action to attach a missing lot number and/or expiry date to existing legacy stock.
- Reclassifies quantity and inventory value with equal OUT/IN stock movements; total warehouse quantity and value are unchanged.
- Runs inside one Room transaction and writes an immutable audit event.
- Supports partial quantities and tracked items whose old stock predates lot/expiry tracking.
- Does not modify Room schema 23 and does not rewrite historical source movements.
