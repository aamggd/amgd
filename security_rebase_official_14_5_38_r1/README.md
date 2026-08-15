# FUSH ERP — Security Rebase on Official Phase 14.5.38 Professional UI (R1)

Branch: `fush/users-permissions`

This package supersedes the older security patch lineage as the merge handoff for this specialized branch. It is generated directly against the official Phase 14.5.38 Professional UI source artifact whose source ZIP SHA-256 is:

`d4ff24af274e3aedc14cdf39762e9a0b3bd6ff4f3b563062063e5b79342ec38f`

Compatibility rules:
- Application ID remains `com.fush.erp.recovery`.
- Source keeps baseline versionCode/versionName; global release numbering belongs to central integration.
- No destructive migration.
- Existing user passwordHash/salt/role are preserved.
- Security schema SQL is isolated in `installUsersSecuritySchema(db)`.
- Specialized branch wraps it as `27 -> 28 SECURITY_BRANCH_ONLY`; central integration must wrap the same installer as `28 -> 29` after accounting owns `27 -> 28`.
- Professional UI is retained; security logic was three-way merged into current UI files.
- No permanent signing key or signing password is stored in this package or CI.

Security functionality includes RBAC, service-level authorization, user/role management, password policy/history, failed-login lockout, sessionVersion/session expiry, last-admin safeguards, privileged TOTP MFA, recovery codes, encrypted MFA secret, critical-action reauthentication, and immutable audit log triggers.

Patch SHA-256:
`d7155b82b0ea7fe70df100f50ee22f964b7531e4577f0058bde1b97932d83bf5`

The patch payload is stored as verified `payload_*.b64` chunks. `apply_patch.py` reconstructs the patch and validates both compressed and uncompressed SHA-256 values. From a clean official Phase 14.5.38 source root, run:

```bash
python path/to/security_rebase_official_14_5_38_r1/apply_patch.py
git apply --check path/to/security_rebase_official_14_5_38_r1/security_rebase.patch
git apply path/to/security_rebase_official_14_5_38_r1/security_rebase.patch
```
