# FUSH ERP — Critical Action Re-authentication 14.5.38

Branch: `fush/users-permissions`

This stage adds fresh re-authentication for security-critical actions on top of Phase 14.5.37.

## Security behavior
- Fresh authentication window: 5 minutes.
- A just-completed password + MFA login counts as fresh authentication.
- Freshness is stored only in app memory and is bound to the current `sessionVersion`; it is never persisted in SQLite and disappears on process restart.
- Privileged re-authentication requires the current password plus MFA TOTP or a one-time recovery code.
- Five bad re-authentication attempts reuse the existing account lockout policy. Lockout invalidates the active session.
- `REAUTH_SUCCESS`, `REAUTH_FAILED`, and `REAUTH_REQUIRED` are written to the immutable audit log.

## Protected actions
- Create user.
- Enable/disable user.
- Change user role.
- Reset another user's password.
- Reset another user's MFA.
- Create/update custom role.
- Change role permissions.
- Stage database restore.

Normal backup creation is intentionally not included because it does not replace current data.

## UI
Users/permissions and database-restore flows automatically open a re-authentication dialog when the five-minute window is missing or expired. After successful verification the pending action resumes.

## Version
- `versionCode 77`
- `versionName 0.15.4.38-critical-reauth`
- Database schema remains 29; no migration is required.

## Validation
- `git diff --check`: PASS.
- Security core Kotlin compilation with database/Room stubs: PASS.
- Executable re-authentication behavior test: PASS.
- Fresh login counts as recent authentication: PASS.
- 5-minute expiry boundary: PASS.
- `sessionVersion` invalidation: PASS.
- Five bad re-auth attempts lock and invalidate session: PASS.
- UI parser scan: no Kotlin syntax diagnostics; Android/Compose dependency resolution is unavailable in the active environment.
- `git apply --check` against clean Phase 14.5.37 source: PASS.
- Exact post-apply comparison: PASS for all 9 changed files.

## Integrity
Uncompressed patch SHA-256:
`39a66f38d18604c4feccfbbdefd691f93a85545d232fa4c90c5c55079e7d27b8`

Compressed patch SHA-256:
`bc7fb763c1c4ef46d2d99fcf9fb4e3e4fae27a37d6c9437c7ba6f1045cbe7d34`

Combined base64 SHA-256:
`b60ce70778f98c03b5b998e8421262abe14d49b0ed8c2ba56e329b6c0169286a`

Full Android unit tests and `assembleDebug` remain pending until an Android SDK/Gradle build environment is available.
