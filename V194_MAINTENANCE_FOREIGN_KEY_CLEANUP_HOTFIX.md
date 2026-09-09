# FUSH ERP Mobile v194 — Maintenance Foreign-Key Cleanup Hotfix

- Fixes `FOREIGN KEY constraint failed` when temporary ADMIN cleanup deletes a test sales invoice that already has a return or other dependent rows.
- Cleanup reads every physical return/receipt dependency for the selected invoice, regardless of business status.
- RESTRICT/NO ACTION children are deleted before the invoice inside one transaction.
- A live `PRAGMA foreign_key_list` check reports the exact blocking table if a future schema adds another dependency.
- No normal sales, return, shipment, or accounting posting logic is changed.
- Room schema remains 49; no destructive migration.
