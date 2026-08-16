# FUSH ERP Mobile — Central Integration Registry

Branch: `fush/integration-current`

Phase: `P0 — Central Integration Registry`

Control status: **ACTIVE / BRANCH ONLY**

> This registry is the control ledger for incremental integration. It does not promote or merge anything to `fush/main` by itself.

## 1. Central Baseline

The branch plan originally recorded Phase 14.5.54 as a central candidate pending the main conversation's adoption decision. The latest main-control state available to this integration branch identifies Phase **14.5.54 Printing Integrated** as the current accepted Central Baseline. If the main conversation publishes a newer Central Baseline, P1 and later work must be re-established on that newer baseline before any handoff is integrated.

| Field | Pinned value |
|---|---|
| Baseline | Phase 14.5.54 Printing Integrated |
| Source/integration branch | `fush/integration-printing-14.5.54` |
| Branch record commit used to establish `fush/integration-current` | `5095ba46a676fd6a8e048f2325c433a1f336d05d` |
| Validated build workflow commit | `36ac48935ecc9d71c899481b0901a1c69b7354be` |
| Workflow run | `31909754750` |
| Artifact | `FushERP-Phase14.5.54-Printing-Integrated-Build` |
| Artifact ID | `9253417429` |
| Artifact digest | `sha256:bdcfd84e2a869589f79a2ce054340202e4467ff1fade1bc191b74235b1216b54` |
| Final integrated source tree | `1b6af7bcaa86138ae75ea3d905db4bdba0fe04ff` |
| Application ID | `com.fush.erp.recovery` |
| Room schema at baseline | `34` |
| Destructive migration | **Not present** |
| Unit tests | **PASS** |
| Release build | **PASS** |
| Zipalign | **PASS** |

`versionCode = 93` and `versionName = 0.15.4.54-printing-integrated` are recorded only as identifiers of the already validated baseline artifact. They are **not** declared by this branch as the final version for any future integrated release.

## 2. Immediate Baseline Lineage / Handoff SHAs

| Sequence | Source | Pinned SHA / identity | Result |
|---|---|---|---|
| 1 | Validated Central Phase 14.5.52 | workflow commit `4d02eecdc22ce1ee67652532683c64cb79c51396`; final source tree `a350a09957e1591dbecb270533d25314aa422a27` | Inherited by 14.5.53 |
| 2 | UI handoff into 14.5.53 | `657f8db9508551dde3d7143c34ee38f3f48aab08` | Integrated and validated |
| 3 | Users/permissions handoff into 14.5.53 | `0ed4877c17d14c6ede05ed3c288cd5d55ca2a7f3` | Integrated and validated |
| 4 | Phase 14.5.53 UI + Security integration | workflow commit `58dbba04ecd6cd1aa10d60059c1c1f950d101476`; source tree `a0e6f339cd604510c3019dd628561a152a44dcaf` | Base for 14.5.54 |
| 5 | Reports/printing handoff into 14.5.54 | `fush/reports-printing@419c2264b6b69f03d20c3b57cb93cfd99a50fde1` | Integrated and validated |
| 6 | Phase 14.5.54 Printing Integrated | workflow commit `36ac48935ecc9d71c899481b0901a1c69b7354be`; final source tree `1b6af7bcaa86138ae75ea3d905db4bdba0fe04ff` | Current Central Baseline |

### Consolidated inherited scope from validated 14.5.52

The 14.5.52 build record identifies these integrated areas: Accounting 14.5.40–14.5.41, Reports/Printing 14.5.47–14.5.51, Professional UI 14.5.48–14.5.50, with existing users/permissions security retained. The old 14.5.52 record is kept as a validated consolidated source-tree identity; this registry does not invent individual historical handoff commit SHAs that are not explicitly pinned by its build record.

## 3. Room / Migration Registry — Initial State

This section is authoritative for integration numbering only when new handoffs are accepted into this branch.

| Schema transition | Origin | Integration status |
|---|---|---|
| `32 -> 33` | Existing Security migration | Registered / inherited |
| `33 -> 34` | Central Fixed Assets migration | Registered / inherited |

Current baseline schema: `34`.

No Room schema change is introduced by P0. No migration number is allocated by P0.

For future handoffs, any branch-local migration/schema number is treated as **BRANCH ONLY / PROVISIONAL** until this registry assigns the final integration sequence.

## 4. Integration Queue

Handoffs are registered here only after dependency review and validation against the pinned Central Baseline. Registration does **not** mean the application-code change has already been applied to the Central source tree.

