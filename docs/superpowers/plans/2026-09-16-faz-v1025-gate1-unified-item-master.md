# FAZ v1.0.25 Gate 1 — Unified Item Master Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build one unified Add Item workflow that lets the user create/select category, brand/company and base unit inline, then creates exactly one operational Variant/SKU while preserving the Product Family → Brand → Template → Variant architecture.

**Architecture:** Add a real Product Category master instead of overloading Product Family. Gate 1 introduces Room Schema 54, adds `categoryId` to `ProductFamilyEntity`, and adds non-accounting reference fields (`referencePurchasePrice`, `imageUri`) to `ProductVariantEntity`. The UI uses the existing Product Master screen and extends the central searchable selector with an inline “+ Add new” action. Saving a simple item atomically creates a Family + Template + Variant/SKU using existing inventory/accounting identity rules; category/brand/unit inline masters are independently committed and immediately selected.

**Tech Stack:** Kotlin, Jetpack Compose, Room, existing FAZ Product Master services/DAOs, Android Activity Result API, CameraX + bundled ML Kit barcode scanning, GitHub Actions/Gradle 9.4.1, Android API 36.

**Spec:** `docs/superpowers/specs/2026-09-16-faz-ops-itemmaster-design.md`

## Global Constraints

- Baseline: FAZ Solar ERP v1.0.24, `applicationId = com.faz.solar`, Room Schema 53.
- Existing Item / ProductVariant IDs and all historical transactions must remain unchanged.
- No `fallbackToDestructiveMigration`.
- Register every new migration in both `AppContainer` and `AccountingWaveBRoomBootstrap`.
- `MASTER_DATA_MANAGE` is required for inline master creation and item creation.
- Hard delete remains disabled for operational SKUs; archive instead.
- Reference purchase price must never change accounting inventory cost.
- Gate 2 must not start until every Gate 1 acceptance test and release build is green.

---

### Task 1: Product Category Master + Room 53→54 migration

**Files:**
- Modify: `app/src/main/java/com/fush/erp/data/entity/ProductMasterEntities.kt`
- Modify: `app/src/main/java/com/fush/erp/data/dao/ProductMasterDao.kt`
- Create: `app/src/main/java/com/fush/erp/data/UnifiedItemMasterMigration.kt`
- Modify: `app/src/main/java/com/fush/erp/data/FushDatabase.kt`
- Modify: `app/src/main/java/com/fush/erp/data/AppContainer.kt`
- Modify: `app/src/main/java/com/fush/erp/data/AccountingWaveBRoomBootstrap.kt`
- Test: `app/src/test/java/com/fush/erp/domain/UnifiedItemMasterMigrationContractTest.kt`

**Interfaces:**
- Produces: `ProductCategoryEntity`, `MIGRATION_53_54_UNIFIED_ITEM_MASTER`, `ProductFamilyEntity.categoryId`, `ProductVariantEntity.referencePurchasePrice`, `ProductVariantEntity.imageUri`.

- [ ] **Step 1: Write the failing migration contract test**

```kotlin
@Test
fun schema54RequiresCategoryMasterAndStartupMigrationParity() {
    val db = source("app/src/main/java/com/fush/erp/data/FushDatabase.kt")
    val app = source("app/src/main/java/com/fush/erp/data/AppContainer.kt")
    val boot = source("app/src/main/java/com/fush/erp/data/AccountingWaveBRoomBootstrap.kt")
    val entities = source("app/src/main/java/com/fush/erp/data/entity/ProductMasterEntities.kt")
    assertTrue(db.contains("FUSH_DB_SCHEMA_VERSION = 54"))
    assertTrue(entities.contains("data class ProductCategoryEntity"))
    assertTrue(entities.contains("val categoryId: Long?"))
    assertTrue(entities.contains("val referencePurchasePrice: Double?"))
    assertTrue(entities.contains("val imageUri: String?"))
    assertTrue(app.contains("MIGRATION_53_54_UNIFIED_ITEM_MASTER"))
    assertTrue(boot.contains("MIGRATION_53_54_UNIFIED_ITEM_MASTER"))
    assertFalse(app.contains("fallbackToDestructiveMigration"))
    assertFalse(boot.contains("fallbackToDestructiveMigration"))
}
```

