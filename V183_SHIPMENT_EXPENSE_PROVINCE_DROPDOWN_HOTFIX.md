# FUSH ERP Mobile v183 — Shipment Expense + Province Dropdown Hotfix

- Fix shipment expense posting operation id: use a valid UUID from TreasuryVoucherOperationIdentity instead of prefixing the UUID before AccountingService validation.
- Shipment destination province is now a selectable list built from active province policies plus existing customer, sales invoice, and shipment province names.
- Add a new province directly from the shipment creation dialog. New provinces are stored in the existing province_policies master data with a selected default currency and can be further configured under Currency & Geography.
- No Room schema change. Room remains 48.
- No destructive migration.
