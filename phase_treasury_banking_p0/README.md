# FUSH ERP Mobile — Treasury/Banking P0 Handoff

Branch: `fush/treasury-banking`

## Central baseline

- Central candidate adopted for this branch: `FushERP-Phase14.5.54-Printing-Integrated-Build`
- Integration branch: `fush/integration-printing-14.5.54`
- Baseline record commit: `5095ba46a676fd6a8e048f2325c433a1f336d05d`
- Baseline workflow run: `31909754750`
- Exact source tree: `1b6af7bcaa86138ae75ea3d905db4bdba0fe04ff`
- Application ID: `com.fush.erp.recovery`
- Room schema: `34` (unchanged by P0)

## Phase P0 scope

Implements only the movement-type classification required by the treasury/banking branch plan:

- `CUSTOMER_RECEIPT`
- `SUPPLIER_PAYMENT`
- `EXPENSE_PAYMENT`
- `EMPLOYEE_PAYMENT`
- `TRANSFER`
- `ADJUSTMENT`

The classification is separated from UI voucher labels so a generic payment voucher is not automatically treated as an expense.

## Functional source patch

- Local reconstructed-source commit: `525983576b79de4f3ffa4c1640eb4d51aa17aae4`
- Resulting source tree: `bd9bf24296c226f470db8e9a668bd123d33301ce`
- Patch SHA-256: `e00c126b5f13c549c4ea85a158020c7b1f5881aa3a978a2a2c9af3b23f172b98`
- Stored payload: `payload.patch.gz.b64`

Changed application files after applying the payload to the exact baseline:

1. `app/src/main/java/com/fush/erp/domain/TreasuryMovementType.kt`
2. `app/src/main/java/com/fush/erp/domain/AccountingService.kt`
3. `app/src/main/java/com/fush/erp/domain/AccountingReportMath.kt`
4. `app/src/main/java/com/fush/erp/data/dao/ReportDao.kt`
5. `app/src/test/java/com/fush/erp/domain/TreasuryMovementTypePolicyTest.kt`
6. `app/src/test/java/com/fush/erp/domain/AccountingReportMathTest.kt`

## Compatibility / accounting impact

- Existing historical `TREASURY_*` source names remain recognized.
- New canonical source names are reversible where the historical equivalents were reversible.
- Internal-transfer detection accepts both historical `TREASURY_TRANSFER` and canonical `TRANSFER`.
- P0 does not change account debit/credit construction, balances, inventory, production, or expense posting formulas beyond selecting a canonical movement source classification.

## Room / migration

No Room schema change. No migration added. `FushDatabase.kt`, `Migrations.kt`, and schema JSON are unchanged.

## Validation

The branch workflow reconstructs the exact Phase 14.5.54 source artifact, verifies the source tree, applies this payload, runs unit tests and `assembleRelease`, checks Application ID, Room schema 34, absence of destructive migration, exact changed-file scope, and uploads the unsigned release APK for branch testing.

This is `BRANCH ONLY / TEST ONLY`; no official versionCode/versionName or schema number is assigned by this branch.
