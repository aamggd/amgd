# Phase 13.5 — Master data editing

This hotfix is inserted before Phase 14.

## Added
- Edit item/material Arabic and English names.
- Edit reorder level and shelf-life settings.
- Edit lot/expiry tracking for non-finished items; finished goods keep mandatory lot/expiry controls.
- Keep item code, category, and base unit immutable in this screen to protect historical stock and unit-conversion integrity.
- Edit unit Arabic/English names while keeping the unit code stable.
- Edit warehouse Arabic/English names and location while keeping warehouse code stable.
- Edit customer Arabic/English name, phone, address, province, channel, classification, currency, sales representative, credit permission, credit limit, and credit days.
- Keep customer code stable.
- Customer credit changes affect future credit sales only; existing receivables remain collectible and are not rewritten.
- All item/unit/warehouse/customer edits create audit events.

## Database
- No schema migration is required. Existing columns are reused.
- Room database version remains unchanged.

## Android version
- versionCode: 23
- versionName: 0.13.5-phase13-master-edit
