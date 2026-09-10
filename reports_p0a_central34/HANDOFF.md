# Reports P0-A — Text-layout fix re-established on Central Baseline

Status: **VALIDATED / READY FOR INTEGRATION**

Source family: `fush/reports-printing`
Validation branch: `fush/reports-printing-rebase-central`
Validation commit: `573c868f1f266298e8529b9c6176ef60d310346c`
Validation workflow run: `31919056133` — SUCCESS
Artifact ID: `9255842260`
Artifact: `FushERP-Reports-P0A-Central34-TextLayout`
Artifact digest: `sha256:dd53de616941890e84263e0772fb4e775dbdede57093acc9a68b4474b15b0dbb`

## Official Central base

- Central phase: **14.5.54 Printing Integrated**
- Integrated source tree: `1b6af7bcaa86138ae75ea3d905db4bdba0fe04ff`
- Central source workflow run: `31909754750`
- Room schema retained: **34**
- Application ID retained: `com.fush.erp.recovery`

## Functional scope

This handoff carries only the previously-tested PDF text-layout correction onto the accepted Central source:

- hard-wrap long voucher/reference/UUID tokens inside their own table cells;
- calculate row height using font metrics;
- clip drawing to cell bounds so text cannot paint over a neighboring column;
- improve width weighting for narrative/reference/amount columns;
- keep repeated table headers on continuation pages.

## Changed application files only

1. `app/src/main/java/com/fush/erp/ui/export/ReportExportSupport.kt`
2. `app/src/main/java/com/fush/erp/ui/export/ReportTextLayout.kt`
3. `app/src/test/java/com/fush/erp/ui/export/ReportTextLayoutTest.kt`

## Safety / acceptance

- Full-source replacement: **NO**
- Room schema change: **NONE**
- Migration change: **NONE**
- Destructive migration fallback: **NOT PRESENT**
- Accounting posting logic change: **NONE**
- Inventory transaction logic change: **NONE**
- Production transaction logic change: **NONE**
- Unit tests: **PASS**
- Room/Compose compilation: **PASS**
- Release build: **PASS**
- Central safety verification: **PASS**
- Zipalign: **PASS**

APK validation SHA-256: `66ae1a302e3f59f02474103a3920b51a45a05846a1556c4ab6de51d5049835bb`
Patch SHA-256: `ce47f8b14f51d26fd8c7a56258aaced60719e90756eb26ccde1b707ced0efb5a`

## Follow-up from the new official reports plan

This P0-A handoff intentionally does **not** claim that arbitrary-length important cell text can never be truncated; the current renderer still has a row line cap. Removing meaningful-data truncation is a separate P2 follow-up after the remaining P0 handoffs.

True rendered-PDF visual regression is also still pending under P4 and is not claimed by this handoff.
