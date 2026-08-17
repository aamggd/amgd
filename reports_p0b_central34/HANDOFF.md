# Reports P0-B — Export hardening re-established on Central 34

Status: **VALIDATED / READY FOR INTEGRATION**

Validation branch: `fush/reports-printing-rebase-central`
Validation commit: `af85dc9e0ca9cb73b67b906c964623e6e1a8dc97`
Workflow run: `31919472743` — SUCCESS
Artifact ID: `9255982776`
Artifact: `FushERP-Reports-P0B-Central34-ExportHardening`
Artifact digest: `sha256:b397e9db8a31e5357767797b861ff3d1e90ae239aa09973ea64c420e1e841611`

## Required application order

1. Accepted Central 14.5.54 source tree `1b6af7bcaa86138ae75ea3d905db4bdba0fe04ff` / Room schema 34.
2. Reports P0-A patch SHA-256 `ce47f8b14f51d26fd8c7a56258aaced60719e90756eb26ccde1b707ced0efb5a`.
3. Reports P0-B patch SHA-256 `6c691ee930cba2276d2a0c342c0ce3198843b4778062e36a5515cf9df47b8d8f`.

The exact P0-B payload is stored immutably on validation commit `af85dc9e0ca9cb73b67b906c964623e6e1a8dc97` under `reports_plan_p0b_central34/payload/part00.txt`, `part01.txt`, `part02.txt`; the workflow reconstructs and verifies the final patch before applying it.

## Functional scope

- data/semantic-aware PDF portrait-vs-landscape selection;
- data-aware PDF column widths;
- centered alignment for codes, dates and money while narrative remains RTL/right aligned;
- larger safe PDF row capacity and continuation handling;
- keep table titles with the first table row/header when possible;
- professional XLSX dynamic widths, merged titles/sections, styled headers, borders, RTL, row heights, page footer and native numeric/currency cells;
- regression tests for layout heuristics and XLSX XML validity.

## P0-B changed files only

- `app/src/main/java/com/fush/erp/ui/export/ReportExportLayout.kt`
- `app/src/main/java/com/fush/erp/ui/export/ReportExportSupport.kt`
- `app/src/test/java/com/fush/erp/ui/export/ReportExportLayoutTest.kt`
- `app/src/test/java/com/fush/erp/ui/export/ReportSpreadsheetExportTest.kt`

## Safety and validation

- Central Room schema retained: **34**
- Application ID retained: `com.fush.erp.recovery`
- Room/Migration change: **NONE**
- Destructive migration fallback: **ABSENT**
- Accounting posting change: **NONE**
- Inventory transaction change: **NONE**
- Production transaction change: **NONE**
- Unit tests: **PASS**
- Room/Compose compilation: **PASS**
- Release build: **PASS**
- Post-build central safety: **PASS**
- Zipalign: **PASS**

Validation APK SHA-256: `10e908a2563191897c4a26f99d5407c53187198881ac4bd84bf2558646e83b0f`
P0-B patch SHA-256: `6c691ee930cba2276d2a0c342c0ce3198843b4778062e36a5515cf9df47b8d8f`

This handoff does not merge directly to `fush/main` and does not claim final versionCode/versionName/schema numbering.
