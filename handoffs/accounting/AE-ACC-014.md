# Accounting Handoff — AE-ACC-014

- Defect ID: AE-ACC-014
- Root Cause: Period-end FX revaluation covered treasury balances only; foreign AR/AP open items were never remeasured at the closing rate.
- Fix: Added `OpenItemFxRevaluationService`. It derives AR/AP monetary exposure from posted invoices minus dated settlements and credit returns, groups by foreign currency, retrieves the closing rate as-of the valuation date, and posts the unrealized delta to AR/AP control against FX gain/loss. The existing registered `FX_REVALUATION` source is reused with stable `OPEN_ITEM:<AR|AP>:<currency>:<date>` identity, giving idempotency without weakening AE-ACC-003.
- Policy: Revaluation requires an OPEN accounting period. Functional carrying amount is compared with outstanding original × closing rate. AR increase = debit AR/credit gain; AP increase = debit loss/credit AP; decreases reverse those directions.
- Database Impact: None beyond parent Room 38.
- Tests: AR gain, AP liability increase, zero delta; QA must exercise foreign invoice + partial settlement + credit return as-of dates and repeat the same valuation to verify idempotency.
- Status: FIXED / READY FOR PRE-INTEGRATION QA.
