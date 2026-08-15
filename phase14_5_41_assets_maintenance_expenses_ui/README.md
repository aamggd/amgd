# FUSH ERP Mobile — Phase 14.5.41 Assets, Maintenance & Operating Expenses UI

Apply after Phase 14.5.40 in this order:
1. `01_scope.patch`
2. `02_build_gradle.patch`
3. `03_maintenance_dashboard_assets.patch`
4. `04_maintenance_workorders_risk.patch`
5. `05_maintenance_helpers.patch`
6. `06_expense_dashboard_filters.patch`
7. `07_expense_movements_contribution.patch`

This phase professionalizes the operational asset/maintenance/safety workspace and the operating-expense dashboard only.

It preserves maintenance plans, equipment status transition rules, inspection/calibration behavior, work-order completion, downtime calculations, expense dimensions, voucher posting, journal entries, chart-of-accounts behavior and sales-representative contribution formulas.

The current source has an operational equipment register plus fixed-asset/depreciation ledger accounts, but no independent fixed-asset depreciation register/schedule model. No depreciation formula or transaction was invented in this UI phase.
