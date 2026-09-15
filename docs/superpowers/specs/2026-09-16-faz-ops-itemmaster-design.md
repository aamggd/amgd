# FAZ v1.0.25 — Item Master + Operational Issue + Custody Purchasing Design

Date: 2026-09-16
Baseline: FAZ Solar ERP v1.0.24 (`com.faz.solar`, Room Schema 53)
Target branch: `faz/v1.0.25-ops-itemmaster`

## 1. Scope

This release combines four related operational/accounting improvements without changing existing item identities or historical postings:

1. Unified item creation screen with inline creation of master data such as category, brand/company, unit and related selectors.
2. Purchasing paid from an employee custody/advance account.
3. Warehouse Issue Order with accounting destination driven by issue purpose.
4. Default accounting support for sales-operation consumables, owner drawings, and small tools/equipment assets.

Existing ProductVariant/SKU identity, historical invoices, stock movements, cost layers, Room history and supplier provenance must remain intact.

## 2. Delivery decomposition

Implementation is phase-gated:

- Gate 1: Unified Item Creation + inline master-data creation.
- Gate 2: Purchase payment source = employee custody.
- Gate 3: Warehouse Issue Order + GL destination rules.
- Gate 4: Full regression, migration verification, release APK, permanent signing.

No later gate is accepted until the previous gate passes its test list.

## 3. Chosen architecture

### 3.1 Item creation

Keep the current item/product master architecture. Do not create a second item subsystem.

The existing Add Item flow becomes a single-page/single-dialog workflow containing:

- Arabic item name — required.
- English item name — optional.
- Category — required.
- Brand/company — required.
- Base unit — required.
- Barcode — optional, scanner supported.
- Sale price.
- Reference purchase price.
- Item image — optional.
- Existing Product Family / Template / Variant fields when relevant.

Every searchable selector supports an inline `+ Add new` action. Example: if the user types `اكلا` and no category matches, show `+ إضافة "اكلا" كقسم جديد`.

Inline creation rules:

- Save the new master record without closing the Add Item screen.
- Auto-select the newly-created record.
- Preserve all unsaved item form values.
- Check duplicates before creation using normalized Arabic/English names and the existing uniqueness rules.
- Respect `MASTER_DATA_MANAGE` permission.
- Write audit history using the existing audit mechanism.
- Used master-data records are archived rather than hard-deleted.

The same interaction pattern applies to Category, Brand/Company, UOM and any compatible existing master selector.

### 3.2 Purchases paid from employee custody

Purchase liability and payment source are separate concepts.

Add a purchase payment source model supporting at least:

- Supplier Credit
- Cashbox
- Bank
- Employee Custody

When `Employee Custody` is selected:

- employee/custody selection becomes required;
- available custody balance is displayed;
- settlement uses the custody sub-ledger already used by employee custody;
- the purchase document remains linked to the supplier for supplier history and provenance.

Posting behavior:

- Inventory purchase paid immediately from employee custody:
  - Dr Inventory / relevant purchase debit account
  - Cr Employee Custody account
- Credit purchase remains:
  - Dr Inventory / relevant purchase debit account
  - Cr Accounts Payable — Supplier
- Paying a supplier later from employee custody:
  - Dr Accounts Payable — Supplier
  - Cr Employee Custody account

The implementation must not reduce employee custody merely because an invoice exists; it reduces custody only when the custody is actually used as settlement source.

### 3.3 Warehouse Issue Order

Add one operational document: `Warehouse Issue Order`.

Required header fields:

- date/time
- warehouse
- issue purpose
- beneficiary/employee/owner when relevant
- cost center/project when available
- notes
- status/draft-posted lifecycle following existing posting conventions

Lines:

- Variant/SKU only
- UOM + base conversion
- quantity
- lot/expiry/serial selection when applicable
- FEFO rules where already required
- actual inventory cost used for posting

Posting reduces stock through the existing stock movement/cost engine. Do not create an independent stock balance.

Issue purposes and accounting destinations:

1. `SALES_OPERATING_EXPENSE`
   - for free small parts/consumables given as part of customer service and not billed.
   - Dr Sales/Operating Expense
   - Cr Inventory

2. `OWNER_DRAWING`
   - for goods withdrawn by owner.
   - Dr Owner Drawings / Owner Current Account
   - Cr Inventory
   - never treated as normal operating expense.

3. `SMALL_TOOLS_ASSET`
   - for inventory items transferred into internal-use small tools/equipment.
   - Dr Small Tools & Equipment Asset
   - Cr Inventory
   - depreciation policy remains separate from this release unless already supported by the asset module.

4. `INTERNAL_USE`
   - generic internal consumption routed to a configurable expense/cost account.

5. Existing production/transfer flows remain separate and are not replaced by this document.

Each posted issue stores the resolved debit account used at posting time so later account-setting changes do not rewrite history.

