# FUSH ERP Mobile — Accounting Phase 14.5.39 Validated

Status: **VALIDATED / READY FOR INTEGRATION REVIEW**

## Base
- Official baseline: Phase 14.5.38 Professional UI
- Accounting rebase validation run: `31863175468`
- Application ID retained: `com.fush.erp.recovery`
- Branch test versionCode retained: `77`
- Branch test versionName retained: `0.15.4.38-ui-inventory-master-data`

## Phase 14.5.39 — Foreign Currency Treasury & Revaluation
The phase adds accounting support for foreign-currency treasury and bank balances by separating original-currency quantity from base-currency carrying value.

Implemented controls include:
- Original-currency treasury balance tracking.
- Foreign-currency cash counts in the treasury currency.
- Foreign-currency bank-statement reconciliation in the statement currency.
- Period-end FX revaluation without changing original currency quantity.
- Unrealized FX gain/loss posting through accounting entries.
- Period-closing checks requiring the applicable foreign-currency revaluation/reconciliation controls.
- UI presentation of original-currency balance and YER_NEW carrying value separately.

## Database
- Accounting branch schema after this phase: `32`.
- Migration introduced in this branch: `MIGRATION_31_32`.
- These numbers are **PROVISIONAL / BRANCH ONLY** and must be renumbered by the integration owner if another branch occupies the same migration sequence.
- No destructive migration is permitted or used.

## GitHub validation
Workflow: `.github/workflows/build-accounting-phase14.5.39.yml`

Successful run: `31865418288`

Results:
- Patch reconstruction and all integrity hashes: PASS
- Patch apply check on validated accounting rebase source: PASS
- Application identity checks: PASS
- Migration safety checks: PASS
- Unit Tests: PASS
- Release Build: PASS
- Room Schema 32 generation: PASS
- Zipalign: PASS
- Artifact upload: PASS

Artifact: `FushERP-Accounting-Phase14.5.39-FX-Treasury-Validated-Build`
Artifact ID: `9241925491`
Artifact digest: `sha256:c83c307c05bce41cb9ab0d6af6a50a0ed58fcf1fd2bc750295c7c75878751327`

Validated output hashes:
- aligned unsigned APK: `7678a4998f0a8c3162a923f61791a3b1c72fba4b7c9742251e462ecdab331159`
- full source ZIP: `baaac9a8e80eaddbc2c6491b09739f379665f0b858cbd9766b69b595954938f3`
- Phase 14.5.39 patch: `61d1b605a77b493aebc93b74f539035d8c7c55931b708b03994e61aed574cd33`

## Integration rule
The authoritative Phase 14.5.39 transfer payload is reconstructed from:
`fush_accounting/rebase/phase14.5.39_chunks_v2/`

The earlier `phase14.5.39_chunks` transport set was removed after failing integrity validation and MUST NOT be used.

For project integration, start from the validated accounting rebase and then apply the Phase 14.5.39 patch. Migration numbers must be reconciled with all other specialized branches before merging to the main line.

## Signing
CI intentionally does not store or use the permanent signing key or password. The generated APK is aligned but unsigned. Official signing must be performed only by the integration/release process with the project's permanent key.
