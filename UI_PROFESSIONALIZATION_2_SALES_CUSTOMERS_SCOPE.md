# FUSH ERP Mobile — UI Professionalization 2: Sales & Customers

Baseline: `0.15.4.34-ui-professionalization-1`
Target: `0.15.4.35-ui-sales-customers`

## Scope
Presentation and usability improvements for Sales and Customers only.

## Implemented
- Executive sales summary cards for invoice volume, open receivables, overdue exposure and open credit invoices.
- Invoice search by number, customer, currency and payment type.
- Professional invoice cards with payment and collection-status pills, due-date visibility and consistent actions.
- Clear sales credit-policy information surface and empty states.
- Customer overview KPIs: customer count, total receivables, overdue receivables and credit-enabled customers.
- Customer cards with avatar, code, province, classification/currency status and risk-aware balance treatment.
- Customer profile header with identity, credit/access status, balance and activity metrics.
- Existing customer profile tabs and all business services remain intact.

## Safety boundary
No Room schema, DAO query, accounting posting, sales posting, collections, returns, inventory posting, commissions or credit-control business logic is intentionally changed.
