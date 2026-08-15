# FUSH ERP Phase 14.5.46 — Professional Printing & Export

Branch: `fush/reports-printing`
Base: verified Phase 14.5.45 Financial Reports artifact.

## Scope

- Replace record-by-record PDF rendering with real tabular rows and columns.
- Render Arabic report table columns right-to-left.
- Automatically use A4 landscape for wide reports and portrait for compact reports.
- Keep Android print-preview media orientation aligned with the generated PDF.
- Repeat table headers when a table continues on a new PDF page.
- Add bounded multi-line wrapping for long table cells.
- Add page footer with Fush ERP, generation timestamp, and page number.
- Improve XLSX page setup for RTL and portrait/landscape printing.
- Export Yemeni-currency values and plain numeric values as real numeric Excel cells instead of inline text when safe.
- Preserve values with leading-zero identifiers (for example 00125) and descriptive balance labels as text.
- Add regression tests for spreadsheet-cell type detection.

## Safety

- Application ID remains `com.fush.erp.recovery`.
- Room schema remains 27; no migration is introduced.
- No accounting or inventory posting logic is changed.
- No signing credential or password is stored in the repository.

## Version

- versionCode: 85
- versionName: `0.15.4.46-printing-export`
