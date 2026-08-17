# FUSH ERP Mobile — Expenses P1 Wave 1 Revalidation Handoff

Status: **COMPLETE / TESTED / READY FOR INTEGRATION**

## Scope

This handoff is a revalidation of the already completed Expenses P1 implementation. P1 was **not** reimplemented from scratch and P2 was not started.

## Exact Central baseline

- Branch: `fush/integration-current`
- Central HEAD: `ddae764a236cb1916a46e194ccb2d3c24ca6b919`
- Central Source Tree: `30b028b75d6463c07afd0419429f53c7937fabb1`
- Application ID: `com.fush.erp.recovery`
- Central Room schema: `35`
- Expenses branch-only provisional schema after applying P1: `36`
- Provisional migration: `MIGRATION_35_36_EXPENSE_WORKFLOW_PROVISIONAL`

## Exact patch identity

- Exact Expenses P1 Patch SHA-256: `b74c2d5766381a4c2d14ecb664b72baed6260bc4ba6863614530ee2197972a54`
- The workflow decodes the stored patch and refuses to proceed unless the SHA-256 is exactly the value above.
- `git apply --check` and `git apply --index` both succeeded over the exact Wave 1 Central source.
- The Sales/Customers P1 `CustomerMovementIdentity` guard in `AccountingService.kt` is asserted after patch application to ensure the newer Central behavior is preserved.

## Validation run

GitHub Actions run: `31981236583`
Job: `95248259518`
Result: **SUCCESS**

Validation gates completed successfully:

- Exact Central HEAD / source-tree identity — PASS
- Exact Expenses P1 patch SHA validation — PASS
- Exact patch apply over latest Central — PASS
- Expenses targeted tests — PASS
  - `ExpenseLifecyclePolicyTest`
  - `ExpenseClassificationPolicyTest`
  - `ExpenseReportAnalyticsTest`
- Accounting / Treasury integration regression — PASS
  - `AccountingIntegrationContractTest`
  - `AccountingP1IntegrityPolicyTest`
  - `TreasuryMovementTypePolicyTest`
  - `TreasuryPartyRequirementPolicyTest`
  - `TreasuryFxMathTest`
  - `TreasuryReconciliationTest`
  - `CustomerMovementIdentityTest`
- Full unit test suite — PASS
- `assembleRelease` — PASS
- Room 35 -> 36 provisional migration preservation — PASS
- Existing-data sentinel preservation — PASS
- Application ID verification — PASS
- No `fallbackToDestructiveMigration` — PASS
- No destructive `DROP TABLE` / `DELETE FROM` / `VACUUM` in Expenses P1 migration — PASS
- `zipalign` + `zipalign -c` — PASS

Aligned unsigned APK SHA-256 from the validation run:

`3dfce12573d9de576aca1874dfb51e9b116d4645d3b4c0cf7623808a8758e5a8`

## Integration notes

- Do not merge this branch directly into `fush/main`.
- Central Integration should consume the exact patch identified above.
- Room `36` is **PROVISIONAL / BRANCH ONLY** and may be renumbered by Central Integration if another schema migration lands first.
- No reset, destructive rebase, force push, or destructive migration was used.
- P2 has not been started.

The final branch HEAD is the Git commit that contains this handoff and is additionally captured as `head_sha` by the final validation run triggered by this handoff commit.
