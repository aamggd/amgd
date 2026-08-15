# FUSH ERP Mobile — Phase 14.5.34 Users & Permissions

Target branch: `fush/users-permissions`
Base source: Phase 14.5.33 Expense Dimensions
Resulting app version: `0.15.4.34-users-permissions` (`versionCode 73`)
Room schema: `28` with migration `27 -> 28`.

## What this phase adds

- Full local RBAC: users, roles, permission catalog, role-permission assignments and custom roles.
- Built-in roles: ADMIN, ACCOUNTANT, CASHIER, SALES, PURCHASING, INVENTORY, PRODUCTION, HR, AUDITOR, VIEWER.
- User administration UI: create, role assignment, enable/disable, reset password, forced password change.
- PBKDF2 password hashing retained with a 15-character strong password policy and last-10 password reuse protection.
- Five failed logins -> 15-minute lock; repeated lockout -> 60 minutes.
- Session invalidation after role/password/account-security changes; idle/absolute expiry checks.
- Navigation and dashboard data are filtered by permission.
- Service-layer authorization protects sensitive sales, purchase, accounting, inventory, production, quality, HR, maintenance, governance, planning, geography, risk, reports and backup operations.
- `ACCESS_DENIED` attempts are written to the governance audit log.
- Last active ADMIN cannot be disabled or demoted.

## Apply to a clean source checkout

Copy this folder into or beside the clean Phase 14.5.33 source checkout, then from the source root run:

```bash
python path/to/phase14_5_34_users_permissions/apply_patch.py
```

The script reconstructs the compressed patch, verifies SHA-256, runs `git apply --check`, then applies it.

Raw patch SHA-256:
`94bc1ae1ef5fe91d0537b94bcaa8cca5b38b94e69a1cd719419e608469f5ce93`

## Validation performed here

- Kotlin security-policy/password-hasher core compilation: PASS.
- Executable password/lockout/PBKDF2 smoke test: PASS.
- Permission catalog uniqueness/default-role validation: PASS (46 permissions, 10 built-in roles).
- Changed call-site review: PASS for the modified service signatures.
- `git diff --check`: PASS.
- `git apply --check` against a clean Phase 14.5.33 source tree: PASS.

## Required before merge/release

The uploaded source package did not contain the Gradle wrapper (`gradlew`/wrapper JAR), and this execution environment has no Android SDK. Therefore a full Android build has **not** been claimed as passed here.

Run in the normal Android/CI environment before merging to `fush/main`:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Then smoke-test login/password change, all built-in roles, a custom role, user disable, lockout, denied operations/audit events, and a real database upgrade from schema 27 to 28.
