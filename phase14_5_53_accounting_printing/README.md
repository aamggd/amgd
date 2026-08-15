# Fush ERP Phase 14.5.53 — Professional Accounting & Treasury Printing

Base: verified Phase 14.5.52 Audit Reporting artifact.
Branch: `fush/reports-printing`.

Scope:
- professional print/PDF/XLSX actions inside all Accounting & Treasury tabs;
- Journal current filtered view export;
- Treasury current balances plus period movement report with internal transfers separated;
- Expenses export matching current filters;
- Manual journal voucher printing immediately after posting;
- General ledger export;
- Trial balance export;
- Profit & loss export;
- Balance sheet export;
- Direct cash-flow export.

Safety:
- Application ID unchanged: `com.fush.erp.recovery`.
- Room schema remains 27.
- No migration.
- No accounting posting logic changed.
