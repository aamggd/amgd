# FUSH ERP Mobile v134 — Audit Trail + Free Quantity

Baseline: v133 TimeSelectionBackupHardening MERGED-FINAL only.

## Audit Trail
- Management-friendly read-only Audit Trail under Governance and `AUDIT_VIEW` / ADMIN.
- Actor resolves to display name + username; system events render as `النظام`.
- `eventAt` is rendered in the fixed Business Time Zone `Asia/Aden`.
- Newest events first; SQLite filtering + 100-row pagination/lazy load.
- Filters: date range, user, section, operation, event type, free-text reference/name/code search.
- Technical event codes are translated for the primary UI and retained in event details.
- Real references are resolved where possible (customer/supplier/item/invoice/receipt/return/journal/production/etc.).
- Details show actor, username, full date/time, operation, module, entity type/id, reference, old/new values, reason, device info, and original technical code.
- No UPDATE/DELETE Audit API is added. Existing DB immutability triggers remain enforced.
- Existing historical audit rows are not rewritten.

## Free Quantity
- Sales line separates paid quantity and free quantity; total movement quantity = paid + free.
- Revenue is calculated from paid quantity only.
- Inventory issue and COGS include paid + free quantities.
- Returns separately track paid and free quantities; free returns restore inventory/cost without creating sales revenue/refund.
- Reports expose free quantity, returned free quantity, net free quantity, free cost, and profit impact.
- Sales representatives have `freeQtyLimitPct`; allowance is evaluated per line/item in base units.
- Exceeding the representative limit requires `SALES_FREE_QTY_APPROVE` and an approval reason; approver is snapshotted and audited.
- Historical data migrates with all free-quantity values = 0 and representative limit = 0.

## Database / identity
- applicationId: `com.fush.erp.recovery`
- versionCode: 134
- versionName: `0.15.4.85-audit-trail-free-quantity1`
- Room schema: 40
- Migration: 39 -> 40, additive and non-destructive.
