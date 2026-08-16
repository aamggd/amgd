# FUSH ERP Mobile — fush/master-data — P0 Master Entity Inventory

Status: P0 INVENTORY COMPLETE / NO FUNCTIONAL CHANGE
Branch: `fush/master-data`
Baseline record commit: `5095ba46a676fd6a8e048f2325c433a1f336d05d`
Baseline source tree: `1b6af7bcaa86138ae75ea3d905db4bdba0fe04ff`
Baseline source ZIP SHA-256: `8adc047bc38703fcb460344b4ddd2c7f18f0c47c3ca1a6c62a51d3ef590450a1`
Application ID: `com.fush.erp.recovery`
Room schema: `34` (unchanged in P0)
Schema status: NO CHANGE
Version status: inherited baseline only; no branch release version assigned

## Purpose of P0

Inventory every master/reference entity in the branch scope, identify its current owner, stable identifier, uniqueness contract, lifecycle behavior, audit coverage, and cross-module consumers. P0 intentionally makes no Room or business-logic changes.

## Master/reference inventory

| Reference | Storage / source of truth | Current owner | Stable ID / key | Current uniqueness | Lifecycle / history behavior | P0 finding |
|---|---|---|---|---|---|---|
| Currencies | `currencies` / `CurrencyEntity` | Seed: `AppContainer`; rate use: `GeographyService` + shared currency DAO | `code` (PK), e.g. `YER_NEW`, `YER_OLD`, `USD` | PK `code` | FK consumers use RESTRICT; DAO exposes active rows; no general edit/deactivate service found | Stable key is good; lifecycle governance is incomplete and must be addressed in later phase before editable currency master is exposed |
| Exchange rates | `exchange_rates` / `ExchangeRateEntity` | `GeographyService` + `CurrencyDao` | composite (`currencyCode`,`effectiveAt`) | composite PK | Historical rows are looked up by effective date; `upsertRate` can replace an exact timestamp | Historical model exists; later audit/immutability policy needed for exact-timestamp replacement |
| FX snapshots | `fx_snapshots` / `FxSnapshotEntity` | `GeographyService` | auto ID; unique `effectiveAt` | unique effective timestamp | append-style insert; also feeds exchange rates | Reference history is time-based and cross-links currency logic |
| Units | `units` / `UnitEntity` | `MasterDataService` | numeric `id`; code `UNT-nnn` | unique `code` | deactivate instead of delete; blocks deactivation when used by active item base unit or active conversion; CREATE/UPDATE/ACTIVATE/DEACTIVATE audited | Strong baseline. Names are not unique; duplicate-name validation is a later P4 concern |
| Item/UOM conversions | `item_unit_conversions` / `ItemUnitConversionEntity` | `MasterDataService` | numeric `id`; unique (`itemId`,`unitId`) | unique item+unit; barcode conflict checked in service | base conversion cannot be deactivated; changes are audited; purchase/sale documents snapshot `factorToBase` on lines | Critical historical field. Existing documents snapshot factor, but later P3 must explicitly protect/audit factor changes and confirm all consumers use snapshots |
| Warehouses | `warehouses` / `WarehouseEntity` | `MasterDataService` | numeric `id`; code `WH-nnn` | unique `code` | deactivate instead of delete; blocks deactivation while absolute warehouse balance is non-zero; audited | Strong baseline. Duplicate-name validation remains a P4 item |
| Items | `items` / `ItemEntity` | `MasterDataService` | numeric `id`; category code `RM/PK/FG-nnnnnn` | unique `code` | deactivate instead of delete; blocks deactivation for non-zero stock and active recipe use; audited | Stable IDs/codes already exist. Name duplicate policy remains P4 |
| Provinces / regional policies | `province_policies` / `ProvincePolicyEntity` | Seed: `AppContainer`; read/use: `GeographyDao` + `GeographyService` | `code` PK (`TAIZ`, `ADEN`, `SANAA`, `OTHER`) | PK `code` | `isActive` exists; policies are seeded with upsert | Stable code exists, but generic mutation/audit ownership is not centralized; later work should avoid accidental overwrite of historical policy semantics |
| Expense cost-center definitions | **No master table**. In-code map inside `AccountingService.insertExpenseDimension()` | Accounting service currently defines accepted codes | string `costCenterCode` stored in `expense_dimensions` with name snapshot | code validated against hard-coded map | Expense rows snapshot code/name; no centrally queryable master record | Scope gap: reference definition exists as code, not shared master data. Must be coordinated with accounting/expenses before any schema change |
| Expense organization unit / branch/facility reference | **No master table**. Free text `organizationUnit` on `expense_dimensions` | Expense/accounting flow | free text | none | snapshot text only | Scope gap: not a canonical reference; cannot guarantee cross-screen consistency yet |
| Expense reference types | In-code allow-list inside `AccountingService` | Accounting service | string enum-like values | service validation | stored as snapshot fields with linked IDs where applicable | Shared status/reference vocabulary is code-level, not a master entity |
| Number sequences | `number_sequences` / `NumberSequenceEntity` | `AutoNumberService` | `sequenceKey` PK | PK `sequenceKey` | DB-backed monotonic increment inside caller transaction; no delete/reuse API | Suitable foundation for P1 non-reusable auto codes. Concurrency/monotonic behavior should get dedicated tests in P1 |
| Common status flags | Distributed string/boolean fields (`isActive`, `status`, etc.) | Per domain | no central master | domain-specific | mixed patterns | Not a single canonical master today; P0 records this rather than creating a new one without coordination |

