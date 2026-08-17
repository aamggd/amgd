# Sales / Customers — Final Audit AE-ACC-009 CASH_REFUND Settlement Handoff

Owner branch: `fush/sales-customers`

Scope: **official Final Audit AE-ACC-009 only** — `SalesService.postReturn` / `CASH_REFUND` settlement eligibility and its receipt-reversal consistency. No P2 work is included.

The earlier customer receipt atomicity hardening is retained separately under `sales_customers_receipt_atomicity/` and is explicitly not this defect.

## Exact baseline

- Central commit: `ddae764a236cb1916a46e194ccb2d3c24ca6b919`
- Exact `central_source` tree: `30b028b75d6463c07afd0419429f53c7937fabb1`
- Room schema: `35`
- Application ID: `com.fush.erp.recovery`

## Defect

`postReturn(... settlementType = "CASH_REFUND")` can currently credit treasury for a sales return without first proving that the returned amount was actually collected from the customer. This can produce an invalid cash outflow for an uncollected CREDIT sale and can over-refund partially collected sales while AR remains inconsistent.

## Fix boundary

1. Add a schema-free DAO aggregate for posted `CASH_REFUND` returns per invoice.
2. Compute refundable collected base as net receipt allocations (receipt reversals are already negative allocations) minus posted cash refunds already consumed.
3. Before any return numbering, return insert, stock movement, treasury resolution, or return journal mutation, reject `CASH_REFUND` when the requested return base exceeds that refundable collected base.
4. Zero-collection CREDIT sales therefore cannot use `CASH_REFUND`.
5. Partially collected sales can only use `CASH_REFUND` for a return whose base amount is within actual refundable collection. If the business return is larger, the excess must remain a separate `CUSTOMER_CREDIT` settlement because the current return entity has one settlement type and this patch deliberately introduces no schema/split-settlement change.
6. Fully collected sales can be refunded up to the unconsumed collected amount.
7. Prior posted cash refunds reduce the remaining refundable amount, preventing repeated/duplicate refund consumption.
8. Receipt reversal is blocked when reversing that receipt would make already-posted cash refunds exceed the remaining net collection for an invoice.

## Accounting invariants

- `CASH_REFUND` return keeps AR unchanged: the existing customer-ledger return credit is offset by the existing `CASH_REFUND` debit event, while the journal credits treasury.
- `CUSTOMER_CREDIT` remains the settlement that reduces AR.
- The patch does not change the existing sales-return journal mapping, stock-return logic, commission reversal logic, or return quantity controls.
- All checks run inside the existing `postReturn` / `reverseReceipt` Room transactions.

## Explicit exclusions

- No P2.
- No full merge.
- No push or modification to `fush/main` or `fush/integration-current`.
- No entity/schema/migration changes.
- No database recreation.
- No destructive migration/fallback.
- No signing/key/version changes.

## Required CI gates

- dedicated AE-ACC-009 source/order regression
- uncollected / partial / full / repeat refund policy tests
- receipt reversal consistency tests
- AR / allocation / Sales / Accounting reconciliation / Treasury reconciliation targeted tests
- full `:app:testDebugUnitTest`
- `:app:assembleRelease`
- Room 35 / schema 35 / App ID / no-destructive / no-schema-change gates
- exact binary patch + reverse-apply check + SHA-256

Stop after validated handoff for Integration Review. Final integrated Central APK retest remains an integration/audit gate after merge.