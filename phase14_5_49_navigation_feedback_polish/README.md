# FUSH ERP Mobile — Phase 14.5.49 Navigation, Feedback & Dialog Polish

Apply after Phase 14.5.48 in this order:
1. `01_scope.patch`
2. `02_build_icons_dependency.patch`
3. `03_shared_feedback_dialog_components.patch`
4. `04_navigation_dashboard_cleanup.patch`
5. `05_commerce_feedback.patch`
6. `06_accounting_inventory_feedback.patch`
7. `07_people_party_feedback.patch`
8. `08_production_planning_feedback.patch`
9. `09_governance_risk_maintenance_polish.patch`
10. `10_geography_feedback.patch`

Highlights:
- Replaces prototype Arabic-letter navigation glyphs with consistent Compose Material icons.
- Adds app-shell Snackbar feedback for successful operations while keeping failures visible inline.
- Removes development phase/version labels from normal operational UI.
- Makes dashboard module cards open their actual destinations and uses operational category badges.
- Makes longer risk/control and maintenance dialog forms scrollable on small phones.

Safety boundary: UI/presentation only. No Room schema/migration, DAO, accounting posting, inventory, production calculation, employee compensation, commissions, risk-service, maintenance-service, authentication, permissions or backup-service behavior is intentionally changed.

Branch identity: versionCode 88 / `0.15.4.49-ui-navigation-feedback-polish`. Central integration owns final integrated versioning and release signing.

Validation:
- All ten patches apply sequentially over the verified Phase 14.5.48 source with `git apply --check` and `git apply`.
- Resulting changed files match the Phase 14.5.49 working source byte-for-byte.
- No whitespace diagnostics or conflict markers were found.
- No changes exist under `data/` or `domain/`.
- Parser-oriented Kotlin checks on the shared components, app shell, risk and maintenance files found no syntax-token errors. Full Android/Compose build and device tests remain central integration gates.
