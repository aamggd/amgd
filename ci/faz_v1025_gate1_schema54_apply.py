#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1])


def read(rel: str) -> str:
    return (root / rel).read_text(encoding="utf-8")


def write(rel: str, text: str) -> None:
    path = root / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)

# 1) Entity model: real category master + family link + non-accounting variant reference fields.
rel = "app/src/main/java/com/fush/erp/data/entity/ProductMasterEntities.kt"
text = read(rel)
category_entity = '''@Entity(
    tableName = "product_categories",
    indices = [
        Index(value = ["code"], unique = true),
        Index(value = ["normalizedName"], unique = true),
        Index("isActive")
    ]
)
data class ProductCategoryEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val nameAr: String,
    val nameEn: String = "",
    val normalizedName: String,
    val isActive: Boolean = true,
    val createdAt: Long = com.fush.erp.domain.TrustedTimeService.now(),
    val updatedAt: Long = com.fush.erp.domain.TrustedTimeService.now()
)

'''
text = replace_once(
    text,
    '@Entity(\n    tableName = "product_families",',
    category_entity + '@Entity(\n    tableName = "product_families",',
    "category entity insertion",
)
text = replace_once(
    text,
    'indices = [Index(value = ["code"], unique = true), Index("isActive")]\n)\ndata class ProductFamilyEntity(',
    'indices = [Index(value = ["code"], unique = true), Index("categoryId"), Index("isActive")]\n)\ndata class ProductFamilyEntity(',
    "family category index",
)
text = replace_once(
    text,
    '    val categoryCode: String = "RAW_MATERIAL",\n    val isActive: Boolean = true,',
    '    val categoryCode: String = "RAW_MATERIAL",\n    val categoryId: Long? = null,\n    val isActive: Boolean = true,',
    "family categoryId",
)
text = replace_once(
    text,
    '    val standardCost: Double? = null,\n    val reorderLevel: Double = 0.0,',
    '    val standardCost: Double? = null,\n    /** Informational purchase reference only; never used as inventory accounting cost. */\n    val referencePurchasePrice: Double? = null,\n    /** Persisted content URI for the optional product image. */\n    val imageUri: String? = null,\n    val reorderLevel: Double = 0.0,',
    "variant reference fields",
)
write(rel, text)

# 2) Product master DAO category CRUD/read surface.
rel = "app/src/main/java/com/fush/erp/data/dao/ProductMasterDao.kt"
text = read(rel)
text = replace_once(
    text,
    'interface ProductMasterDao {\n    @Query("SELECT * FROM product_families ORDER BY isActive DESC, nameAr, code")',
    '''interface ProductMasterDao {
    @Query("SELECT * FROM product_categories ORDER BY isActive DESC, nameAr, code")
    fun observeCategories(): Flow<List<ProductCategoryEntity>>

    @Query("SELECT * FROM product_categories ORDER BY isActive DESC, nameAr, code")
    suspend fun allCategories(): List<ProductCategoryEntity>

    @Query("SELECT * FROM product_categories WHERE id = :id LIMIT 1")
    suspend fun categoryById(id: Long): ProductCategoryEntity?

    @Query("SELECT * FROM product_categories WHERE code = :code COLLATE NOCASE LIMIT 1")
    suspend fun categoryByCode(code: String): ProductCategoryEntity?

    @Query("SELECT * FROM product_categories WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun categoryByNormalizedName(normalizedName: String): ProductCategoryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCategory(row: ProductCategoryEntity): Long

    @Update
    suspend fun updateCategory(row: ProductCategoryEntity)

    @Query("SELECT * FROM product_families ORDER BY isActive DESC, nameAr, code")''',
    "category DAO surface",
)
write(rel, text)

