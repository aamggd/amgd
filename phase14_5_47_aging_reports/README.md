# Fush ERP Phase 14.5.47 — AR/AP Aging Reports

Base: verified Phase 14.5.46 Professional Printing artifact.

Scope:
- Add a unified debt-aging report to the Reports Center for customers (AR) and suppliers (AP).
- Bucket open credit invoices by due date: current, 1–30, 31–60, 61–90, and over 90 days.
- Keep direct party vouchers that are not explicitly allocated to an invoice in a separate adjustment column instead of silently assigning them to an invoice.
- Preserve historical voucher reversals: a reversed voucher affects historical balances until its reversal date, then its effect is cancelled.
- Add professional PDF/Excel export through the existing Phase 14.5.46 export engine.
- Add pure regression tests for aging buckets, unapplied adjustments, and credit balances.

Safety:
- Application ID remains `com.fush.erp.recovery`.
- Room schema remains 27.
- No database migration is introduced.
- No posting/accounting transaction creation logic is changed.
- This phase is isolated on `fush/reports-printing`; integration into `fush/main` remains the responsibility of the main integration chat.