### 3.4 Default accounts

Add or ensure configurable chart-of-account mappings for:

- Sales Operating Consumables Expense
- Owner Drawings / Owner Current
- Small Tools & Equipment Asset
- Generic Internal Use Expense

If the application has an account bootstrap/default account mechanism, these accounts must be created or mapped through that mechanism rather than hard-coded IDs.

The Small Tools & Equipment account is an Asset-type account, not an expense account.

## 4. Data model principles

- No destructive migration.
- Existing Room Schema 53 is the baseline; the next schema number is introduced only if persistent entities/columns are actually needed.
- Item/Variant IDs are never rewritten.
- Purchase/sales/stock history remains untouched.
- Posted warehouse issue documents are immutable except through an explicit reversal/correction flow consistent with existing posted documents.
- All accounting postings retain document references and audit linkage.

Expected persistent additions may include:

- WarehouseIssueOrder header/line entities.
- IssuePurpose / resolved posting account fields.
- Purchase settlement source and custody reference fields if no reusable payment-source abstraction already exists.

Exact schema additions will follow existing project patterns after source inspection; no duplicate entity will be introduced when a compatible one already exists.

## 5. UX behavior

### Unified Add Item

The user must be able to complete the whole definition without navigating away.

Example flow:

1. Open `إضافة صنف جديد`.
2. Enter item name.
3. Type a category not found.
4. Tap `+ إضافة ... كقسم جديد`.
5. New category is saved and immediately selected.
6. Repeat for brand/company or UOM if required.
7. Scan/type barcode.
8. Enter prices/image.
9. Save item once.

No previously-entered field is cleared by inline master-data creation.

### Warehouse Issue

The user selects an issue purpose first. The UI then shows only relevant beneficiary/account fields and displays the resolved accounting destination before posting.

## 6. Validation and error handling

- Prevent duplicate barcode where existing product rules require uniqueness.
- Prevent duplicate master-data names after normalization.
- Block issue quantity above available stock unless current inventory policy explicitly allows negative stock.
- Block employee-custody payment without a valid employee/custody account.
- Block payment above permitted custody balance when custody policy disallows overdraft.
- Block posting when required debit account mapping is missing.
- Show posting failure without partially updating inventory or GL; use one transactional service boundary.

## 7. Testing / acceptance gates

### Gate 1 — Item Master UX

- Existing category can be selected.
- Missing category can be created inline and becomes selected.
- Same for brand/company and UOM.
- Other item form values remain unchanged after inline creation.
- Duplicate inline master record is rejected.
- Permission is enforced.
- Barcode scanner still works.
- Item save creates exactly one operational Variant/SKU and preserves Product Master architecture.

### Gate 2 — Employee Custody Purchasing

- Cash/bank/supplier-credit behavior remains unchanged.
- Immediate purchase from employee custody posts inventory debit / custody credit.
- Supplier history still contains the purchase.
- Supplier-credit purchase does not touch custody.
- Later supplier payment from custody clears AP and reduces custody.
- Insufficient custody is blocked according to existing custody rules.

### Gate 3 — Warehouse Issue

For one SKU with known cost:

- Sales operating issue reduces stock and debits Sales Operating Expense.
- Owner withdrawal reduces stock and debits Owner Drawings, not expense.
- Small-tools transfer reduces stock and debits an Asset account.
- Internal-use issue uses configured expense/cost account.
- Lot/expiry/FEFO behavior remains valid.
- Reposting the same posted issue cannot duplicate stock or GL.
- Audit trail and document references exist.

### Gate 4 — Release

- Upgrade from v1.0.24 preserves database and all history.
- No destructive migration fallback.
- Complete unit test suite passes.
- Release APK builds successfully.
- `applicationId = com.faz.solar`.
- Version code increases from v1.0.24.
- Permanent FAZ certificate matches the approved certificate.
- APK signing profile preserves v2/v3 and one signer.

## 8. Non-goals for this release

- Full fixed-asset depreciation engine redesign.
- Replacing the Product Master / Variant architecture.
- Replacing existing production issue or warehouse transfer documents.
- Consignment supplier accounting redesign.
- Automatic AI-generated accounting classifications.

## 9. Implementation approach alternatives considered

### A. Extend existing flows — selected

Reuse existing purchase, custody, stock movement, GL posting, audit and Product Master services. Add only the missing document/fields and UI behavior.

Benefits: lowest regression risk, preserves history, consistent accounting and permissions.

### B. Build separate quick-purchase and quick-issue subsystems

Rejected because it duplicates stock/GL logic and risks inconsistent balances.

### C. Treat all non-sale stock reductions as expenses

Rejected because owner drawings and small tools assets have materially different accounting treatment.

## 10. Release rule

The signed APK is not considered final until build, migration checks, complete tests, package/version verification, certificate fingerprint verification and APK v2/v3 verification all pass.
