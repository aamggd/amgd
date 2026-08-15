# FUSH ERP Mobile — Central Integration Phase 14.5.52

## Trusted base
- Base release: validated Phase 14.5.48 final integrated source.
- Base workflow run: `31870087495`.
- Base applicationId: `com.fush.erp.recovery`.
- Base versionCode: `87`.
- Base Room schema: `33`.

## Integrated updates
- Accounting Phase 14.5.40 — Fixed Asset Accounting.
- Accounting Phase 14.5.41 — Multi-Invoice Settlement.
- Reports/Printing Phases 14.5.47–14.5.51, including aging, expense analysis, treasury reports, period comparison and professional inventory analytics.
- UI Phases 14.5.48–14.5.50, including lazy-key crash fixes, navigation/feedback polish and Arabic/English localization foundation.

## Central conflict policy
The integration preserves the validated security stream from Phase 14.5.48. UI changes are ported around the secure authentication/session/RBAC flow rather than replacing it. In particular:
- Login remains through `SecurityService.authenticate`.
- MFA setup/required flows remain active.
- Initial-admin and forced-password-change flows remain active.
- Home navigation remains permission filtered and session timeout enforcement remains active.
- New customer/supplier multi-invoice settlements retain central permission checks.

## Database/version ownership
- applicationId: `com.fush.erp.recovery`
- versionCode: `91`
- versionName: `0.15.4.52-integrated-updates`
- Room schema: `34`
- Existing central security migration remains `32 -> 33`.
- Fixed-assets migration is centrally renumbered to `33 -> 34`.
- No destructive migration or destructive fallback is permitted.

## Deterministic payload
The nine files under `payload/` concatenate in lexical order to a Base64-encoded gzip patch generated from the exact validated 14.5.48 source package.

- Base64 payload SHA256: `8e1ba7f68263a7d107c50b794f5cd14ff7810e68cc14155ed39c418ee3535161`
- Gzip payload SHA256: `c10d0f04d854684fc258ffe6683d6a70352257b160e6878fe2a691cef1a6edf0`
- Decoded patch SHA256: `4f2cded03d35668227617fe7087689cd7b4e613e56a0ce2b005ee98d3f337463`
- Expected integrated source tree before build products: `769c4ecd7f7a31ae2c009c05c0258d55574f9a0c`

The GitHub Actions validation must reproduce this exact tree before unit tests and release build are allowed to run.