- [ ] **Step 2: Run the test and verify RED**

Run:
```bash
gradle --no-daemon :app:testDebugUnitTest --tests 'com.fush.erp.domain.UnifiedItemMasterMigrationContractTest'
```
Expected: FAIL because Schema 54/category master do not exist.

- [ ] **Step 3: Add the entity/columns and DAO**

Add:
```kotlin
@Entity(
    tableName = "product_categories",
    indices = [Index(value = ["code"], unique = true), Index(value = ["normalizedName"], unique = true), Index("isActive")]
)
data class ProductCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val nameAr: String,
    val nameEn: String = "",
    val normalizedName: String,
    val isActive: Boolean = true,
    val createdAt: Long = TrustedTimeService.now(),
    val updatedAt: Long = TrustedTimeService.now()
)
```

Extend `ProductFamilyEntity` with nullable `categoryId` and index it. Extend `ProductVariantEntity` with nullable `referencePurchasePrice` and `imageUri`. Add `observeCategories()`, `allCategories()`, `categoryById()`, `categoryByNormalizedName()`, `insertCategory()`, `updateCategory()` to `ProductMasterDao`.

- [ ] **Step 4: Implement migration 53→54**

Migration must:
1. Create `product_categories`.
2. Add nullable `categoryId` to `product_families`.
3. Add nullable `referencePurchasePrice` and `imageUri` to `product_variants`.
4. Seed three compatibility categories for existing families: RAW_MATERIAL, PACKAGING, FINISHED_GOOD.
5. Backfill every existing family `categoryId` from its existing `categoryCode`.
6. Create required indexes.
7. Never rewrite item/variant IDs or operational history.

- [ ] **Step 5: Register migration in both startup paths and raise schema to 54**

Register `MIGRATION_53_54_UNIFIED_ITEM_MASTER` in `AppContainer` and `AccountingWaveBRoomBootstrap`.

- [ ] **Step 6: Run migration contract and generate Room schema**

Run:
```bash
gradle --no-daemon :app:testDebugUnitTest --tests 'com.fush.erp.domain.UnifiedItemMasterMigrationContractTest'
gradle --no-daemon :app:assembleDebug
```
Expected: PASS and generated `54.json` with the new table/columns.

---

### Task 2: Normalized duplicate-safe inline master creation

**Files:**
- Modify: `app/src/main/java/com/fush/erp/domain/AutoNumberService.kt`
- Modify: `app/src/main/java/com/fush/erp/domain/ProductMasterService.kt`
- Modify: `app/src/main/java/com/fush/erp/domain/MasterDataService.kt`
- Modify: `app/src/main/java/com/fush/erp/data/dao/ProductMasterDao.kt`
- Modify: `app/src/main/java/com/fush/erp/data/dao/Daos.kt`
- Create: `app/src/main/java/com/fush/erp/domain/MasterNameNormalizer.kt`
- Test: `app/src/test/java/com/fush/erp/domain/MasterNameNormalizerTest.kt`
- Test: `app/src/test/java/com/fush/erp/domain/InlineMasterCreationContractTest.kt`

**Interfaces:**
- Produces: `MasterNameNormalizer.normalize(String): String`, `createCategoryQuick(...)`, `createBrandQuick(...)`, existing `createUnit(...)` with duplicate protection.

- [ ] **Step 1: Write RED normalization tests**

```kotlin
@Test fun normalizesWhitespaceTatweelAndArabicDiacritics() {
    assertEquals("اكلا", MasterNameNormalizer.normalize("  اَكـلا  "))
}
@Test fun keepsDifferentRealNamesDifferent() {
    assertNotEquals(MasterNameNormalizer.normalize("اكلا"), MasterNameNormalizer.normalize("مشروبات"))
}
```

- [ ] **Step 2: Implement `MasterNameNormalizer`**

Normalize by trim, collapse spaces, lowercase Latin, remove tatweel and Arabic diacritics. Do not transliterate letters or merge materially different Arabic words.

- [ ] **Step 3: Add snapshot DAO queries and auto-number sequences**

