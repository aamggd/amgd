# FUSH ERP Mobile — fush/master-data — P1 Stable Auto Codes

Status: **COMPLETE / TESTED / READY FOR HANDOFF**

Branch: `fush/master-data`
Starting Central: `fush/integration-current@2cb8da801fc54aec8c1f0d6a83588f097ca85117`
Final Wave Central used for exact reapply: `fush/integration-current@ccbe1273ba5648b1e546eb5838aa4b33fcdb5d48`
Final validated application commit: `afb539516a6358ba9c92e6271d4d571e2990b1f1`
Application ID: `com.fush.erp.recovery`
Starting Room schema: `34`
Final inherited Central Room schema: `35`
Room change by P1: **NO**
Migrations added by P1: **NONE**

## Official phase scope

P1 implements the official master-data requirement: **stable automatic codes that are not reusable**. P2 referential/deactivation work was not started.

## Functional change

`AutoNumberService` now reserves each sequence value inside a Room transaction. When called from an existing business transaction, it participates in that transaction; otherwise it creates the atomic boundary itself. The read/increment/write reservation therefore executes under a database transaction instead of separate unprotected DAO calls.

The allocator applies `AutoNumberSequencePolicy`:

- negative/corrupt sequence values are rejected rather than reset;
- `Long.MAX_VALUE` is rejected rather than wrapping to a reused/negative value;
- the sequence row is re-read after write and the reservation must equal the persisted value;
- no reset/delete API is introduced for number sequences;
- existing namespaces and formats are preserved (`SUP`, `CUS`, `UNT`, `WH`, item category prefixes, and dated operational document prefixes).

No existing master ID or existing code is rewritten.

## Historical/non-reuse contract

A committed sequence value only moves forward. Deactivating or deleting a business master elsewhere does not alter `number_sequences`, so a code previously committed for that namespace is not recycled. If an outer business transaction fails, its sequence increment rolls back with the same transaction; a value that was never committed is not an issued historical code.

Configured display widths are minimum padding widths, not maximum widths: exceeding the width grows the numeric portion instead of truncating or wrapping it.

## Exact application patch

Only these two application files differ from the final Wave Central used for validation:

- `central_source/app/src/main/java/com/fush/erp/domain/AutoNumberService.kt`
- `central_source/app/src/test/java/com/fush/erp/domain/AutoNumberSequencePolicyTest.kt`

Exact P1 runtime blob:
`4224ce145965cf45e77b61b14b40b7157875d798`

Exact P1 test blob:
`d1d7e05fef4c3a0a31e0e563ae953cdc9dd60e0c`

The final Central copy of `AutoNumberService.kt` before P1 reapply had blob:
`a10ff60d9f4878d47b637a73f6be422692de76ed`

Branch-only workflow/documentation files are not runtime application changes.

## Validation history

Initial implementation commit:
`030253fbb12ab2e60b9f98bae1706727c887e8b3`

Initial validation run:
`31978777558` — **SUCCESS**

After Accounting P1, Treasury P1 and Purchases P1 completed in Central, the exact P1 two-file application patch was reapplied on:
`ccbe1273ba5648b1e546eb5838aa4b33fcdb5d48`

Final validated application commit:
`afb539516a6358ba9c92e6271d4d571e2990b1f1`

Final validation run:
`31979422093` — **SUCCESS**

Final gates all passed:

1. final Central ancestry and exact two-file application scope;
2. focused AutoNumber domain tests;
3. SQLite concurrent reservation contract: 4 workers / 200 committed reservations produced exactly 1..200, followed by 201; an independent namespace produced 1 then 2;
4. full JVM unit suite;
5. `assembleRelease`;
6. Room/schema/migration preservation checks;
7. Application ID and no-destructive checks.

## Room / migration preservation

P1 introduced **no Room schema change and no migration**.

During the concurrent Wave, Central Accounting P1 advanced Central from schema 34 to schema 35 and added `MIGRATION_34_35_ACCOUNTING_P1`. That migration and schema 35 are inherited Central changes, not owned or renumbered by master-data P1. The final P1 validator required `FushDatabase.kt`, `Migrations.kt`, `AppContainer.kt`, and all Room schema files to remain byte-for-byte unchanged from the final Wave Central baseline.

## Impact

- Accounting calculation/posting logic: **NO CHANGE**. Shared document-number reservation is safer/atomic only; Accounting P1 Central changes are preserved.
- Inventory quantities/cost logic: **NO CHANGE**. Master code reservation is safer/atomic only.
- Production logic: **NO CHANGE**.
- Room schema by P1: **NO CHANGE**.
- P1 migrations: **NONE**.
- Existing IDs: **UNCHANGED**.
- Existing codes: **UNCHANGED**.
- Destructive migration introduced by P1: **NO**.
- versionCode/versionName: **NO branch release version assigned**.
- signing key/certificate: **NO CHANGE / NO NEW KEY CREATED**.

## Manual regression steps

1. Create two new units and confirm their generated `UNT-...` codes move forward.
2. Deactivate a previously created unit, create another unit, and confirm the deactivated unit code is not reused.
3. Repeat the same monotonic check for warehouses and items.
4. Create suppliers/customers and confirm `SUP-...` / `CUS-...` codes advance without recycling prior committed codes.
5. Create multiple same-day operational documents using a shared document prefix and confirm the suffix advances without duplicates.
6. Confirm existing historical master rows retain their original IDs and codes.
7. Confirm accounting, stock quantities/costs, and production results are unchanged apart from safer number reservation.

Expected result: every committed namespace sequence advances monotonically; no committed code is recycled, no duplicate reservation appears, historical IDs/codes remain unchanged, and all unrelated business logic remains unchanged.

## Known issues / limits

No known P1 functional defect remains after the final CI run. P1 does not claim Android instrumentation coverage for concurrent Room calls; concurrency was covered by the transaction contract test plus the full JVM/release build gates. No Room migration test was required because P1 itself changed no Room schema.

## Integration handoff

Use the exact two-file application delta validated by commit:
`afb539516a6358ba9c92e6271d4d571e2990b1f1`

Do not replace newer Central files wholesale. The integration branch should apply only the two P1 application files/delta above on its then-current Central, rerun the same gates, and preserve any newer Central Room/migration/version changes.

## Stop boundary

P2 is explicitly out of scope and was not started.
