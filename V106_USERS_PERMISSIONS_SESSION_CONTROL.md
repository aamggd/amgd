# FUSH ERP Mobile v106 — Users, Permissions & Session Control

## Scope implemented

- Exposed the existing Users / Roles / Permissions feature in the actual application navigation.
- Added **Users & Permissions** to the navigation drawer and dashboard modules.
- Wired the route to `UsersPermissionsScreen` so authorized users can manage:
  - users and activation status,
  - role assignment,
  - password reset,
  - MFA reset,
  - custom roles,
  - role permissions,
  - session policy.
- Added a top-bar **Session duration** button for users with `ROLES_MANAGE` (ADMIN has it automatically).
- Added quick session presets: 15, 30, 60, 120, 240, and 480 minutes.
- Added a custom minute value, valid from 1 to 43,200 minutes.
- Removed the legacy hard-coded effective caps of 3 minutes for ADMIN and 5 minutes for normal users.
- New default session duration is 60 minutes.
- Added a SharedPreferences settings migration that replaces only the old untouched default `5 / 480` values with the new 60-minute default, while preserving custom values.
- Session setting changes continue to be recorded through the existing security audit service.

## Database compatibility

- Room schema remains **38**.
- No Room entities, tables, columns, indexes, or migrations were changed.
- Existing user / role / permission tables and services are reused; this change exposes and completes the UI wiring rather than creating a parallel security model.

## Version

- `versionCode`: 106
- `versionName`: `0.15.4.57-users-permissions-session-control`

## Validation performed in this workspace

- Original uploaded source SHA-256 matched the provided checksum.
- `SecurityPolicy.kt` compiled with the available Kotlin compiler.
- Runtime-like policy assertions passed for 60-minute defaults and configurable ADMIN duration.
- Static wiring assertions passed for the Users & Permissions route, dashboard module, drawer item, session dialog, and permission guard.
- Arabic and English `strings.xml` files were XML-parsed successfully.

## Build limitation of this workspace

The uploaded source does not contain a Gradle wrapper, and this workspace does not have an Android SDK / Gradle installation or the app signing key. Therefore a newly compiled and signed APK is not included in this handoff. The modified source is ready to open/build in Android Studio with the project build environment and signing key.
