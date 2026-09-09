package com.fush.erp.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.Locale

/** v204: formal Governorate -> District -> Area hierarchy without destructive migration. */
val MIGRATION_50_51_GEOGRAPHY_HIERARCHY = object : Migration(50, 51) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `geo_governorates` (
                `id` TEXT NOT NULL, `code` TEXT NOT NULL, `nameAr` TEXT NOT NULL, `nameEn` TEXT NOT NULL,
                `sortOrder` INTEGER NOT NULL, `source` TEXT NOT NULL, `isOfficialSeed` INTEGER NOT NULL,
                `isActive` INTEGER NOT NULL, `updatedBy` INTEGER, `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_geo_governorates_code` ON `geo_governorates` (`code`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_geo_governorates_nameAr` ON `geo_governorates` (`nameAr`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_geo_governorates_isActive` ON `geo_governorates` (`isActive`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `geo_districts` (
                `id` TEXT NOT NULL, `code` TEXT NOT NULL, `governorateId` TEXT NOT NULL,
                `nameAr` TEXT NOT NULL, `nameEn` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL,
                `source` TEXT NOT NULL, `isOfficialSeed` INTEGER NOT NULL, `isActive` INTEGER NOT NULL,
                `updatedBy` INTEGER, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`),
                FOREIGN KEY(`governorateId`) REFERENCES `geo_governorates`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_geo_districts_code` ON `geo_districts` (`code`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_geo_districts_governorateId` ON `geo_districts` (`governorateId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_geo_districts_governorateId_nameAr` ON `geo_districts` (`governorateId`,`nameAr`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_geo_districts_isActive` ON `geo_districts` (`isActive`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `geo_areas` (
                `id` TEXT NOT NULL, `code` TEXT NOT NULL, `districtId` TEXT NOT NULL,
                `nameAr` TEXT NOT NULL, `nameEn` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL,
                `source` TEXT NOT NULL, `isOfficialSeed` INTEGER NOT NULL, `isActive` INTEGER NOT NULL,
                `updatedBy` INTEGER, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`),
                FOREIGN KEY(`districtId`) REFERENCES `geo_districts`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_geo_areas_code` ON `geo_areas` (`code`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_geo_areas_districtId` ON `geo_areas` (`districtId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_geo_areas_districtId_nameAr` ON `geo_areas` (`districtId`,`nameAr`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_geo_areas_isActive` ON `geo_areas` (`isActive`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `geo_governorate_aliases` (
                `normalizedAlias` TEXT NOT NULL, `governorateId` TEXT NOT NULL, `displayAlias` TEXT NOT NULL,
                `source` TEXT NOT NULL DEFAULT 'MIGRATION', PRIMARY KEY(`normalizedAlias`),
                FOREIGN KEY(`governorateId`) REFERENCES `geo_governorates`(`id`) ON UPDATE CASCADE ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_geo_governorate_aliases_governorateId` ON `geo_governorate_aliases` (`governorateId`)")

        val now = System.currentTimeMillis()
        val seeds = listOf(
            GovSeed("YE11", "إب", "Ibb", 1),
            GovSeed("YE12", "أبين", "Abyan", 2),
            GovSeed("YE13", "أمانة العاصمة", "Sana'a City", 3),
            GovSeed("YE14", "البيضاء", "Al Bayda", 4),
            GovSeed("YE15", "تعز", "Ta'iz", 5),
            GovSeed("YE16", "الجوف", "Al Jawf", 6),
            GovSeed("YE17", "حجة", "Hajjah", 7),
            GovSeed("YE18", "الحديدة", "Al Hodeidah", 8),
            GovSeed("YE19", "حضرموت", "Hadramawt", 9),
            GovSeed("YE20", "ذمار", "Dhamar", 10),
            GovSeed("YE21", "شبوة", "Shabwah", 11),
            GovSeed("YE22", "صعدة", "Sa'dah", 12),
            GovSeed("YE23", "صنعاء", "Sana'a", 13),
            GovSeed("YE24", "عدن", "Aden", 14),
            GovSeed("YE25", "لحج", "Lahj", 15),
            GovSeed("YE26", "مأرب", "Ma'rib", 16),
            GovSeed("YE27", "المحويت", "Al Mahwit", 17),
            GovSeed("YE28", "المهرة", "Al Maharah", 18),
            GovSeed("YE29", "عمران", "Amran", 19),
            GovSeed("YE30", "الضالع", "Ad Dali'", 20),
            GovSeed("YE31", "ريمة", "Raymah", 21),
            GovSeed("YE32", "سقطرى", "Socotra", 22)
        )
        seeds.forEach { g ->
            db.execSQL(
                "INSERT OR IGNORE INTO geo_governorates(id,code,nameAr,nameEn,sortOrder,source,isOfficialSeed,isActive,updatedBy,updatedAt) VALUES(?,?,?,?,?,'OPEN_ADMIN_DATA',1,1,NULL,?)",
                arrayOf<Any?>(g.id, g.id, g.nameAr, g.nameEn, g.sortOrder, now)
            )
            listOf(g.nameAr, g.nameEn, g.id).filter { it.isNotBlank() }.forEach { alias ->
                db.execSQL("INSERT OR REPLACE INTO geo_governorate_aliases(normalizedAlias,governorateId,displayAlias,source) VALUES(?,?,?,'CANONICAL')",
                    arrayOf(normalizeGeoAlias(alias), g.id, alias))
            }
        }
        listOf("tauz", "taiz", "ta'izz", "الحوبان", "تعز/الحوبان", "تعز / الحوبان", "تعز بير باشا", "تعز/بيرباشا", "تعز/ بيرباشا", "تعز/ المسبح", "تعز/المسبح", "تعز/ وادي القاضي", "تعز/وادي القاضي", "تعز/ الدحي", "تعز/الدحي", "تعز/باب موسى", "تعز/ باب موسى", "تعز/ سوق القرشي", "تعز/سوق القرشي", "تعز/ شارع المبيدات", "تعز/شارع المبيدات", "تعز/ البعرارة", "تعز/البعرارة", "تعز/ الزنقل", "تعز/الزنقل", "تعز / الشنيني", "تعز/الشنيني", "تعز / المدينة", "تعز/المدينة", "تعز / باب الكبير", "تعز/باب الكبير", "تعز /الهريش", "تعز/الهريش", "تعز/التحرير الاسفل بعد سوق التلفونات", "تعز/الثوره جوار بوفية الامين", "تعز/شارع الذهب", "تعز/شارع26 مقابل بهارات تعز", "تعز/ سوق المبيدات مقابل النهضه").forEach { alias ->
            db.execSQL("INSERT OR REPLACE INTO geo_governorate_aliases(normalizedAlias,governorateId,displayAlias,source) VALUES(?,?,?,'LEGACY_FUSH')",
                arrayOf(normalizeGeoAlias(alias), "YE15", alias))
        }

        // New FK columns are nullable so no existing row can be lost or invalidated.
        db.execSQL("ALTER TABLE `customers` ADD COLUMN `governorateId` TEXT DEFAULT NULL REFERENCES `geo_governorates`(`id`) ON DELETE RESTRICT")
        db.execSQL("ALTER TABLE `customers` ADD COLUMN `districtId` TEXT DEFAULT NULL REFERENCES `geo_districts`(`id`) ON DELETE RESTRICT")
        db.execSQL("ALTER TABLE `customers` ADD COLUMN `areaId` TEXT DEFAULT NULL REFERENCES `geo_areas`(`id`) ON DELETE RESTRICT")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_governorateId` ON `customers` (`governorateId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_districtId` ON `customers` (`districtId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_areaId` ON `customers` (`areaId`)")

        db.execSQL("ALTER TABLE `sales_invoices` ADD COLUMN `governorateId` TEXT DEFAULT NULL REFERENCES `geo_governorates`(`id`) ON DELETE RESTRICT")
        db.execSQL("ALTER TABLE `sales_invoices` ADD COLUMN `districtId` TEXT DEFAULT NULL REFERENCES `geo_districts`(`id`) ON DELETE RESTRICT")
        db.execSQL("ALTER TABLE `sales_invoices` ADD COLUMN `areaId` TEXT DEFAULT NULL REFERENCES `geo_areas`(`id`) ON DELETE RESTRICT")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_invoices_governorateId` ON `sales_invoices` (`governorateId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_invoices_districtId` ON `sales_invoices` (`districtId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_invoices_areaId` ON `sales_invoices` (`areaId`)")

        db.execSQL("ALTER TABLE `sales_shipments` ADD COLUMN `destinationGovernorateId` TEXT DEFAULT NULL REFERENCES `geo_governorates`(`id`) ON DELETE RESTRICT")
        db.execSQL("ALTER TABLE `sales_shipments` ADD COLUMN `destinationDistrictId` TEXT DEFAULT NULL REFERENCES `geo_districts`(`id`) ON DELETE RESTRICT")
        db.execSQL("ALTER TABLE `sales_shipments` ADD COLUMN `destinationAreaId` TEXT DEFAULT NULL REFERENCES `geo_areas`(`id`) ON DELETE RESTRICT")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_shipments_destinationGovernorateId` ON `sales_shipments` (`destinationGovernorateId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_shipments_destinationDistrictId` ON `sales_shipments` (`destinationDistrictId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_shipments_destinationAreaId` ON `sales_shipments` (`destinationAreaId`)")

        // Preserve the historical free text as a snapshot, but resolve formal governorate keys.
        backfillGovernorate(db, "customers", "province", "governorateId")
        backfillGovernorate(db, "sales_invoices", "province", "governorateId")
        backfillGovernorate(db, "sales_shipments", "destinationProvince", "destinationGovernorateId")

        // Current FUSH data contains detailed Taiz locations in the old province field. They all map
        // to one governorate key; district/area remain null until explicitly selected (no guessing).
        val taizNorm = normalizeSqlExpression("province")
        db.execSQL("UPDATE customers SET governorateId='YE15' WHERE governorateId IS NULL AND ($taizNorm LIKE 'تعز%' OR $taizNorm='الحوبان' OR $taizNorm='tauz')")
        db.execSQL("UPDATE sales_invoices SET governorateId='YE15' WHERE governorateId IS NULL AND ($taizNorm LIKE 'تعز%' OR $taizNorm='الحوبان' OR $taizNorm='tauz')")
        val shipNorm = normalizeSqlExpression("destinationProvince")
        db.execSQL("UPDATE sales_shipments SET destinationGovernorateId='YE15' WHERE destinationGovernorateId IS NULL AND ($shipNorm LIKE 'تعز%' OR $shipNorm='الحوبان' OR $shipNorm='tauz')")
    }
}

private data class GovSeed(val id: String, val nameAr: String, val nameEn: String, val sortOrder: Int)

private fun normalizeGeoAlias(raw: String): String = raw.trim().lowercase(Locale.ROOT)
    .replace("محافظة", "").replace("أ", "ا").replace("إ", "ا").replace("آ", "ا").replace("ٱ", "ا")
    .replace("ة", "ه").replace("ى", "ي").replace("ـ", "")
    .replace(Regex("[\\s\\-_/.,،()'’]+"), "")

private fun normalizeSqlExpression(column: String): String =
    "replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(trim($column),'محافظة',''),'أ','ا'),'إ','ا'),'آ','ا'),'ة','ه'),'ى','ي'),'ـ',''),' ',''),'/',''),'-',''),'.','')"

private fun backfillGovernorate(db: SupportSQLiteDatabase, table: String, textColumn: String, idColumn: String) {
    val normalized = normalizeSqlExpression(textColumn)
    db.execSQL("UPDATE $table SET $idColumn=(SELECT governorateId FROM geo_governorate_aliases a WHERE a.normalizedAlias=$normalized LIMIT 1) WHERE $idColumn IS NULL AND TRIM($textColumn)<>''")
}
