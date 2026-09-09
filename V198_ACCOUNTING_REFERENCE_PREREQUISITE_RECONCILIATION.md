# FUSH ERP Mobile v198 — Accounting Reference Prerequisite Reconciliation

## Root cause verified from the user's two v197 backups

The manager device contained 44 chart accounts, 3 treasury accounts, 2 employees and 1 sales representative.
The accountant device contained 42 chart accounts, 2 treasury accounts, 0 employees and 0 sales representatives.
Six posted journals on the manager depended on ledger account `1132`, which did not exist on the accountant device.
Treasury vouchers also referenced employee/sales-representative codes that were absent locally.

The shared 265 journals were verified to be semantically identical. The problem was missing prerequisite master references, not corrupt journal content.

## v198 design

- Adds a versioned, hashed accounting-reference snapshot carried as metadata inside one semantically unchanged canonical cloud journal JSON document.
- Requires no new Supabase table or SQL migration.
- OWNER/ADMIN can refresh the reference snapshot.
- Every active company member can hydrate missing local references from the snapshot.
- Snapshot order: chart accounts -> treasury accounts -> employees -> sales representatives.
- Existing local master rows are never overwritten by this repair path; it inserts missing rows only.
- `Sync All` now runs accounting-reference prerequisites immediately after ordinary master data and before any transaction domain.
- Full accounting sync also performs the prerequisite reconciliation before journal/voucher hydration.
- The carrier journal publishes the exact cloud snapshot metadata back with the journal payload so the existing full-JSON cloud conflict contract remains idempotent.

## Data safety

- Application ID remains `com.fush.erp.recovery`.
- Room schema remains 49.
- No destructive migration and no database deletion.
- No posted accounting journal is created, changed, reversed or deleted by the reference snapshot itself.
- The snapshot is SHA-256 verified before local application.

## Backup simulation result

Applying the manager's reference set to a copy of the accountant backup produced:
- Accounts: 44
- Treasury accounts: 3
- Employees: 2
- Sales representatives: 1
- Missing manager journal account codes after reconciliation: 0
- Missing employee/sales-representative voucher references after reconciliation: 0
