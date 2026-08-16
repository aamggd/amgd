# FUSH ERP Mobile — UI Branch Plan P0 Inventory

Status: **COMPLETE / TESTED / HANDOFF READY**

This phase implements only **P0** from the official UI/UX/language branch plan: inventory of screens, shared UI components, and hard-coded UI text. No P1/P2/P3/P4/P5 implementation is included.

## Baseline inspected

- Central candidate: `fush/integration-printing-14.5.54`
- Central build record commit: `5095ba46a676fd6a8e048f2325c433a1f336d05d`
- Workflow source commit: `36ac48935ecc9d71c899481b0901a1c69b7354be`
- Final integrated source tree recorded by build: `1b6af7bcaa86138ae75ea3d905db4bdba0fe04ff`
- Source ZIP SHA-256: `8adc047bc38703fcb460344b4ddd2c7f18f0c47c3ca1a6c62a51d3ef590450a1`
- Application ID: `com.fush.erp.recovery`
- Branch/integration versionCode: `93`
- Branch/integration versionName: `0.15.4.54-printing-integrated`
- Room schema: `34`
- `fallbackToDestructiveMigration`: absent

The historical UI chain started from older sources, but P0 was performed against the exact 14.5.54 central artifact. No historical application file was copied over the central baseline.

## P0 inventory result

- Kotlin UI files: **29**
- Screen Kotlin files: **22**
- `@Composable` functions: **231**
- `stringResource(...)` calls: **336**
- Direct `Text("...")` / `Text(text = "...")` literals: **1,512**
- Arabic string literals in UI Kotlin: **4,617**
- UI-like literal occurrences: **5,177**
- Unique UI-like literals: **3,262**
- Default locale string resource keys: **432**
- Arabic string resource keys: **432**
- Resource-key parity: **PASS**

Full per-file counts are in `UI_INVENTORY_REPORT.md` and `ui_file_inventory.csv`.

## Existing shared UI foundation found

Central 14.5.54 already contains the professional UI foundation and shared components including design/theme, navigation shell, professional cards/states, professional form fields, language/theme controls, accessibility/state polish, profile/inventory scroll fixes, and localization foundation. P0 therefore records these as existing and does not rebuild them.

## Room / migrations

P0 adds no Room/entity/schema/migration change. The inspected central source remains schema 34 with the existing continuous migration chain through `32->33 (SECURITY)` and `33->34 (FIXED_ASSETS)`. No destructive migration fallback was found.

## Business/data impact

- Business Logic: unchanged
- Accounting posting/calculation: unchanged
- Inventory quantity/cost/lot/posting: unchanged
- Production/quality calculation: unchanged
- Authentication/security behavior: unchanged

## Validation

P0 audit validation: **PASS**.

The validated central 14.5.54 build record reports:
- Unit tests: PASS
- Release build: PASS
- Zipalign: PASS
- Application ID verified
- destructive migration absent

These central tests were not rerun solely for P0 because P0 changes no application source code. P0-specific verification consists of exact source-artifact checksum verification, UI inventory scan, resource-key parity, and application/schema/destructive-migration guards.

## Known issues / findings

P0 intentionally does not fix findings. The main finding is substantial remaining hard-coded/localization debt, especially in Accounting, Production, Planning, Purchases, HomeShell, Employees, Sales, Inventory, Parties, Security, Maintenance/Risk, Sales Representatives, and report/export definitions.

## Merge policy

No merge to `fush/main` or `fush/integration-current` is performed by this phase. The handoff contains only audit/report files; there is no application patch to apply.

## Stage gate

**P0 is complete. P1 has not been started.**
