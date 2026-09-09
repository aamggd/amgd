package com.fush.erp.data

import android.content.Context
import com.fush.erp.data.entity.AreaEntity
import com.fush.erp.data.entity.DistrictEntity
import com.fush.erp.data.entity.GovernorateAliasEntity
import com.fush.erp.data.entity.GovernorateEntity
import org.json.JSONObject
import java.util.Locale

/**
 * v204 official starter geography.
 *
 * Governorates/districts use stable Open Admin Data Yemen IDs. Areas are seeded from the
 * YemenOpenSource uzaal dataset and remain editable operational master data. Seed inserts are
 * IGNORE-only so later user edits are never overwritten by an app update.
 */
object YemenGeographyHierarchy {
    const val ASSET = "yemen_geography_seed_v204.json"

    fun normalizeAlias(raw: String): String = raw
        .trim()
        .lowercase(Locale.ROOT)
        .replace("محافظة", "")
        .replace("أ", "ا").replace("إ", "ا").replace("آ", "ا").replace("ٱ", "ا")
        .replace("ة", "ه").replace("ى", "ي")
        .replace("ـ", "")
        .replace(Regex("[\\s\\-_/.,،()'’]+"), "")

    suspend fun ensureOfficialSeed(context: Context, db: FushDatabase) {
        // A migrated v50 database already contains the 22 governorates so this intentionally checks
        // districts/areas independently instead of using a single all-or-nothing marker.
        val root = context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
        val now = System.currentTimeMillis()

        if (db.geographyDao().governorateCount() < 22) {
            val rows = root.getJSONArray("governorates")
            val out = ArrayList<GovernorateEntity>(rows.length())
            for (i in 0 until rows.length()) {
                val o = rows.getJSONObject(i)
                out += GovernorateEntity(
                    id = o.getString("id"), code = o.getString("code"),
                    nameAr = o.getString("nameAr"), nameEn = o.optString("nameEn"),
                    sortOrder = o.optInt("sortOrder", i + 1), source = o.optString("source", "OPEN_ADMIN_DATA"),
                    isOfficialSeed = true, updatedAt = now
                )
            }
            db.geographyDao().insertGovernoratesIgnore(out)
        }

        if (db.geographyDao().districtCount() < 335) {
            val rows = root.getJSONArray("districts")
            val out = ArrayList<DistrictEntity>(rows.length())
            for (i in 0 until rows.length()) {
                val o = rows.getJSONObject(i)
                out += DistrictEntity(
                    id = o.getString("id"), code = o.getString("code"), governorateId = o.getString("governorateId"),
                    nameAr = o.getString("nameAr"), nameEn = o.optString("nameEn"),
                    sortOrder = o.optInt("sortOrder", i + 1), source = o.optString("source", "OPEN_ADMIN_DATA"),
                    isOfficialSeed = true, updatedAt = now
                )
            }
            // Batches avoid large SQLite bind bursts on older devices.
            out.chunked(250).forEach { db.geographyDao().insertDistrictsIgnore(it) }
        }

        if (db.geographyDao().areaCount() < 2234) {
            val rows = root.getJSONArray("areas")
            val out = ArrayList<AreaEntity>(rows.length())
            for (i in 0 until rows.length()) {
                val o = rows.getJSONObject(i)
                out += AreaEntity(
                    id = o.getString("id"), code = o.getString("code"), districtId = o.getString("districtId"),
                    nameAr = o.getString("nameAr"), nameEn = o.optString("nameEn"),
                    sortOrder = o.optInt("sortOrder", i + 1), source = o.optString("source", "YEMEN_OPEN_SOURCE_UZLA"),
                    isOfficialSeed = true, updatedAt = now
                )
            }
            out.chunked(250).forEach { db.geographyDao().insertAreasIgnore(it) }
        }

        val governorates = db.geographyDao().allGovernorates()
        val aliases = buildList {
            governorates.forEach { g ->
                add(GovernorateAliasEntity(normalizeAlias(g.nameAr), g.id, g.nameAr, "CANONICAL"))
                if (g.nameEn.isNotBlank()) add(GovernorateAliasEntity(normalizeAlias(g.nameEn), g.id, g.nameEn, "CANONICAL_EN"))
                add(GovernorateAliasEntity(normalizeAlias(g.code), g.id, g.code, "CODE"))
            }
            // Existing FUSH data observed before v204. These keep historical text untouched while
            // resolving every variant to one governorate key.
            listOf(
                "tauz", "taiz", "ta'izz", "تعز بير باشا", "تعز/بيرباشا", "تعز/ بيرباشا",
                "تعز/المسبح", "تعز/ المسبح", "تعز/وادي القاضي", "تعز/ وادي القاضي",
                "تعز/الدحي", "تعز/ الدحي", "تعز/باب موسى", "تعز/ باب موسى",
                "تعز/سوق القرشي", "تعز/ سوق القرشي", "تعز/شارع المبيدات", "تعز/ شارع المبيدات",
                "تعز/البعرارة", "تعز/ البعرارة", "تعز/الزنقل", "تعز/ الزنقل",
                "تعز/الشنيني", "تعز / الشنيني", "تعز/المدينة", "تعز / المدينة",
                "تعز/باب الكبير", "تعز / باب الكبير", "تعز/الهريش", "تعز /الهريش",
                "تعز/التحرير الاسفل بعد سوق التلفونات", "تعز/الثوره جوار بوفية الامين",
                "تعز/شارع الذهب", "تعز/شارع26 مقابل بهارات تعز", "تعز/سوق المبيدات مقابل النهضه",
                "الحوبان", "تعز/الحوبان", "تعز / الحوبان"
            ).forEach { add(GovernorateAliasEntity(normalizeAlias(it), "YE15", it, "LEGACY_FUSH")) }
        }.distinctBy { it.normalizedAlias }
        db.geographyDao().upsertGovernorateAliases(aliases)
    }
}
