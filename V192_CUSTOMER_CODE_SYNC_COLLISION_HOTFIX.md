# FUSH ERP Mobile v192 — Customer Code Sync Collision Hotfix

- Baseline: v191 Admin Temporary Test Cleanup.
- AppID remains `com.fush.erp.recovery`.
- versionCode: 192.
- versionName: `0.15.4.143-customer-code-sync-collision-hotfix`.
- Room schema unchanged; no destructive migration.

## Fix

Customer creation now reconciles `MASTER:CUS` against the highest `CUS-xxxxxx` already present in the local customer table after cloud hydration. This prevents a stale local sequence from reusing an existing `customers.code`.

When the local user is cloud-linked, a generated customer code is also reserved through the existing company document-number reservation service under `CUSTOMER_MASTER`; if another phone already reserved the candidate, the local sequence advances and retries. Offline/local-only operation remains supported.

No existing customer row is deleted or renumbered.
