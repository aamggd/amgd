# FUSH ERP Mobile — Phase 14.5.47 Inventory Final Layout Handoff

Apply after Phase 14.5.46 in this order:
1. `01_build_gradle.patch`
2. `02_inventory_tabs_layout.patch`

Purpose:
- Keep the single vertical Inventory `LazyColumn` introduced by Phase 14.5.45.
- Replace the six-section horizontal `LazyRow` selector with a stable 2-column × 3-row layout.
- Give every inventory section chip a minimum 48dp touch height, centered wrapping text, and flexible row height.
- Increase bottom content padding so the final inventory content is not visually crowded by the app bottom navigation.

Safety boundary:
- UI/layout only.
- No inventory quantity, costing, count, transfer, reorder, lot/expiry, opening balance, Room entity/schema/migration, DAO, repository or domain service logic changes.
- `applicationId` remains `com.fush.erp.recovery`.
- Room schema remains owned by central integration and is not changed by this handoff.

Branch identification version: 86 / `0.15.4.47-ui-inventory-final-layout`.
Central integration owns the final integrated versionCode/versionName and APK release.

This handoff supersedes the separate experimental `fush/ui-inventory-final-layout-14.5.46` build line. The emulator visual workflow on that experimental line is no longer a required gate. The professional UI branch is the canonical location for this layout fix.