| Order | Branch | Handoff SHA | Dependency review | Room delta | Status |
|---|---|---|---|---|---|
| 1 | `fush/users-permissions` — P1 Session Lifecycle | `b04efcc95c6ce241610610fac2b639617910751e` | Exact Phase 14.5.54 source; selective 5-file patch; full Unit + `assembleRelease` PASS in run `31917946736`; handoff files under `integration_current/handoffs/users-permissions/P1_session_lifecycle/` | None — stays `34` | **READY / VALIDATED HANDOFF — NOT YET APPLIED TO CENTRAL APP SOURCE** |
| 2 | `fush/audit-evaluation` — Part 1 Initial Technical Audit | `066608d1baaf2bee4ffeb799693b13a2b1fd89ba` | Re-established on accepted Phase 14.5.54; audit/documentation only; eight findings contain ID, severity, owner, impact, expected/actual, evidence, reproduction, acceptance criteria and retest requirement; handoff under `integration_current/handoffs/audit-evaluation/P1_initial_technical_audit/` | None — audit documentation only | **REGISTERED / AUDIT EVIDENCE — NO APP PATCH TO APPLY** |
| 3 | `fush/accounting` — P0 Accounting Integration Contract | validation record `b4b1138498c7cc1f7593fc6b646dd7574585e556`; validated workflow head `09764d6b74b2d0cb4e122694f91ac500c5202fbb` | Exact accepted Phase 14.5.54 source; exactly 3 new files; source-contract regression + Unit + `assembleRelease` + Zipalign PASS in run `31918423520`; handoff under `integration_current/handoffs/accounting/P0_integration_contract/` | None — stays `34` | **READY / VALIDATED HANDOFF — NOT YET APPLIED TO CENTRAL APP SOURCE** |
| 4 | `fush/audit-evaluation` — Part 2A Accounting Settlement Integrity | `7cb867b55384c8fa3cdd0b53b11296bb077c39bf` | Exact accepted Phase 14.5.54 source; audit/documentation only; static document↔journal↔subledger proof identifies two HIGH return-settlement findings (`AE-ACC-009`, `AE-ACC-010`) and defines the remaining dynamic accounting E2E matrix; handoff under `integration_current/handoffs/audit-evaluation/P2A_accounting_settlement_integrity/` | None — audit documentation only | **REGISTERED / AUDIT EVIDENCE — PART 2 DYNAMIC E2E STILL REQUIRED** |
| 5 | `fush/users-permissions` — P4 SQLCipher At-Rest Encryption | `291cff73472224cc2b5cce3670920e550176c4c6` | Exact Phase 14.5.54 + registered P1; selective 7-file patch; Unit + Release + Android Emulator plaintext→SQLCipher upgrade 2/2 PASS in run `31918572570`; artifact `9255807712`; handoff under `integration_current/handoffs/users-permissions/P4_sqlcipher_at_rest/` | None — stays `34`; physical representation only | **CANDIDATE — EMULATOR VALIDATED / REAL-DEVICE UPGRADE TEST REQUIRED BEFORE READY** |
| 6 | `fush/audit-evaluation` — Part 2B Dynamic Accounting Reproduction | `016a70dcb311c9cbbe557a1ac050d16b4c6c0de6` | Exact accepted Phase 14.5.54 artifact/source tree restored and verified; audit-only dynamic harness reproduced `AE-ACC-009`, `AE-ACC-010`, and closed-period bypass `AE-ACC-011`; targeted tests + full Unit + `assembleRelease` + App ID/Room/no-destructive invariants PASS in run `31919065033`; handoff under `integration_current/handoffs/audit-evaluation/P2B_dynamic_accounting_reproduction/` | None — audit/CI evidence only | **REGISTERED / VALIDATED AUDIT EVIDENCE — FINDINGS REMAIN OPEN / PART 2 STILL IN PROGRESS** |

## 5. P0 Validation Gate

P0 changes integration-control documentation only; it does not modify Android application source, Room schema, accounting logic, inventory logic, production logic, or signing configuration.

The exact pinned Phase 14.5.54 application source tree was already validated by workflow run `31909754750` with:

- Unit tests: **PASS**
- Release build: **PASS**
- Application ID: `com.fush.erp.recovery`
- Room schema: `34`
- Destructive migration fallback: **not present**
- Zipalign: **PASS**

Because P0 does not modify the application source tree, these source-level gates remain the pinned baseline verification for this phase. Any application-code integration in P1 or later must run the full gate again after applying exactly one handoff.

