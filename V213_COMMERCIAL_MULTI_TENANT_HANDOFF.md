# FUSH ERP Mobile v213 — Commercial Multi-Tenant Foundation

## Release identity

- Application ID: `com.fush.erp.recovery`
- versionCode: `213`
- versionName: `0.15.4.164-commercial-multitenant-foundation`
- Room schema: `54`
- Baseline: **v212 only**
- Destructive migration: **not used**

## What v213 fixes

v212 compiled one cloud organization into the APK. v213 removes that compile-time tenant and makes the authenticated organization a runtime security boundary.

### Android tenant context

`CloudSession` now carries `organizationId`. After Supabase authentication, `CloudSyncRepository` loads the user's ACTIVE rows from `fush_organization_members` and resolves the active tenant. All synchronization engines consume `session.requireOrganizationId()` and therefore fail closed if no tenant is selected.

Covered cloud surfaces include:

- Master data
- Sales / receivables
- Purchases
- Supplier payments / treasury
- Inventory / production
- Accounting / journals
- Sales auxiliary / shipments
- Document-number reservation
- Device registration
- Cloud member provisioning
- FUSH AI remote requests

### Local database tenant boundary

A new singleton Room table `cloud_tenant_binding` is stored **inside the database file**. Once bound to an organization, the database cannot synchronize while authenticated to a different organization.

This design deliberately makes the boundary travel with portable backups. Restoring Company A's backup and then logging into Company B will fail before upload instead of mixing the data.

### Room migration

`MIGRATION_53_54_COMMERCIAL_TENANT_BINDING` creates only:

- `cloud_tenant_binding`
- unique index `index_cloud_tenant_binding_organization_id`

It does not update, delete, truncate, rebuild, or reseed any business table.

An Android migration test was added at:

`app/src/androidTest/java/com/fush/erp/data/CommercialTenantBindingMigrationTest.kt`

The test preserves a pre-existing currency row while validating 53 -> 54 and verifies that the new tenant table starts empty.

## Supabase commercial tenant foundation

### Existing FUSH deployment

Apply only:

`cloud/supabase/010_commercial_multitenant_foundation.sql`

It does **not** delete or rename the existing historical FUSH organization. It adds:

- `fush_create_organization(code, name)`
- random UUID tenant creation using `gen_random_uuid()`
- automatic OWNER membership for the authenticated creator
- tenant-scoped device uniqueness `(organization_id, user_id, device_key)`
- ACTIVE-only self-membership discovery needed by Android tenant resolution
- active-membership requirement for cloud binding reads
- revocation of Data API execution from legacy internal privileged trigger helpers when present
- pinned search path for the legacy treasury trigger helper when present

### Fresh customer deployment

Use:

`FUSH_ERP_Mobile_v213-CommercialMultiTenant-Supabase.sql`

The fresh installer does not seed the historical fixed FUSH organization UUID.

After an Auth user signs in, create the customer's company using:

```sql
select public.fush_create_organization('ACME', 'ACME Trading');
```

The company receives a random `organization_id`; the caller becomes its `OWNER`.

## Mandatory isolation gates before production

Run on a **staging Supabase project**:

1. `V213_MULTI_TENANT_POLICY_AUDIT.sql`
2. `V213_MULTI_TENANT_RLS_TEST.sql`

The RLS acceptance test requires two real Supabase Auth user UUIDs to replace its placeholders. The acceptance condition is:

- User A can read/write Tenant A only.
- User A cannot read/update/insert Tenant B.
- User B can read/write Tenant B only.
- User B cannot read/update/insert/delete Tenant A.
- Every `fush_*` table containing `organization_id` has RLS enabled.
- Dynamic cross-read scan over every tenant-scoped `fush_*` table returns zero opposite-tenant rows.
- Privileged SECURITY DEFINER RPCs reject cross-tenant calls for document numbers, accounting, sales auxiliary, and inventory.

## Existing v212 upgrade behavior

On first open, Room migrates 53 -> 54 with an empty tenant marker.

On the first successful cloud authentication/membership check:

- If the account has exactly one ACTIVE organization, that organization is selected and the Room DB becomes bound to it.
- If the Room DB is already bound and that organization is still an ACTIVE membership, it is selected.
- If the account belongs to multiple ACTIVE organizations and the DB is not already bound, v213 fails closed and requires an explicit company-selection feature before synchronization.

No local data is deleted to resolve ambiguity.

## Important limitation intentionally left for the next commercial gate

v213 is the **foundation**, not the full customer onboarding UI. A user belonging to multiple companies on a fresh/unbound database does not yet get a graphical company selector. The app refuses synchronization instead of guessing. The proper company creation/selection/setup wizard belongs to the commercial onboarding phase.

## Validation status in this delivery

- Original static multi-tenant source gate: **39/39 PASS**
- Executable host commercial tenant gate: **48/48 PASS**
- Host SQLite Migration 53 → 54 preservation/uniqueness probe: **PASS**
- Newly introduced model/entity/migration Kotlin host compile probe: **PASS**
- Connected Supabase baseline audit: **READ-ONLY PASS for 42/42 tenant tables having RLS**
- Production Supabase migration: **NOT applied**
- Room MigrationTestHelper device test: **added, not executed here**
- Live two-user staging RLS/RPC A/B test: **packaged, not executed here**
- Gradle unit tests / release build / lint: **not executed here** because this execution environment does not contain the required Android build toolchain and the supplied baseline was missing `gradle-wrapper.jar`.
- APK signing: **not executed here**

Do not promote v213 to production until the remaining release gates in `V213_RELEASE_VALIDATION.txt` are PASS.
