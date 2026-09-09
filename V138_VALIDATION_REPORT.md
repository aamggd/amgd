# v138 Validation Report — Vendor Support Provisioning

## Identity
- applicationId: `com.fush.erp.recovery`
- versionCode: `138`
- versionName: `0.15.4.89-vendor-support-provisioning1`
- Room schema: `43`
- Migration: `42 -> 43`, additive/non-destructive

## Final-source regression evidence
The final frozen application source was tested after the last code/schema change.

- `@Test` annotations in `app/src/test`: **443**
- JUnit tests executed: **443**
- Passed: **443**
- Failures: **0**
- Errors: **0**
- Skipped: **0**

## Release gates
- KSP / Room schema generation: PASS
- Room schema `43.json`: present
- Migration 42 -> 43 smoke: PASS
- `assembleRelease`: PASS
- `lintVitalRelease`: PASS
- `zipalign`: PASS
- APK signing verification: v2 PASS / v3 PASS
- signer SHA-256: `22d5e2a8bd48ddd2339abdc4748648b509e02d046524d6e118fbe05088155586`

## Vendor provisioning
- Fresh install provisioning challenge: implemented
- Recent re-authentication before provisioning operations: enforced
- Signed `FSP1` package verification: implemented
- Installation binding + one-time challenge binding: implemented
- Local ADMIN cannot create/assign/reset/manage `FUSH_SUPPORT`: preserved
- Vendor private signing key is not included in this source package or APK
- Remote Support transport/backend: intentionally outside v138 scope
