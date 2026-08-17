# FUSH ERP — Security Rebase on Official Phase 14.5.38 Professional UI

Branch-only security integration package. This work starts from the official Phase 14.5.38 Professional UI source artifact and ports only the users/permissions/security functionality previously developed on the older Phase 14.5.33 lineage.

## Immutable project compatibility
- Application ID remains `com.fush.erp.recovery`.
- Official baseline versionCode/versionName remain unchanged in this specialized branch source; central integration owns final version numbers.
- No destructive migration, database recreation, or uninstall path is used.
- Existing user identity, passwordHash, salt, role, active state, mustChangePassword and createdAt are preserved by migration.
- No signing key or signing password is stored in source, Gradle, documentation, GitHub patches, or CI.

## Security functionality ported
- RBAC with role/permission catalog and service-level enforcement.
- User management, role assignment, password reset/change, last-admin safeguards.
- Failed-login lockout.
- Session versioning, single-current-session behavior, idle/session/absolute timeouts.
- Password policy and password history.
- First-run administrator setup without embedded default credentials.
- Mandatory privileged MFA with TOTP and one-time recovery codes.
- Encrypted MFA secret and salted recovery-code hashes.
- Five-minute fresh reauthentication for critical actions.
- Immutable audit log triggers (UPDATE/DELETE blocked).
- Permission-aware navigation/dashboard/exports/governance/backup restore.

## Room migration strategy
The official baseline is schema 27. For isolated validation on this specialized branch only, security uses:
- `MIGRATION_27_28_SECURITY_BRANCH_ONLY`
- branch-only schema 28

All security SQL is isolated in `installUsersSecuritySchema(db)`. During central integration, where accounting owns schema 27 -> 28, the main integration branch must wrap the same installer as security schema 28 -> 29. This avoids two unrelated migrations competing for the same global version number.

## UI merge strategy
Professional UI is retained. Security behavior was three-way merged into the official Phase 14.5.38 screens; old UI files were not copied over the professional baseline where the UI stream had changed them.
