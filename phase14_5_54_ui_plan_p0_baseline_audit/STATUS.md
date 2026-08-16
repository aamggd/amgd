# FUSH ERP Mobile — UI Branch New Plan P0 Baseline Audit

Status: COMPLETE (audit only; no application source changed)

## Governing plan
Official branch plan: UI / UX / language for `fush/ui-professional-redesign`.
New work must be based on the current Central Baseline and transferred selectively; older UI source must not replace newer integrated files.

## Historical UI branch checkpoint before this audit
- Branch: `fush/ui-professional-redesign`
- Checkpoint HEAD before P0 audit: `6a7cbf99be5d6a68b2f24e4129e33fa79a8f9308`
- The branch contains historical ordered UI patch packages and temporary `tmp_phase52` transfer chunks.
- Original Phase 14.5.34 UI chain started from `0.15.4.33-expense-dimensions-reporting` and must NOT be used as the base for new work.

## Authoritative baseline inspected for P0
- Validated central candidate branch: `fush/integration-printing-14.5.54`
- Current branch record commit: `5095ba46a676fd6a8e048f2325c433a1f336d05d`
- Workflow source commit: `36ac48935ecc9d71c899481b0901a1c69b7354be`
- Final integrated source tree recorded by build: `1b6af7bcaa86138ae75ea3d905db4bdba0fe04ff`
- Source ZIP SHA-256: `8adc047bc38703fcb460344b4ddd2c7f18f0c47c3ca1a6c62a51d3ef590450a1`
- Application ID: `com.fush.erp.recovery`
- Branch/integration versionCode: `93`
- Branch/integration versionName: `0.15.4.54-printing-integrated`
- Room schema: `34`
- `fallbackToDestructiveMigration`: absent

## Room migrations present in the inspected source
The database builder registers a continuous safe chain:
`1->2, 2->3, 3->4, 4->5, 5->6, 6->7, 7->8, 8->9, 9->10, 10->11, 11->12, 12->13, 13->14, 14->15, 15->16, 16->17, 17->18, 18->19, 19->20, 20->21, 21->22, 22->23, 23->24, 24->25, 25->26, 26->27, 27->28, 28->29, 29->30, 30->31, 31->32, 32->33 (SECURITY), 33->34 (FIXED_ASSETS)`.

UI branch adds no migration and must not change this chain without an explicit shared ticket.

## Existing UI professionalization already present in Central 14.5.54
The source contains the UI professionalization scopes 1 through 17, including:
- foundation/design system
- sales/customers
- purchases/suppliers
- accounting/treasury
- inventory/master data
- production/quality
- employees/sales reps
- assets/maintenance/expenses
- reports/adaptive layout
- accessibility/states
- final consistency
- inventory scroll hotfix
- profile scroll polish
- professional forms/date fields
- lazy-key collision hotfix
- navigation/feedback polish
- localization foundation

The baseline also contains persistent Light/Dark and Arabic/English controls in the active application shell. These successful central changes must be preserved rather than reapplied as whole files from the historical UI branch.

## P0 hard-coded UI inventory on Central 14.5.54
Current Kotlin UI tree scan:
- `stringResource(...)` calls: 336
- direct `Text("...")` calls: 1,470
- quoted Arabic literals in UI Kotlin (rough inventory): 4,617
- Arabic-containing UI Kotlin lines: 3,615
- default English string resources: 432
- Arabic string resources: 432

Largest remaining direct `Text("...")` counts:
- AccountingScreens.kt: 184
- ProductionScreens.kt: 133
- PlanningScreen.kt: 98
- HomeShell.kt: 98
- PurchaseScreens.kt: 96
- EmployeeScreens.kt: 96
- AdvancedInventoryScreens.kt: 94
- SalesScreens.kt: 93
- PartyScreens.kt: 83
- SecurityScreens.kt: 77
- RiskControlScreen.kt: 73
- SalesRepresentativeScreens.kt: 70
- MaintenanceScreens.kt: 70
- GeographyScreens.kt: 64
- ReportsScreen.kt: 33 (but has a very high number of other Arabic literals used in report/export definitions)
- GovernanceScreen.kt: 32
- ExpenseScreens.kt: 30
- BackupRestoreScreen.kt: 23
- CollectionsDetailScreen.kt: 7
- ReauthenticationDialog.kt: 5
- LoginScreen.kt: 2

Conclusion: localization foundation exists, but P2 acceptance is NOT met. There are still many operational hard-coded strings and mixed-language risk areas.

## Previous UI work not yet safe to merge wholesale
Historical packages after the centrally present foundation include additional localization/preferences/calendar work, especially:
- Phase 14.5.51 core operational localization
- Phase 14.5.51.1 visible language/theme controls package
- Phase 14.5.52 deep operations localization
- Phase 14.5.52.1 calendar-only dates

Some equivalent functionality is already present in Central 14.5.54. Therefore these packages must be compared and functionally transplanted only where the current baseline is still missing behavior. Do not apply the historical files wholesale.

## Business/data impact of P0
- Business Logic changed: none
- Room/entity/schema changed: none
- Accounting posting/calculation changed: none
- Inventory quantity/cost/lot/posting changed: none
- Production/quality calculation changed: none
- Authentication/security changed: none

## Central validation observed
Validated 14.5.54 build record reports:
- Unit tests: PASS
- Release build: PASS
- Zipalign: PASS
- Application ID verified
- destructive migration absent
The extracted source artifact contains 39 JVM unit-test files and 0 androidTest files.

## Remaining plan items after P0
P1 — Design system: substantially complete centrally; only gap audit/fixes, no rebuild.
P2 — Localization completion: OPEN / highest priority. Remove operational hard-coded text outside documented exceptions.
P3 — RTL/LTR full-screen audit: OPEN. Verify alignment, icon direction/order and full-screen language switching on every screen.
P4 — Form UX completion: PARTIAL. Shared professional inputs/feedback exist; audit remaining validation/loading/empty/error/success and duplicate-click guards on mutating actions.
P5 — Visual regression/screenshot checks: OPEN. No complete core-screen screenshot regression suite is present.

## Rebase / transfer risks
1. `AccountingScreens.kt` has newer 14.5.54 printing/export behavior; old UI patches touching it must be transplanted around that code, never replace the file.
2. Central security/roles/session behavior is newer than the historical UI seed; shell/login/navigation patches must preserve it.
3. Historical UI numbering contains overlapping Phase 14.5.47 package names; use explicit package/file intent, not phase number alone.
4. An obsolete parallel inventory layout patch exists in older history and must not be applied again.
5. `tmp_phase52` chunks are transfer residue, not source-of-truth application changes; clean them separately after confirming package preservation.
6. Current Draft PR #1 is not suitable for direct merge into `fush/main`; selective current-baseline patches/handoffs are required.

## Next execution slice
P2-A: localization completion on the highest-risk current-baseline screens, starting with Production + Employees + Sales Representatives + Maintenance, with resource parity and no domain/data changes. Before each slice, compare against Central 14.5.54 and transplant only missing UI behavior.
