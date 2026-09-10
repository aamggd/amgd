# FUSH ERP Mobile — Phase 14.5.43 Accessibility & Final UX Polish

Apply after Phase 14.5.42 in this order:
1. `01_scope.patch`
2. `02_build_gradle.patch`
3. `03_shared_accessibility_components.patch`
4. `04_shell_login_accessibility.patch`
5. `05_sales_purchases_parties_states.patch`
6. `06_inventory_production_states.patch`
7. `07_people_collections_states.patch`
8. `08_backup_restore_states.patch`

Highlights:
- Shared Material 3 state cards for empty, loading and error situations.
- Shared destructive confirmation dialog with explicit action wording.
- Accessible heading/live-region semantics for important states and login errors.
- Decorative logo/glyph announcements are suppressed where surrounding text already provides meaning.
- Clickable shared metric/module cards expose button roles/action labels and explicit >=48dp minimum touch targets.
- High-frequency empty states are consistent across sales, purchases, customers/suppliers, inventory, production, employees, sales representatives and collections.
- Production recipe/order deletion uses a consistent destructive confirmation pattern without changing deletion rules.
- Backup/restore exposes clearer loading/error/permission states without changing backup or restore behavior.

Safety boundary: UI/accessibility only. No Room schema, DAO queries, posting/reversal rules, report formulas, stock quantities/costing, production transitions, compensation/commission calculations, authentication verification or backup/restore service behavior is intentionally changed.

Full Android/Compose compile plus TalkBack, large-font and device-level accessibility checks remain central integration tests because the supplied source package does not include the project Gradle wrapper/Android SDK toolchain in this environment.
