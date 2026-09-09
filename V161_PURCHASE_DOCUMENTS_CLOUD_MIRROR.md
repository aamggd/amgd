# FUSH ERP Mobile v161 — Purchase Documents Cloud Mirror

## Baseline
v160 Sales Conflict Inspector.

## Scope
- Mirrors posted purchase invoices and their lines.
- Mirrors posted purchase returns and their lines.
- OWNER/ADMIN is the authoritative publisher; organization members download the company mirror.
- Uses business keys (invoice/return numbers plus line order) instead of Android Room row IDs.
- Conflicting local employee documents are preserved and counted; they are never overwritten silently.

## Explicitly excluded from v161
- Supplier payments and payment allocations.
- Stock movements / inventory balances.
- Treasury movements and balances.
- General journal entries.
- Production movements.

These are excluded so downloading a purchase document cannot duplicate financial or inventory side effects.

## Database safety
- Android Room schema remains 46.
- No destructive migration and no fallbackToDestructiveMigration.
- Supabase migration: `cloud/supabase/005_purchase_documents_mirror.sql`.
