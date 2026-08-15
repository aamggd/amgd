# FUSH ERP — APK Signing Bugfix Status

Date: 2026-08-15
Branch: `fush/bugfixes-errors`
Affected target: Phase 14.5.35 UI Sales & Customers
Package: `com.fush.erp.recovery`
Version code: `74`
Room schema: `27` (unchanged by the UI package)

## Confirmed root cause

The Phase 14.5.35 UI Integration workflow produced and uploaded an aligned **unsigned** APK. Android cannot accept that APK as an installable update.

## Verified installed/update identity

The known-good Phase 14.5.33 Recovery APK verifies with Android `apksigner` and uses:

- Certificate DN: `CN=Fush ERP Recovery, OU=Fush, O=Fush, L=Taiz, ST=Taiz, C=YE`
- Certificate SHA-256: `22d5e2a8bd48ddd2339abdc4748648b509e02d046524d6e118fbe05088155586`
- RSA key size: 4096
- APK Signature Scheme v2: true
- APK Signature Scheme v3: true

Every future in-place update for package `com.fush.erp.recovery` must be signed by the compatible Recovery signing identity (or a formally valid signing-certificate lineage).

## Build validation already completed

Workflow run `31858121024` successfully completed all steps before signing:

- restore verified Phase 14.5.33 source: PASS
- apply Phase 14.5.34 UI package: PASS
- apply Phase 14.5.35 Sales & Customers UI package: PASS
- Compose compatibility correction: PASS
- unit tests: PASS
- release build: PASS
- Room schema 27 unchanged: PASS
- zipalign: PASS

The workflow then intentionally failed at the signing gate because no Recovery private signing key was configured. It refused to publish another unsigned APK.

## Signing workflow hardening

`.github/workflows/build-phase14-5-35-bugfix-signed.yml` now:

1. Builds the release.
2. Aligns the APK before signing.
3. Requires the private Recovery keystore through GitHub Actions secrets.
4. Runs `apksigner verify --verbose --print-certs`.
5. Compares the resulting certificate SHA-256 to the fixed Recovery fingerprint above.
6. Deletes/rejects the output if the signer is wrong.
7. Uploads only a verified signed APK.

## Current blocker

The private key corresponding to the Recovery certificate is not present in the repository and is not available to the current GitHub Actions workflow. It must **not** be committed to this public repository.

The older `FushERP-Update-Signing.p12` identity is a different certificate (`8c249d...`) and must not be substituted blindly for the current Recovery signer (`22d5e2...`).

## Required input to finish

Recover/provide the private keystore that corresponds to the Recovery certificate SHA-256:

`22d5e2a8bd48ddd2339abdc4748648b509e02d046524d6e118fbe05088155586`

Once available, sign Phase 14.5.35 and re-run signature verification before device acceptance testing.
