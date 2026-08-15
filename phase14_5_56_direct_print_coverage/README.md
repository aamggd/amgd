# Fush ERP Phase 14.5.56 — Direct Professional Print Coverage

Base: verified Phase 14.5.55 Report Export Hardening artifact.
Branch: `fush/reports-printing`.

Scope:
- Add professional PDF/XLSX/share/print-preview actions directly inside operational screens that previously depended on the central Reports Center.
- Sales: current invoice search/filter result + receivables summary.
- Purchases: current invoice search/filter result + supplier balances summary.
- Customers: current customer search result + receivable/overdue balances.
- Suppliers: current supplier search result + payable/overdue balances.
- Inventory: export only the currently open section (balances, alerts, counts, transfers, lots, or movements).
- Employees: current employee search/status filter + training and equipment authorizations.
- Sales representatives: current search/status filter.
- Maintenance & safety: current asset search/status/criticality filter + work orders, breakdowns, safety incidents.
- Governance: export only the currently open tab (documents, changes, approvals, audit).
- Collections detail: current movement filter.

Correctness rule: direct exports must mirror the current UI filters/section, not silently export unrelated hidden rows.

Safety:
- Application ID unchanged: `com.fush.erp.recovery`.
- Room schema remains 27.
- No migration.
- No accounting posting changes.

Version:
- versionCode 95
- versionName `0.15.4.56-direct-print-coverage`
