# Sales / Customers — AE-ACC-009 Customer Receipt Atomicity Handoff

Owner branch: `fush/sales-customers`

Scope: **AE-ACC-009 only**. No P2 work is included.

## Exact baseline

- Central branch: `fush/integration-current`
- Central commit: `ddae764a236cb1916a46e194ccb2d3c24ca6b919`
- Exact `central_source` tree: `30b028b75d6463c07afd0419429f53c7937fabb1`
- Application ID: `com.fush.erp.recovery`
- Room schema: `35`

## Defect boundary

Customer receipt posting must be one atomic accounting operation. Receipt header, invoice allocations, number sequence, commission effects, and cash/AR journal posting must either all commit or all roll back. A journal-posting failure must never leave the customer/receipt side committed.

## Fix

The exact Central source is changed only in the customer-receipt path:

1. `postReceipt` now has its own explicit `db.withTransaction` boundary and directly invokes the internal receipt mutation routine.
2. The internal mutation routine fails closed unless a Room transaction is active.
3. Collection-journal GL account resolution and accounting validation are performed before the first receipt/sequence/allocation mutation.
4. The already validated collection journal lines are passed to journal posting after the receipt-side writes.
5. Journal exceptions are intentionally not caught; they escape the same Room transaction so Room rolls back receipt header, allocations, sequence, commission changes, and any partial journal write together.
6. `postReceiptAllocations` and auto-allocation retain their existing Room transaction boundaries.
7. Receipt reversal remains transaction-scoped; a focused reversal-math regression is added without changing reversal production logic.

## Explicit exclusions

- No P2.
- No full merge.
- No changes to `fush/main` or `fush/integration-current`.
- No schema/entity changes.
- No migration changes.
- No database recreation.
- No destructive migration/fallback.

## Required validation

The branch workflow restores the exact Central source tree above, applies this fix deterministically, and requires all of the following:

- dedicated `AE-ACC-009` transaction/order regression
- customer receipt atomicity unit regression
- AR / allocation / identity targeted tests
- receipt reversal targeted test
- reconciliation targeted tests
- full `:app:testDebugUnitTest`
- `:app:assembleRelease`
- Room 35 / schema 35 safety gate
- Application ID safety gate
- no-destructive / no-schema-change gate
- exact binary patch generation + reverse-apply check + SHA-256

The generated patch and Release APK are CI handoff artifacts for Integration Review. Stop after this handoff; do not begin P2.
