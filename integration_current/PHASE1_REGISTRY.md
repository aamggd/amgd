# FUSH ERP Mobile — Phase 1 / P0 Central Integration Registry

Working branch: `fush/integration-current-phase1-p0`
Target integration branch for handoff: `fush/integration-current`

Status: **PHASE 1 ONLY / HANDOFF CANDIDATE / NOT MERGED**

This file implements the first stage in the `fush/integration-current` work plan: establish a central integration ledger before any specialized branch is integrated.

## Baseline pinned for this phase

The branch plan names Phase 14.5.54 as the documented central candidate while leaving final promotion authority to the main conversation. This phase therefore pins the already validated Phase 14.5.54 artifact as its starting Baseline and does not promote it to `fush/main` or `fush/integration-current`.

| Field | Pinned value |
|---|---|
| Baseline | Phase 14.5.54 Printing Integrated — validated central candidate |
| Source branch | `fush/integration-printing-14.5.54` |
| Baseline record commit | `5095ba46a676fd6a8e048f2325c433a1f336d05d` |
| Validated workflow commit | `36ac48935ecc9d71c899481b0901a1c69b7354be` |
| Validated workflow run | `31909754750` |
| Artifact ID | `9253417429` |
| Artifact name | `FushERP-Phase14.5.54-Printing-Integrated-Build` |
| Artifact digest | `sha256:bdcfd84e2a869589f79a2ce054340202e4467ff1fade1bc191b74235b1216b54` |
| Final source tree | `1b6af7bcaa86138ae75ea3d905db4bdba0fe04ff` |
| Application ID | `com.fush.erp.recovery` |
| Room schema | `34` |
| Destructive migration | none |

The baseline's recorded `versionCode = 93` and `versionName = 0.15.4.54-printing-integrated` are identifiers of that already validated artifact only. This phase does **not** assign a future official versionCode/versionName.

## Baseline lineage / pinned handoffs

| Sequence | Source | Pinned identity | State |
|---|---|---|---|
| 1 | Phase 14.5.52 validated integration | workflow commit `4d02eecdc22ce1ee67652532683c64cb79c51396`; final source tree `a350a09957e1591dbecb270533d25314aa422a27` | inherited |
| 2 | UI handoff into 14.5.53 | `657f8db9508551dde3d7143c34ee38f3f48aab08` | integrated/validated upstream |
| 3 | Users & permissions handoff into 14.5.53 | `0ed4877c17d14c6ede05ed3c288cd5d55ca2a7f3` | integrated/validated upstream |
| 4 | Phase 14.5.53 UI + Security integration | workflow commit `58dbba04ecd6cd1aa10d60059c1c1f950d101476`; source tree `a0e6f339cd604510c3019dd628561a152a44dcaf` | inherited by 14.5.54 |
| 5 | Reports/printing handoff | `fush/reports-printing@419c2264b6b69f03d20c3b57cb93cfd99a50fde1` | integrated/validated upstream |
| 6 | Phase 14.5.54 Printing Integrated | workflow commit `36ac48935ecc9d71c899481b0901a1c69b7354be`; source tree `1b6af7bcaa86138ae75ea3d905db4bdba0fe04ff` | pinned Baseline for this phase |

## Room / Migration registry — initial state

| Transition | Origin | Registry state |
|---|---|---|
| `32 -> 33` | existing Security migration | inherited / registered |
| `33 -> 34` | central Fixed Assets migration | inherited / registered |

Current baseline schema: `34`.

Phase 1 introduces **no** Room schema change and allocates **no** migration number. Any future specialized branch schema/migration number remains `BRANCH ONLY / PROVISIONAL` until the integration branch assigns the final sequence.

## Integration queue

No specialized handoff is integrated in Phase 1.

| Order | Branch | Handoff SHA | Room delta | State |
|---|---|---|---|---|
| — | — | — | — | `PENDING PHASE 2 / P1` |

## Phase 1 impact

- Android application source modified: **No**
- Business Logic modified: **No**
- Room schema modified: **No**
- Migration added: **No**
- Accounting logic affected: **No**
- Inventory logic affected: **No**
- Production logic affected: **No**
- Expense logic affected: **No**
- Application ID modified: **No**
- Signing material added/modified: **No**

## Validation rule for this phase

The companion workflow `.github/workflows/validate-integration-current-phase1-p0.yml` reconstructs the exact pinned Phase 14.5.54 source from artifact `9253417429`, verifies source-tree identity, Application ID, schema 34 and absence of destructive migration fallback, then runs unit tests and an unsigned release build.

No keystore, signing key, certificate private material, or password is used by that workflow.

## Handoff rule

This working branch must not merge itself into `fush/integration-current` or `fush/main`. The receiving integration conversation may cherry-pick the Phase 1 commit after reviewing this registry and the validation workflow result.
