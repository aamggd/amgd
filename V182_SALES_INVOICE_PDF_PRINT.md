# v182 — Sales Invoice PDF / Print

- Adds customer-facing PDF export, PDF sharing, and Android print preview from Sales Invoice Details.
- PDF includes invoice/customer metadata, sold/free quantities, unit prices, discounts, totals, customer-borne additional charges, lot/batch and expiry details, notes and signature lines.
- Internal COGS, commission and company shipment-cost data are deliberately excluded from the customer invoice.
- Application ID remains `com.fush.erp.recovery`.
- Room schema remains 48; no database migration is required.
- Upgrade is in-place over v181; no destructive migration is introduced.
