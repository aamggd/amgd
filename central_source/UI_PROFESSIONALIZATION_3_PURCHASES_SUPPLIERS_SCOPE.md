# FUSH ERP Mobile — UI Professionalization 3: Purchases & Suppliers

Baseline: `0.15.4.35-ui-sales-customers`
Target: `0.15.4.36-ui-purchases-suppliers`

## Scope
Presentation and usability improvements for Purchases and Suppliers only.

## Implemented
- Purchases KPIs for invoice volume, supplier payables, overdue supplier balances and credit purchase count.
- Purchase invoice search by number, supplier, currency and payment type.
- Professional purchase invoice cards and action hierarchy.
- Supplier aging access promoted as a clear secondary action.
- Supplier overview KPIs and risk-aware payable cards.
- Professional supplier profile identity, payment-term status, balance and activity metrics.
- Improved supplier information and unified ledger presentation.

## Safety boundary
No Room schema, DAO query, purchase posting, inventory costing, supplier payment posting, purchase returns, accounting posting or other business logic is intentionally changed.
