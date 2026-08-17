# FUSH ERP Mobile — UI Professionalization 13

## Phase
14.5.46 — Scroll, Profile & Secondary Workspace Polish

## Scope
- Remove remaining fixed-header viewport traps from customer, supplier, employee and sales-representative profiles.
- Make profile identity, KPIs, actions, messages and tabs participate in the same vertical scroll as the selected profile content.
- Preserve lazy rendering for long ledgers, invoices, vouchers and audit histories where practical.
- Convert Governance & Audit and Risk & Internal Control to one vertical lazy-scroll surface so KPI blocks no longer permanently consume phone viewport height.
- Keep horizontal tab/selector scrolling independent from vertical page scrolling.
- Preserve existing dialogs, actions, permissions and business-service calls.

## Safety boundary
UI/layout only. No Room schema/entity/migration, DAO query, accounting posting, customer/supplier balance logic, employee compensation, sales-representative commission logic, governance approval rule, risk-control rule, authentication, inventory, production or other domain/business service is intentionally changed.

## Validation target
- Apply ordered patches after Phase 14.5.45 with `git apply --check`.
- `git diff --check` clean and no conflict markers.
- No Phase 14.5.46 changes under `data/` or `domain/`.
- On phone, profile/KPI/action headers must scroll off-screen with content instead of reserving a fixed large viewport block.
- Full Android/Compose build and device gesture regression remain central integration checks.
