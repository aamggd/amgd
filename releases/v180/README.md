# FUSH ERP Mobile v180 — Shipment FIFO Lot Auto-Select

## Release identity
- Application ID: `com.fush.erp.recovery`
- versionCode: `180`
- versionName: `0.15.4.131-shipment-fifo-lot-autoselect`
- Room schema: `48` (unchanged from v179)
- No destructive migration and no database deletion.

## Functional change
Shipment creation no longer relies on manually typing the lot/batch. Available positive stock is ordered by oldest stock movement (FIFO). Quantity already committed to open shipments and not yet sold is deducted. If the requested quantity is larger than the oldest available lot, allocation automatically continues into the next FIFO lot. The domain service revalidates the FIFO allocation inside the shipment creation transaction.

## Preserved baseline
This change is a delta over the final v179 Sales Additional Charges + Shipment Costs source. It does not replace or roll back v179 functionality.

## Validation completed before full Android build
- Static source checks: 15/15 PASS.
- Kotlin FIFO policy smoke: PASS.
- Reservation SQL smoke: 30 shipped - 10 sold = 20 reserved: PASS.
- Added FIFO allocation unit tests and UI/service contract tests.

## Offline Android toolchain prepared
GitHub Actions successfully exported:
- Android/Gradle dependency modules needed for AGP 9.2.0 / Gradle 9.4.1 builds.
- Android SDK 36 / Build Tools 36.0.0.

The permanent signing key/password are intentionally not committed to GitHub.

## Release gate still required
A final v180 release is not considered complete until the exact v180 source is compiled and the full gates pass: unit tests, `assembleRelease`, `lintVitalRelease`, zipalign, APK signature v2/v3, package/version inspection, and signer certificate SHA-256 verification against the permanent FUSH certificate:
`22d5e2a8bd48ddd2339abdc4748648b509e02d046524d6e118fbe05088155586`.

## Archived delta
- `FUSH_ERP_Mobile_v180-ShipmentFifoLotAutoSelect.patch`
