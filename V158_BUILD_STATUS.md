# FUSH ERP Mobile v158 — Build / Validation Status

## Scope
- Baseline: v157 Cloud Sync Self-Service Access Hotfix.
- applicationId: `com.fush.erp.recovery`.
- versionCode: `158`.
- versionName: `0.15.4.109-master-data-cloud-sync`.
- Room schema remains `46`; no Room migration was added.

## Implemented
- Supabase master-data schema and RLS migration: `cloud/supabase/003_master_data_sync.sql`.
- Sync scopes: currencies, units, warehouses, items, item-unit conversions, customers, suppliers.
- OWNER/ADMIN-only initial bootstrap when cloud master data is empty.
- Cloud-first first baseline on employee phones.
- First-baseline local-only quarantine: unchanged stale local rows do not leak into cloud on the next periodic sync.
- Hash checkpoint conflict detection: both-sides-changed rows are reported, not silently overwritten.
- Manual sync in Cloud Sync screen.
- Automatic foreground sync 5 seconds after login and once per minute while the app remains open.
- Existing v157 aapt2 apostrophe fix retained (`employee\'s account`).

## Validation completed in this environment
- Static contract validation: 35/35 PASS.
- English and Arabic Android strings parse as XML.
- `MasterDataCloudSyncEngine.kt` compiles successfully with isolated Kotlin type stubs, confirming Kotlin syntax/type flow of the new engine independent of Android/Room generated classes.

## Full Android build attempt
Attempted:
`./gradlew testDebugUnitTest assembleRelease lintVitalRelease --no-daemon`

The Gradle wrapper stopped before project configuration because this environment could not resolve `services.gradle.org` while fetching Gradle 9.4.1 (`UnknownHostException`). Therefore no APK is claimed from this environment. The full Android test/build/sign gate must be run in the build environment that already has Gradle 9.4.1 / Android SDK 36 available.

## Required Supabase deployment order
Run `cloud/supabase/003_master_data_sync.sql` once before using v158 master-data sync.
Then perform the first data sync on the OWNER/ADMIN phone so it initializes the empty cloud baseline. Employee phones should sync only after the owner bootstrap succeeds.
