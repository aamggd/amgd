# Phase 14.5.50 — Localization Foundation

Apply after **14.5.49** in this exact order:

1. `01_scope.patch`
2. `02_build_gradle.patch`
3. `03_strings_en.patch`
4. `04_strings_ar.patch`
5. `05_shared_components.patch`
6. `06_startup_login.patch`
7. `07_shell_navigation.patch`
8. `08_dashboard_localization.patch`

## What this phase does

- Creates matched English/default and Arabic resource sets (192 string keys in each).
- Preserves the existing `ar` / `en` locale declaration and RTL support.
- Localizes the shared brand/accessibility/loading/error/form controls.
- Localizes login and startup/database-state presentation.
- Localizes the app shell: top title, drawer, bottom navigation and tablet navigation rail.
- Localizes the executive dashboard, KPI labels, alert text and module cards.
- Keeps the existing internal Arabic route keys and all stored business/status codes unchanged, so localization does not change navigation or persisted data contracts.

## Important scope boundary

This is the **localization foundation and core-shell slice**, not a claim that every operational screen is already translated. Module-specific screen strings and domain/service validation messages remain for later localization slices. No `data/` or `domain/` files are changed in this package.

## Validation performed

- All eight patches pass `git apply --check` sequentially over the verified 14.5.49 source.
- Applying all eight patches reproduces the working 14.5.50 changed files byte-for-byte.
- `git diff --check` is clean.
- English and Arabic resource files contain the exact same 192 resource keys.
- Format placeholders were compared between English and Arabic resources.
- XML resources parse successfully.
- Parser-oriented Kotlin checks found no syntax-token errors in changed Kotlin files. This is **not** a full Android/Compose build.
- No Phase 14.5.50 changes exist under `data/` or `domain/`.

## Branch test identity

- versionCode `89`
- versionName `0.15.4.50-ui-localization-foundation`

Central integration owns the final version, migrations, release build and permanent signing.
