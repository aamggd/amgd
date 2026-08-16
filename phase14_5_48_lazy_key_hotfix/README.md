# FUSH ERP Mobile — Phase 14.5.48 Lazy List Key Collision Hotfix

Apply after Phase 14.5.47 in this order:
1. `01_scope.patch`
2. `02_build_gradle.patch`
3. `03_expense_collections_keys.patch`
4. `04_maintenance_keys.patch`
5. `05_production_reports_keys.patch`
6. `06_people_party_keys.patch`
7. `07_governance_risk_keys.patch`
8. `08_geography_keys.patch`

Purpose: prevent Compose lazy-list crashes caused by duplicate keys across independent entity lists or non-unique derived keys.

Confirmed crash pattern: Expense rows and sales-representative contribution rows were siblings in the same `LazyColumn` and used independent numeric IDs directly. When an expense ID and representative ID matched, Compose detected the duplicate only when the lower item entered the viewport, causing the app to close while scrolling near the bottom.

The audit also namespaces keys in other multi-list workspaces (maintenance, production detail, production reports, employee/representative profiles, governance, risk/internal control, geography, party helpers) and makes collection-detail/material-availability keys explicitly unique.

Safety boundary: UI/Compose item identity only. No schema, DAO, posting, calculations, balances, stock, production, maintenance, commission, compensation, risk/governance rules, authentication or business services are changed.

Branch version: 87 / `0.15.4.48-ui-lazy-key-hotfix` for UI-branch testing only.
