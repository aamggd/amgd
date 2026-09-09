# Security Rebase Validation

Official baseline source SHA-256: `d4ff24af274e3aedc14cdf39762e9a0b3bd6ff4f3b563062063e5b79342ec38f`

## Local / patch validation
- Application ID remains `com.fush.erp.recovery`: PASS
- official `versionCode = 77` and `versionName = 0.15.4.38-ui-inventory-master-data` unchanged: PASS
- destructive migration scan: PASS
- merge-conflict marker scan: PASS
- signing credential / removed legacy default credential scan: PASS
- SQLite security migration against a simulated existing schema-27 user: PASS
- existing username/passwordHash/salt/role preservation: PASS
- RBAC/MFA tables creation: PASS
- Password/Sessions/TOTP/AES-GCM/recovery-code core smoke: PASS
- SecurityService + AuthorizationGuards Kotlin compilation with Room/database stubs: PASS
- unified patch `git diff --check`: PASS
- unified patch `git apply --check` against the official source artifact: PASS
- all changed paths reproduce byte-for-byte after patch application: PASS

## Full Android validation
GitHub Actions run `31863881767`, using the official Phase 14.5.38 source artifact, applied the security patch plus the compile correction and completed:
- `:app:testDebugUnitTest`: PASS — `BUILD SUCCESSFUL`
- `:app:assembleRelease`: PASS — `BUILD SUCCESSFUL`
- generated Room schema `28.json` exists and reports version 28: PASS
- unsigned release APK generated: PASS

That run was marked failed only after the successful Android build because its CI source-ZIP packaging command used an incorrect relative output path. This is a workflow packaging defect, not an Android source/build failure. The final unified validation workflow corrects that path and is the release handoff gate.

## Signing
Signing is intentionally not performed by this specialized branch CI. The permanent signing key and its password are not uploaded to GitHub or embedded in source. Final signing remains a central release-gate responsibility.
