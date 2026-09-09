# FUSH ERP Mobile v180 — Shipment FIFO Lot Auto-Select

## Scope
- Shipment creation no longer accepts a manually typed lot number.
- After warehouse + item + shipment date are selected, the app shows the oldest actually available lot automatically.
- FIFO is based primarily on the oldest stock movement date in the selected warehouse.
- If the requested quantity exceeds the oldest lot, the line is split automatically across the next FIFO lots.
- Existing open shipment quantities that have not yet been allocated/sold are deducted from availability to prevent the same physical stock from being assigned to two shipments.
- The domain service validates the FIFO allocation again inside the create transaction; the UI cannot bypass the rule.
- No Room schema change. Existing Room schema remains 48 and no destructive migration is introduced.

## Version
- versionCode: 180
- versionName: 0.15.4.131-shipment-fifo-lot-autoselect