## Current automatic code contracts

- Supplier: sequence `MASTER:SUP`, format `SUP-000001`.
- Customer: sequence `MASTER:CUS`, format `CUS-000001`.
- Unit: sequence `MASTER:UNT`, format `UNT-001`.
- Warehouse: sequence `MASTER:WH`, format `WH-001`.
- Item raw material: sequence `MASTER:ITEM:RM`, format `RM-000001`.
- Item packaging: sequence `MASTER:ITEM:PK`, format `PK-000001`.
- Item finished good: sequence `MASTER:ITEM:FG`, format `FG-000001`.
- Operational documents use per-prefix, per-day keys `DOC:<prefix>:yyyyMMdd` and a four-digit suffix.

P0 observation: sequence rows are updated with Room `REPLACE`, but there is no API that resets/deletes a sequence. P1 should formalize and regression-test the non-reuse guarantee instead of changing numbering opportunistically in P0.

## Referential and historical protection already present

1. `CurrencyEntity.code` is referenced by exchange rates, suppliers/customers, sales/accounting/geography records through foreign keys using RESTRICT where declared.
2. `ItemEntity.baseUnitId` references units with RESTRICT.
3. Operational purchase/return lines persist `unitId`, `factorToBase`, `baseQuantity`, and cost/price snapshots; this protects historical quantity math from later conversion edits for those documents.
4. Unit deactivation checks active base-item and active conversion use.
5. Warehouse deactivation checks absolute stock balance.
6. Item deactivation checks absolute stock balance and active production-recipe usage.
7. MasterDataService already writes immutable governance audit events for create/update/activate/deactivate of unit, warehouse, item, and item conversion.

## Gaps routed to later phases

- P1: formal tests ensuring master auto codes are monotonic and never reused.
- P2: broaden referential/deactivation coverage for master types not currently governed by `MasterDataService`, especially currencies/province policies if they become editable.
- P3: explicit audit/history policy for currency/rate/province-policy changes and UOM conversion factor changes; verify every operational consumer snapshots historical conversion/rate data.
- P4: central duplicate/blank-name validation policy. Current services reject blank Arabic names for unit/warehouse/item but do not reject duplicate names.
- P4/P5 coordination issue: expense cost centers and organization units are not master tables. Any conversion to canonical master data will require coordinated accounting/expenses design and, if Room changes, a safe `BRANCH ONLY / PROVISIONAL` migration with migration tests.

## P0 impact statement

- Business logic changed: NO.
- Room schema changed: NO.
- Migrations added: NONE.
- Accounting behavior changed: NO.
- Inventory behavior changed: NO.
- Production behavior changed: NO.
- Existing IDs changed: NO.
- Existing conversion factors changed: NO.
- Destructive migration introduced: NO.

## Files reviewed for P0

- `app/src/main/java/com/fush/erp/data/entity/Entities.kt`
- `app/src/main/java/com/fush/erp/data/entity/PurchaseEntities.kt`
- `app/src/main/java/com/fush/erp/data/entity/GeographyEntities.kt`
- `app/src/main/java/com/fush/erp/data/entity/ExpenseEntities.kt`
- `app/src/main/java/com/fush/erp/data/dao/Daos.kt`
- `app/src/main/java/com/fush/erp/data/dao/PurchaseDaos.kt`
- `app/src/main/java/com/fush/erp/data/dao/GeographyDao.kt`
- `app/src/main/java/com/fush/erp/data/dao/ExpenseDao.kt`
- `app/src/main/java/com/fush/erp/domain/MasterDataService.kt`
- `app/src/main/java/com/fush/erp/domain/MasterDataMath.kt`
- `app/src/main/java/com/fush/erp/domain/AutoNumberService.kt`
- `app/src/main/java/com/fush/erp/domain/GeographyService.kt`
- `app/src/main/java/com/fush/erp/domain/AccountingService.kt`
- `app/src/main/java/com/fush/erp/data/AppContainer.kt`
- `app/src/main/java/com/fush/erp/data/FushDatabase.kt`
- `app/src/main/java/com/fush/erp/data/Migrations.kt`
- `app/schemas/com.fush.erp.data.FushDatabase/34.json`
- current unit tests, including `MasterDataMathTest.kt` and `AutoNumberFormatTest.kt`

## P0 acceptance

P0 is complete when this inventory is committed on `fush/master-data`, the exact central source tree is independently validated, unit tests pass, release build passes, Application ID remains correct, schema remains 34, and no destructive migration is present.
