# v138 — Vendor Support Provisioning + Synchronized Release Gate

- Baseline: v137 Safe Support Repair FINAL only.
- Fresh installs no longer depend on a pre-existing FUSH_SUPPORT user.
- ADMIN creates an installation-bound one-time challenge after recent re-authentication.
- FUSH returns a signed `FSP1` provisioning package.
- APK contains only the RSA public key; the Vendor private key is never bundled or uploaded to GitHub.
- The signed package is bound to the installation binding + current challenge, expires within 24 hours, and is consumed once.
- The package creates the fixed `fush.support` account with role `FUSH_SUPPORT`; local ADMIN still cannot create/assign/reset/manage that identity.
- FUSH_SUPPORT login and permissions fail closed when the immutable vendor identity record does not match this installation.
- Room 42 -> 43 adds only the immutable `vendor_support_identities` table.
- Remote Support remains outside scope; this provisions a trusted local Vendor Identity only.
