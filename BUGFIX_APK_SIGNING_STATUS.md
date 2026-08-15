# FUSH ERP — APK Installation Bugfix Status

Date: 2026-08-15
Branch: `fush/bugfixes-errors`
Affected target: Phase 14.5.35 UI Sales & Customers
Package: `com.fush.erp.recovery`
Room schema: `27`

## Bug 1 — unsigned APK

The original Phase 14.5.35 UI integration artifact was aligned but unsigned. This was fixed by recovering the permanent Recovery signing key and producing an APK signed with:

- Certificate DN: `CN=Fush ERP Recovery, OU=Fush, O=Fush, L=Taiz, ST=Taiz, C=YE`
- Certificate SHA-256: `22d5e2a8bd48ddd2339abdc4748648b509e02d046524d6e118fbe05088155586`
- RSA key size: 4096
- APK Signature Scheme v2: PASS
- APK Signature Scheme v3: PASS

The certificate is identical to the known-good Phase 14.5.33 Recovery APK.

## Bug 2 — installer still reports invalid package

After correcting the signer, the device still displayed `App not installed as package appears to be invalid.` Android PackageInstaller can use this generic invalid-package message for `INSTALL_FAILED_VERSION_DOWNGRADE`, so the next hotfix removes the version-code ambiguity.

A dedicated install-hotfix workflow was added:

`.github/workflows/build-phase14-5-35-install-hotfix.yml`

Workflow run: `31859996518`

Result: PASS

- Restore verified Phase 14.5.33 source: PASS
- Apply Phase 14.5.34 UI package: PASS
- Apply Phase 14.5.35 Sales & Customers UI package: PASS
- Unit tests: PASS
- Release build: PASS
- Room schema 27 unchanged: PASS
- 4 KB / 16 KB APK zip alignment: PASS
- Output versionCode: `100`
- Output versionName: `0.15.4.35-install-hotfix-1`

The APK was then signed outside the public repository with the permanent Recovery private key and verified again with Android `apksigner`.

## Permanent release control

Future installable builds must:

1. Keep package `com.fush.erp.recovery` unless an intentional migration is approved.
2. Use a monotonically increasing versionCode greater than every previously installed build.
3. Use the permanent Recovery signing certificate or a formally valid certificate lineage.
4. Run unit tests and release build.
5. Verify Room schema/migrations before packaging.
6. Align uncompressed native libraries for 16 KB page-size devices before signing.
7. Verify the final APK signature and certificate fingerprint after signing.
8. Never publish `*-unsigned.apk` as a user-installable release.
