# Phase 14.5.12 — Inventory Count Missing Snapshot Lines

- Allow adding an item/lot physically found during a draft inventory count even when it was absent from the opening snapshot.
- Added lines use system quantity 0 and the entered physical quantity as the positive variance.
- Lot and expiry tracking requirements are enforced from item master data.
- Duplicate item/lot/expiry count lines are rejected.
- User enters unit cost so positive inventory gains are valued and posted correctly.
- Addition is audit logged.
- Room schema remains 21.
