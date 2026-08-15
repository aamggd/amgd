# FUSH ERP Mobile — Phase 14.5.34 UI Professionalization 1

Base source: `0.15.4.33-expense-dimensions-reporting`
Target UI version: `0.15.4.34-ui-professionalization-1`
Working branch: `fush/ui-professional-redesign`

This change set is UI/UX only. It does not intentionally change Room entities, migrations, accounting calculations, inventory posting, production logic, sales posting, purchase posting, or business services.

## Patch order
Apply the files in `patches/` in numeric order:

1. `01_scope.patch`
2. `02_build_gradle.patch`
3. `03_app_shell.patch`
4. `04_theme.patch`
5. `05_components.patch`
6. `06_home_shell.patch`
7. `07_login.patch`

From the root of the Phase 14.5.33 Android source, the complete patch can be assembled and applied with:

```bash
cat phase14_5_34_ui_professionalization/patches/*.patch > /tmp/fush-ui-14.5.34.patch
git apply /tmp/fush-ui-14.5.34.patch
```

## UI scope
- Material 3 light/dark design system.
- Central typography and shape tokens.
- Shared professional ERP UI components.
- Redesigned login experience.
- Redesigned app startup/error presentation.
- Redesigned main drawer, top bar and bottom navigation.
- Improved executive dashboard hierarchy, KPI cards and alert severity presentation.
- Navigation destination state survives common activity recreation.

## Merge policy
This branch should remain presentation-focused. Business/accounting/database changes belong in their specialized branches and should be merged into the central baseline separately.
