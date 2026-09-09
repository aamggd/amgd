# FUSH Vendor Support Lifecycle Tooling (v139)

These tools run **outside the Android application** on a FUSH-controlled offline machine. Private signing keys must never be copied into the APK, client device, source ZIP, backup, or GitHub.

## Identity lifecycle (FSP2)
The app creates a signed request token whose purpose is automatically one of:
- `PROVISION` — fresh install.
- `LEGACY_CLAIM` — a legacy `FUSH_SUPPORT` exists without a Vendor Identity; login remains fail-closed until signed claim.
- `REBIND` — a portable backup was restored on another device/installation. Historical Vendor Identity rows remain immutable and a new superseding row is appended.
- `ROTATE` — signed credential rotation for the current Vendor Identity.

Create the returned package with:
`create_provisioning_package.py --request '<request>' --pkcs12 /secure/current-vendor-key.p12`

The package contains a PBKDF2 password hash + salt, not the plaintext password. It is installation-bound, challenge-bound, short-lived and one-time.

## Vendor signing-key supersession (FSK1)
The app creates a `KEY_ROTATE` challenge. Generate a new RSA >=3072 key pair offline, then have the **current** trusted Vendor key sign the supersession package:
`create_key_rotation_package.py --request '<request>' --current-pkcs12 /secure/current.p12 --new-public-der /secure/new-public.der --new-key-id fush-support-v2`

The client stores only the new public key as an immutable trust-history row. The previous public key remains in history but is no longer the current verifier.
