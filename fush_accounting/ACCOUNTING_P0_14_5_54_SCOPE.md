# Accounting P0 — Integration Contract on Central 14.5.54

## Binding plan
Implements **P0 only** from the new `fush/accounting` work plan: define the accounting integration contract, journal-creating events, and source/reference identity rules before duplicate-posting protection is reintroduced.

## Central Baseline
- Phase: `14.5.54 Printing Integrated`
- Source branch: `fush/integration-printing-14.5.54`
- Validated workflow run: `31909754750`
- Artifact: `FushERP-Phase14.5.54-Printing-Integrated-Build`
- Application ID: `com.fush.erp.recovery`
- Central Room schema: `34`

## Scope
1. Add executable `AccountingIntegrationContract` registry.
2. Cover accounting, treasury, sales, purchases, inventory, production and fixed-assets journal events.
3. Record source reference semantics, replay-safety classification and reversal policy.
4. Expose deterministic canonical key construction only for registered source types.
5. Explicitly identify current source-reference gaps that must be corrected in P1 before idempotency is enforced.
6. Add direct Unit tests and a CI source-contract regression check.

## Important finding
The older Phase 14.5.43 idempotency patch is **not safe to copy wholesale** to the new Central Baseline. Examples that need stable event identities first include commission accrual/reversal events, production correction events, generic treasury vouchers, fiscal-year close/reclose cycles and selected fixed-asset flows.

## Explicit exclusions
- No posting amount changes.
- No customer/supplier balance changes.
- No inventory quantity changes.
- No production quantity changes.
- No UI redesign.
- No Room entity/schema/migration change.
- No permissions/security change.
- No signing change.

## Patch
`fush_accounting/rebase/ACCOUNTING_P0_INTEGRATION_CONTRACT_14_5_54.patch`

SHA-256:
`c16ae280137aa3dc8777011e36d93af00e2ed0c34f9178ddcc0c99dc9fbc100c`

## Acceptance gate
- Patch applies cleanly to validated Central 14.5.54 source.
- Contract source types are unique and resolvable.
- Contract covers all operational domains required by the plan.
- CI verifies every current journal-producing source type is represented by the contract or is an explicitly dynamic treasury type represented by the registry.
- Unit tests PASS.
- Room remains schema 34; no migration is introduced.
- No destructive migration fallback exists.
- Application ID remains `com.fush.erp.recovery`.
- `assembleRelease` PASS and APK zipalign PASS.
