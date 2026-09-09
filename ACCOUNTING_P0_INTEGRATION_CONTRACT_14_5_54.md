# FUSH ERP Mobile — Accounting P0 Integration Contract

Branch owner: `fush/accounting`

Central baseline for this stage: **Phase 14.5.54 Printing Integrated**.

Application ID: `com.fush.erp.recovery`.

Room: **unchanged at Central Schema 34**. This stage introduces no entity, DAO or migration.

## Purpose

P0 freezes the contract between operational modules and the general ledger before duplicate-posting protection is reintroduced on the current Central Baseline.

Every automated journal event has:

1. a canonical `sourceType`;
2. a documented `sourceId` business reference;
3. a replay-safety classification;
4. a reversal policy;
5. an owning operational domain.

The executable registry is `AccountingIntegrationContract.kt` and its regression tests are in `AccountingIntegrationContractTest.kt`.

## Important finding for P1

The old branch-local idempotency patch must **not** be copied wholesale. Several current Central 14.5.54 references can identify more than one legitimate event or are generated only after a retry starts. Examples include:

- `SALES_COMMISSION` / commission reversals using invoice id while multiple collection/return events can exist;
- production correction journals using only `orderNo` while multiple corrections can exist;
- generic treasury vouchers using generated UUIDs rather than an immutable request/voucher event id;
- fiscal-year close using only fiscal year while close/reopen/reclose cycles can exist;
- fixed-asset acquisition/disposal references that are not immutable across retry/reversal/repost cycles.

P1 must first establish immutable event identities for these cases, then add duplicate-posting protection. Events already classified `STABLE_SOURCE` can use the canonical `sourceType:sourceId` key after payload consistency checks are added.

## No cross-domain mutation

This P0 stage does not change inventory quantities, production quantities, sales totals, purchase totals, journal posting amounts, permissions, UI styling, or Room schema. It is a contract + tests stage only.
