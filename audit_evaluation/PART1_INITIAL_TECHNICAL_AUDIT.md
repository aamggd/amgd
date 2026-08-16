# FUSH ERP Mobile — Part 1 Initial Technical Audit

Branch: `fush/audit-evaluation`

Status: **COMPLETE AS INITIAL STATIC/BUILD AUDIT — NOT FINAL AUDIT**

## 1. Governing baseline

This phase is re-established on the current accepted Central Baseline:

- Baseline: **Phase 14.5.54 Printing Integrated**
- Source/integration branch: `fush/integration-printing-14.5.54`
- Branch record commit: `5095ba46a676fd6a8e048f2325c433a1f336d05d`
- Validated build workflow commit: `36ac48935ecc9d71c899481b0901a1c69b7354be`
- Final integrated source tree: `1b6af7bcaa86138ae75ea3d905db4bdba0fe04ff`
- Application ID: `com.fush.erp.recovery`
- versionCode of audited artifact: `93` (baseline identity only)
- versionName of audited artifact: `0.15.4.54-printing-integrated` (baseline identity only)
- Room schema: `34`

No application file was replaced with a Phase 14.5.38 file. No repair was applied in the audit branch.

## 2. What Part 1 actually proves

Part 1 is a technical baseline inspection. It proves/records:

1. Build identity and package identity of the validated 14.5.54 candidate.
2. Room schema and registered migration chain present in the audited source.
3. Absence of destructive migration fallback in the inspected application builder.
4. Existing build record for unit tests, release assembly and zipalign.
5. Static findings that require dedicated owner branches and later audit retest.

Part 1 does **not** prove end-to-end accounting correctness, inventory valuation correctness, production costing correctness, upgrade safety from every historical database, visual PDF correctness on a device, or production readiness.

## 3. Build / packaging / signing state inherited from validated Central baseline

The validated Central record reports:

- Unit tests: PASS
- Release build: PASS
- Zipalign: PASS
- Application ID: `com.fush.erp.recovery`
- Room schema: `34`
- Destructive migration fallback: not present
- Official signing certificate SHA-256: `22:D5:E2:A8:BD:48:DD:D2:33:9A:BD:C4:74:86:48:B5:09:E0:2D:04:65:24:D6:E1:18:FB:E0:50:88:15:55:86`
- APK Signature Scheme v2: true
- APK Signature Scheme v3: true

Part 1 changes documentation only; it does not alter the validated application source tree, signing configuration, versionCode, or versionName.

## 4. Room / migration state

Current Room schema in the audited 14.5.54 source: `34`.

Registered migrations:

- `1 -> 2`
- `2 -> 3`
- `3 -> 4`
- `4 -> 5`
- `5 -> 6`
- `6 -> 7`
- `7 -> 8`
- `8 -> 9`
- `9 -> 10`
- `10 -> 11`
- `11 -> 12`
- `12 -> 13`
- `13 -> 14`
- `14 -> 15`
- `15 -> 16`
- `16 -> 17`
- `17 -> 18`
- `18 -> 19`
- `19 -> 20`
- `20 -> 21`
- `21 -> 22`
- `22 -> 23`
- `23 -> 24`
- `24 -> 25`
- `25 -> 26`
- `26 -> 27`
- `27 -> 28`
- `28 -> 29`
- `29 -> 30`
- `30 -> 31`
- `31 -> 32`
- `32 -> 33` — security migration
- `33 -> 34` — fixed assets migration

The source registers these migrations in `AppContainer.kt` and does not use `fallbackToDestructiveMigration` in the inspected builder.

Historical Room schema JSON present in the source package:

`12-23`, `25-28`, `31-34`.

Gaps relative to the code migration chain are recorded under finding `AE-DB-002`; they must not be reconstructed from guesses.

## 5. Application logic impact introduced by audit branch

- Business Logic changed: **No**
- Room schema changed: **No**
- Migration added: **No**
- Accounting logic changed: **No**
- Inventory logic changed: **No**
- Production logic changed: **No**
- Sales/purchasing/HR/expenses/treasury logic changed: **No**
- Security implementation changed: **No**
- UI/localization implementation changed: **No**
- Reports/printing implementation changed: **No**
- Signing material changed: **No**

## 6. Findings formalized in Part 1

See `audit_evaluation/FINDINGS_REGISTRY.md` for full evidence, reproduction and acceptance criteria.

Part 1 registers eight findings:

1. `AE-SEC-001` — unencrypted backup archive — HIGH.
2. `AE-DB-002` — missing real Room migration instrumentation tests — HIGH.
3. `AE-DATA-003` — startup historical production/inventory data mutation — HIGH.
4. `AE-I18N-004` — incomplete localization due to hard-coded user-visible text — MEDIUM.
5. `AE-SEC-005` — automatic logout disabled by default — MEDIUM.
6. `AE-PRINT-006` — PDF body-cell truncation after four wrapped lines — MEDIUM.
7. `AE-PRINT-007` — no visual/device-level PDF/printing regression suite — MEDIUM.
8. `AE-BUILD-008` — delivered source lacks a self-contained Gradle Wrapper — MEDIUM.

All eight remain open for owner-branch correction and audit retest. No defect is closed by this phase.

## 7. Part 1 validation gate

Because Part 1 changes audit documentation only and deliberately does not change Android application source:

- Unit test result used for the pinned application baseline: **PASS**.
- Release build result used for the pinned application baseline: **PASS**.
- Application source delta introduced by Part 1: **none**.
- Room delta introduced by Part 1: **none**.
- Destructive migration introduced by Part 1: **none**.

The next application-affecting owner-branch fix must run its own full Unit/Integration/Database/Regression/Release gates before it can be retested or closed by this audit branch.

## 8. Remaining official plan

- Part 2 — accounting End-to-End: **NOT STARTED**.
- Part 3 — inventory/cost/lots/expiry/count/transfers: **NOT STARTED**.
- Part 4 — production End-to-End: **NOT STARTED**.
- Part 5 — security/permissions/backup/real upgrade testing: **NOT STARTED** except the initial static findings captured in Part 1.
- Part 6 — localization/RTL/UI/visual printing/real-device performance: **NOT STARTED** except initial static findings captured in Part 1.
- Part 7 — Master Audit Report and closure/retest ledger: **NOT STARTED**.

## 9. Part 1 handoff intent

This phase is safe to register in `fush/integration-current` as an **audit/documentation handoff only**. It must not be interpreted as an application-code integration or a defect fix.
