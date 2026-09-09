# FUSH ERP Mobile v159 — Sales & Receivables Cloud Mirror

## Baseline
- Built directly on v158 Master Data Cloud Sync.
- applicationId remains `com.fush.erp.recovery`.
- versionCode: `159`.
- versionName: `0.15.4.110-sales-receivables-cloud-mirror`.
- Room schema remains `46`; no Room migration and no destructive fallback.

## Scope
v159 adds an organization-wide cloud mirror for posted sales/receivables documents:
- sales invoice headers
- sales invoice lines
- sales cost/lot allocations used by sales profitability views
- customer receipts
- receipt-to-invoice allocations, including settlement discounts
- receipt reversals through natural receipt-number linkage
- sales return headers
- sales return lines

The cloud model uses business/natural keys (invoice number, receipt number, return number, customer/item/unit/warehouse code) rather than Android-local Room primary-key IDs. Employee phones resolve those business keys to their own local Room IDs when downloading.

## Safety model
This wave is intentionally OWNER/ADMIN-authored:
- all active organization members can read the company sales/receivables mirror;
- only OWNER/ADMIN can publish transaction documents to these v159 cloud tables;
- employee-local transaction documents that are not in the cloud are skipped and never silently uploaded;
- a natural-key collision with different header content is reported as a conflict rather than overwritten;
- no stock movement, journal, general party voucher, purchase or production side-effect is replayed by this wave.

This prevents a remote sales document from silently duplicating inventory/accounting postings before those domains receive their own transaction-sync implementation.

## Automatic sync
While the app is open and the user has a valid cloud session:
1. v158 master-data sync runs first;
2. if it succeeds, v159 sales/receivables mirror sync runs;
3. the foreground cycle repeats every 60 seconds.

## Supabase migration
Run `cloud/supabase/004_sales_receivables_mirror.sql` after the prior cloud migrations.
