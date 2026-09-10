# Fush ERP Phase 14.5.13 — Per-Batch Direct Labor Cost

- Removes the hidden 30,000 YER default from production order data and UI.
- Requires the user to explicitly enter direct labor cost for every new production order/batch.
- Allows any valid non-negative amount, including zero.
- Existing orders retain their stored labor cost; no schema migration is needed.
- Direct labor remains part of actual production cost and WIP posting exactly as before.
- Adds domain validation and tests for blank, arbitrary and negative labor-cost inputs.
