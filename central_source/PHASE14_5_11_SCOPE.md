# Phase 14.5.11 — Production Issue Guard

- Rejects any production-order material set that does not exactly match the order BOM components.
- Revalidates BOM integrity both before reservation and again before inventory issue.
- Lot-tracked materials cannot be issued from stock rows without a lot number.
- Expiry-tracked materials cannot be issued from stock rows without a valid expiry date.
- Production issue availability is calculated only from usable, fully traceable stock lots.
- Room schema remains 21; no migration is required.
