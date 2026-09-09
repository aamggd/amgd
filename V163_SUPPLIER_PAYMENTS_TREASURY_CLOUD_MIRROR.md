# FUSH ERP Mobile v163 — Supplier Payments & Treasury Cloud Mirror

## Baseline
- Direct baseline: v162 Purchase Conflict Inspector.
- `applicationId = com.fush.erp.recovery`
- `versionCode = 163`
- `versionName = 0.15.4.114-supplier-payments-treasury-cloud-mirror`
- Room schema remains `46`; no Room migration was added.
- No `fallbackToDestructiveMigration`.

## Added cloud scope
v163 adds an OWNER/ADMIN-authored cloud mirror for:
1. Treasury account directory metadata (code, name, kind, ledger account code, currency, bank metadata, active flag).
2. Supplier payment headers.
3. Supplier payment allocations to purchase invoices.
4. Supplier-payment reversal linkage by stable payment number.
5. Non-destructive conflict reporting for treasury/payment business-field mismatches.

## Safety boundary
v163 intentionally does **not** replay:
- General journal entries or journal lines.
- Generic party receipt/payment vouchers.
- Cash counts or variance resolutions.
- Bank statements/reconciliation.
- Treasury FX revaluation.
- Inventory movements or production effects.

Downloaded supplier payments affect supplier subledger balances through their allocations, but treasury book balances remain dependent on the later general-ledger cloud wave. This prevents double-posting accounting side effects.

## Sync order
Foreground automatic sync order is now:
Master Data → Sales/Receivables → Purchase Documents → Supplier Payments/Treasury Directory.

## Supabase migration
Run `cloud/supabase/006_supplier_payments_treasury_mirror.sql` once before using the v163 domain sync.
