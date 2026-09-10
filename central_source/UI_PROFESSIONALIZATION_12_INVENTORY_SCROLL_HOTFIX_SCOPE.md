# FUSH ERP Mobile — UI Professionalization 12

## Phase
14.5.45 — Inventory Scroll & Viewport Hotfix

## Defect
The inventory workspace rendered its dashboard, KPI cards, quick actions and section selector in a fixed parent `Column`, while only the selected inventory subsection used its own `LazyColumn`. On phone-height viewports this consumed most of the usable screen and made the upper inventory controls appear permanently pinned while only a small lower area could scroll.

## Fix
- Replace the fixed inventory parent `Column` + nested vertical lists with one vertical `LazyColumn` for the entire inventory workspace.
- Make the inventory title, KPI cards, quick actions, section selector, status message and selected subsection participate in the same vertical scroll.
- Convert balance, alerts, count, transfer, lot and movement list renderers to `LazyListScope` item builders so there is no same-axis nested vertical scrolling.
- Preserve horizontal filter rows and all existing dialogs/actions.
- Keep the app-level top bar and bottom/rail navigation behavior unchanged.

## Safety boundary
UI/layout only. No DAO query, Room schema/migration, stock quantity, costing, lot/expiry rule, reorder calculation, inventory count posting, warehouse transfer posting, opening-stock posting or business service is changed.

## Validation target
- Ordered patch apply on top of Phase 14.5.44.
- `git diff --check` clean.
- No conflict markers.
- No changes under `data/` or `domain/`.
- Kotlin parser check reports no syntax-token errors.
- Device verification: swiping upward from inventory content must scroll the KPI/actions area off-screen and expose the selected list using the full viewport.
