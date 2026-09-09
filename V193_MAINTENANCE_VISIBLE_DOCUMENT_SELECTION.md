# v193 Maintenance Visible Document Selection

- Baseline: v192.
- Admin temporary test cleanup no longer asks for hidden Room row IDs.
- Sales invoice cleanup uses a searchable selector keyed by the visible invoice number / JE number.
- Shipment-expense cleanup uses a searchable selector keyed by visible shipment number, payment voucher number, description, reference and expense type.
- The selected visible record is resolved to its internal ID only after explicit selection.
- Existing test-only reason/session/audit safeguards remain unchanged.
- No Room schema change and no destructive migration.
