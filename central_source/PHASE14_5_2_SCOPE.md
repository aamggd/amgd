# Phase 14.5.2 — Last Purchase Price Comparison

- App version: 0.15.4.2-phase14.5-purchase-price-compare (versionCode 41).
- Room schema remains 17; no migration.
- Purchase entry shows the latest POSTED purchase price for the selected item + purchase unit + currency.
- Shows previous purchase date, supplier, and direct price variance amount and percentage while entering the new price.
- Comparison is intentionally limited to the same unit and currency to avoid misleading differences caused by unit conversion or FX.
- No inventory/accounting posting logic changed.
