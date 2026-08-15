# FUSH ERP Mobile — Phase 14.5.44 Reports Correctness

Base: verified Phase 14.5.43 Integrated Professional UI build.

## Scope
- Align report customer receivables with the existing accounting/customer balance logic.
- Include posted linked customer party vouchers in report outstanding balances:
  - RECEIPT reduces customer receivable.
  - PAYMENT increases customer receivable.
- Reduce overdue receivables by unallocated posted customer receipt vouchers using an oldest-debt assumption; customer payment vouchers do not automatically become aged overdue debt.
- Use the corrected customer balance in both the executive report and customer sales report.
- Add focused report math regression tests.
- Keep Room schema version 27 unchanged.

## Explicit non-scope
- No accounting posting rule changes.
- No database migration or schema change.
- No customer/supplier master-data change.
- No PDF/Excel layout changes in this phase; those follow after correctness is verified.

## Regression scenario
1. Credit sale to a customer: 100,000 base currency.
2. Posted customer RECEIPT party voucher: 100,000.
3. Expected report receivable: 0.
4. Expected overdue: 0.
5. Then post customer PAYMENT party voucher: 5,000.
6. Expected report receivable: 5,000.
7. Expected overdue remains 0 until an aging rule/date makes that new receivable overdue.
