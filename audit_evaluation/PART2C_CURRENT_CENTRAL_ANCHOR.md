# FUSH ERP Mobile — Part 2C Current Central Anchor

Status: **LIVE PRE-MERGE ANCHOR / NOT FINAL**

This file supersedes the earlier baseline paragraph in `PART2C_P1_CROSS_MODULE_TEST_MATRIX.md` whenever Central advances during consolidation. The scenario matrix itself remains applicable; only the module integration state and exact Central identity are re-pinned here.

## Current Central

- Branch: `fush/integration-current`
- HEAD: `ccbe1273ba5648b1e546eb5838aa4b33fcdb5d48`
- Repository tree: `21a893101a01d5a28c288a44b40050bd8fbc336f`
- `central_source` tree: `af291928524bafffea73c07439008a5236813289`
- Application ID: `com.fush.erp.recovery`
- Room schema: `35`
- Destructive migration/fallback: forbidden and not introduced by the P1 integrations under this matrix.

## P1 state at this anchor

| P1 | State in Central |
|---|---|
| Accounting P1 — Journal Integrity | **INTEGRATED** |
| Treasury P1 — Party Requirement | **INTEGRATED** |
| Purchases P1 — Supplier/AP Integrity | **INTEGRATED** |
| Sales P1 — Customer Movement Identity | **PENDING** |

Purchases P1 Central integration record reports targeted/domain regression PASS, full Unit PASS, `assembleRelease` PASS, data/schema preservation PASS, destructive migration/reset guard PASS and zipalign PASS, with Room schema remaining 35 and no new migration.

## Current pre-merge audit action

The P2C pre-merge workflow is re-pinned to this exact Central and now applies **only the Sales P1 functional patch** to a temporary copy of `central_source` for compatibility/contract testing.

The workflow must fail if:

- Central HEAD/tree/source tree changes during the run;
- any specialist branch identity differs from the pinned SHA;
- the Sales P1 patch blob differs;
- Sales P1 cannot apply cleanly over the current Central;
- the patch touches Room schema/migrations/build identity outside the exact allowlist;
- focused cross-module tests fail;
- full Unit regression fails;
- `assembleRelease` fails;
- Application ID/Room/no-destructive/zipalign guards fail.

A PASS remains only `PRE-MERGE COMPATIBILITY / CONTRACT TESTED`.

## Finality rule

Part 2C remains **IN PROGRESS / NOT FINAL** until Sales P1 is actually integrated by the Central merger, a new Central APK is built from the resulting final source, and the prepared post-merge source + APK + business E2E gate is executed against that exact build. Open audit findings `AE-ACC-009`, `AE-ACC-010` and `AE-ACC-011` remain open until their final integrated retests pass.
