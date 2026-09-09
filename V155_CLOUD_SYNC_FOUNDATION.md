# FUSH ERP Mobile v155 — Cloud Sync Foundation

## Release identity
- Application ID: `com.fush.erp.recovery`
- versionCode: `155`
- versionName: `0.15.4.106-cloud-sync-foundation`
- Room schema: `46` (unchanged)
- No destructive migration added.

## What was added
1. Supabase endpoint/public client configuration through `BuildConfig`.
2. A cloud authentication session store protected with Android Keystore AES/GCM.
3. Supabase password sign-in and refresh-token handling using the Auth REST API.
4. Organization membership check through the existing RLS-protected `fush_organizations` table.
5. Device registration/upsert into `fush_sync_devices` using a SHA-256 device key derived from package + Android ID.
6. New bilingual **Cloud Sync / المزامنة السحابية** screen.
7. Cloud Sync navigation is restricted to users with `ROLES_MANAGE` (ADMIN by default).
8. Reference SQL for the Supabase foundation under `cloud/supabase/001_cloud_sync_foundation.sql`.

## Safety boundary
- No sales, inventory, purchase, production, accounting, customer, supplier, or attachment data is uploaded in v155.
- The existing Room database remains authoritative and unchanged.
- No Room entity, DAO, migration, schema JSON, or backup format was changed.
- The Supabase publishable key is a client key; no service-role/secret key is included.
- Cloud access tokens and refresh tokens are encrypted at rest with Android Keystore.

## Operator test
1. Sign in locally to FUSH ERP as an administrator.
2. Open **Cloud Sync / المزامنة السحابية** from the dashboard or drawer.
3. Enter the Supabase Auth email/password created for FUSH Cloud.
4. Tap **Sign in**.
5. Tap **Test connection and register this phone**.
6. Expected result: company `FUSH ERP`, device name, and cloud device UUID are shown.
7. Install the same update on the second phone and repeat with the same cloud account for the simplest two-phone setup.
8. In Supabase, `fush_sync_devices` should then contain two different device rows.

## Validation performed in this environment
- XML resources parsed successfully.
- English/Arabic string resource names were checked for duplicates.
- Cloud Kotlin layer (`CloudModels`, `CloudSessionStore`, `CloudSyncRepository`) compiled successfully with Kotlin 2.x against local Android/API stubs and the local coroutines library.
- New Compose `CloudSyncScreen` compiled successfully against local Compose API stubs to catch Kotlin/import/type errors in the new screen.
- Added `V155CloudSyncFoundationContractTest` for schema safety, key safety, device registration path, and permission-gated navigation.
- Source diff confirms no Room schema/database file changed.
- Full Gradle unit tests / Android release build were attempted but could not start because the environment cannot resolve `services.gradle.org` to download Gradle 9.4.1.
