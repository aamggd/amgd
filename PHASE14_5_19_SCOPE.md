# Phase 14.5.19 — Net Collections Detail List

- The executive dashboard Net Collections KPI now opens its own detailed list.
- The list covers the exact same business definition: customer receipts minus posted CASH_REFUND sales returns.
- Each row shows date/time, receipt or return reference, customer, province, invoice, original currency amount, base-currency amount, and notes/reason.
- Summary shows gross receipts, cash refunds, and net collections for the last 30 days.
- Filters: all, receipts, cash refunds.
- Cash sale auto-receipts are included because they are part of customer_receipts and therefore part of the KPI.
- Room schema remains 23; no migration is required.
