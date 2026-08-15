# FUSH ERP Mobile — Phase 14.5.44 Final UI Consistency & Integration Handoff

Apply after Phase 14.5.43 in this order:
1. `01_scope.patch`
2. `02_handoff_manifest.patch`
3. `03_build_gradle.patch`
4. `04_shared_inline_states.patch`
5. `05_governance_risk.patch`
6. `06_planning_geography.patch`
7. `07_operations_state_consistency.patch`
8. `08_people_commercial_state_consistency.patch`

Highlights:
- Final consistency pass across secondary workspaces and nested no-data/warning/success states.
- Governance & Audit and Risk & Internal Control now use the same executive KPI language and shared state components as the rest of the ERP.
- Planning/seasonality and currency/geography use shared headers and consistent result/error/no-data presentation.
- Remaining raw no-data surfaces across inventory, maintenance, expenses, reports, party profiles, employees, representatives, sales, purchases and production are normalized.
- New `FushInlineState` / `FushNotice` reusable presentation components are UI-only.
- Includes an explicit handoff manifest telling central integration to preserve newer accounting/security/data logic during conflicts.

Safety boundary: no Room schema/entity/migration, DAO, accounting, inventory, production, quality, compensation, commission, geography calculation, planning formula, risk-control rule, governance approval rule, authentication or backup service is intentionally changed.

Branch version is 83 / `0.15.4.44-ui-final-consistency` for UI-branch identification only. Central integration owns the final integrated versionCode/versionName.
