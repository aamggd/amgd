# FUSH ERP Mobile — Phase 14.5.46 Scroll, Profile & Secondary Workspace Polish

Apply after Phase 14.5.45 in this order:
1. `01_scope.patch`
2. `02_build_gradle.patch`
3. `03_customer_profile_scroll.patch`
4. `04_supplier_profile_scroll.patch`
5. `05_customer_ledger_lazy.patch`
6. `06_supplier_ledger_lazy.patch`
7. `07_party_aux_lazy.patch`
8. `08_employee_profile_scroll.patch`
9. `09_sales_rep_profile_scroll.patch`
10. `10_governance_scroll.patch`
11. `11_risk_scroll.patch`
12. `12_master_data_scroll.patch`

Highlights:
- Customer and supplier profile identity/KPI/action/tab blocks now scroll with the selected content instead of permanently consuming phone viewport height.
- Long customer/supplier ledgers, invoices, vouchers and audit histories remain emitted as lazy-list items.
- Employee and sales-representative profiles now use one vertical LazyColumn for profile header, KPIs, tabs and selected content.
- Governance & Audit and Risk & Internal Control now use one vertical LazyColumn instead of fixed KPI/tab headers plus a nested list.
- Master Data (items/units/warehouses/conversions) now scrolls as one page, fixing the same fixed-dashboard pattern previously found in Inventory.
- Horizontal tabs and selectors remain horizontally scrollable.

Safety boundary: UI/layout only. No Room schema/entity/migration, DAO query, accounting posting, customer/supplier balance logic, employee compensation, commission logic, governance approval rule, risk-control rule, inventory, production, authentication or other domain/business service is intentionally changed.

Branch version is 85 / `0.15.4.46-ui-scroll-profile-polish` for UI-branch identification only. Central integration owns the final integrated versionCode/versionName, migration ordering, signing and official APK release.

Validation:
- All twelve patches apply sequentially over the verified Phase 14.5.45 source with `git apply --check`.
- Resulting changed files match the Phase 14.5.46 working source byte-for-byte.
- `git diff --check` is clean and no conflict markers are present.
- No Phase 14.5.46 changes exist under `data/` or `domain/`.
- Parser-oriented Kotlin check found no syntax-token errors. Full Android/Compose compilation and device gesture testing remain central integration gates.
