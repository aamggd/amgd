# FUSH ERP Mobile — UI Professionalization 1

Baseline: `0.15.4.33-expense-dimensions-reporting`
UI branch: `0.15.4.34-ui-professionalization-1`

## Scope
This branch changes presentation only. It does not modify accounting math, inventory posting,
production workflows, Room entities, DAO behavior, or business services.

## Implemented
- Central Material 3 design system with complete light and dark color schemes.
- Central typography scale and rounded shape system.
- Professional reusable UI components for brand, status, metrics, modules, avatars, and startup states.
- Redesigned login screen with clearer hierarchy, progress feedback and error state.
- Removed visible test credentials from the login UI.
- Redesigned main drawer header and navigation presentation.
- Redesigned top app bar and primary bottom navigation.
- Dashboard section headers, KPI cards and module cards now use the shared design system.
- Alert severity is represented with status pills instead of emoji decoration.
- Current destination uses rememberSaveable to survive common activity recreation.
- Startup/loading/database error states use the same visual language as the app.

## Intentionally not changed
- Room database schema and migrations.
- Accounting, sales, purchase, production or inventory calculations.
- Existing screen-level workflows/forms beyond inherited MaterialTheme improvements.
- APK signing configuration.

## Next UI phases
1. Sales + customers.
2. Purchases + suppliers.
3. Accounting + cash/banks.
4. Inventory + master data.
5. Production + quality.
6. Employees + sales representatives.
7. Reports + responsive tablet/list-detail layouts.
8. Accessibility pass, empty states, loading skeletons, confirmation dialogs and final polish.
