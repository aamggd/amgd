# v139 Validation Report — Vendor Support Lifecycle

## Identity
- applicationId: `com.fush.erp.recovery`
- versionCode: `139`
- versionName: `0.15.4.90-vendor-support-lifecycle1`
- Room schema: `44`
- Migration: `43 -> 44`

## Final-source regression gate
Executed on the final source tree after the last code/test modification:
- `@Test` annotations in `app/src/test`: **456**
- JUnit XML tests executed: **456**
- failures: **0**
- errors: **0**
- skipped: **0**
- `testDebugUnitTest`: **PASS**
- `compileDebugKotlin`: **PASS**
- KSP/Room generation: **PASS**

## Release gate
- `packageRelease`: **PASS**
- `lintVitalRelease`: **PASS** (executed as dependency of final package gate)
- `assembleRelease`: **PASS**
- `zipalign -c`: **PASS**
- APK signature v2: **PASS**
- APK signature v3: **PASS**
- signer certificate SHA-256: `22d5e2a8bd48ddd2339abdc4748648b509e02d046524d6e118fbe05088155586`

## Lifecycle checks
- signed `REBIND` path preserves historical Vendor Identity and appends a superseding identity: **PASS**
- legacy FUSH_SUPPORT login/permission/session activation fail closed until signed claim: **PASS**
- signed credential rotation is append-only and invalidates stale sessions: **PASS**
- signed Vendor public-key supersession chain: **PASS**
- failed provisioning uses sanitized immutable Audit event: **PASS**
- migration 43->44 SQLite smoke including old-row preservation + append-only rebind + immutable UPDATE/DELETE triggers: **PASS**
- provisioning/key-rotation Python tooling syntax compile: **PASS**
- private Vendor key files in source tree: **NONE**

## Support Session duration checks
- active maintenance grant is not truncated by generic session timeout: **PASS**
- allowed durations are exactly 60 / 360 / 1440 minutes: **PASS**
- backend grant is active immediately before selected expiry and inactive at exact expiry: **PASS**
- after grant end, generic idle timer begins from grant-end transition instead of retroactively expiring the shell: **PASS by source contract + unit policy tests**

## Non-goals
- No Remote Support transport/backend in v139.
- No General Repair / direct SQL editor.
- Repair command coverage remains intentionally fail closed.
