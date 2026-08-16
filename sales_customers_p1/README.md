# FUSH ERP Mobile — Sales & Customers P1 Customer Movement Identity

Branch: `fush/sales-customers`

Baseline: exact Central `fush/integration-current` HEAD `2cb8da801fc54aec8c1f0d6a83588f097ca85117`.

Central source tree: `7733c6570357eb813f7e05e5093752ea26788749`.

Application ID: `com.fush.erp.recovery`.

Room schema: `34`.

## Scope

Implements P1 only from the official Sales/Customers plan: every persisted customer receivable movement must use a real positive `customerId`; customer names remain display snapshots only and are never accepted as transaction identity.

The current Central schema already stores mandatory non-null `customerId` foreign keys on sales invoices, customer receipts and sales returns. P1 therefore does not change Room. It adds a shared `CustomerMovementIdentity` guard and applies it to sales posting, customer collection/auto-allocation, receipt reversal, sales returns, and generic customer-control-account vouchers.

Receipt reversal additionally verifies that all allocated invoices belong to the same customer recorded on the original receipt before creating the reversal movement.

## Safety

- No Room entity change.
- No migration added.
- Schema remains 34.
- No destructive migration / no `fallbackToDestructiveMigration`.
- Application ID unchanged.
- Inventory allocation/cost logic unchanged.
- Production unchanged.
- Accounting posting formulas unchanged; only customer identity validation at posting boundaries is strengthened.
- No final project version is assigned.
- No signing material is created or stored.

Patch SHA-256: `5edb9fff2c533556c4c5520c853c2cf6dcbd023af3f3561440678cb2845ae7c7`.
