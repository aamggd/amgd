# FUSH ERP Mobile — Treasury/Banking P1 Handoff

Branch: `fush/treasury-banking`

Status: **COMPLETE / TESTED / READY FOR HANDOFF**

## Exact Central baseline

- Central branch: `fush/integration-current`
- Central HEAD: `2cb8da801fc54aec8c1f0d6a83588f097ca85117`
- Exact Central application source tree: `7733c6570357eb813f7e05e5093752ea26788749`
- Application ID: `com.fush.erp.recovery`
- Room schema: `34`

P1 was re-established from this exact Central source. No application file from the historical treasury branch was used as a base, and no reset/force-push was performed.

## P1 scope

Official P1 requirement: a party is mandatory when the posting account requires `Customer`, `Supplier`, or `Employee` identity.

The P1 patch makes that requirement an explicit treasury policy and invokes it at the party-link boundary in `AccountingService`. It also preserves the inherited sales-representative payable protection.

Important compatibility rule: generic treasury vouchers remain blocked from direct posting to trade control accounts `1300` (customers) and `2100` (suppliers). Customer collections and supplier payments continue through their dedicated Sales/Purchase workflows, which require concrete party IDs and preserve subledger/allocation contracts. P1 does not weaken that protection.

## Exact handoff patch

- File: `treasury-banking-p1.patch`
- SHA-256: `bc31448a56946982d7c07e1bdb70c3ff5b426a38bbd2a7a6ebbb239f6edc47c6`
- Resulting application source tree after applying P1 to the exact Central source: `afb258273e9c50d54b419c62c1ab1eec277138ae`
- Changed application files: exactly 3
  1. `app/src/main/java/com/fush/erp/domain/AccountingService.kt`
  2. `app/src/main/java/com/fush/erp/domain/TreasuryPartyRequirementPolicy.kt`
  3. `app/src/test/java/com/fush/erp/domain/TreasuryPartyRequirementPolicyTest.kt`

## Business logic

- `1300` requires exactly `customerId`.
- `2100` requires exactly `supplierId`.
- `2200` requires exactly `employeeId`.
- `2300` retains the inherited requirement for exactly `salesRepId`.
- General/non-party accounts reject orphan party IDs.
- Entity existence and active-state validation remains in the existing service/DAO path.
- Existing generic-voucher restrictions on `1300`/`2100` remain intact; customer/supplier settlement continues through dedicated workflows.
- No debit/credit construction, amount calculation, treasury balance, P0 movement classification, transfer handling, inventory, production, or expense formula is changed.

## Room / migration

- Room schema remains `34`.
- No Room entity/schema change.
- No migration added or allocated.
- `FushDatabase.kt`, `Migrations.kt`, and schema JSON are unchanged by the P1 application patch.
- `fallbackToDestructiveMigration`, `deleteDatabase`, and `clearAllTables` destructive guards were checked by CI.

## Impact

- Accounting: party identity validation at the treasury/account boundary is explicit; posting values and debit/credit construction are unchanged.
- Inventory: no impact.
- Production: no quantity/cost/BOM logic changed; employee-payable party identity protection only.
- Expenses: no amount/formula/classification change.
- Security/auth: no change.

## Validation evidence

Validated workflow head: `492a9a299b16fff48aed55af6a3d874cbf941984`

Workflow run: `31977322600`

Result: **SUCCESS**

Validation gates completed successfully:

- export exact `fush/integration-current@2cb8da801fc54aec8c1f0d6a83588f097ca85117` source: PASS;
- exact input source tree `7733c6570357eb813f7e05e5093752ea26788749`: PASS;
- exact P1 patch digest and three-file allowlist: PASS;
- targeted P1 + control-account + P0 movement regression unit tests: PASS;
- full Unit suite: PASS;
- `assembleRelease`: PASS;
- Application ID `com.fush.erp.recovery`: PASS;
- Room schema 34 and schema JSON: PASS;
- no destructive migration/database reset: PASS;
- dedicated customer/supplier workflow guard assertions: PASS;
- zipalign validation: PASS;
- unsigned validation APK upload: PASS.

Validation artifact:

- Artifact ID: `9271484240`
- Name: `FushERP-Treasury-Banking-P1-Validation`
- Artifact SHA-256: `d5d46584dac3a2783deee4637e9d466806a835608410b859a1a761e7450ab74d`
- Unsigned APK SHA-256: `47b61f585f17dc258a05d2b4caecc78d4f968df4a471062e4701b2c0a3ec4707`

## Known issues / validation notes

Two earlier CI attempts stopped before tests because of validation-harness metadata (patch hunk position, then digest pinning). Both were corrected without changing Central or relaxing any safety gate. The final run above passed all application tests and build gates. No open P1 blocker is known from this validation.

## Manual test expectations

1. Customer collection with a valid customer through the dedicated collection workflow succeeds; missing/invalid customer is rejected.
2. Supplier payment with a valid supplier through the dedicated supplier-payment workflow succeeds; missing/invalid supplier is rejected.
3. Treasury voucher using employee payable `2200` succeeds only with exactly one valid employee and rejects missing/mixed party identity.
4. Sales-representative payable `2300` remains sales-rep-only.
5. A general account with no party follows existing behavior; attaching an orphan party ID is rejected.
6. Generic treasury posting directly to `1300`/`2100` remains blocked so the dedicated customer/supplier subledger workflows are not bypassed.
7. Debit/credit values, voucher amount, P0 movement source classification and treasury balance behavior remain unchanged.

P2 has not been started.

No merge to `fush/main` or `fush/integration-current` was performed by this branch.
