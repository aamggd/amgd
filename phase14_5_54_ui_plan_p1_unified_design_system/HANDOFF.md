# Handoff — P1 Unified Design System

## Stage

- Name: **P1 — Unified Design System**
- Official scope: unified spacing / typography / inputs / buttons / dialogs / states.
- Status: **COMPLETE / TESTED / READY FOR REVIEW**
- P2: **NOT STARTED**.

## Baseline

- Central candidate: `fush/integration-printing-14.5.54`
- Central build record commit: `5095ba46a676fd6a8e048f2325c433a1f336d05d`
- Workflow source commit: `36ac48935ecc9d71c899481b0901a1c69b7354be`
- Final integrated source tree: `1b6af7bcaa86138ae75ea3d905db4bdba0fe04ff`
- Source ZIP SHA-256: `8adc047bc38703fcb460344b4ddd2c7f18f0c47c3ca1a6c62a51d3ef590450a1`
- Application ID: `com.fush.erp.recovery`
- Room schema: `34`

## Commits / patch

- P1 payload commit: `7bdd99e68a213b6dc7e5345a4609a9d3cd6589e5`
- Successful CI-validation commit: `8b462227e580b14b40653e016328d5a1a0e11099`
- Successful CI run: `31920104218`
- P1 decoded application patch SHA-256: `946e1f848bbd579b4cdf0d2b174b71609c3eecd0f5c52bab8badc600eba5c38c`
- Delivery payload: `phase14_5_54_ui_plan_p1_unified_design_system/payload/p1.patch.gz.b64`

The authoritative final handoff branch HEAD is the branch SHA containing this document; no merge is performed by this specialized branch.

## Application files changed by the P1 patch

1. `UI_PLAN_P1_UNIFIED_DESIGN_SYSTEM_SCOPE.md`
2. `app/src/main/java/com/fush/erp/ui/FushDesignSystem.kt`
3. `app/src/main/java/com/fush/erp/ui/FushTheme.kt`
4. `app/src/main/java/com/fush/erp/ui/ProfessionalComponents.kt`
5. `app/src/main/java/com/fush/erp/ui/ProfessionalFormComponents.kt`
6. `app/src/test/java/com/fush/erp/ui/FushDesignSystemContractTest.kt`

Delivery/validation metadata on the UI branch also includes the P1 payload, P1 workflow, README, VALIDATION and this HANDOFF document. These metadata files are not part of the application patch to transplant.

## Completed features

- Central spacing scale (`FushSpacing`).
- Central radius scale (`FushRadius`).
- Central dimensions including 48dp minimum touch target and 56dp input minimum height (`FushDimensions`).
- Central subtle elevation (`FushElevation`).
- Centralized status-tone enum/color mapping.
- Material theme shapes bound to the shared radius tokens.
- Existing professional cards/states/status/avatar/brand/header components normalized to shared tokens.
- Shared primary, secondary, destructive and text-action buttons.
- Existing date/decimal/integer/phone/code-selection shared inputs normalized to shared field shape/height.
- Generic shared `FushTextField` added for later adoption.
- Long-form dialog body maximum height standardized to 560dp.
- New JVM design-system contract test for dimensions/touch targets/spacing ordering.

## Business Logic

**No Business Logic changed.**

No service, DAO, repository, calculation, posting rule, authorization rule, workflow transition, stock rule or production rule changed. Existing callbacks and validation contracts are preserved.

## Room / Migrations

**No Room change. No migration added.**

- Schema remains 34.
- Existing schema JSON 34 remains present.
- No entity/DAO/schema change is included by P1.
- `fallbackToDestructiveMigration` remains absent.

## Accounting / Inventory / Production / Security impact

- Accounting: **none** — no posting, journal, report formula or reconciliation logic changed.
- Inventory: **none** — no stock quantity, costing, lot, expiry, count or transfer logic changed.
- Production: **none** — no recipe, material issue, receipt, yield, quality, compensation or costing logic changed.
- Security: **none** — no authentication, roles, permissions, session, MFA or re-auth behavior changed.

## Tests

Successful end-to-end validation run: `31920104218`.

- Exact Central 14.5.54 source checksum: PASS
- P1 payload checksum: PASS
- P1 decoded patch checksum: PASS
- `git apply --check`: PASS
- changed-file allowlist / no data-domain-screen-schema-version changes: PASS
- Unit Tests `:app:testDebugUnitTest`: **PASS**
- Release Build `:app:assembleRelease`: **PASS**
- Application ID guard: **PASS**
- Room schema 34 guard: **PASS**
- destructive migration guard: **PASS**
- `zipalign -c -p 4`: **PASS**
- unsigned release APK SHA-256: `fd377443f087a708cb6f1e7ee5caa76ae95b3d682a4f277adf49e3f8ff84c698`

Validation artifact: `FushERP-UI-P1-14.5.54-Validation` (artifact ID `9256165539`).

## Known issues / remaining stages

1. P1 deliberately does not remove the large hard-coded localization debt found by P0. That belongs to P2.
2. Full per-screen RTL/LTR alignment/icon/order audit remains P3.
3. Remaining workflow-level form validation/loading/empty/error/success/duplicate-click work remains P4.
4. Core-screen screenshot/visual-regression suite remains P5.
5. P1 centralizes shared primitives but does not rewrite every functional screen merely to replace every local padding/button in this stage; doing so would unnecessarily increase regression surface and violate the small-delivery principle.
6. The first CI run `31920049344` failed only because the validation script did not include new untracked files in its allowlist comparison. The script was fixed; the application patch itself was not changed. Corrected run `31920104218` passed fully.

## Manual test steps

After the integration owner applies only the decoded P1 patch to the approved current Central baseline:

1. Open Login/Home and several operational screens in Light mode; confirm the existing color scheme is unchanged and shared cards/status elements use consistent rounded shapes/spacing.
2. Switch to Dark mode; confirm the same shared components remain readable and status tones remain distinct.
3. Open forms containing date, decimal, integer, phone and code-selection inputs; confirm fields are at least 56dp tall and existing values/callback behavior is unchanged.
4. Exercise a shared primary/secondary/destructive/text action; confirm touch targets are at least 48dp and labels/actions remain unchanged.
5. Trigger a destructive confirmation dialog; confirm the destructive action uses the error/destructive visual tone and confirmation behavior is unchanged.
6. Open a long shared form dialog (for example a risk/maintenance form using the common dialog body); confirm content can scroll and does not overflow on phone portrait.
7. Check loading/empty/error/notice/status components for consistent spacing and tone in Light/Dark themes.
8. Repeat representative screens at font scales 100%, 150% and 200%; confirm labels/buttons remain usable without clipped touch targets.
9. Regression-check one accounting, one inventory and one production workflow; confirm values, posting/stock/production behavior are unchanged.
10. Do not begin P2 until this P1 handoff is reviewed and approved.

## Merge rule

Do **not** merge this branch directly to `fush/main` or `fush/integration-current`. The integration owner should transplant only the P1 application patch after review against the then-current Central Baseline.