Add synchronous suspend list methods for active/all categories, brands, families and units used by validation. Add `nextProductCategoryCode()`, `nextProductFamilyCode()`, `nextBrandCode()` to `AutoNumberService`.

- [ ] **Step 4: Add quick-create services with permission/audit/duplicate checks**

`createCategoryQuick(nameAr, nameEn, createdBy)` and `createBrandQuick(nameAr, nameEn, createdBy)` must reject a normalized duplicate before insert and audit the successful creation. Enhance `MasterDataService.createUnit` with the same normalized duplicate guard.

- [ ] **Step 5: Run targeted tests**

Run:
```bash
gradle --no-daemon :app:testDebugUnitTest --tests 'com.fush.erp.domain.MasterNameNormalizerTest' --tests 'com.fush.erp.domain.InlineMasterCreationContractTest'
```
Expected: PASS.

---

### Task 3: Atomic unified simple-item creation service

**Files:**
- Modify: `app/src/main/java/com/fush/erp/domain/ProductMasterService.kt`
- Modify: `app/src/main/java/com/fush/erp/data/dao/ProductMasterDao.kt`
- Modify: `app/src/main/java/com/fush/erp/domain/AutoNumberService.kt`
- Test: `app/src/test/java/com/fush/erp/domain/UnifiedItemCreationContractTest.kt`

**Interfaces:**
- Consumes: selected `categoryId`, `brandId`, `baseUnitId`.
- Produces:
```kotlin
data class UnifiedItemCreateRequest(
    val nameAr: String,
    val nameEn: String,
    val categoryId: Long,
    val brandId: Long,
    val baseUnitId: Long,
    val barcode: String?,
    val salePrice: Double?,
    val referencePurchasePrice: Double?,
    val imageUri: String?,
    val compatibilityCategoryCode: String = "FINISHED_GOOD"
)

suspend fun createUnifiedItem(request: UnifiedItemCreateRequest, createdBy: Long): ProductVariantEntity
```

- [ ] **Step 1: Write the failing service contract test**

The contract must assert one transaction path creates Family → Template → Item/Variant and that `ProductVariant.id == Item.id`; barcode validation remains unique; reference purchase price is stored only on the Variant; and optional sale price is stored in the active RETAIL price list.

- [ ] **Step 2: Add `firstActiveRetailPriceList()` DAO query**

```sql
SELECT * FROM price_lists
WHERE isActive = 1 AND priceType = 'RETAIL'
ORDER BY id LIMIT 1
```

- [ ] **Step 3: Implement `createUnifiedItem` inside one Room transaction**

Rules:
- require `MASTER_DATA_MANAGE`;
- require active category, brand and base unit;
- auto-generate Family code and SKU;
- create a Family named after the item and linked to selected category;
- create Template for Family + selected Brand;
- insert exactly one `ItemEntity` then one `ProductVariantEntity` using the same item ID;
- insert base unit conversion factor 1.0;
- validate barcode against both Variant and unit-conversion barcode spaces;
- store `referencePurchasePrice` without touching costing layers/GL;
- if `salePrice != null`, require `salePrice > 0` and insert one current RETAIL `ProductVariantPriceEntity`;
- audit creation.

- [ ] **Step 4: Run targeted service contracts**

Run:
```bash
gradle --no-daemon :app:testDebugUnitTest --tests 'com.fush.erp.domain.UnifiedItemCreationContractTest'
```
Expected: PASS.

---

### Task 4: Searchable selector inline “+ Add new” behavior

**Files:**
- Modify: `app/src/main/java/com/fush/erp/ui/FushSearchableSelectionField.kt`
- Create: `app/src/main/java/com/fush/erp/ui/InlineCreateSelectionMath.kt`
- Test: `app/src/test/java/com/fush/erp/ui/InlineCreateSelectionMathTest.kt`

**Interfaces:**
- Extends `FushSearchableSelectionField` with optional:
```kotlin
createActionLabel: ((query: String) -> String)? = null,
onCreateRequested: ((query: String) -> Unit)? = null
```

- [ ] **Step 1: Write RED helper tests**

Test that `+ Add new` is offered only when query is nonblank and there is no normalized exact match. Partial matches do not suppress creation unless one normalized option equals the query.

- [ ] **Step 2: Implement pure helper**

