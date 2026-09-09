# v130 Generic Production Batch Number

- Removed size-specific `60` / `200` batch-number hardcoding from `ProductionService`.
- Batch prefix now derives from the product master-data `code`.
- Product names and package volume do not participate in batch numbering.
- New products such as 100ml require no code change.
- Existing historical batch numbers are untouched.
- Room schema remains 39; no migration and no database recreation.
