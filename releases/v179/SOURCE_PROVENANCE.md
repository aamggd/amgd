# FUSH ERP Mobile v179 — Source Provenance

This branch is an archival checkpoint for v179 built strictly on the official v178 source.

## Baseline
- v178 source archive: `FushERP-Mobile-v178-ProductionBackfillAccountant-FINAL-Source.zip`
- SHA-256: `438a8cae1b71f47e7d16916542d239d355cde101a2669ae99b4f1bce6e1d4f19`

## v179 source delta stored in GitHub
- `source_delta/v179-code-only.patch.zst.b64`
  - Base64 file SHA-256: `e661b43eae8b998644739e1b44bb8e056dbb3e510c1bf38659b95531652ade26`
  - Decoded `.zst` SHA-256: `3ef5d70c0ec2a76ad20f26a163957b4157391230d1b115d795cd083789a08a23`
- `source_delta/v179-room-schemas-47-48.tar.zst.b64`
  - Base64 file SHA-256: `dea7fa1c2ead5d70ed93242a5d9bbe761eaa74b3d2f9a583b52a31fd56af7a40`
  - Decoded `.tar.zst` SHA-256: `36ff70f7fd0d6412fffb0e58686e7ea62c08b51c423eb653cd27e36f2e037c46`
- `source_delta/RESTORE_V179_DELTA.sh` verifies the baseline and both archived source payloads before applying them.

## Final local release artifacts
- Clean source ZIP SHA-256: `3f465ba74174ddca99f8cb08246ca1c17793d43d6a823191263b9966404d650e`
- Signed APK SHA-256: `0493692b44f99a543691aa18045a9060fd9f60d5bb2fb3b3e91d8582867a4b6a`
- Supabase SQL SHA-256: `8f7260a36a96258d5da4549964030468a44ea34dbd74d1f6889cc0a516c559a4`

## Identity and safety
- Application ID: `com.fush.erp.recovery`
- versionCode: `179`
- versionName: `0.15.4.130-sales-additional-charges-shipment-costs`
- Room schema: `48`
- Upgrade path: `46 -> 47 -> 48`
- No destructive migration.
- Permanent signing material is intentionally NOT committed to GitHub.

The signed APK itself is kept as a release artifact outside source control; its immutable SHA-256 is recorded here and in `FUSH_ERP_Mobile_v179-FINAL-SHA256.txt`.