```kotlin
fun shouldOfferInlineCreate(query: String, optionTexts: List<String>): Boolean
```
using `MasterNameNormalizer`.

- [ ] **Step 3: Extend the Compose selector**

When helper returns true, render a `DropdownMenuItem` after search results with the supplied action label. Clicking it calls `onCreateRequested(query.trim())` without dismissing or clearing unrelated parent form state.

- [ ] **Step 4: Run helper tests and compile**

Run:
```bash
gradle --no-daemon :app:testDebugUnitTest --tests 'com.fush.erp.ui.InlineCreateSelectionMathTest'
gradle --no-daemon :app:compileDebugKotlin
```
Expected: PASS.

---

### Task 5: Unified Add Item dialog + local barcode scanner

**Files:**
- Create: `app/src/main/java/com/fush/erp/ui/screens/UnifiedItemCreationDialog.kt`
- Create: `app/src/main/java/com/fush/erp/ui/BarcodeScannerDialog.kt`
- Modify: `app/src/main/java/com/fush/erp/ui/screens/ProductMasterScreens.kt`
- Modify: `app/build.gradle.kts`
- Test: `app/src/test/java/com/fush/erp/ui/screens/UnifiedItemCreationContractTest.kt`

**Interfaces:**
- Consumes categories/brands/units Flows and quick-create services.
- Produces one `onSaved(ProductVariantEntity)` result.

- [ ] **Step 1: Add a RED source contract for the required UX**

The test must require labels/paths for:
- `إضافة صنف جديد`
- `اسم الصنف`
- `القسم`
- `الشركة / العلامة التجارية`
- `الوحدة الأساسية`
- barcode + scanner action
- sale price + reference purchase price
- optional image
- inline create handlers for category, brand and unit
- `createUnifiedItem` save call.

- [ ] **Step 2: Add bundled local scanner dependencies**

Use CameraX Preview/ImageAnalysis and bundled ML Kit barcode scanning so runtime barcode recognition does not depend on a separate scanner app.

- [ ] **Step 3: Implement `BarcodeScannerDialog`**

It returns one decoded barcode, debounces duplicate frames, closes on success/cancel, and does not mutate the form until a code is returned.

- [ ] **Step 4: Implement the single unified dialog**

State lives in the dialog so inline master creation does not clear name/barcode/prices/image. Category/brand/unit use `FushSearchableSelectionField` with the new create callbacks. Newly-created master rows are immediately selected after service success.

Image selection uses `ActivityResultContracts.OpenDocument` with persisted read permission and stores the selected URI string in `imageUri`.

- [ ] **Step 5: Wire Product Master screen**

Add a prominent `+ إضافة صنف` action in the Structure tab. Keep existing advanced Family/Brand/Template/Variant actions available; do not remove expert workflows.

- [ ] **Step 6: Run UX contract and compile**

Run:
```bash
gradle --no-daemon :app:testDebugUnitTest --tests 'com.fush.erp.ui.screens.UnifiedItemCreationContractTest'
gradle --no-daemon :app:compileDebugKotlin
```
Expected: PASS.

---

### Task 6: Gate 1 full verification and artifact

**Files:**
- Create CI workflow/patch scripts on branch `faz/v1.0.25-ops-itemmaster` as needed to apply the plan to the v1.0.24 source artifact.

- [ ] **Step 1: Run all Gate 1 targeted tests**

Run all migration, normalizer, inline selector, unified item service and unified UI contract tests together.

- [ ] **Step 2: Run the complete unit test suite**

```bash
gradle --no-daemon :app:testDebugUnitTest
```
Expected: 0 failures.

- [ ] **Step 3: Build release**

```bash
gradle --no-daemon :app:assembleRelease
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Verify Room Schema 54**

Check generated `54.json` contains `product_categories`, `product_families.categoryId`, and the two new ProductVariant reference fields. Verify both startup builders register 53→54 and no destructive migration fallback exists.

- [ ] **Step 5: Package Gate 1 evidence**

Artifact contains unsigned APK, final Gate 1 source ZIP, `54.json`, targeted test logs, full test reports, release-build log and a Gate 1 verification text. Do not sign or call v1.0.25 final yet; later Gates still remain.
