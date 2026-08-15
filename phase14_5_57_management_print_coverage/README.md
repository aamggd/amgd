# Fush ERP Phase 14.5.57 — Management / Planning Print Coverage

Base: verified Phase 14.5.56 Direct Professional Print Coverage artifact.
Branch: `fush/reports-printing`.

Scope:
- Currency & geography: FX history, province policies, product province prices, geographic quote, geographic profitability.
- Planning & seasonality: selected product/province, forecast, seasonality, demand plan, weekly budget vs actual, province seasonality, inventory planning policy, production plan and material requirements.
- Risk & internal control: export only the currently open tab (risks, controls, tests, exceptions, segregation of duties).
- Executive dashboard: 30-day financial/operational KPIs, current alerts and system pulse.
- Master data: export only the currently selected section and current search result (items, units, warehouses, conversions).
- Backup/restore: print/export a verification certificate only for the backup actually created in the current session.

Correctness rule: direct exports mirror current UI scope; no fabricated backup history or hidden-tab rows.

Safety:
- Application ID unchanged: `com.fush.erp.recovery`.
- Room schema remains 27.
- No migration.
- No accounting posting changes.

Version:
- versionCode 96
- versionName `0.15.4.57-management-print-coverage`
