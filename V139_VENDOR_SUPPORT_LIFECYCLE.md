# v139 — Vendor Support Lifecycle + Support Session Duration Hardening

Baseline: v138 VendorSupportProvisioning FINAL source only.

## Release identity
- applicationId: `com.fush.erp.recovery`
- versionCode: `139`
- versionName: `0.15.4.90-vendor-support-lifecycle1`
- Room: `44`
- Migration: `43 -> 44`, additive/history-preserving lifecycle migration.

## Vendor identity lifecycle

### Signed rebind after Portable Restore
Portable Backup restores the database history but does not clone the installation secret. On another device/install, the current installation binding differs from the latest Vendor Identity.

The app therefore fails closed and creates a signed `REBIND` challenge. A valid FUSH-signed FSP2 package:
- must match the new installation binding and one-time challenge;
- must identify the historical Support user;
- must reference the immediately previous package fingerprint;
- appends a new Vendor Identity lifecycle row;
- never updates/deletes the historical identity row;
- rotates the Support credential and increments `sessionVersion` so stale logins are invalidated.

### Legacy FUSH_SUPPORT
A `FUSH_SUPPORT` user with no Vendor Identity is treated as legacy/untrusted:
- login fails closed;
- permissions resolve to none;
- a Support Session cannot be activated for it;
- one legacy account can be reviewed through a signed `LEGACY_CLAIM` package;
- multiple legacy accounts fail closed and require explicit Vendor review instead of guessing which identity is official.

### Signed credential rotation
For a current, verified Vendor Identity, ADMIN can create a signed `ROTATE` challenge after recent re-authentication. A valid FSP2 rotation package:
- must supersede the latest package fingerprint;
- increments credential version;
- writes a new append-only Vendor Identity lifecycle record;
- replaces only the Support user's signed credential material;
- increments user `sessionVersion` to invalidate stale sessions.

### Vendor signing-key rotation
`KEY_ROTATE` creates a signed key-supersession challenge. FSK1 must be signed by the *currently trusted* Vendor private key and may install only a new RSA >=3072 public key. Key history is append-only in `vendor_support_keys`; previous public keys remain historical but only the latest key is used for new package verification.

Private Vendor signing keys are external to the Android app/source/backup and must remain FUSH-controlled offline secrets.

## Failed provisioning audit
Rejected provisioning/rebind/legacy/rotation attempts write immutable management Audit events:
- `VENDOR_SUPPORT_PROVISION_FAILED`
- safe reason only: failure code + lifecycle purpose

No signed token, password hash, salt, challenge, package payload, or private-key material is written to the failure Audit reason.

Key rotation failures use `VENDOR_SUPPORT_KEY_ROTATION_FAILED` with a sanitized failure code.

## Support Session duration fix
A company-authorized 1h/6h/24h Support Session is now authoritative for FUSH_SUPPORT maintenance access.

While a Support Session is active:
- generic application idle/absolute session timers cannot truncate the authorized maintenance grant;
- backend Support commands still re-check trusted time and session expiry on every call.

At the exact Support Session expiry:
- Support commands become unavailable immediately;
- the maintenance grant is not extended;
- the app does not immediately log the technician out merely because a generic absolute timer elapsed during the authorized grant;
- a fresh normal idle window starts for the now-locked Support shell.

This fixes the case where a 6h/24h company grant was being cut short by the generic application session timeout.

## Scope retained
Repair Commands remain intentionally limited and fail closed. v139 does not add a general database editor and does not expand repair coverage merely for breadth.

## Local-only limitation
v139 remains Local Support Mode. Rebind secures the restored database copy on the new installation, but without a remote FUSH backend it cannot remotely revoke an old physical device that still owns a separate pre-restore database copy. Cross-device remote revocation/command transport belongs to the future Remote Support Architecture.
