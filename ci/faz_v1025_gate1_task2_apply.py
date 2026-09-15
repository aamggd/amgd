#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1])


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one anchor, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def write(rel: str, text: str) -> None:
    path = root / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


write(
    "app/src/main/java/com/fush/erp/domain/MasterNameNormalizer.kt",
    r'''package com.fush.erp.domain

import java.util.Locale

/**
 * Canonical comparison form for user-entered master-data names.
 * This is deliberately conservative: it removes presentation-only Arabic marks,
 * normalizes whitespace/case, and never transliterates or merges different words.
 */
object MasterNameNormalizer {
    private val arabicMarks = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]")
    private val whitespace = Regex("\\s+")

    fun normalize(value: String): String {
        val withoutTatweel = value.replace("\u0640", "")
        val withoutMarks = arabicMarks.replace(withoutTatweel, "")
        return whitespace.replace(withoutMarks.trim(), " ").lowercase(Locale.ROOT)
    }

    fun isDuplicate(candidate: String, existing: Iterable<String>): Boolean {
        val normalized = normalize(candidate)
        if (normalized.isBlank()) return false
        return existing.any { normalize(it) == normalized }
    }
}
'''
)

# Central auto-numbering: reserve separate namespaces for Product Master records.
auto = root / "app/src/main/java/com/fush/erp/domain/AutoNumberService.kt"
replace_once(
    auto,
    '    suspend fun nextUnitCode(): String = nextMasterCode("UNT", "UNT", 3)\n\n    suspend fun nextWarehouseCode(): String = nextMasterCode("WH", "WH", 3)\n',
    '    suspend fun nextUnitCode(): String = nextMasterCode("UNT", "UNT", 3)\n\n'
    '    suspend fun nextProductCategoryCode(): String = nextMasterCode("PCAT", "CAT", 4)\n\n'
    '    suspend fun nextProductFamilyCode(): String = nextMasterCode("PFAM", "FAM", 5)\n\n'
    '    suspend fun nextBrandCode(): String = nextMasterCode("BRAND", "BRD", 4)\n\n'
    '    suspend fun nextWarehouseCode(): String = nextMasterCode("WH", "WH", 3)\n',
    "AutoNumber Product Master methods",
)

# DAOs need synchronous all-record reads so archived names are also protected.
pmdao = root / "app/src/main/java/com/fush/erp/data/dao/ProductMasterDao.kt"
replace_once(
    pmdao,
    '    @Query("SELECT * FROM brands ORDER BY isActive DESC, nameAr, code")\n    fun observeBrands(): Flow<List<BrandEntity>>\n',
    '    @Query("SELECT * FROM brands ORDER BY isActive DESC, nameAr, code")\n'
    '    fun observeBrands(): Flow<List<BrandEntity>>\n\n'
    '    @Query("SELECT * FROM brands ORDER BY isActive DESC, nameAr, code")\n'
    '    suspend fun allBrands(): List<BrandEntity>\n',
    "ProductMasterDao allBrands",
)

daos = root / "app/src/main/java/com/fush/erp/data/dao/Daos.kt"
replace_once(
    daos,
    '    @Query("SELECT * FROM units WHERE isActive = 1 ORDER BY id")\n    suspend fun allActive(): List<UnitEntity>\n',
    '    @Query("SELECT * FROM units WHERE isActive = 1 ORDER BY id")\n'
    '    suspend fun allActive(): List<UnitEntity>\n\n'
    '    @Query("SELECT * FROM units ORDER BY isActive DESC, id")\n'
    '    suspend fun allIncludingInactive(): List<UnitEntity>\n',
    "UnitDao allIncludingInactive",
)

# ProductMasterService: reuse existing permission and audit mechanisms.
pms = root / "app/src/main/java/com/fush/erp/domain/ProductMasterService.kt"
replace_once(
    pms,
    'class ProductMasterService(private val db: FushDatabase) {\n',
    'class ProductMasterService(private val db: FushDatabase) {\n'
    '    private val numbering = AutoNumberService(db)\n',
    "ProductMasterService numbering",
)

