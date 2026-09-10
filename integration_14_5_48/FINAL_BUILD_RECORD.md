# FUSH ERP Mobile — Final Integrated 14.5.48

Status: **VALIDATED / BUILD PASSED / LOCALLY SIGNED**

- GitHub Actions run: `31870087495`
- Validated workflow commit: `1707d726c671a187f6e8b6e1ec24bf6d93e7a27b`
- Artifact ID: `9243243834`
- Artifact digest: `sha256:0d9effe9e74a647c695e4155f2724b1bb7ac7df72993d91ffacfe8afa27617ab`
- App ID: `com.fush.erp.recovery`
- versionCode: `87`
- versionName: `0.15.4.48-final-integrated`
- Room schema: `33`
- Integrated tree before audited compatibility fix: `2648b3227ed529c52fccb1d4b3a914d749fa10c6`
- Validated final source tree: `530e2aa1a0fae6aebdc59cae4e91ca8380e0940e`
- Unit Tests: PASS
- Release Build: PASS
- package/version/schema validation: PASS
- destructive migration fallback check: PASS
- zipalign: PASS

Local signing record (no signing secret stored):
- Signed APK SHA256: `a4e4bd00a775258655a3f13d89f15c2abedd4a0a75cec46b1f30ea828bc5157c`
- Certificate SHA256: `22:D5:E2:A8:BD:48:DD:D2:33:9A:BD:C4:74:86:48:B5:09:E0:2D:04:65:24:D6:E1:18:FB:E0:50:88:15:55:86`
- RSA 4096
- APK Signature Scheme v2: true
- APK Signature Scheme v3: true
- Number of signers: 1

Install/update rule: install over the existing FUSH application without uninstalling so the existing application data is preserved and Room migrations can run normally.
