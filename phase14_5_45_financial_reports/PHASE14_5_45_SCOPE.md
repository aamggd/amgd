# FUSH ERP Phase 14.5.45 — Financial Reports & Party Statements

Branch: `fush/reports-printing`
Base: verified Phase 14.5.44 Reports Correctness artifact.

## Scope

- Add Balance Sheet / Statement of Financial Position to the unified Reports Center.
- Include assets, liabilities, equity, current profit/loss, total liabilities + equity, and balance difference.
- Add General Ledger as a first-class report with account selector, opening balance, period movements, running balance, and closing balance.
- Add Party Statement report with Customer/Supplier selector.
- Party statement supports opening balance before the selected period, detailed movement during the period, and closing balance.
- Customer balance direction: debit minus credit.
- Supplier balance direction: credit minus debit.
- Export the Balance Sheet, General Ledger, and Party Statement through the existing PDF / Excel / Print report document layer.
- Add regression unit tests for customer and supplier statement running-balance math.

## Safety

- Application ID remains `com.fush.erp.recovery`.
- Room schema remains 27; no migration is introduced.
- Existing accounting posting logic is not changed.
- Existing customer/supplier ledger DAO sources are reused as the source of truth.
- No signing credentials or passwords are stored in the repository.

## Version

- versionCode: 84
- versionName: `0.15.4.45-financial-reports`
