# FUSH ERP Mobile v156 — Release Handoff

v156 is an update over the signed v155 Cloud Sync Foundation and implements the multi-user cloud identity layer required before business-data synchronization.

## User behavior
- The existing owner/admin keeps the v155 Supabase session; after the v156 server SQL is applied, v156 claims and binds it to the local ADMIN username.
- Every accountant, worker, production operator, salesperson, etc. receives a separate cloud account mapped to their existing local FUSH username and role.
- The owner provisions employee cloud accounts from **Users & Permissions → Cloud**.
- A fresh employee phone can choose **Join existing FUSH company** instead of creating a new local administrator. The cloud binding supplies the local username/display name/role and the app creates the first local account on that phone.
- Cloud access/refresh tokens are stored per local user and encrypted with Android Keystore AES/GCM.

## Supabase prerequisite
Run exactly once:
`supabase/V156_MULTI_USER_CLOUD_IDENTITY.sql`

This adds `fush_cloud_user_bindings`, an owner test helper, an owner self-claim RPC for upgrading from v155, and an OWNER-only member provisioning RPC.

## Security boundaries
- Only the existing publishable Supabase key remains in the APK.
- No database password, `service_role`, `sb_secret_...`, or backend secret is present in source/build config.
- Provisioning RPC requires an authenticated organization OWNER/ADMIN on the server.
- Cloud sign-in is rejected if the cloud binding username or role does not match the current local FUSH user.
- Local role `ADMIN` is compatible only with cloud `OWNER`/`ADMIN`; other roles must match exactly.

## Preserved release guarantees
- App ID `com.fush.erp.recovery`.
- versionCode `156`.
- Room `46`, no migration.
- No destructive database fallback.
- Existing v154/v155 ERP business data is untouched.

## Build status in this workspace
Targeted static validation: **23/23 PASS**.
A release APK could not be built locally only because the sandbox cannot resolve/download Gradle 9.4.1 from `services.gradle.org`. Release build/signing must be executed by the build environment that produced v155.
