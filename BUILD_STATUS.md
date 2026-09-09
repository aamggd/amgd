# FUSH ERP Mobile v156 — Build Status

## Identity
- Application ID: `com.fush.erp.recovery`
- versionCode: `156`
- versionName: `0.15.4.107-multi-user-cloud-identity`
- Room schema: `46` (unchanged; no Room migration required)

## v156 scope
- Per-local-user encrypted Supabase sessions instead of one device-wide cloud identity.
- Automatic migration of the existing v155 owner session only to a local `ADMIN`.
- Server-verified binding between local FUSH username, cloud user, and role.
- OWNER-only cloud provisioning flow from Users & Permissions without embedding `service_role` or secret keys.
- Fresh-device onboarding for accountants, production workers, sales users, etc. via **Join existing FUSH company**.
- Existing business data remains local; automatic ERP data synchronization is intentionally deferred to the next data-sync phase.

## Database safety
- Room remains schema 46.
- No `fallbackToDestructiveMigration`.
- No Room entity/table changes were introduced by v156.
- Supabase migration is additive and contains no `DROP TABLE`, `TRUNCATE`, or business-data deletion.

## Static validation
- 23/23 targeted v156 checks PASS.
- Arabic/English string XML parses successfully and has no duplicate string keys.
- All new cloud `R.string` references exist in both languages.
- App ID/version/Room safety checks PASS.
- Supabase RLS / anonymous-revoke / SECURITY DEFINER provisioning checks PASS.

## Local build attempt
`./gradlew --version` attempted to resolve the pinned Gradle 9.4.1 wrapper but the current sandbox cannot resolve `services.gradle.org` and fails with `java.net.UnknownHostException`. No APK is claimed from this environment.

The release builder must run the normal gates: unit tests, `assembleRelease`, `lintVitalRelease`, zipalign, permanent-signing v2/v3, and certificate verification.
