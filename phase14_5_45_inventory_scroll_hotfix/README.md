# FUSH ERP Mobile — Phase 14.5.45 Inventory Scroll Hotfix

Apply after Phase 14.5.44 in this order:
1. `01_scope.patch`
2. `02_build_gradle.patch`
3. `03_inventory_scroll_shell.patch`
4. `04_inventory_balance_items.patch`
5. `05_inventory_alert_items.patch`
6. `06_inventory_section_items.patch`

Fixes the inventory viewport defect where the KPI/action area remained fixed and consumed most of the phone screen while only the lower subsection scrolled.

The inventory workspace now uses one vertical `LazyColumn` from the dashboard through the selected inventory subsection. Balance, alerts, count, transfer, lot and movement sections are emitted as `LazyListScope` items, removing same-axis nested vertical lists.

Expected phone behavior after the fix: swiping upward inside Inventory scrolls the title, KPI cards, quick actions and section chips off-screen, allowing the selected inventory list to use the full available viewport. The app-level top bar and bottom navigation remain fixed by design.

UI/layout only. No stock, cost, count, transfer, reorder, lot/expiry, opening balance, Room, DAO or domain logic is changed.

Branch version: 84 / `0.15.4.45-ui-inventory-scroll-hotfix` (TEST / BRANCH ONLY). Central integration owns the final integrated version.
