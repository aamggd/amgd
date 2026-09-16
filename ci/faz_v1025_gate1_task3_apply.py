#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1])


def read(rel: str) -> str:
    return (root / rel).read_text(encoding="utf-8")


def write(rel: str, text: str) -> None:
    p = root / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor, found {count}")
    return text.replace(old, new, 1)

# DAO: choose the first active RETAIL list for simple unified item entry.
rel = "app/src/main/java/com/fush/erp/data/dao/ProductMasterDao.kt"
text = read(rel)
text = replace_once(
    text,
    '    @Query("SELECT * FROM price_lists WHERE id = :id LIMIT 1")\n    suspend fun priceListById(id: Long): PriceListEntity?\n',
    '''    @Query("SELECT * FROM price_lists WHERE id = :id LIMIT 1")
    suspend fun priceListById(id: Long): PriceListEntity?

    @Query("SELECT * FROM price_lists WHERE isActive = 1 AND priceType = 'RETAIL' ORDER BY id LIMIT 1")
    suspend fun firstActiveRetailPriceList(): PriceListEntity?
''',
    "retail price list lookup",
)
write(rel, text)

# Unified item request + atomic create. The user category is independent from the legacy
# compatibility classification used by existing item reports/services.
rel = "app/src/main/java/com/fush/erp/domain/ProductMasterService.kt"
text = read(rel)
class_anchor = 'class ProductMasterService(private val db: FushDatabase) {\n'
request = '''data class UnifiedItemCreateRequest(
    val nameAr: String,
    val nameEn: String = "",
    val categoryId: Long,
    val brandId: Long,
    val baseUnitId: Long,
    val barcode: String? = null,
    val referencePurchasePrice: Double? = null,
    val salePrice: Double = 0.0,
    val imageUri: String? = null,
    val reorderLevel: Double = 0.0,
    val compatibilityCategoryCode: String = "FINISHED_GOOD"
)

'''
text = replace_once(text, class_anchor, request + class_anchor, "unified request")

