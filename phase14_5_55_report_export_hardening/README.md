# Fush ERP Phase 14.5.55 — Professional Report Export Hardening

Base: verified Phase 14.5.54 PDF Text Layout Fix artifact.
Branch: `fush/reports-printing`.

Scope:
- Smart portrait/landscape selection based on semantic/content pressure, not only column count.
- Adaptive PDF column widths using headers and real row content.
- Prevent orphan table headings at page bottoms.
- Professional alignment for dates, codes, amounts and narrative text.
- Keep hard-wrap and cell clipping protections from Phase 14.5.54.
- Professional XLSX styling: RTL, dynamic widths, merged section titles, shaded/bordered table headers, bordered data cells, numeric/currency formats, generated timestamp and print footer/page numbering.
- XLSX regression test parses every generated XML entry to verify workbook structural validity.

Safety:
- Application ID unchanged: `com.fush.erp.recovery`.
- Room schema remains 27.
- No migration.
- No accounting posting logic changes.

Version:
- versionCode 94
- versionName `0.15.4.55-report-export-hardening`
