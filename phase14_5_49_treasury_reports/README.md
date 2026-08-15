# Fush ERP Phase 14.5.49 — Treasury & Bank Movement Reports

Base: verified Phase 14.5.48 Expense Analysis artifact.
Branch: `fush/reports-printing`.

## Scope
- Add a central Reports tab: `الخزائن والبنوك`.
- Show opening balance, external inflows, external outflows, internal transfer-in, internal transfer-out, and closing balance for each cash/bank treasury account.
- Include detailed treasury movement rows for the selected period.
- Export the report through the existing professional PDF/XLSX engine.
- Preserve internal-transfer classification when a treasury transfer is reversed by resolving the reversal `sourceId` back to the original `TREASURY_TRANSFER` entry.
- Keep `com.fush.erp.recovery` and Room schema 27 unchanged.

## Accounting rule
Internal transfers must affect each treasury's balance but must not inflate business external cash inflow/outflow. Reversals of internal transfers remain classified as internal movements.
