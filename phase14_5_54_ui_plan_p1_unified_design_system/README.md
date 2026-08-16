# FUSH ERP Mobile — P1 Unified Design System

Status: **COMPLETE / TESTED / READY FOR REVIEW**

Official plan stage: **P1 — Unified Design System: spacing / type / inputs / buttons / dialogs / states**.

This delivery is a selective UI-only delta built and validated against the exact Central **14.5.54 Printing Integrated** source. It does not replace newer Central screen files and does not start P2 localization work.

## Baseline

- Central candidate: `fush/integration-printing-14.5.54`
- Central build record commit: `5095ba46a676fd6a8e048f2325c433a1f336d05d`
- Workflow source commit: `36ac48935ecc9d71c899481b0901a1c69b7354be`
- Final integrated source tree: `1b6af7bcaa86138ae75ea3d905db4bdba0fe04ff`
- Source ZIP SHA-256: `8adc047bc38703fcb460344b4ddd2c7f18f0c47c3ca1a6c62a51d3ef590450a1`
- Application ID: `com.fush.erp.recovery`
- Room schema: `34`

## P1 implementation

The P1 delta centralizes shared UI design tokens and applies them only to the existing shared UI components. No functional screen, service, DAO, domain, schema or build-version file is replaced.

Implemented:

- `FushSpacing`: shared spacing scale.
- `FushRadius`: shared corner-radius scale.
- `FushDimensions`: 48dp minimum touch target, 56dp field minimum height, shared avatar/brand/dialog dimensions.
- `FushElevation`: shared subtle elevation.
- Centralized `FushStatusTone` and tone color mapping.
- Material theme shapes consume shared radius tokens.
- Shared professional components consume the common spacing/dimension/tone tokens.
- Shared `FushPrimaryButton`, `FushSecondaryButton`, `FushDestructiveButton`, `FushTextAction`.
- Shared form fields use a common 56dp minimum height and shape.
- New generic `FushTextField` for later screen adoption without changing field contracts.
- Shared long-form dialog maximum height standardized to 560dp.
- Unit contract test for the design-system dimensional invariants.

## Application patch changed files

1. `UI_PLAN_P1_UNIFIED_DESIGN_SYSTEM_SCOPE.md`
2. `app/src/main/java/com/fush/erp/ui/FushDesignSystem.kt`
3. `app/src/main/java/com/fush/erp/ui/FushTheme.kt`
4. `app/src/main/java/com/fush/erp/ui/ProfessionalComponents.kt`
5. `app/src/main/java/com/fush/erp/ui/ProfessionalFormComponents.kt`
6. `app/src/test/java/com/fush/erp/ui/FushDesignSystemContractTest.kt`

## Patch delivery

Payload:

`payload/p1.patch.gz.b64`

Decode with:

```bash
base64 -d payload/p1.patch.gz.b64 | gzip -dc > p1.patch
```

- Payload SHA-256: `5447855bb849e90daa5f4d4d2d23d3a76d0721bd0dedc2869cd6ac2ee43800bd`
- Decoded patch SHA-256: `946e1f848bbd579b4cdf0d2b174b71609c3eecd0f5c52bab8badc600eba5c38c`
- Payload commit: `7bdd99e68a213b6dc7e5345a4609a9d3cd6589e5`

## Validation

Successful GitHub Actions run: `31920104218` on UI branch commit `8b462227e580b14b40653e016328d5a1a0e11099`.

Results:

- exact Central source checksum: PASS
- patch checksum: PASS
- `git apply --check`: PASS
- changed-file allowlist: PASS
- no `data/` or `domain/` changes: PASS
- no `ui/screens/` changes: PASS
- no schema or `app/build.gradle.kts` changes: PASS
- Application ID guard: PASS
- Room schema 34 guard: PASS
- destructive migration guard: PASS
- Unit Tests: PASS
- `assembleRelease`: PASS
- `zipalign -c`: PASS

Validation artifact: `FushERP-UI-P1-14.5.54-Validation`.

## Scope boundary

P1 changes presentation infrastructure only.

- Business Logic: unchanged.
- Accounting posting/calculation: unchanged.
- Inventory quantity/cost/lot/posting: unchanged.
- Production/quality/costing: unchanged.
- Security/authentication: unchanged.
- Room/entities/migrations: unchanged.
- Signing: unchanged; no key created or uploaded.

**P2 has NOT been started.**
