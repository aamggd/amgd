# v136 — FUSH Support / Maintenance Mode

Baseline: v135 AuditTrailRoomQueryFix FINAL only.

## Security model
- `FUSH_SUPPORT` is a dedicated system role, separate from company `ADMIN`.
- Support-only permissions: `SUPPORT_VIEW`, `SUPPORT_DIAGNOSE`, `SUPPORT_REPAIR`, `SUPPORT_RECALCULATE`, `SUPPORT_CORRECT_DATA`.
- Normal roles cannot receive Support permissions. `FUSH_SUPPORT` cannot receive normal operating permissions through role-permission editing.
- Every Support command revalidates the user, role, permission, ticket, session, company/branch scope and trusted expiration time inside the backend service.
- Closing/revoking/expiring a Support Session removes command access immediately; keeping the UI open does not bypass the backend guard.

## Data model
Room 40 -> 41 adds, non-destructively:
- `support_tickets`
- `support_sessions`
- `support_snapshots`
- `support_audit_log`
- `support_validation_results`

Snapshots, Support Audit and Support Validation rows are immutable through DB `BEFORE UPDATE/DELETE` abort triggers.

## Support Center
Company ADMIN can:
- open a Support Ticket;
- select an active `FUSH_SUPPORT` account;
- authorize 1 hour / 6 hours / 24 hours / until manual cancellation;
- revoke a session immediately;
- close a ticket after active access is ended.

`FUSH_SUPPORT` can use only an active session assigned to that same account.

Read/diagnostic tools:
- Diagnose Invoice
- Recalculate Inventory from ledger
- Recalculate Average Cost from cost layers
- Check Customer Balance
- Check Supplier Balance
- Check Cash/Bank Balance
- Find Unbalanced Journal Entries
- Find Failed/Incomplete Transactions

Typed repair commands currently enabled:
- Rebuild Accounting Entry
- Repost Transaction

These repair commands are intentionally limited to balanced `STAGING` journals whose source is one of:
`SALE`, `PURCHASE`, `CUSTOMER_RECEIPT`, `SUPPLIER_PAYMENT`, `SALES_RETURN`, `PURCHASE_RETURN`.

They never delete the source transaction and never invent a balancing account. A before snapshot is stored first, the transition runs in a Room transaction, then cross-module validation runs. Unsupported repair types are rejected and require a new reviewed typed Repair Command.

## Post-repair validation
In addition to accounting status/balance, validation checks source-specific links:
- Sales / purchases: posted source document, inventory movements, and treasury leg when cash.
- Customer receipt / supplier payment: source record and treasury leg.
- Sales / purchase returns: posted return, inventory restoration/removal and cash-refund treasury leg where applicable.

The validation result is persisted in `support_validation_results` and summarized in the immutable Support Audit entry.

## Direct DB correction
There is deliberately no generic DB/table editor. `SUPPORT_CORRECT_DATA` is a capability gate for future reviewed typed commands; the current generic exceptional method rejects direct mutation.

## Deployment note
This Android application is currently a local/offline ERP. The feature provides controlled support access **inside the company database on the device where the support account is authenticated**. It does not by itself create remote network access from FUSH to a customer's phone. True remote support requires a separately designed secure backend/device-pairing transport.
