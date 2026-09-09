# V127 — Searchable Dropdown Keyboard / Popup Fix

Baseline: v126 CollectionDate-SettlementDiscount-GlobalAutocomplete.

Fixes:
- Editable exposed dropdown anchors now use `ExposedDropdownMenuAnchorType.PrimaryEditable`.
- Search popup height is capped at 300dp and remains scrollable.
- Customer search results are capped to the first 30 matches.
- Customer collection and global searchable selectors preserve text-field keyboard focus while suggestions are open.
- No database schema change; Room remains 39.
