# FUSH ERP Mobile — Phase 14.5.42 Reports, Analytics & Adaptive Layout

Apply after Phase 14.5.41 in this order:
1. `01_scope.patch`
2. `02_build_gradle.patch`
3. `03_reports_workspace.patch`
4. `04_reports_analytics.patch`
5. `05_adaptive_shell.patch`

Highlights:
- 840dp+ application shell uses a persistent NavigationRail while the full drawer remains available.
- Reports use a dedicated side navigation panel on wide screens and compact chips on phones.
- KPI grids expand to 3/4 columns where width allows; detail cards become two-column on wide screens.
- Sales, purchases, inventory and finance gain executive summaries calculated only from report data already loaded by the existing DAOs/services.
- Existing PDF/Excel/share/print export behavior is preserved.

No DAO query, accounting formula, report-source calculation, posting rule, Room schema, permission rule or business service is intentionally changed.
