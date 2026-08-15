# Fush ERP Phase 14.5.48 — Expense Analysis & Dimensions

Base: verified Phase 14.5.47 AR/AP Aging Reports artifact.

Scope:
- Add a dedicated `المصروفات` report to the Reports Center.
- Filter expense rows by the selected report period.
- Analyze posted expenses by expense account, cost center, organization unit, employee/sales rep, and payment method.
- Show total expense, voucher count, average voucher, and attachment count.
- Export summary breakdowns plus full expense dimensions to the existing professional PDF/Excel engine.
- Preserve historical reporting for reversed expense vouchers: the original expense is reported on its original voucher date, and a negative reversal row is reported on the reversal date.
- Add pure regression tests for expense dimension aggregation.

Safety:
- Application ID remains `com.fush.erp.recovery`.
- Room schema remains 27; no migration is introduced.
- No expense posting or reversal transaction logic is changed.
- Existing expense dimensions remain the source of truth; this phase only adds reporting/read logic.
- Integration into `fush/main` remains the responsibility of the main integration chat.