## 6. Signing Control

The existing 14.5.54 validation record reports the official certificate SHA-256 as:

`22:D5:E2:A8:BD:48:DD:D2:33:9A:BD:C4:74:86:48:B5:09:E0:2D:04:65:24:D6:E1:18:FB:E0:50:88:15:55:86`

No keystore, certificate private key, or password is stored or added by this registry. P0 performs no signing.

## 7. P0 Impact Statement

- Business Logic changed: **No**
- Room Schema changed: **No**
- Migration added: **No**
- Accounting affected: **No**
- Inventory affected: **No**
- Production affected: **No**
- Expense logic affected: **No**
- Application ID changed: **No**
- Signing material changed: **No**

## 8. Rules for P1+

1. Confirm the latest main-adopted Central Baseline before every integration phase.
2. Integrate only one specialized branch phase at a time.
3. Pin the exact handoff commit SHA before applying it.
4. Record included and excluded files/changes.
5. Resolve Room migration numbering here; never accept branch-local final numbering blindly.
6. Run Unit, integration, migration (when applicable), accounting sanity, inventory sanity, regression, release build, and install/upgrade gates as applicable.
7. Do not merge directly to `fush/main`.
8. Do not sign in a public workflow and do not store signing secrets in GitHub.

## 9. P0 Closure Cycle — ordered acceptance

| Order | Branch | Phase | Handoff SHA | Patch identity | Room delta | Fresh cumulative validation | Status |
|---|---|---|---|---|---|---|---|
| 1 | `fush/ui-professional-redesign` | P0 Audit / UI inventory | `19ed5ade8863aea1d7cf4e38986afab64f66aae7` | No application patch; audit implementation `d67077387a02ebef692027d44a133134843d96b9` | None — stays `34` | PASS run `31920927855`: UI resource parity + full Unit + Release + App ID + Room/no-destructive + Zipalign | **INTEGRATED / P0 CLOSED** |

## 10. P0 Live Branch Snapshot — 2026-08-16

This snapshot refreshes branch-control metadata only. **No application patch is applied by this refresh.** It is intended to satisfy P0's requirement to keep the latest Central Baseline and exact SHA/status of each current specialized delivery visible before P1 proceeds.

| Branch | Current head SHA | Latest relevant validated SHA / run | P0 disposition |
|---|---|---|---|
| `fush/accounting` | `3cf0ca5c4b4ba1e597337b5b68041b3518dbc1ae` | validated implementation `09764d6b74b2d0cb4e122694f91ac500c5202fbb`; run `31918423520` PASS | **REGISTERED — P0 contract handoff only; not applied to Central app source** |
| `fush/reports-printing` | `4fb50345ca8d338f8c3b9cef52a7cacceb953aef` | run `31915869146` PASS for Phase 14.5.58 Non-Blocking Export at same SHA | **DISCOVERED / BRANCH VALIDATED — dependency and handoff review required before P1 integration** |
| `fush/users-permissions` | `291cff73472224cc2b5cce3670920e550176c4c6` | runs `31918572585` and `31918572591` PASS at same SHA | **REGISTERED CANDIDATE — SQLCipher emulator validated; real-device upgrade evidence still required before READY** |
| `fush/ui-professional-redesign` | `a90367181746a2840f336882b5ccce826f04bb35` | implementation `8b462227e580b14b40653e016328d5a1a0e11099`; run `31920104218` PASS | **REGISTERED — P1 design-system handoff available; not applied to Central app source** |
| `fush/audit-evaluation` | `016a70dcb311c9cbbe557a1ac050d16b4c6c0de6` | dynamic harness `85640a9859609f8418cc715ae925910f83503e4e`; run `31919065033` PASS | **REGISTERED AUDIT EVIDENCE — no app patch; findings remain open** |
| `fush/bugfixes-errors` | `e0be43d2a62855c5eb9283e3f562f9ad82c28a16` | no current Central-14.5.54 handoff validation | **HISTORICAL / NOT QUEUED** |

### P0 refresh invariants

- Central Baseline remains **Phase 14.5.54 Printing Integrated**.
- Central Room schema remains **34**.
- No new migration number is allocated.
- No versionCode/versionName is declared for a future release.
- No branch-local provisional schema number is adopted.
- No application source from any specialized branch is merged in this P0 refresh.
- No signing secret or signing material is added to GitHub.
- P1 must start from this exact Central Baseline unless the main conversation declares a newer one first.
