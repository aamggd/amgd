# FUSH ERP Mobile — Treasury/Banking P1 Handoff

Branch: `fush/treasury-banking`

Status: **IN PROGRESS — validation pending**

## Exact Central baseline

- Central branch: `fush/integration-current`
- Central HEAD: `2cb8da801fc54aec8c1f0d6a83588f097ca85117`
- Exact Central application source tree: `7733c6570357eb813f7e05e5093752ea26788749`
- Application ID: `com.fush.erp.recovery`
- Room schema: `34`

P1 is re-established from this Central source. No application file from the historical treasury branch is used as a base.

## P1 scope

Official P1 requirement: a party is mandatory when the posting account requires `Customer`, `Supplier`, or `Employee` identity.

This patch makes that requirement an explicit treasury policy and invokes it at the party-link boundary in `AccountingService`. It preserves the inherited sales-representative payable protection as well.

Important compatibility rule: generic treasury vouchers remain blocked from direct posting to trade control accounts `1300` (customers) and `2100` (suppliers). Customer collections and supplier payments continue through their dedicated Sales/Purchase workflows, which already require concrete party IDs and allocation context. P1 does not weaken that subledger protection.

## Patch

- File: `treasury-banking-p1.patch`
- SHA-256: `5124c5abb47af24e997b6727aa3122b423893ed5c967e2cfeea8657a5e6b5da7`
- Expected changed application files: exactly 3
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
- No debit/credit construction, amount calculation, treasury balance, movement classification, transfer handling, inventory, production, or expense formula is changed.

## Room / migration

No Room entity/schema change and no migration is introduced by P1. Schema remains `34`.

## Validation gate

The branch workflow must export `central_source` from the exact Central HEAD above, prove the input source tree, apply only this patch, run targeted P1 tests, the full Unit suite and `assembleRelease`, then verify Application ID, Room 34, no destructive migration, exact changed-file allowlist, and preservation of the dedicated customer/supplier control-account workflows.

No merge to `fush/main` or `fush/integration-current` is performed by this branch.
