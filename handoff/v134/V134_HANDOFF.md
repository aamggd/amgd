# FUSH ERP Mobile v134 — Audit Trail + Free Quantity

Baseline: v133 TimeSelectionBackupHardening MERGED-FINAL only.

Identity:
- applicationId: com.fush.erp.recovery
- versionCode: 134
- versionName: 0.15.4.85-audit-trail-free-quantity1
- Room schema: 40
- Migration: 39 -> 40 additive/non-destructive

Implemented:
- Professional read-only Governance Audit Trail with actor name/username, system actor, Asia/Aden date/time, Arabic descriptions, real references, old/new/reason details, filters, SQLite pagination (100 rows), and AUDIT_VIEW/ADMIN authorization.
- Audit table remains immutable; no historical rows rewritten.
- Sales paid quantity and free quantity are independent; revenue uses paid quantity only while inventory/COGS include paid + free.
- Free returns restore inventory/cost without sales refund/revenue effect.
- Reports show free quantity, returns, net free, free cost, and profit impact.
- Representative free-quantity percentage limit is enforced per line/item; excess requires SALES_FREE_QTY_APPROVE plus reason and is audited.

Validation performed in the delivery environment:
- SQLite migration 39->40 smoke: PASS.
- Audit immutability contract: PASS.
- Direct Kotlin Audit presentation + Free Quantity policy smoke: PASS.
- 418 @Test methods present in source.
- Full Gradle test/build was attempted but could not start because Gradle 9.4.1 was not cached locally and services.gradle.org DNS was unavailable. No APK build/pass is claimed.

Patch storage:
- `V134_AUDIT_TRAIL_FREE_QUANTITY.patch.gz.b64` is gzip-compressed unified patch encoded with base64.
- Restore with: `base64 -d V134_AUDIT_TRAIL_FREE_QUANTITY.patch.gz.b64 | gzip -d > V134_AUDIT_TRAIL_FREE_QUANTITY.patch`

No merge to master was performed.
