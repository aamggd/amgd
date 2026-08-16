# Reports P0-A — Re-establish text-layout fix on Central Baseline

Branch family: `fush/reports-printing`.
Validation branch: `fush/reports-printing-rebase-central`.

Official base: Central Baseline Phase 14.5.54 Printing Integrated.
- Integration source branch: `fush/integration-printing-14.5.54`
- Validated workflow commit: `36ac48935ecc9d71c899481b0901a1c69b7354be`
- Workflow run: `31909754750`
- Final integrated source tree: `1b6af7bcaa86138ae75ea3d905db4bdba0fe04ff`
- Room schema: 34

Scope of this small handoff only:
- Re-establish the already-tested report text-layout fix from specialized Phase 14.5.54 on the Central 14.5.54 source.
- Hard-wrap long voucher/reference/UUID tokens inside their cells.
- Use font metrics for row height.
- Clip drawing to cell bounds to prevent cross-column painting.
- Improve widths for narrative/reference/amount columns.
- Keep repeated table headers.

Changed application files only:
- `app/src/main/java/com/fush/erp/ui/export/ReportExportSupport.kt`
- `app/src/main/java/com/fush/erp/ui/export/ReportTextLayout.kt`
- `app/src/test/java/com/fush/erp/ui/export/ReportTextLayoutTest.kt`

Safety:
- No full-source replacement.
- No Room entity/schema/migration change.
- Central Room schema 34 is retained.
- No accounting posting change.
- No inventory or production transaction change.
- `Application ID = com.fush.erp.recovery` retained.
- No destructive migration fallback.
- No signing material.
- Central version identifiers are left untouched; integration controls final numbering.

Known follow-up required by the new branch plan:
- This re-establishes the previous successful fix but does not yet solve arbitrary cell content beyond the current row line cap. That is a separate P2 follow-up after P0 handoffs.
- True rendered-PDF visual regression remains P4 and is not claimed here.
