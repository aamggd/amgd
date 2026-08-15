# FUSH ERP Mobile — Final UI + Security Integration 14.5.53

Status: **VALIDATED / BUILD PASSED / LOCALLY SIGNED**

- GitHub Actions run: `31907038178`
- Workflow commit: `58dbba04ecd6cd1aa10d60059c1c1f950d101476`
- Artifact ID: `9252710822`
- Artifact name: `FushERP-Phase14.5.53-UI-Security-Integrated-Build`
- Artifact digest: `sha256:d27b1946597139d2354fe8b8fa52d11e18ed1ad91323a9a246bba04a4e193283`
- Base: validated central Phase 14.5.52
- UI pinned SHA: `657f8db9508551dde3d7143c34ee38f3f48aab08`
- Users/permissions pinned SHA: `0ed4877c17d14c6ede05ed3c288cd5d55ca2a7f3`
- App ID: `com.fush.erp.recovery`
- versionCode: `92`
- versionName: `0.15.4.53-ui-security-updates`
- Room schema: `34` (unchanged)
- Unit Tests: PASS
- Release Build: PASS
- package/version/schema validation: PASS
- destructive migration fallback check: PASS
- zipalign: PASS

Integrated scope only:
- UI: theme toggle, Arabic/English core localization, language/theme drawer preferences.
- Users/permissions/security: configurable automatic session logout/timeouts and audit trail for policy changes.

Compatibility preservation:
- fixed assets
- periods/reconciliation
- original-currency treasury balance
- cash count
- bank reconciliation
- existing reporting/export permissions

No newer accounting, reports/printing, bugfix, or other specialized branch updates were merged.

Local signing record (no signing secret stored):
- Signed APK SHA256: `86eec9385447a2a749ec1b1547fd2f825f537af0c474fce5ed65eaede77aa992`
- Certificate SHA256: `22:D5:E2:A8:BD:48:DD:D2:33:9A:BD:C4:74:86:48:B5:09:E0:2D:04:65:24:D6:E1:18:FB:E0:50:88:15:55:86`
- RSA 4096
- APK Signature Scheme v2: true
- APK Signature Scheme v3: true
- Number of signers: 1

Install this release over the existing FUSH app without uninstalling. Room schema remains 34 and no destructive migration is used.