anchor = '''    suspend fun createFamily(code: String, nameAr: String, nameEn: String, categoryCode: String, createdBy: Long): ProductFamilyEntity = db.withTransaction {
'''
quick_methods = r'''    suspend fun createCategoryQuick(
        nameAr: String,
        createdBy: Long,
        nameEn: String = ""
    ): ProductCategoryEntity = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.MASTER_DATA_MANAGE)
        val cleanAr = nameAr.trim()
        val cleanEn = nameEn.trim()
        val normalized = MasterNameNormalizer.normalize(cleanAr)
        require(normalized.isNotBlank()) { "اسم القسم مطلوب" }

        val dao = db.productMasterDao()
        val existingNames = dao.allCategories().flatMap { listOf(it.nameAr, it.nameEn) }.filter { it.isNotBlank() }
        require(dao.categoryByNormalizedName(normalized) == null) { "القسم موجود مسبقاً" }
        require(!MasterNameNormalizer.isDuplicate(cleanAr, existingNames)) { "القسم موجود مسبقاً" }
        require(cleanEn.isBlank() || !MasterNameNormalizer.isDuplicate(cleanEn, existingNames)) { "القسم موجود مسبقاً" }

        val now = TrustedTimeService.now()
        val code = numbering.nextProductCategoryCode()
        val row = ProductCategoryEntity(
            code = code,
            nameAr = cleanAr,
            nameEn = cleanEn,
            normalizedName = normalized,
            createdAt = now,
            updatedAt = now
        )
        val id = dao.insertCategory(row)
        row.copy(id = id).also {
            audit(
                createdBy,
                "CREATE",
                "PRODUCT_CATEGORY",
                id,
                "$code|${it.nameAr}|${it.nameEn}|$normalized",
                "إضافة قسم سريع من شاشة الصنف"
            )
        }
    }

    suspend fun createBrandQuick(
        nameAr: String,
        createdBy: Long,
        nameEn: String = "",
        manufacturerName: String = ""
    ): BrandEntity = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.MASTER_DATA_MANAGE)
        val cleanAr = nameAr.trim()
        val cleanEn = nameEn.trim()
        val normalized = MasterNameNormalizer.normalize(cleanAr)
        require(normalized.isNotBlank()) { "اسم العلامة التجارية مطلوب" }

        val dao = db.productMasterDao()
        val existingNames = dao.allBrands().flatMap { listOf(it.nameAr, it.nameEn) }.filter { it.isNotBlank() }
        require(!MasterNameNormalizer.isDuplicate(cleanAr, existingNames)) { "العلامة التجارية موجودة مسبقاً" }
        require(cleanEn.isBlank() || !MasterNameNormalizer.isDuplicate(cleanEn, existingNames)) { "العلامة التجارية موجودة مسبقاً" }

        val code = numbering.nextBrandCode()
        val row = BrandEntity(
            code = code,
            nameAr = cleanAr,
            nameEn = cleanEn,
            manufacturerName = manufacturerName.trim()
        )
        val id = dao.insertBrand(row)
        row.copy(id = id).also {
            audit(
                createdBy,
                "CREATE",
                "BRAND",
                id,
                "$code|${it.nameAr}|${it.nameEn}|${it.manufacturerName}",
                "إضافة علامة تجارية سريعة من شاشة الصنف"
            )
        }
    }

'''
replace_once(pms, anchor, quick_methods + anchor, "ProductMasterService quick creation")

# MasterDataService: duplicate guard runs before consuming the unit sequence.
mds = root / "app/src/main/java/com/fush/erp/domain/MasterDataService.kt"
old_unit = '''    suspend fun createUnit(nameAr: String, nameEn: String = "", createdBy: Long): UnitEntity = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.MASTER_DATA_MANAGE)
        require(nameAr.isNotBlank()) { "اسم الوحدة مطلوب" }
        val code = numbering.nextUnitCode()
        val row = UnitEntity(code = code, nameAr = nameAr.trim(), nameEn = nameEn.trim())
        val id = db.unitDao().insert(row)
        val saved = row.copy(id = id)
        audit(createdBy, "CREATE", "UNIT", id.toString(), newValue = "${saved.code}|${saved.nameAr}|${saved.nameEn}|${saved.isActive}", reason = "إنشاء وحدة قياس")
        saved
    }
'''
new_unit = '''    suspend fun createUnit(nameAr: String, nameEn: String = "", createdBy: Long): UnitEntity = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.MASTER_DATA_MANAGE)
        val cleanAr = nameAr.trim()
        val cleanEn = nameEn.trim()
        val normalized = MasterNameNormalizer.normalize(cleanAr)
        require(normalized.isNotBlank()) { "اسم الوحدة مطلوب" }

        val existingNames = db.unitDao().allIncludingInactive()
            .flatMap { listOf(it.nameAr, it.nameEn) }
            .filter { it.isNotBlank() }
        require(!MasterNameNormalizer.isDuplicate(cleanAr, existingNames)) { "الوحدة موجودة مسبقاً" }
        require(cleanEn.isBlank() || !MasterNameNormalizer.isDuplicate(cleanEn, existingNames)) { "الوحدة موجودة مسبقاً" }

        val code = numbering.nextUnitCode()
        val row = UnitEntity(code = code, nameAr = cleanAr, nameEn = cleanEn)
        val id = db.unitDao().insert(row)
        val saved = row.copy(id = id)
        audit(createdBy, "CREATE", "UNIT", id.toString(), newValue = "${saved.code}|${saved.nameAr}|${saved.nameEn}|${saved.isActive}", reason = "إنشاء وحدة قياس")
        saved
    }
'''
replace_once(mds, old_unit, new_unit, "MasterDataService createUnit duplicate guard")

print("GATE1_TASK2_PATCH_APPLIED=PASS")
