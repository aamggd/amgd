# FUSH ERP Mobile — Final Integrated Updates 14.5.52

Status: **VALIDATED / BUILD PASSED / LOCALLY SIGNED**

- GitHub Actions run: `31873356076`
- Validated workflow commit: `4d02eecdc22ce1ee67652532683c64cb79c51396`
- Artifact ID: `9244126215`
- Artifact digest: `sha256:cf0326d89b4d5a881300a9901a48b1d4754c0a689e44794c3cd04bd4dd4e2af7`
- App ID: `com.fush.erp.recovery`
- versionCode: `91`
- versionName: `0.15.4.52-integrated-updates`
- Room schema: `34`
- Baseline source tree: `0eb08bebe459a691255cd8e521bae07f7b618632`
- Integrated tree before audited compatibility fix: `769c4ecd7f7a31ae2c009c05c0258d55574f9a0c`
- Validated final source tree: `a350a09957e1591dbecb270533d25314aa422a27`
- Integration patch SHA256: `4f2cded03d35668227617fe7087689cd7b4e613e56a0ce2b005ee98d3f337463`
- Compatibility fix SHA256: `8fef76899a8f53619ab68ac6ddb94e3854395d098fd433fc4a6ae6a737d89445`
- Unit Tests: PASS
- Release Build: PASS
- package/version/schema validation: PASS
- destructive migration fallback check: PASS
- zipalign: PASS

Integrated updates:
- Accounting 14.5.40–14.5.41
- Reports/Printing 14.5.47–14.5.51
- Professional UI 14.5.48–14.5.50
- Existing users/permissions security retained

Database upgrade:
- existing Security migration `32 -> 33` remains registered
- central Fixed Assets migration `33 -> 34` is registered
- no destructive migration fallback

Local signing record (no signing secret stored):
- Signed APK SHA256: `773091ece7c48008baa731e1537e3b36116eacd50244fb7926ffbfbfc910ecea`
- Source ZIP SHA256: `37cb7580cbfb6bba3f4bd2a9ded23c4099c8d4e0e8542cf51a96ce97afa8e8f0`
- Certificate SHA256: `22:D5:E2:A8:BD:48:DD:D2:33:9A:BD:C4:74:86:48:B5:09:E0:2D:04:65:24:D6:E1:18:FB:E0:50:88:15:55:86`
- RSA 4096
- APK Signature Scheme v2: true
- APK Signature Scheme v3: true
- Number of signers: 1

Install/update rule: install over the existing FUSH application without uninstalling so existing application data is preserved and Room migrations can run normally.
