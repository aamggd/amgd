# FUSH ERP Mobile — Sales & Customers Phase 1 (P0)

Branch: `fush/sales-customers`

Baseline: validated `Phase 14.5.54 Printing Integrated` source artifact from workflow run `31909754750` / artifact `9253417429`.

Exact baseline source tree: `1b6af7bcaa86138ae75ea3d905db4bdba0fe04ff`.

## Phase 1 scope

Implements P0 only: separate sales operations from the customer profile while keeping explicit navigation links between the customer workspace and Sales.

- Sales workspace remains the transaction workspace for invoices, customer collections, returns and collection reversal/correction.
- Customer profile remains the customer master/follow-up workspace and displays linked invoices, receipts, returns and statement history without posting sales transactions from the profile.
- A customer-profile action explicitly opens Sales.
- Existing customer-wide auto-allocation collection was relocated to Sales; its `SalesService` posting calls and rules were not changed.
- Existing receipt reversal was relocated to Sales invoice detail; its `SalesService.reverseReceipt` call and rules were not changed.
- Legacy/dead customer add/edit/statement transaction UI was removed from `SalesScreens.kt`; customer master creation remains in the Customers workspace.

## Safety

- Application ID: unchanged (`com.fush.erp.recovery`).
- Room schema: unchanged (`34`).
- No migration added.
- No destructive migration or `fallbackToDestructiveMigration`.
- Accounting/inventory business logic: unchanged; only UI ownership/invocation was moved. Existing `SalesService` posting methods remain the source of truth.
- versionCode/versionName: unchanged from the baseline; no branch-final version assigned.
- Signing: CI produces unsigned release output only.

## Apply

The repository stores the patch XZ-compressed and Base64-encoded as `sales_customers_phase1/phase1_p0.patch.xz.b64` to keep the branch handoff compact and transport-safe. Reconstruct and apply it from an exact Phase 14.5.54 source tree:

```bash
base64 -d sales_customers_phase1/phase1_p0.patch.xz.b64 | xz -dc > /tmp/phase1_p0.patch
git apply --check /tmp/phase1_p0.patch
git apply /tmp/phase1_p0.patch
```

The branch workflow independently restores the validated baseline artifact, verifies its source-tree hash, reconstructs and applies this patch, runs unit tests and `assembleRelease`, validates the P0 workspace boundary, and verifies the branch safety constraints. The successful workflow artifact also exports the raw `phase1_p0.patch` plus an unsigned aligned APK.

Validation transport: XZ payload only; the superseded gzip transport was removed from the branch.

Validation payload SHA-256: `c28d2a3b1489008c29cd2c2be9e6fd8a7e427ac2da7daf333206a9af4eb9af08`.
