# Phase 14.5.50.1 — Persistent Dark / Light Theme Toggle

Apply after **14.5.50** in this order:
1. `01_scope.patch`
2. `02_build_gradle.patch`
3. `03_theme_strings.patch`
4. `04_theme_state_persistence.patch`
5. `05_theme_toggle_button.patch`

Adds a top-app-bar Dark/Light toggle, applies the existing `FushTheme` immediately, and persists the choice locally with SharedPreferences. The system theme is used only when no user choice exists yet.

Arabic/English button labels and accessibility descriptions are included. No `data/` or `domain/` changes.

Branch test identity: versionCode 90 / `0.15.4.50.1-ui-theme-toggle`.
