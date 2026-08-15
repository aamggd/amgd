# FUSH ERP Mobile — Phase 14.5.41 Assets, Maintenance & Operating Expenses UI

Apply after Phase 14.5.40 in this order:
1. `01_scope.patch`
2. `02_build_gradle.patch`
3. `03_maintenance_workspace.patch`
4. `04_expense_dashboard.patch`

This phase professionalizes the operational asset/maintenance/safety workspace and the operating-expense dashboard only.

It preserves maintenance plans, equipment status transition rules, inspection/calibration behavior, work-order completion, downtime calculations, expense dimensions, voucher posting, journal entries, chart-of-accounts behavior and sales-representative contribution formulas.

The current source has an operational equipment register plus fixed-asset/depreciation ledger accounts, but no independent fixed-asset depreciation register/schedule model. No depreciation formula or transaction was invented in this UI phase.
