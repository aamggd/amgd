# FUSH ERP Mobile v105 — Final Source Handoff

This package is the maintained source handoff for the v105 customer-statement release.

## Release identity
- Application ID: com.fush.erp.recovery
- versionCode: 105
- versionName: 0.15.4.56-customer-statement-hotfix1
- Room schema: 38 (unchanged; no database migration in this release)

## Production baseline
The signed production APK was produced as a minimal update over the last known-good v104 APK that already contained the Backup + Arabic runtime fixes.

Baseline APK SHA-256:
d60c07064d0fb182a1b8185f0132fe70b53fae7c5c445b9aabdedf10183198f7

Final signed v105 APK SHA-256:
5fddc7376bbcdf60fdd65b978a5746809b438d70d3462db0db79cce346a3517a

## Source changes represented here
- Professional customer statement/report UI and PDF/Excel/print export integration.
- Customer account statement uses the canonical customer ledger direction: debit - credit.
- versionCode/versionName raised to v105.
- Backup encryption source keeps the Android Keystore key alias and uses provider-generated randomized IV behavior.
- MainActivity keeps localized LocalContext while providing ActivityResultRegistryOwner explicitly.

## Exact shipped binary reference
The folder FINAL_RELEASE_REFERENCE/exact_binary_patch contains the exact AndroidManifest.xml and changed/new DEX files from the signed v105 APK. These are included only as a release reference so the shipped APK can be compared with the maintained source tree.

## Important
- Do not downgrade Room below 38.
- Do not replace this baseline with historical Room27/Room35 sources.
- Signing key and password are intentionally NOT included.
