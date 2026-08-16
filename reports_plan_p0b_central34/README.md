# Reports P0-B — Report Export Hardening on Central 34

Small specialized handoff for `fush/reports-printing`.

Base chain used for validation:
1. Accepted Central 14.5.54 Printing Integrated source tree `1b6af7bcaa86138ae75ea3d905db4bdba0fe04ff`, Room schema 34.
2. Validated Reports P0-A patch `ce47f8b14f51d26fd8c7a56258aaced60719e90756eb26ccde1b707ced0efb5a`.
3. P0-B functional diff only, derived from the previously successful specialized 14.5.55 report-export hardening work.

P0-B scope:
- semantic/data-aware page orientation and column weights;
- better PDF cell alignment and larger safe row capacity;
- prevent table titles from being orphaned at page bottoms;
- dynamic XLSX column widths;
- professional XLSX merged section titles, headers, borders, RTL, row heights, footer and numeric/currency styles;
- regression tests for layout heuristics and XLSX XML structure.

No Room schema, migration, accounting posting, inventory transaction, production transaction, signing or final version-number changes are part of this handoff.