# 3) Room version/entities.
rel = "app/src/main/java/com/fush/erp/data/FushDatabase.kt"
text = read(rel)
text = replace_once(text, "const val FUSH_DB_SCHEMA_VERSION = 53", "const val FUSH_DB_SCHEMA_VERSION = 54", "schema version")
text = replace_once(
    text,
    '        ProductFamilyEntity::class,\n        BrandEntity::class,',
    '        ProductCategoryEntity::class,\n        ProductFamilyEntity::class,\n        BrandEntity::class,',
    "database category entity",
)
write(rel, text)

# 4) 53 -> 54 migration. Existing IDs/history are not rewritten.
write(
    "app/src/main/java/com/fush/erp/data/UnifiedItemMasterMigration.kt",
    '''package com.fush.erp.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_53_54_UNIFIED_ITEM_MASTER = object : Migration(53, 54) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `product_categories` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `code` TEXT NOT NULL,
                `nameAr` TEXT NOT NULL,
                `nameEn` TEXT NOT NULL,
                `normalizedName` TEXT NOT NULL,
                `isActive` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_product_categories_code` ON `product_categories` (`code`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_product_categories_normalizedName` ON `product_categories` (`normalizedName`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_categories_isActive` ON `product_categories` (`isActive`)")

        val now = System.currentTimeMillis()
        db.execSQL(
            "INSERT OR IGNORE INTO product_categories(code,nameAr,nameEn,normalizedName,isActive,createdAt,updatedAt) VALUES('RAW_MATERIAL','مواد خام','Raw Materials','مواد خام',1,?,?)",
            arrayOf(now, now)
        )
        db.execSQL(
            "INSERT OR IGNORE INTO product_categories(code,nameAr,nameEn,normalizedName,isActive,createdAt,updatedAt) VALUES('PACKAGING','مواد تعبئة وتغليف','Packaging','مواد تعبئة وتغليف',1,?,?)",
            arrayOf(now, now)
        )
        db.execSQL(
            "INSERT OR IGNORE INTO product_categories(code,nameAr,nameEn,normalizedName,isActive,createdAt,updatedAt) VALUES('FINISHED_GOOD','منتجات تامة','Finished Goods','منتجات تامة',1,?,?)",
            arrayOf(now, now)
        )

        db.execSQL("ALTER TABLE `product_families` ADD COLUMN `categoryId` INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_families_categoryId` ON `product_families` (`categoryId`)")
        db.execSQL(
            """
            UPDATE product_families
            SET categoryId = (
                SELECT pc.id FROM product_categories pc
                WHERE pc.code = product_families.categoryCode
                LIMIT 1
            )
            """.trimIndent()
        )

        db.execSQL("ALTER TABLE `product_variants` ADD COLUMN `referencePurchasePrice` REAL")
        db.execSQL("ALTER TABLE `product_variants` ADD COLUMN `imageUri` TEXT")
    }
}
''',
)

# 5) Register the same migration in both DB-opening paths. This is mandatory after the v1.0.23 startup-crash regression.
rel = "app/src/main/java/com/fush/erp/data/AppContainer.kt"
text = read(rel)
text = replace_once(
    text,
    "MIGRATION_51_52_PRODUCT_MASTER, MIGRATION_52_53_SUPPLIER_PROVENANCE).build()",
    "MIGRATION_51_52_PRODUCT_MASTER, MIGRATION_52_53_SUPPLIER_PROVENANCE, MIGRATION_53_54_UNIFIED_ITEM_MASTER).build()",
    "AppContainer migration registration",
)
write(rel, text)

rel = "app/src/main/java/com/fush/erp/data/AccountingWaveBRoomBootstrap.kt"
text = read(rel)
text = replace_once(
    text,
    "            MIGRATION_51_52_PRODUCT_MASTER,\n            MIGRATION_52_53_SUPPLIER_PROVENANCE\n",
    "            MIGRATION_51_52_PRODUCT_MASTER,\n            MIGRATION_52_53_SUPPLIER_PROVENANCE,\n            MIGRATION_53_54_UNIFIED_ITEM_MASTER\n",
    "startup migration registration",
)
write(rel, text)

print("GATE1_SCHEMA54_PATCH_APPLIED=PASS")
