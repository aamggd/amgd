# Phase 14.5.51.1 — In-App Language & Theme Controls

Apply after **14.5.51** in this order:

1. `01_scope.patch`
2. `02_build_gradle.patch`
3. `03_preference_strings.patch`
4. `04_language_theme_state.patch`
5. `05_drawer_preferences.patch`

## What this fixes
- Adds explicit Arabic / English controls inside the navigation drawer.
- Adds explicit Light / Dark controls inside the navigation drawer so the option is visible even while the drawer is open.
- Persists language and theme choices in the existing local UI preferences.
- Applies the selected locale immediately to Compose resources and RTL/LTR layout without logging the user out or rerunning database startup.
- Keeps the existing top-app-bar theme action.

## Safety boundary
UI/resource preference only. No Room schema/migration, DAO, posting, inventory, production, payroll/commission, permission/authentication, or backup behavior changes.

## Branch test identity
- versionCode `92`
- versionName `0.15.4.51.1-ui-language-theme-controls`
