# FUSH ERP — Privileged MFA 14.5.37

Branch: `fush/users-permissions`

This stage adds mandatory multi-factor authentication to privileged FUSH ERP accounts on top of Phase 14.5.36.

## Security behavior
- MFA is mandatory for ADMIN and any role that can manage users, manage roles, or restore backups.
- TOTP uses the standard 30-second / 6-digit HMAC-SHA1 format compatible with common authenticator apps.
- The TOTP secret is never persisted as plaintext. It is AES-GCM encrypted with a key derived from the user's password using PBKDF2-HMAC-SHA256.
- Changing the password re-encrypts the MFA secret under the new password.
- An administrator password reset clears the target user's MFA and forces privileged accounts to enroll again.
- Ten one-time recovery codes are created during enrollment. Only salted hashes are stored.
- A new login session does not receive privileged permissions until MFA is verified for the current `sessionVersion`.
- Changing a role's permission set invalidates every active session assigned to that role.
- Administrators can reset MFA for another user, but cannot disable MFA for their own active session.
- MFA failures share the existing account lockout policy and are written to the immutable audit log.

## Database
- Schema: 28 -> 29
- Adds MFA fields to `users`.
- Adds `user_mfa_recovery_codes` with foreign key cleanup and indexes.

## Validation performed
- TOTP core compiled and executed with the RFC 6238 SHA-1 test secret: expected six-digit result `287082`: PASS.
- AES-GCM MFA secret round-trip, randomized ciphertext, and wrong-password rejection: PASS.
- Recovery-code uniqueness, normalization, salted hashing, and verification: PASS.
- `SecurityService.kt` Kotlin compilation with Room/database stubs: PASS.
- `AuthorizationGuards.kt` Kotlin compilation with stubs: PASS.
- SQLite migration 28 -> 29 preserving an existing user: PASS.
- `git diff --check`: PASS.
- `git apply --check` against clean Phase 14.5.36 source: PASS.
- Exact post-apply byte comparison: PASS for all 15 changed files.

## Patch integrity
Uncompressed patch SHA-256:
`7154ecbac11935d34e1ec23f478948ebb10253aa56d47b0c8626dd0a684282d0`

Compressed/base64 bundle SHA-256:
`5b59c820199e273144067d199e1e23be27f134115bfc7778ff7f0d95fb8f3720`

## Apply
From a clean Phase 14.5.36 users/security source checkout:

```bash
python phase14_5_37_privileged_mfa/apply_patch.py
```

Full Android `assembleDebug` is still pending because the active execution environment does not contain the Android SDK/Gradle distribution required by this project. Do not treat the stage as release-ready until the central integration/build environment runs the Android unit tests and APK build.
