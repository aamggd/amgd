# FUSH ERP Mobile v170 — Old YER Receipt FX Fix

- Baseline: v169 Customer Receipt Print.
- Fix: customer collection and invoice-specific receipt dialogs now load the exchange rate for the **receipt date**, not the invoice historical rate.
- YER_OLD labels explicitly define the direction: `1 YER_OLD = X YER_NEW`.
- Invoice posting continues to use the approved historical exchange rate for the invoice date.
- Room schema remains 46; no migration.
