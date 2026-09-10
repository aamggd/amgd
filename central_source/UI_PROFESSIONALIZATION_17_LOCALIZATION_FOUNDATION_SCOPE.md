# FUSH ERP Mobile — UI Professionalization 17
## Phase 14.5.50 — Localization Foundation (Arabic / English)

### Objective
Establish a safe Android-resource localization foundation without changing route keys, persisted values, DAO/domain contracts, accounting, inventory, production, security, or database behavior.

### Implemented in this phase
- Default `values/strings.xml` is now the English UI resource set.
- `values-ar/strings.xml` contains the matching Arabic UI resource set.
- Both resource sets have the same 192 string keys.
- Existing `locales_config.xml` (`ar`, `en`) and `android:supportsRtl="true"` are preserved.
- App name, brand subtitle, accessibility descriptions, loading/error defaults, confirm/cancel actions and professional form date/phone actions now come from resources.
- Login and startup/database-state UI are resource-backed in Arabic and English.
- App shell navigation (top title, drawer, bottom navigation, large-screen rail) is resource-backed while keeping the existing internal route keys unchanged.
- Executive dashboard titles, KPI labels, alert titles/details, status labels and module cards are resource-backed.
- Module cards retain the same navigation targets; only their displayed title/subtitle/badge is localized.
- Alert format strings preserve the same counts and source values.

### Deliberate boundary
This is the localization foundation and core shell slice, not a claim that every operational screen is translated yet. Many module-specific screens still contain Arabic literals and domain/service validation messages are outside the UI-only branch. Those will be migrated in following localization phases on top of this foundation.

### Safety boundary
No intended changes to:
- Room schema/entities/migrations
- DAO queries
- Accounting posting/reversal/calculation
- Inventory quantity/cost/lot logic
- Sales/purchase calculations
- Production/quality calculations
- Employee compensation or sales-rep commissions
- Authentication/permissions/security rules
- Backup/restore service behavior
- Stored internal route keys or persisted code values

### Test / branch identity only
- versionCode: 89
- versionName: `0.15.4.50-ui-localization-foundation`

Central integration owns final integrated versioning, Room migration ordering, signing and official APK release.

### Required integration/device checks
1. Build debug + unit tests on the authoritative Android source.
2. Arabic locale: verify Arabic strings and RTL layout in login, app shell and dashboard.
3. English locale: verify English strings and LTR layout in login, app shell and dashboard.
4. Switch app/system language and relaunch; internal navigation must still open the same modules because route keys were not translated.
5. Verify dashboard counts/amounts/alerts are numerically identical in Arabic and English.
6. Verify date fields still submit `yyyy-MM-dd` and posting/calculation behavior is unchanged.
