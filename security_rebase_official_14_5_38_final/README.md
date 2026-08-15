# FUSH ERP Users / Permissions — Official 14.5.38 Security Rebase Final

- Target baseline: official Phase 14.5.38 Professional UI source artifact
- Baseline source SHA-256: `d4ff24af274e3aedc14cdf39762e9a0b3bd6ff4f3b563062063e5b79342ec38f`
- Unified patch SHA-256: `629117dc269ee836c741df1c43fa61275b980eb99ac8caf8f0d426ab3feef0b1`
- Compressed patch SHA-256: `26aa15a46705f0dcd702ec7d854d8ad3ab778ccb89fe184d39bf977186cd2717`
- Payload parts: 13
- Application ID remains `com.fush.erp.recovery`
- Specialized-branch Room schema: 28, using `MIGRATION_27_28_SECURITY_BRANCH_ONLY`

This final payload already includes the compile correction discovered by full Android CI. It supersedes the earlier split `security_rebase.patch` + `compile_fix_01.patch` application path.

## Validation already completed
- clean apply against official source: PASS
- exact byte comparison of 47 changed/new paths: PASS
- Android `:app:testDebugUnitTest`: PASS
- Android `:app:assembleRelease`: PASS
- Room schema 28 generation: PASS
- unsigned release APK generation: PASS

The previous CI red status after a successful Android build was caused only by an incorrect relative path while creating the source ZIP. The final CI workflow uses the corrected output path.

## Apply
From a clean official Phase 14.5.38 source checkout/artifact:

`python path/to/apply_patch.py`

Then from the source root:

`git apply --check path/to/security_rebase_final.patch`
`git apply path/to/security_rebase_final.patch`

Central integration must renumber the security migration if another stream already owns schema 28. Reuse `installUsersSecuritySchema(db)`; do not create two competing 27→28 migrations.
