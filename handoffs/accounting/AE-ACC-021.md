# Accounting Handoff — AE-ACC-021

- Defect ID: AE-ACC-021
- Root Cause: AR/AP reports had balance calculations but there was no explicit open-item ledger contract proving that each invoice is reduced only by dated settlement allocations and credit returns, with an independent reconciliation to the control GL.
- Fix: Added `OpenItemLedgerService`. Open items are derived, never manually stored: invoice amount minus dated receipt/payment allocations and credit returns as-of the requested date. It returns document/party/currency/open-original/open-functional values and reconciles aggregate AR/AP open functional amounts to control accounts 1300/2100, excluding unrealized FX journals which are a separate valuation layer from AE-ACC-014.
- Integrity: Negative over-settled open items are rejected by policy; control reconciliation has a fixed monetary tolerance and reports variance explicitly.
- Database Impact: None beyond parent Room 38.
- Tests: valid open item; negative open item rejection; exact control reconciliation; mismatch rejection. QA should test partial settlement, reversal, credit return and historical as-of dates.
- Status: FIXED / READY FOR PRE-INTEGRATION QA.
