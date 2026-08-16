# P0 UI Inventory — Central Baseline Audit

Baseline source artifact: Phase 14.5.54 Printing Integrated
Source ZIP SHA-256: `8adc047bc38703fcb460344b4ddd2c7f18f0c47c3ca1a6c62a51d3ef590450a1`

## Totals

- Kotlin UI files: **29**
- Screen Kotlin files: **22**
- `@Composable` functions: **231**
- `stringResource(...)` calls: **336**
- Direct visible `Text("...")` / `Text(text = "...")` calls: **1,512**
- Arabic string literals in UI Kotlin: **4,617**
- UI-like literal occurrences (Arabic/English heuristic): **5,177**
- Unique UI-like literals: **3,262**
- Default string resource keys: **432**
- Arabic string resource keys: **432**
- Missing keys in Arabic: **0**
- Missing keys in default locale: **0**

The direct-Text count is intentionally broader than the earlier rough grep because it includes both positional and named `text =` forms. Arabic-literal inventory intentionally over-counts report/export definition strings so later localization work does not miss them.

## Screen inventory

| Screen file | Composables | Direct Text | Arabic literals | UI-like literals | stringResource | Inputs | Dialogs | LazyColumn/Row |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| `AccountingScreens.kt` | 25 | 189 | 402 | 462 | 50 | 46 | 13 | 10 |
| `AccountingSectionExport.kt` | 0 | 0 | 193 | 198 | 0 | 0 | 0 | 0 |
| `AdvancedInventoryScreens.kt` | 11 | 94 | 252 | 282 | 58 | 13 | 12 | 7 |
| `BackupRestoreScreen.kt` | 1 | 23 | 35 | 40 | 0 | 0 | 2 | 0 |
| `CollectionsDetailScreen.kt` | 2 | 8 | 20 | 24 | 0 | 0 | 0 | 1 |
| `EmployeeScreens.kt` | 11 | 96 | 228 | 244 | 0 | 23 | 8 | 2 |
| `ExpenseScreens.kt` | 2 | 31 | 120 | 144 | 0 | 8 | 1 | 2 |
| `GeographyScreens.kt` | 9 | 64 | 123 | 133 | 0 | 17 | 3 | 4 |
| `GovernanceScreen.kt` | 3 | 32 | 65 | 74 | 0 | 11 | 2 | 1 |
| `HomeShell.kt` | 20 | 99 | 360 | 378 | 77 | 26 | 9 | 4 |
| `LoginScreen.kt` | 1 | 2 | 7 | 7 | 11 | 3 | 0 | 0 |
| `MaintenanceScreens.kt` | 9 | 73 | 175 | 184 | 0 | 26 | 8 | 1 |
| `PartyScreens.kt` | 16 | 86 | 232 | 259 | 54 | 23 | 7 | 4 |
| `PlanningScreen.kt` | 10 | 103 | 213 | 227 | 0 | 11 | 9 | 2 |
| `ProductionScreens.kt` | 17 | 142 | 483 | 526 | 0 | 33 | 12 | 7 |
| `PurchaseScreens.kt` | 8 | 100 | 177 | 195 | 28 | 7 | 7 | 1 |
| `ReauthenticationDialog.kt` | 1 | 6 | 8 | 8 | 0 | 2 | 1 | 0 |
| `ReportsScreen.kt` | 23 | 35 | 875 | 933 | 0 | 0 | 0 | 15 |
| `RiskControlScreen.kt` | 7 | 73 | 106 | 123 | 0 | 27 | 6 | 2 |
| `SalesRepresentativeScreens.kt` | 9 | 70 | 158 | 175 | 0 | 19 | 5 | 2 |
| `SalesScreens.kt` | 9 | 96 | 191 | 210 | 37 | 17 | 7 | 1 |
| `SecurityScreens.kt` | 13 | 81 | 153 | 156 | 0 | 18 | 5 | 3 |

## Shared UI component inventory

| File | Composables | Direct Text | Arabic literals | stringResource | Inputs | Dialogs |
|---|---:|---:|---:|---:|---:|---:|
| `FushErpApp.kt` | 1 | 0 | 0 | 6 | 0 | 0 |
| `FushTheme.kt` | 1 | 0 | 0 | 0 | 0 | 0 |
| `ProfessionalComponents.kt` | 16 | 0 | 4 | 10 | 0 | 1 |
| `ProfessionalFormComponents.kt` | 5 | 1 | 0 | 5 | 4 | 0 |
| `ReportExportActions.kt` | 1 | 8 | 13 | 0 | 0 | 0 |
| `ReportExportSupport.kt` | 0 | 0 | 23 | 0 | 0 | 0 |
| `SpreadsheetCellValue.kt` | 0 | 0 | 1 | 0 | 0 | 0 |

Shared component names already present include: `FushBrand`, `FushUserAvatar`, `FushSectionHeader`, `FushStatusPill`, `FushMetricCard`, `FushModuleCard`, `FushSystemState`, `FushContentStateCard`, `FushEmptyState`, `FushLoadingState`, `FushErrorState`, `FushInlineState`, `FushNotice`, `FushDialogForm`, `FushOperationMessage`, `FushConfirmDialog`, `FushDateField`, `FushDecimalField`, `FushIntegerField`, `FushPhoneField`, and `FushCodeSelectionField`.

## Highest hard-coded/localization debt

By direct Text literals: Accounting (189), Production (142), Planning (103), Purchases (100), HomeShell (99), Employees (96), Sales (96), Inventory (94), Parties (86), Security (81), Maintenance/Risk (73 each), Sales Representatives (70).

By Arabic literals: Reports (875), Production (483), Accounting (402), HomeShell (360), Inventory (252), Parties (232), Employees (228), Planning (213), Accounting export definitions (193), Sales (191), Purchases (177), Maintenance (175), Sales Representatives (158), Security (153).

## Resource parity

**PASS** — default and Arabic `strings.xml` have the same 432-key set.

## P0 conclusion

The UI inventory requested by P0 is complete for the inspected Central 14.5.54 baseline. The localization foundation exists, but hard-coded operational text remains widespread. P0 only records the current state and prioritization; it deliberately performs no production UI rewrite, no Business Logic change, no Room/schema change, and no accounting/inventory/production change.
