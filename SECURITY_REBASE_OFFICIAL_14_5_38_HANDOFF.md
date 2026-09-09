# Users / Permissions Security Rebase — Integration Handoff

## Baseline
- Official source: Phase 14.5.38 Professional UI
- Baseline source SHA-256: `d4ff24af274e3aedc14cdf39762e9a0b3bd6ff4f3b563062063e5b79342ec38f`
- Application ID: `com.fush.erp.recovery`
- Baseline versionCode/versionName are intentionally retained in this specialized branch. Central integration owns release numbering.

## Delivered functionality
- RBAC permission catalog and role-permission mapping.
- Built-in and custom roles, user role assignment, service-layer authorization guards.
- User activation/deactivation, password reset/change, password history and strong password policy.
- Protection against disabling/demoting the last active administrator.
- Failed-login lockouts.
- Session versioning, single-current-session enforcement and idle/session/absolute expiry.
- First-run administrator creation with no embedded default administrator password.
- Mandatory MFA for privileged accounts using TOTP plus single-use recovery codes.
- Encrypted MFA secret and salted recovery-code hashes.
- Five-minute fresh reauthentication for critical security/restore actions.
- Immutable audit-event UPDATE/DELETE SQLite triggers.
- Permission-aware UI navigation and service actions while retaining the official Professional UI.

## Database / migration
The official baseline is Room schema 27. This specialized branch validates security independently as schema 28 using `MIGRATION_27_28_SECURITY_BRANCH_ONLY`.

The reusable security SQL is isolated in `installUsersSecuritySchema(db)`. **Do not merge two unrelated 27→28 migrations.** If central integration already uses schema 28 for accounting or another stream, allocate the next global schema number and call the same installer from that next migration (for example security 28→29), then generate and commit the corresponding Room schema JSON.

No destructive migration, database recreation or hard-delete migration is introduced.

## Existing-data impact
The security migration preserves the existing user identity and credential fields (`username`, `passwordHash`, `salt`, role, active state, `mustChangePassword`, `createdAt`) and adds security/session/MFA metadata plus new security mapping/history tables.

## Accounting / inventory impact
No accounting amounts, journal posting formulae, inventory quantities, costing logic, stock valuation logic or document totals are changed by this stream. Accounting/inventory services receive authorization checks only. Unauthorized callers are rejected before protected operations execute.

## UI integration hotspots
Central integration should resolve overlaps deliberately in these files instead of taking either branch wholesale:
- `FushErpApp.kt`
- `HomeShell.kt`
- `LoginScreen.kt`
- `BackupRestoreScreen.kt`
- Professional module screens that gained permission guards

The current specialized source already three-way merges security behavior with the official Phase 14.5.38 Professional UI.

## Validation
- patch applies cleanly to the official Phase 14.5.38 source artifact
- 46+ changed/new paths are byte-compared after clean patch application (exact count is recorded by the final package)
- Android unit tests PASS
- Android `assembleRelease` PASS
- Room schema 28 generation PASS in isolated branch CI
- unsigned release APK generated

## Release gate
The specialized branch must not place the permanent signing key/password in GitHub. Central integration/release should perform final migration renumbering if needed, rebuild tests/release, align/sign with the permanent project certificate, verify the certificate fingerprint, and perform an in-place upgrade test against a real backup of the previous signed application.
