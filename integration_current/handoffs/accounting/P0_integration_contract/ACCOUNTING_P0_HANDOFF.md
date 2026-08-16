# FUSH ERP Mobile — Accounting P0 Handoff

## Handoff status
**READY / VALIDATED HANDOFF — NOT YET APPLIED TO CENTRAL APP SOURCE**

This handoff is delivered by `fush/accounting` under the new binding accounting work plan. It must be reviewed/applied by `fush/integration-current`; it does not merge anything directly to `fush/main`.

## Source branch
- Branch: `fush/accounting`
- Validated workflow head: `09764d6b74b2d0cb4e122694f91ac500c5202fbb`
- Validation-record commit after successful build: `b4b1138498c7cc1f7593fc6b646dd7574585e556`

## Central Baseline used
- Phase: `14.5.54 Printing Integrated`
- Central source branch: `fush/integration-printing-14.5.54`
- Central validation run: `31909754750`
- Central source ZIP SHA-256: `8adc047bc38703fcb460344b4ddd2c7f18f0c47c3ca1a6c62a51d3ef590450a1`
- Application ID: `com.fush.erp.recovery`
- Room schema: `34`

## Payload
Exact patch is pinned on the source branch:
- Path: `fush_accounting/rebase/ACCOUNTING_P0_INTEGRATION_CONTRACT_14_5_54.patch`
- Git blob SHA: `79519e120c66edf5e2d53f3c064f26667664a313`
- Patch SHA-256: `c16ae280137aa3dc8777011e36d93af00e2ed0c34f9178ddcc0c99dc9fbc100c`
- Pin to commit: `b4b1138498c7cc1f7593fc6b646dd7574585e556`

Validation artifact:
- Run: `31918423520`
- Artifact ID: `9255691655`
- Name: `FushERP-Accounting-P0-14.5.54-Validated-Handoff`
- Artifact digest: `sha256:71a543b430b232ff2a998f2503a32e4ec5c065bb2b7a53064495ebf4ebed2efd`

## Application files changed by the patch
Exactly three **new** files:
1. `ACCOUNTING_P0_INTEGRATION_CONTRACT_14_5_54.md`
2. `app/src/main/java/com/fush/erp/domain/AccountingIntegrationContract.kt`
3. `app/src/test/java/com/fush/erp/domain/AccountingIntegrationContractTest.kt`

No Central application file is overwritten by an older branch copy.

## Functional scope
P0 defines the accounting integration contract only:
- 36 journal source types are registered.
- Accounting, treasury, sales, purchases, inventory, production and fixed-assets domains are covered.
- Each source type records its source-reference semantics, replay-safety policy and reversal policy.
- P0 explicitly identifies unsafe current references that require immutable event IDs before P1 idempotency.

## Validation
- Exact Central baseline digest: PASS
- Exact patch digest: PASS
- Patch applies cleanly: PASS
- `git diff --check`: PASS
- Application ID: PASS (`com.fush.erp.recovery`)
- Contract/source regression: PASS
- Unit tests: PASS
- `assembleRelease`: PASS
- Room schema 34 unchanged: PASS
- No destructive migration/fallback: PASS
- Zipalign: PASS

## Room / Migration
- Room delta: **NONE**
- Migration: **NONE**
- Central schema remains `34`.

## Impact
- Accounting posting amounts changed: **NO**
- Customer/supplier balances changed: **NO**
- Inventory quantities changed: **NO**
- Production quantities changed: **NO**
- Security/permissions changed: **NO**
- UI styling changed: **NO**
- Signing material changed: **NO**

## Known P1 dependency / risk
Do **not** copy the historical branch-local Phase 14.5.43 idempotency patch wholesale. P0 found source-reference collisions/instability for commission events, production corrections, generated treasury voucher references, fiscal-year close/reclose cycles, and selected fixed-asset events. P1 must establish immutable event identities first and then enforce replay safety/payload matching selectively.

## Integration instruction
1. Reconfirm that Phase 14.5.54 remains the accepted Central Baseline when this handoff is actually applied.
2. Fetch the exact patch at the pinned source-branch commit above.
3. Verify SHA-256 before application.
4. Apply only this 3-file patch.
5. Re-run Unit tests, contract regression and `assembleRelease` on the integration source.
6. Do not allocate a Room migration number; P0 has no Room change.
7. Do not merge directly to `fush/main`.

`ACCOUNTING_P0_HANDOFF_READY`
