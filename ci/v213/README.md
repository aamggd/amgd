# FUSH ERP Mobile v213 GitHub Build

Branch: `v213-build-clean`

Expected source snapshot SHA-256:
`97e5eaa84945983b44b39f4eb1105fb375bb7e6d585cb463f4ba3835d98a06e`

Expected release identity:
- Application ID: `com.fush.erp.recovery`
- versionCode: `213`
- Gradle: `9.4.1`
- AGP: `9.2.0`
- Kotlin: `2.3.21`
- compileSdk: `36`
- Build Tools: `36.1.0`

The workflow reconstructs `ci/v213/source/part-*`, verifies the source hash and release identity, rejects destructive Room migration fallback, runs unit tests, assembles release, and uploads the unsigned APK as an Actions artifact. Signing is intentionally kept outside the public repository.
