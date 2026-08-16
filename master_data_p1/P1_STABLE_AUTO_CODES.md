# FUSH ERP Mobile — fush/master-data — P1 Stable Auto Codes

Status: **IMPLEMENTED / INITIAL VALIDATION PENDING / FINAL CENTRAL REVALIDATION REQUIRED**

Branch: `fush/master-data`
Starting Central: `fush/integration-current@2cb8da801fc54aec8c1f0d6a83588f097ca85117`
Application ID: `com.fush.erp.recovery`
Starting Room schema: `34`
Room change by P1: **NO**
Migrations added by P1: **NONE**

## Official phase scope

P1 implements the official master-data requirement: **stable automatic codes that are not reusable**. It does not begin P2 referential/deactivation work.

## Functional change

`AutoNumberService` now reserves each sequence value inside a Room transaction. When called from an existing business transaction, it participates in that transaction; otherwise it creates the atomic boundary itself. The read/increment/write sequence is therefore serialized by the database transaction instead of being performed as separate unprotected DAO operations.

The allocator also applies `AutoNumberSequencePolicy`:

- negative/corrupt sequence values are rejected rather than reset;
- `Long.MAX_VALUE` is rejected rather than wrapping to a reused/negative value;
- the sequence row is re-read after write and the reservation must equal the persisted value;
- there is no reset/delete API for number sequences;
- existing namespaces and formats are preserved (`SUP`, `CUS`, `UNT`, `WH`, item category prefixes, and dated operational document prefixes).

No existing master ID or existing code is rewritten.

## Historical/non-reuse contract

A committed sequence value only moves forward. Deactivating or deleting a business master elsewhere does not alter `number_sequences`, so a code previously committed for that namespace is not recycled. If an outer business transaction fails, its sequence increment rolls back with the same transaction; a value that was never committed is therefore not considered an issued historical code.

Configured display widths are minimum padding widths, not maximum widths: exceeding the width grows the numeric portion instead of truncating or wrapping it.

## Files in the P1 application patch

- `central_source/app/src/main/java/com/fush/erp/domain/AutoNumberService.kt`
- `central_source/app/src/test/java/com/fush/erp/domain/AutoNumberSequencePolicyTest.kt`

Branch-only validation/documentation files are not application runtime changes.

## Tests/gates

Initial branch validation must run:

1. focused AutoNumber domain tests;
2. SQLite concurrent reservation contract (4 workers / 200 reservations, exact 1..200 sequence, then 201; independent namespace 1..2);
3. full JVM unit test suite;
4. release build;
5. Application ID / Room schema / no-migration / no-destructive guards.

After the concurrent Central wave finishes, this exact two-file application patch must be reapplied to the latest Central and the same gates rerun before status can become `COMPLETE / TESTED / READY FOR HANDOFF`.

## Impact

- Accounting calculation/posting logic: **NO CHANGE**. Shared document-number reservation is safer/atomic only.
- Inventory quantities/cost logic: **NO CHANGE**. Master code reservation is safer/atomic only.
- Production logic: **NO CHANGE**.
- Room schema: **NO CHANGE**.
- Migrations: **NONE**.
- Existing IDs: **UNCHANGED**.
- Existing codes: **UNCHANGED**.
- Destructive migration: **NONE introduced**.

## Stop boundary

P2 is explicitly out of scope for this commit and must not be started as part of P1.