method_anchor = '    suspend fun setVariantActive(variantId: Long, active: Boolean, updatedBy: Long): ProductVariantEntity = db.withTransaction {\n'
method = '''    suspend fun createUnifiedItem(
        request: UnifiedItemCreateRequest,
        createdBy: Long
    ): ProductVariantEntity = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.MASTER_DATA_MANAGE)

        val cleanNameAr = text(request.nameAr, "اسم الصنف")
        val cleanNameEn = request.nameEn.trim()
        require(request.categoryId > 0L) { "القسم مطلوب" }
        require(request.brandId > 0L) { "العلامة التجارية مطلوبة" }
        require(request.baseUnitId > 0L) { "الوحدة الأساسية مطلوبة" }
        require(request.compatibilityCategoryCode in setOf("RAW_MATERIAL", "PACKAGING", "FINISHED_GOOD")) {
            "تصنيف التوافق غير مدعوم"
        }
        require(request.referencePurchasePrice == null ||
            (request.referencePurchasePrice >= 0.0 && request.referencePurchasePrice.isFinite())) {
            "سعر الشراء المرجعي غير صالح"
        }
        require(request.salePrice >= 0.0 && request.salePrice.isFinite()) { "سعر البيع غير صالح" }
        require(request.reorderLevel >= 0.0 && request.reorderLevel.isFinite()) { "حد إعادة الطلب غير صالح" }

        val category = requireNotNull(db.productMasterDao().categoryById(request.categoryId)) { "القسم غير موجود" }
        val brand = requireNotNull(db.productMasterDao().brandById(request.brandId)) { "العلامة التجارية غير موجودة" }
        val baseUnit = requireNotNull(db.unitDao().byId(request.baseUnitId)) { "الوحدة الأساسية غير موجودة" }
        require(category.isActive) { "القسم موقوف" }
        require(brand.isActive) { "العلامة التجارية موقوفة" }
        require(baseUnit.isActive) { "الوحدة الأساسية موقوفة" }

        val normalizedBarcode = MasterDataMath.normalizeBarcode(request.barcode)
        if (normalizedBarcode != null) {
            require(db.productMasterDao().barcodeConflictCount(normalizedBarcode) == 0) { "الباركود مستخدم لـ Variant آخر" }
            require(db.itemUnitConversionDao().barcodeConflictCount(normalizedBarcode, 0) == 0) { "الباركود مستخدم في تحويل وحدة" }
        }

        // Resolve sale-price dependency before creating rows. The transaction still guarantees rollback.
        val retailPriceList = if (request.salePrice > 0.0) {
            requireNotNull(db.productMasterDao().firstActiveRetailPriceList()) {
                "لا توجد قائمة أسعار تجزئة نشطة لإضافة سعر البيع"
            }
        } else null

        var familyCode = numbering.nextProductFamilyCode()
        while (db.productMasterDao().familyByCode(familyCode) != null) {
            familyCode = numbering.nextProductFamilyCode()
        }
        var itemCode = numbering.nextItemCode(request.compatibilityCategoryCode)
        while (db.itemDao().byCode(itemCode) != null || db.productMasterDao().skuConflictCount(itemCode) > 0) {
            itemCode = numbering.nextItemCode(request.compatibilityCategoryCode)
        }

        val now = TrustedTimeService.now()
        val family = ProductFamilyEntity(
            code = familyCode,
            nameAr = cleanNameAr,
            nameEn = cleanNameEn,
            categoryCode = request.compatibilityCategoryCode,
            categoryId = category.id,
            createdAt = now,
            updatedAt = now
        )
        val familyId = db.productMasterDao().insertFamily(family)

        val template = ProductTemplateEntity(
            productFamilyId = familyId,
            brandId = brand.id,
            nameAr = cleanNameAr,
            nameEn = cleanNameEn,
            baseUomId = baseUnit.id,
            hasVariants = false,
            createdAt = now,
            updatedAt = now
        )
        val templateId = db.productMasterDao().insertTemplate(template)

        val item = ItemEntity(
            code = itemCode,
            nameAr = cleanNameAr,
            nameEn = cleanNameEn,
            category = request.compatibilityCategoryCode,
            baseUnitId = baseUnit.id,
            reorderLevel = request.reorderLevel,
            shelfLifeDays = null,
            lotTracked = false,
            expiryTracked = false,
            isActive = true
        )
        val itemId = db.itemDao().insert(item)

        val variant = ProductVariantEntity(
            id = itemId,
            productTemplateId = templateId,
            sku = itemCode,
            barcode = normalizedBarcode,
            nameAr = cleanNameAr,
            nameEn = cleanNameEn,
            purchaseUomId = baseUnit.id,
            salesUomId = baseUnit.id,
            inventoryUomId = baseUnit.id,
            costingMethod = "MOVING_AVERAGE",
            standardCost = null,
            referencePurchasePrice = request.referencePurchasePrice,
            imageUri = request.imageUri?.trim()?.takeIf { it.isNotEmpty() },
            reorderLevel = request.reorderLevel,
            trackLot = false,
            trackExpiry = false,
            trackSerial = false,
            attributeSignature = "",
            createdAt = now,
            updatedAt = now
        )
        db.productMasterDao().insertVariant(variant)

        db.itemUnitConversionDao().insert(
            ItemUnitConversionEntity(
                itemId = itemId,
                unitId = baseUnit.id,
                factorToBase = 1.0,
                allowPurchase = true,
                allowSale = true,
                barcode = null,
                isActive = true
            )
        )

        if (request.salePrice > 0.0) {
            val priceList = requireNotNull(retailPriceList)
            val price = ProductVariantPriceEntity(
                variantId = itemId,
                priceListId = priceList.id,
                unitPrice = request.salePrice,
                validFrom = now,
                validTo = null,
                minimumQty = 1.0,
                isActive = true
            )
            val priceId = db.productMasterDao().insertVariantPrice(price)
            audit(
                createdBy,
                "CREATE",
                "PRODUCT_VARIANT_PRICE",
                priceId,
                "variant=$itemId|list=${priceList.id}|price=${request.salePrice}",
                "إضافة سعر البيع من نموذج الصنف الموحد"
            )
        }

        audit(createdBy, "CREATE", "PRODUCT_FAMILY", familyId, "code=$familyCode|category=${category.id}", "إنشاء تلقائي من نموذج الصنف الموحد")
        audit(createdBy, "CREATE", "PRODUCT_TEMPLATE", templateId, "family=$familyId|brand=${brand.id}|uom=${baseUnit.id}", "إنشاء تلقائي من نموذج الصنف الموحد")
        audit(createdBy, "CREATE", "PRODUCT_VARIANT", itemId, "sku=$itemCode|category=${category.id}|brand=${brand.id}|barcode=${normalizedBarcode.orEmpty()}", "إنشاء صنف تشغيلي موحد؛ معرف Variant مطابق لمعرف itemId")
        variant
    }

'''
text = replace_once(text, method_anchor, method + method_anchor, "unified create method")
write(rel, text)

print("GATE1_TASK3_PATCH_APPLIED=PASS")
