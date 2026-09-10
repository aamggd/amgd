# Phase 14.5.22 — Production Expiry Diagnostics

- Keep production expiry/lot validation rules unchanged.
- When FEFO production issue is blocked by missing tracking data, identify the exact material by Arabic name and item code.
- Preserve the previous generic ProductionMath messages when no material context is supplied.
- No Room schema change; schema remains 23.
- No existing data is migrated or modified.
