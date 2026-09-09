# Release handoff — v159 Sales & Receivables Cloud Mirror

This source is based directly on the validated v158 master-data cloud-sync baseline.

Required Supabase migration before runtime testing:
- `cloud/supabase/004_sales_receivables_mirror.sql`

Recommended runtime test order:
1. Install signed v159 as an update on OWNER/ADMIN phone.
2. Confirm master data sync succeeds.
3. Run **Sales & receivables sync** on OWNER/ADMIN first to establish the company transaction mirror.
4. Install the same signed v159 on the accountant/employee phone.
5. Sign in with that employee's linked cloud identity.
6. Run master-data sync first, then sales/receivables sync.
7. Verify sales invoices, customer receipts, sales returns, dashboard sales and customer outstanding balances appear.

Safety boundary:
- Employee-only local transaction documents are not published by v159.
- Purchases, stock movements, general party vouchers/journals, and production transactions are not cloud-synchronized in this release.
