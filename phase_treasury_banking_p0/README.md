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
- Re-confirmed against current integration control: `fush/integration-current@72d8f7fa5298ed577776c3c66c07d4f2c63fef44`, whose registry still pins Phase 14.5.54 as the accepted Central Baseline. No newer Central Baseline was published before this P0 re-validation.

## Phase P0 scope

Implements only the movement-type classification required by the treasury/banking branch plan:

- `CUSTOMER_RECEIPT`
- `SUPPLIER_PAYMENT`
- `EXPENSE_PAYMENT`
- `EMPLOYEE_PAYMENT`
- `TRANSFER`
- `ADJUSTMENT`

The classification is separated from UI voucher labels so a generic payment voucher is not automatically treated as an expense. P0 intentionally does not add the P1 party-mandatory/party-validity rules; unmatched legacy voucher/party combinations remain postable and classify as `ADJUSTMENT` rather than being rejected by this phase.

## Functional source patch

- Local reconstructed-source commit: `610e5f5e9b3eca50b1e810ef1071305e7bede2e7`
- Resulting source tree: `e904951e4c7fe279cf619fcabe18c8b347d46de7`
- Patch SHA-256: `f0471f24fca05c1babdbb888975cd37cae8bac5c7640c28329a8575e528b1e2e`
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
- P0 does not change debit/credit construction, account balances, party requirements, inventory, production, or expense posting formulas beyond selecting a canonical movement source classification.

## Room / migration

No Room schema change. No migration added. `FushDatabase.kt`, `Migrations.kt`, and schema JSON are unchanged.

## Validation

The branch workflow reconstructs the exact Phase 14.5.54 source artifact, verifies the source tree, applies this payload, runs unit tests and `assembleRelease`, checks Application ID, Room schema 34, absence of destructive migration, exact changed-file scope, and uploads the unsigned release APK for branch testing.

This is `BRANCH ONLY / TEST ONLY`; no official versionCode/versionName or schema number is assigned by this branch.
