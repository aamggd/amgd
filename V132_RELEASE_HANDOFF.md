# FUSH ERP Mobile v132 — Release Handoff

## Release identity
- App ID: `com.fush.erp.recovery`
- Version code: `132`
- Version name: `0.15.4.83-integrated-release-hardening-trusted-time1`
- Room: `39`
- Update lineage: v128 Release Hardening → v129 Cash Refund Actual-Cash Guard → v130 Generic Production Batch Number → v131 Trusted Time → v132 Integrated Release.

## Verified gates
- Unit tests: 402 passed, 0 failed, 0 errors.
- Lint Vital: PASS.
- Release assembly: PASS.
- APK signer: v2/v3 PASS.
- zipalign: PASS.
- Signing certificate SHA-256: `22d5e2a8bd48ddd2339abdc4748648b509e02d046524d6e118fbe05088155586`.
- Final APK SHA-256: `28f32506cef083a3a2c1d09ba6c5d6d5a7d8e225ed5c40c1aba2c7ad7a4c0601`.

## Source portability
The final source package excludes build outputs, Gradle cache, `local.properties`, APKs and signing material. No machine-local Maven repository path remains in `settings.gradle.kts`.

## Exclusions / policy
- The historical production-data difference 3,669.10 is intentionally not changed.
- Device/emulator instrumented execution is not a v132 release gate by explicit user instruction.
- This handoff is documentation-only for GitHub. **Do not merge to master/Central unless explicitly requested by the user.**
