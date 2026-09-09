package com.fush.erp.cloud

import android.content.Context
import com.fush.erp.BuildConfig
import com.fush.erp.R
import com.fush.erp.data.FushDatabase
import com.fush.erp.data.YemenGeographyHierarchy
import com.fush.erp.data.entity.AuditEventEntity
import com.fush.erp.data.entity.AreaEntity
import com.fush.erp.data.entity.DistrictEntity
import com.fush.erp.data.entity.GovernorateEntity
import com.fush.erp.data.entity.CurrencyEntity
import com.fush.erp.data.entity.CustomerEntity
import com.fush.erp.data.entity.ItemEntity
import com.fush.erp.data.entity.ItemUnitConversionEntity
import com.fush.erp.data.entity.SupplierEntity
import com.fush.erp.data.entity.UnitEntity
import com.fush.erp.data.entity.UserEntity
import com.fush.erp.data.entity.WarehouseEntity
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

internal class MasterDataCloudSyncEngine(
    private val context: Context,
    private val db: FushDatabase,
) {
    private val store = MasterDataSyncStore(context.applicationContext)

    fun lastSuccessAt(localUserId: Long): Long = store.lastSuccessAt(localUserId)

    suspend fun sync(
        localUser: UserEntity,
        session: CloudSession,
        cloudRole: String,
    ): CloudOperationResult<MasterDataSyncResult> {
        return try {
            val role = cloudRole.trim().uppercase() // display/audit only; transport authority is active membership
            val firstBaseline = !store.baselineComplete(localUser.id)

            var remote = fetchAll(session)
            val remoteTotal = remote.totalRows()
            var bootstrapped = false

            if (firstBaseline && remoteTotal == 0) {
                // v185: any active company member may bootstrap transport data.
                bootstrapCloud(session)
                remote = fetchAll(session)
                bootstrapped = true
            }

            val counters = Counters()
            val localUserId = localUser.id

            syncCurrencies(localUserId, role, firstBaseline, session, remote.currencies, counters)
            syncUnits(localUserId, role, firstBaseline, session, remote.units, counters)
            syncWarehouses(localUserId, role, firstBaseline, session, remote.warehouses, counters)
            syncItems(localUserId, role, firstBaseline, session, remote.items, counters)
            syncConversions(localUserId, role, firstBaseline, session, remote.conversions, counters)
            // v204 geography seed is deterministic and safe to bootstrap even when this device's
            // general master-data baseline is being established for the first time. This prevents
            // official governorates/districts/areas from being quarantined as local-only rows.
            syncGovernorates(localUserId, role, false, session, remote.governorates, counters)
            syncDistricts(localUserId, role, false, session, remote.districts, counters)
            syncAreas(localUserId, role, false, session, remote.areas, counters)
            syncCustomers(localUserId, role, firstBaseline, session, remote.customers, counters)
            syncSuppliers(localUserId, role, firstBaseline, session, remote.suppliers, counters)

            store.markBaselineComplete(localUser.id)
            val completedAt = System.currentTimeMillis()
            store.markSuccess(localUser.id, completedAt)

            val result = MasterDataSyncResult(
                uploaded = counters.uploaded,
                downloaded = counters.downloaded,
                unchanged = counters.unchanged,
                conflicts = counters.conflicts,
                skippedLocalOnFirstBaseline = counters.skippedBaseline,
                skippedUnauthorized = counters.skippedUnauthorized,
                bootstrappedCloud = bootstrapped,
                completedAtEpochMillis = completedAt,
            )

            if (result.changed > 0 || result.conflicts > 0 || result.bootstrappedCloud) {
                db.governanceDao().insertAudit(
                    AuditEventEntity(
                        userId = localUser.id,
                        action = "CLOUD_MASTER_SYNC",
                        entityType = "SYSTEM",
                        entityId = session.requireOrganizationId(),
                        newValue = "uploaded=${result.uploaded};downloaded=${result.downloaded};conflicts=${result.conflicts};skipped=${result.skippedUnauthorized}",
                        reason = if (result.bootstrappedCloud) "Cloud master-data bootstrap and sync" else "Cloud master-data sync",
                        deviceInfo = "ANDROID_CLOUD_SYNC",
                    )
                )
            }
            CloudOperationResult.Success(result)
        } catch (error: Throwable) {
            CloudOperationResult.Failure(
                error.message?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.cloud_master_data_sync_failed)
            )
        }
    }

    private suspend fun bootstrapCloud(session: CloudSession) {
        val org = session.requireOrganizationId()
        val userId = session.userId

        val currencies = db.currencyDao().allRows().map { currencyJson(it, org, userId) }
        upsertBatch("fush_md_currencies", "organization_id,code", currencies, session)

        val units = db.unitDao().allRows()
        upsertBatch("fush_md_units", "organization_id,code", units.map { unitJson(it, org, userId) }, session)

        val warehouses = db.warehouseDao().allRows()
        upsertBatch("fush_md_warehouses", "organization_id,code", warehouses.map { warehouseJson(it, org, userId) }, session)

        val unitCodeById = units.associate { it.id to it.code }
        val items = db.itemDao().allRows()
        upsertBatch(
            "fush_md_items",
            "organization_id,code",
            items.map { item ->
                val baseUnitCode = unitCodeById[item.baseUnitId]
                    ?: error("Missing base unit for item ${item.code}")
                itemJson(item, baseUnitCode, org, userId)
            },
            session,
        )

        val itemCodeById = items.associate { it.id to it.code }
        val conversions = db.itemUnitConversionDao().allRows()
        upsertBatch(
            "fush_md_item_unit_conversions",
            "organization_id,item_code,unit_code",
            conversions.map { row ->
                val itemCode = itemCodeById[row.itemId] ?: error("Missing item for conversion ${row.id}")
                val unitCode = unitCodeById[row.unitId] ?: error("Missing unit for conversion ${row.id}")
                conversionJson(row, itemCode, unitCode, org, userId)
            },
            session,
        )

        upsertBatch(
            "fush_md_governorates",
            "organization_id,code",
            db.geographyDao().allGovernorates().map { governorateJson(it, org, userId) },
            session,
        )
        val allGovernorates = db.geographyDao().allGovernorates()
        val allDistricts = allGovernorates.flatMap { db.geographyDao().allDistrictsForGovernorate(it.id) }
        upsertBatch(
            "fush_md_districts",
            "organization_id,code",
            allDistricts.map { districtJson(it, org, userId) },
            session,
        )
        val allAreas = allDistricts.flatMap { db.geographyDao().allAreasForDistrict(it.id) }
        upsertBatch(
            "fush_md_areas",
            "organization_id,code",
            allAreas.map { areaJson(it, org, userId) },
            session,
        )

        upsertBatch(
            "fush_md_customers",
            "organization_id,code",
            db.customerDao().allCustomers().map { customerJson(it, org, userId) },
            session,
        )
        upsertBatch(
            "fush_md_suppliers",
            "organization_id,code",
            db.supplierDao().allSuppliers().map { supplierJson(it, org, userId) },
            session,
        )
    }

    private suspend fun syncCurrencies(
        userId: Long,
        role: String,
        firstBaseline: Boolean,
        session: CloudSession,
        remoteRows: List<JSONObject>,
        counters: Counters,
    ) {
        val locals = db.currencyDao().allRows()
        syncScope(
            scope = "currency",
            localUserId = userId,
            firstBaseline = firstBaseline,
            canWrite = true,
            session = session,
            table = "fush_md_currencies",
            conflictColumns = "organization_id,code",
            localRows = locals,
            localKey = { it.code },
            localCanonical = { canonicalCurrency(it) },
            remoteRows = remoteRows,
            remoteKey = { it.getString("code") },
            remoteCanonical = { canonicalCurrency(it) },
            toJson = { currencyJson(it, session.requireOrganizationId(), session.userId) },
            applyRemote = { row ->
                db.currencyDao().upsert(
                    CurrencyEntity(
                        code = row.getString("code"),
                        nameAr = row.optString("name_ar"),
                        nameEn = row.optString("name_en"),
                        symbol = row.optString("symbol"),
                        decimals = row.optInt("decimals", 2),
                        isBase = row.optBoolean("is_base", false),
                        isActive = row.optBoolean("is_active", true),
                    )
                )
            },
            counters = counters,
        )
    }

    private suspend fun syncUnits(
        userId: Long,
        role: String,
        firstBaseline: Boolean,
        session: CloudSession,
        remoteRows: List<JSONObject>,
        counters: Counters,
    ) {
        syncScope(
            scope = "unit",
            localUserId = userId,
            firstBaseline = firstBaseline,
            canWrite = true,
            session = session,
            table = "fush_md_units",
            conflictColumns = "organization_id,code",
            localRows = db.unitDao().allRows(),
            localKey = { it.code },
            localCanonical = { canonicalUnit(it) },
            remoteRows = remoteRows,
            remoteKey = { it.getString("code") },
            remoteCanonical = { canonicalUnit(it) },
            toJson = { unitJson(it, session.requireOrganizationId(), session.userId) },
            applyRemote = { row ->
                val code = row.getString("code")
                val existing = db.unitDao().byCode(code)
                val value = UnitEntity(
                    id = existing?.id ?: 0,
                    code = code,
                    nameAr = row.optString("name_ar"),
                    nameEn = row.optString("name_en"),
                    isActive = row.optBoolean("is_active", true),
                )
                if (existing == null) db.unitDao().insert(value) else db.unitDao().update(value)
            },
            counters = counters,
        )
    }

    private suspend fun syncWarehouses(
        userId: Long,
        role: String,
        firstBaseline: Boolean,
        session: CloudSession,
        remoteRows: List<JSONObject>,
        counters: Counters,
    ) {
        syncScope(
            scope = "warehouse",
            localUserId = userId,
            firstBaseline = firstBaseline,
            canWrite = true,
            session = session,
            table = "fush_md_warehouses",
            conflictColumns = "organization_id,code",
            localRows = db.warehouseDao().allRows(),
            localKey = { it.code },
            localCanonical = { canonicalWarehouse(it) },
            remoteRows = remoteRows,
            remoteKey = { it.getString("code") },
            remoteCanonical = { canonicalWarehouse(it) },
            toJson = { warehouseJson(it, session.requireOrganizationId(), session.userId) },
            applyRemote = { row ->
                val code = row.getString("code")
                val existing = db.warehouseDao().byCode(code)
                val value = WarehouseEntity(
                    id = existing?.id ?: 0,
                    code = code,
                    nameAr = row.optString("name_ar"),
                    nameEn = row.optString("name_en"),
                    location = row.optString("location"),
                    isActive = row.optBoolean("is_active", true),
                )
                if (existing == null) db.warehouseDao().insert(value) else db.warehouseDao().update(value)
            },
            counters = counters,
        )
    }

    private suspend fun syncItems(
        userId: Long,
        role: String,
        firstBaseline: Boolean,
        session: CloudSession,
        remoteRows: List<JSONObject>,
        counters: Counters,
    ) {
        val units = db.unitDao().allRows()
        val unitCodeById = units.associate { it.id to it.code }
        syncScope(
            scope = "item",
            localUserId = userId,
            firstBaseline = firstBaseline,
            canWrite = true,
            session = session,
            table = "fush_md_items",
            conflictColumns = "organization_id,code",
            localRows = db.itemDao().allRows(),
            localKey = { it.code },
            localCanonical = { item ->
                canonicalItem(item, unitCodeById[item.baseUnitId] ?: "")
            },
            remoteRows = remoteRows,
            remoteKey = { it.getString("code") },
            remoteCanonical = { canonicalItem(it) },
            toJson = { item ->
                val baseUnitCode = unitCodeById[item.baseUnitId]
                    ?: error("Missing base unit for item ${item.code}")
                itemJson(item, baseUnitCode, session.requireOrganizationId(), session.userId)
            },
            applyRemote = { row ->
                val code = row.getString("code")
                val baseUnitCode = row.getString("base_unit_code")
                val baseUnit = db.unitDao().byCode(baseUnitCode)
                    ?: error("Cloud item $code references missing unit $baseUnitCode")
                val existing = db.itemDao().byCode(code)
                val value = ItemEntity(
                    id = existing?.id ?: 0,
                    code = code,
                    nameAr = row.optString("name_ar"),
                    nameEn = row.optString("name_en"),
                    category = row.optString("category"),
                    baseUnitId = baseUnit.id,
                    reorderLevel = row.optDouble("reorder_level", 0.0),
                    shelfLifeDays = row.optNullableInt("shelf_life_days"),
                    lotTracked = row.optBoolean("lot_tracked", false),
                    expiryTracked = row.optBoolean("expiry_tracked", false),
                    isActive = row.optBoolean("is_active", true),
                )
                if (existing == null) db.itemDao().insert(value) else db.itemDao().update(value)
            },
            counters = counters,
        )
    }

    private suspend fun syncConversions(
        userId: Long,
        role: String,
        firstBaseline: Boolean,
        session: CloudSession,
        remoteRows: List<JSONObject>,
        counters: Counters,
    ) {
        val items = db.itemDao().allRows()
        val units = db.unitDao().allRows()
        val itemCodeById = items.associate { it.id to it.code }
        val unitCodeById = units.associate { it.id to it.code }
        syncScope(
            scope = "conversion",
            localUserId = userId,
            firstBaseline = firstBaseline,
            canWrite = true,
            session = session,
            table = "fush_md_item_unit_conversions",
            conflictColumns = "organization_id,item_code,unit_code",
            localRows = db.itemUnitConversionDao().allRows(),
            localKey = { row -> "${itemCodeById[row.itemId].orEmpty()}|${unitCodeById[row.unitId].orEmpty()}" },
            localCanonical = { row ->
                canonicalConversion(
                    row,
                    itemCodeById[row.itemId].orEmpty(),
                    unitCodeById[row.unitId].orEmpty(),
                )
            },
            remoteRows = remoteRows,
            remoteKey = { "${it.getString("item_code")}|${it.getString("unit_code")}" },
            remoteCanonical = { canonicalConversion(it) },
            toJson = { row ->
                val itemCode = itemCodeById[row.itemId] ?: error("Missing item for conversion ${row.id}")
                val unitCode = unitCodeById[row.unitId] ?: error("Missing unit for conversion ${row.id}")
                conversionJson(row, itemCode, unitCode, session.requireOrganizationId(), session.userId)
            },
            applyRemote = { row ->
                val item = db.itemDao().byCode(row.getString("item_code"))
                    ?: error("Cloud conversion references missing item ${row.getString("item_code")}")
                val unit = db.unitDao().byCode(row.getString("unit_code"))
                    ?: error("Cloud conversion references missing unit ${row.getString("unit_code")}")
                val existing = db.itemUnitConversionDao().byItemAndUnitAny(item.id, unit.id)
                db.itemUnitConversionDao().upsert(
                    ItemUnitConversionEntity(
                        id = existing?.id ?: 0,
                        itemId = item.id,
                        unitId = unit.id,
                        factorToBase = row.optDouble("factor_to_base", 1.0),
                        allowPurchase = row.optBoolean("allow_purchase", true),
                        allowSale = row.optBoolean("allow_sale", false),
                        barcode = row.optNullableString("barcode"),
                        isActive = row.optBoolean("is_active", true),
                    )
                )
            },
            counters = counters,
        )
    }

    private suspend fun syncGovernorates(
        userId: Long,
        role: String,
        firstBaseline: Boolean,
        session: CloudSession,
        remoteRows: List<JSONObject>,
        counters: Counters,
    ) {
        syncScope(
            scope = "geo_governorate", localUserId = userId, firstBaseline = firstBaseline, canWrite = true,
            session = session, table = "fush_md_governorates", conflictColumns = "organization_id,code",
            localRows = db.geographyDao().allGovernorates(), localKey = { it.code },
            localCanonical = { canonicalGovernorate(it) }, remoteRows = remoteRows,
            remoteKey = { it.getString("code") }, remoteCanonical = { canonicalGovernorate(it) },
            toJson = { governorateJson(it, session.requireOrganizationId(), session.userId) },
            applyRemote = { row ->
                val code = row.getString("code")
                val existing = db.geographyDao().governorateById(row.optString("geo_id", code))
                    ?: db.geographyDao().governorateByCode(code)
                db.geographyDao().upsertGovernorate(
                    GovernorateEntity(
                        id = existing?.id ?: row.optString("geo_id", code),
                        code = code, nameAr = row.optString("name_ar"), nameEn = row.optString("name_en"),
                        sortOrder = row.optInt("sort_order", 0), source = row.optString("source", "CLOUD"),
                        isOfficialSeed = row.optBoolean("is_official_seed", false),
                        isActive = row.optBoolean("is_active", true), updatedBy = existing?.updatedBy,
                        updatedAt = row.optLong("updated_at_ms", System.currentTimeMillis()),
                    )
                )
            },
            counters = counters,
        )
    }

    private suspend fun syncDistricts(
        userId: Long,
        role: String,
        firstBaseline: Boolean,
        session: CloudSession,
        remoteRows: List<JSONObject>,
        counters: Counters,
    ) {
        val locals = db.geographyDao().allGovernorates().flatMap { db.geographyDao().allDistrictsForGovernorate(it.id) }
        syncScope(
            scope = "geo_district", localUserId = userId, firstBaseline = firstBaseline, canWrite = true,
            session = session, table = "fush_md_districts", conflictColumns = "organization_id,code",
            localRows = locals, localKey = { it.code }, localCanonical = { canonicalDistrict(it) },
            remoteRows = remoteRows, remoteKey = { it.getString("code") }, remoteCanonical = { canonicalDistrict(it) },
            toJson = { districtJson(it, session.requireOrganizationId(), session.userId) },
            applyRemote = { row ->
                val governorateCode = row.getString("governorate_code")
                val governorate = requireNotNull(db.geographyDao().governorateByCode(governorateCode)) {
                    "المحافظة السحابية $governorateCode غير موجودة قبل مزامنة المديرية"
                }
                val code = row.getString("code")
                val requestedId = row.optString("geo_id", code)
                val existing = db.geographyDao().districtById(requestedId) ?: db.geographyDao().districtByCode(code)
                if (existing != null) require(existing.governorateId == governorate.id) {
                    "لا يمكن نقل مديرية $code بين المحافظات عبر المزامنة"
                }
                db.geographyDao().upsertDistrict(
                    DistrictEntity(
                        id = existing?.id ?: requestedId, code = code, governorateId = governorate.id,
                        nameAr = row.optString("name_ar"), nameEn = row.optString("name_en"),
                        sortOrder = row.optInt("sort_order", 0), source = row.optString("source", "CLOUD"),
                        isOfficialSeed = row.optBoolean("is_official_seed", false),
                        isActive = row.optBoolean("is_active", true), updatedBy = existing?.updatedBy,
                        updatedAt = row.optLong("updated_at_ms", System.currentTimeMillis()),
                    )
                )
            }, counters = counters,
        )
    }

    private suspend fun syncAreas(
        userId: Long,
        role: String,
        firstBaseline: Boolean,
        session: CloudSession,
        remoteRows: List<JSONObject>,
        counters: Counters,
    ) {
        val districts = db.geographyDao().allGovernorates().flatMap { db.geographyDao().allDistrictsForGovernorate(it.id) }
        val locals = districts.flatMap { db.geographyDao().allAreasForDistrict(it.id) }
        syncScope(
            scope = "geo_area", localUserId = userId, firstBaseline = firstBaseline, canWrite = true,
            session = session, table = "fush_md_areas", conflictColumns = "organization_id,code",
            localRows = locals, localKey = { it.code }, localCanonical = { canonicalArea(it) },
            remoteRows = remoteRows, remoteKey = { it.getString("code") }, remoteCanonical = { canonicalArea(it) },
            toJson = { areaJson(it, session.requireOrganizationId(), session.userId) },
            applyRemote = { row ->
                val districtCode = row.getString("district_code")
                val district = requireNotNull(db.geographyDao().districtByCode(districtCode)) {
                    "المديرية السحابية $districtCode غير موجودة قبل مزامنة المنطقة"
                }
                val code = row.getString("code")
                val requestedId = row.optString("geo_id", code)
                val existing = db.geographyDao().areaById(requestedId) ?: db.geographyDao().areaByCode(code)
                if (existing != null) require(existing.districtId == district.id) {
                    "لا يمكن نقل منطقة $code بين المديريات عبر المزامنة"
                }
                db.geographyDao().upsertArea(
                    AreaEntity(
                        id = existing?.id ?: requestedId, code = code, districtId = district.id,
                        nameAr = row.optString("name_ar"), nameEn = row.optString("name_en"),
                        sortOrder = row.optInt("sort_order", 0), source = row.optString("source", "CLOUD"),
                        isOfficialSeed = row.optBoolean("is_official_seed", false),
                        isActive = row.optBoolean("is_active", true), updatedBy = existing?.updatedBy,
                        updatedAt = row.optLong("updated_at_ms", System.currentTimeMillis()),
                    )
                )
            }, counters = counters,
        )
    }

    private suspend fun syncCustomers(
        userId: Long,
        role: String,
        firstBaseline: Boolean,
        session: CloudSession,
        remoteRows: List<JSONObject>,
        counters: Counters,
    ) {
        syncScope(
            scope = "customer",
            localUserId = userId,
            firstBaseline = firstBaseline,
            canWrite = true,
            session = session,
            table = "fush_md_customers",
            conflictColumns = "organization_id,code",
            localRows = db.customerDao().allCustomers(),
            localKey = { it.code },
            localCanonical = { canonicalCustomer(it) },
            remoteRows = remoteRows,
            remoteKey = { it.getString("code") },
            remoteCanonical = { canonicalCustomer(it) },
            toJson = { customerJson(it, session.requireOrganizationId(), session.userId) },
            applyRemote = { row ->
                val code = row.getString("code")
                val existing = db.customerDao().byCode(code)
                val value = CustomerEntity(
                    id = existing?.id ?: 0,
                    code = code,
                    nameAr = row.optString("name_ar"),
                    nameEn = row.optString("name_en"),
                    phone = row.optString("phone"),
                    address = row.optString("address"),
                    province = row.optString("province"),
                    governorateId = row.optNullableString("governorate_id")
                        ?: db.geographyDao().governorateIdForAlias(YemenGeographyHierarchy.normalizeAlias(row.optString("province"))),
                    districtId = row.optNullableString("district_id"),
                    areaId = row.optNullableString("area_id"),
                    channel = row.optString("channel", "RETAIL"),
                    classification = row.optString("classification", "C"),
                    currencyCode = row.optString("currency_code", "YER_NEW"),
                    creditLimitBase = row.optDouble("credit_limit_base", 0.0),
                    creditDays = row.optInt("credit_days", 0),
                    allowCredit = row.optBoolean("allow_credit", false),
                    salesRepName = row.optString("sales_rep_name"),
                    salesRepId = existing?.salesRepId,
                    isActive = row.optBoolean("is_active", true),
                    createdAt = row.optLong("created_at_ms", existing?.createdAt ?: System.currentTimeMillis()),
                )
                if (existing == null) db.customerDao().insert(value) else db.customerDao().update(value)
            },
            counters = counters,
        )
    }

    private suspend fun syncSuppliers(
        userId: Long,
        role: String,
        firstBaseline: Boolean,
        session: CloudSession,
        remoteRows: List<JSONObject>,
        counters: Counters,
    ) {
        syncScope(
            scope = "supplier",
            localUserId = userId,
            firstBaseline = firstBaseline,
            canWrite = true,
            session = session,
            table = "fush_md_suppliers",
            conflictColumns = "organization_id,code",
            localRows = db.supplierDao().allSuppliers(),
            localKey = { it.code },
            localCanonical = { canonicalSupplier(it) },
            remoteRows = remoteRows,
            remoteKey = { it.getString("code") },
            remoteCanonical = { canonicalSupplier(it) },
            toJson = { supplierJson(it, session.requireOrganizationId(), session.userId) },
            applyRemote = { row ->
                val code = row.getString("code")
                val existing = db.supplierDao().byCode(code)
                val value = SupplierEntity(
                    id = existing?.id ?: 0,
                    code = code,
                    nameAr = row.optString("name_ar"),
                    nameEn = row.optString("name_en"),
                    phone = row.optString("phone"),
                    address = row.optString("address"),
                    currencyCode = row.optString("currency_code", "YER_NEW"),
                    paymentTermsDays = row.optInt("payment_terms_days", 0),
                    isActive = row.optBoolean("is_active", true),
                    createdAt = row.optLong("created_at_ms", existing?.createdAt ?: System.currentTimeMillis()),
                )
                if (existing == null) db.supplierDao().insert(value) else db.supplierDao().update(value)
            },
            counters = counters,
        )
    }

    private suspend fun <T> syncScope(
        scope: String,
        localUserId: Long,
        firstBaseline: Boolean,
        canWrite: Boolean,
        session: CloudSession,
        table: String,
        conflictColumns: String,
        localRows: List<T>,
        localKey: (T) -> String,
        localCanonical: (T) -> String,
        remoteRows: List<JSONObject>,
        remoteKey: (JSONObject) -> String,
        remoteCanonical: (JSONObject) -> String,
        toJson: (T) -> JSONObject,
        applyRemote: suspend (JSONObject) -> Unit,
        counters: Counters,
    ) {
        val localByKey = localRows.associateBy(localKey)
        val remoteByKey = remoteRows.associateBy(remoteKey)

        if (firstBaseline) {
            for ((key, remote) in remoteByKey) {
                val remoteHash = digest(remoteCanonical(remote))
                val local = localByKey[key]
                if (local == null || digest(localCanonical(local)) != remoteHash) {
                    applyRemote(remote)
                    counters.downloaded++
                } else {
                    counters.unchanged++
                }
                store.saveRowHash(localUserId, scope, key, remoteHash)
            }
            // Local-only rows on a pre-existing employee phone are quarantined on the
            // first baseline. Saving their hash means an unchanged stale row will keep
            // being skipped later; a genuine edit after the baseline changes the hash
            // and can then be uploaded if this role is allowed to write the scope.
            for ((key, local) in localByKey) {
                if (key !in remoteByKey) {
                    store.saveRowHash(localUserId, scope, key, digest(localCanonical(local)))
                    counters.skippedBaseline++
                }
            }
            return
        }

        val uploads = mutableListOf<Pair<String, T>>()
        val allKeys = LinkedHashSet<String>().apply {
            addAll(localByKey.keys)
            addAll(remoteByKey.keys)
        }

        for (key in allKeys) {
            val local = localByKey[key]
            val remote = remoteByKey[key]
            when {
                local != null && remote != null -> {
                    val localHash = digest(localCanonical(local))
                    val remoteHash = digest(remoteCanonical(remote))
                    val previous = store.rowHash(localUserId, scope, key)
                    when {
                        localHash == remoteHash -> {
                            store.saveRowHash(localUserId, scope, key, localHash)
                            counters.unchanged++
                        }
                        previous == null -> {
                            counters.conflicts++
                        }
                        localHash == previous -> {
                            applyRemote(remote)
                            store.saveRowHash(localUserId, scope, key, remoteHash)
                            counters.downloaded++
                        }
                        remoteHash == previous -> {
                            if (canWrite) uploads += key to local else counters.skippedUnauthorized++
                        }
                        else -> counters.conflicts++
                    }
                }
                local != null -> {
                    val localHash = digest(localCanonical(local))
                    val previous = store.rowHash(localUserId, scope, key)
                    when {
                        // Quarantined local-only data from the first cloud baseline must
                        // not leak into the company dataset on the next periodic sync.
                        previous != null && localHash == previous -> counters.skippedBaseline++
                        canWrite -> uploads += key to local
                        else -> counters.skippedUnauthorized++
                    }
                }
                remote != null -> {
                    applyRemote(remote)
                    store.saveRowHash(localUserId, scope, key, digest(remoteCanonical(remote)))
                    counters.downloaded++
                }
            }
        }

        if (uploads.isNotEmpty()) {
            upsertBatch(table, conflictColumns, uploads.map { toJson(it.second) }, session)
            uploads.forEach { (key, row) ->
                store.saveRowHash(localUserId, scope, key, digest(localCanonical(row)))
                counters.uploaded++
            }
        }
    }

    private fun fetchAll(session: CloudSession): RemoteMasterData = RemoteMasterData(
        currencies = fetchRows("fush_md_currencies", session),
        units = fetchRows("fush_md_units", session),
        warehouses = fetchRows("fush_md_warehouses", session),
        items = fetchRows("fush_md_items", session),
        conversions = fetchRows("fush_md_item_unit_conversions", session),
        governorates = fetchRows("fush_md_governorates", session),
        districts = fetchRows("fush_md_districts", session),
        areas = fetchRows("fush_md_areas", session),
        customers = fetchRows("fush_md_customers", session),
        suppliers = fetchRows("fush_md_suppliers", session),
    )

    private fun fetchRows(table: String, session: CloudSession): List<JSONObject> {
        val org = encode(session.requireOrganizationId())
        val path = "/rest/v1/$table?select=*&organization_id=eq.$org&limit=5000"
        return when (val response = request("GET", path, null, session.accessToken, null)) {
            is HttpResult.Error -> {
                if (response.code == 404) {
                    error(context.getString(R.string.cloud_master_data_schema_missing))
                }
                error(apiErrorMessage(response))
            }
            is HttpResult.Ok -> {
                val array = runCatching { JSONArray(response.body) }.getOrElse {
                    error(context.getString(R.string.cloud_master_data_invalid_response))
                }
                List(array.length()) { index -> array.getJSONObject(index) }
            }
        }
    }

    private fun upsertBatch(
        table: String,
        conflictColumns: String,
        rows: List<JSONObject>,
        session: CloudSession,
    ) {
        if (rows.isEmpty()) return
        rows.chunked(200).forEach { chunk ->
            val payload = JSONArray().apply { chunk.forEach { put(it) } }.toString()
            val path = "/rest/v1/$table?on_conflict=${encodeConflictColumns(conflictColumns)}"
            when (val response = request(
                method = "POST",
                path = path,
                body = payload,
                bearerToken = session.accessToken,
                prefer = "resolution=merge-duplicates,return=minimal",
            )) {
                is HttpResult.Ok -> Unit
                is HttpResult.Error -> error(apiErrorMessage(response))
            }
        }
    }

    private fun currencyJson(row: CurrencyEntity, org: String, userId: String) = JSONObject()
        .put("organization_id", org).put("code", row.code)
        .put("name_ar", row.nameAr).put("name_en", row.nameEn).put("symbol", row.symbol)
        .put("decimals", row.decimals).put("is_base", row.isBase).put("is_active", row.isActive)
        .put("updated_by", userId)

    private fun unitJson(row: UnitEntity, org: String, userId: String) = JSONObject()
        .put("organization_id", org).put("code", row.code)
        .put("name_ar", row.nameAr).put("name_en", row.nameEn).put("is_active", row.isActive)
        .put("updated_by", userId)

    private fun warehouseJson(row: WarehouseEntity, org: String, userId: String) = JSONObject()
        .put("organization_id", org).put("code", row.code)
        .put("name_ar", row.nameAr).put("name_en", row.nameEn).put("location", row.location)
        .put("is_active", row.isActive).put("updated_by", userId)

    private fun itemJson(row: ItemEntity, baseUnitCode: String, org: String, userId: String) = JSONObject()
        .put("organization_id", org).put("code", row.code)
        .put("name_ar", row.nameAr).put("name_en", row.nameEn).put("category", row.category)
        .put("base_unit_code", baseUnitCode).put("reorder_level", row.reorderLevel)
        .put("shelf_life_days", row.shelfLifeDays ?: JSONObject.NULL)
        .put("lot_tracked", row.lotTracked).put("expiry_tracked", row.expiryTracked)
        .put("is_active", row.isActive).put("updated_by", userId)

    private fun conversionJson(
        row: ItemUnitConversionEntity,
        itemCode: String,
        unitCode: String,
        org: String,
        userId: String,
    ) = JSONObject()
        .put("organization_id", org).put("item_code", itemCode).put("unit_code", unitCode)
        .put("factor_to_base", row.factorToBase).put("allow_purchase", row.allowPurchase)
        .put("allow_sale", row.allowSale).put("barcode", row.barcode ?: JSONObject.NULL)
        .put("is_active", row.isActive).put("updated_by", userId)

    private fun governorateJson(row: GovernorateEntity, org: String, userId: String) = JSONObject()
        .put("organization_id", org).put("code", row.code).put("geo_id", row.id)
        .put("name_ar", row.nameAr).put("name_en", row.nameEn).put("sort_order", row.sortOrder)
        .put("source", row.source).put("is_official_seed", row.isOfficialSeed).put("is_active", row.isActive)
        .put("updated_at_ms", row.updatedAt).put("updated_by", userId)

    private fun districtJson(row: DistrictEntity, org: String, userId: String) = JSONObject()
        .put("organization_id", org).put("code", row.code).put("geo_id", row.id)
        .put("governorate_code", row.governorateId).put("name_ar", row.nameAr).put("name_en", row.nameEn)
        .put("sort_order", row.sortOrder).put("source", row.source).put("is_official_seed", row.isOfficialSeed)
        .put("is_active", row.isActive).put("updated_at_ms", row.updatedAt).put("updated_by", userId)

    private fun areaJson(row: AreaEntity, org: String, userId: String) = JSONObject()
        .put("organization_id", org).put("code", row.code).put("geo_id", row.id)
        .put("district_code", row.districtId).put("name_ar", row.nameAr).put("name_en", row.nameEn)
        .put("sort_order", row.sortOrder).put("source", row.source).put("is_official_seed", row.isOfficialSeed)
        .put("is_active", row.isActive).put("updated_at_ms", row.updatedAt).put("updated_by", userId)

    private fun customerJson(row: CustomerEntity, org: String, userId: String) = JSONObject()
        .put("organization_id", org).put("code", row.code).put("name_ar", row.nameAr)
        .put("name_en", row.nameEn).put("phone", row.phone).put("address", row.address)
        .put("province", row.province)
        .put("governorate_id", row.governorateId ?: JSONObject.NULL)
        .put("district_id", row.districtId ?: JSONObject.NULL)
        .put("area_id", row.areaId ?: JSONObject.NULL)
        .put("channel", row.channel).put("classification", row.classification)
        .put("currency_code", row.currencyCode).put("credit_limit_base", row.creditLimitBase)
        .put("credit_days", row.creditDays).put("allow_credit", row.allowCredit)
        .put("sales_rep_name", row.salesRepName).put("is_active", row.isActive)
        .put("created_at_ms", row.createdAt).put("updated_by", userId)

    private fun supplierJson(row: SupplierEntity, org: String, userId: String) = JSONObject()
        .put("organization_id", org).put("code", row.code).put("name_ar", row.nameAr)
        .put("name_en", row.nameEn).put("phone", row.phone).put("address", row.address)
        .put("currency_code", row.currencyCode).put("payment_terms_days", row.paymentTermsDays)
        .put("is_active", row.isActive).put("created_at_ms", row.createdAt).put("updated_by", userId)

    private fun canonicalCurrency(row: CurrencyEntity) = listOf(
        row.code, row.nameAr, row.nameEn, row.symbol, row.decimals, row.isBase, row.isActive
    ).joinToString(SEP)

    private fun canonicalCurrency(row: JSONObject) = listOf(
        row.optString("code"), row.optString("name_ar"), row.optString("name_en"), row.optString("symbol"),
        row.optInt("decimals", 2), row.optBoolean("is_base", false), row.optBoolean("is_active", true)
    ).joinToString(SEP)

    private fun canonicalUnit(row: UnitEntity) = listOf(row.code, row.nameAr, row.nameEn, row.isActive).joinToString(SEP)
    private fun canonicalUnit(row: JSONObject) = listOf(
        row.optString("code"), row.optString("name_ar"), row.optString("name_en"), row.optBoolean("is_active", true)
    ).joinToString(SEP)

    private fun canonicalWarehouse(row: WarehouseEntity) = listOf(
        row.code, row.nameAr, row.nameEn, row.location, row.isActive
    ).joinToString(SEP)
    private fun canonicalWarehouse(row: JSONObject) = listOf(
        row.optString("code"), row.optString("name_ar"), row.optString("name_en"), row.optString("location"),
        row.optBoolean("is_active", true)
    ).joinToString(SEP)

    private fun canonicalItem(row: ItemEntity, baseUnitCode: String) = listOf(
        row.code, row.nameAr, row.nameEn, row.category, baseUnitCode, row.reorderLevel,
        row.shelfLifeDays ?: "", row.lotTracked, row.expiryTracked, row.isActive
    ).joinToString(SEP)
    private fun canonicalItem(row: JSONObject) = listOf(
        row.optString("code"), row.optString("name_ar"), row.optString("name_en"), row.optString("category"),
        row.optString("base_unit_code"), row.optDouble("reorder_level", 0.0),
        row.optNullableInt("shelf_life_days") ?: "", row.optBoolean("lot_tracked", false),
        row.optBoolean("expiry_tracked", false), row.optBoolean("is_active", true)
    ).joinToString(SEP)

    private fun canonicalConversion(row: ItemUnitConversionEntity, itemCode: String, unitCode: String) = listOf(
        itemCode, unitCode, row.factorToBase, row.allowPurchase, row.allowSale, row.barcode ?: "", row.isActive
    ).joinToString(SEP)
    private fun canonicalConversion(row: JSONObject) = listOf(
        row.optString("item_code"), row.optString("unit_code"), row.optDouble("factor_to_base", 1.0),
        row.optBoolean("allow_purchase", true), row.optBoolean("allow_sale", false),
        row.optNullableString("barcode") ?: "", row.optBoolean("is_active", true)
    ).joinToString(SEP)

    private fun canonicalGovernorate(row: GovernorateEntity) = listOf(
        row.code, row.id, row.nameAr, row.nameEn, row.sortOrder, row.source, row.isOfficialSeed, row.isActive
    ).joinToString(SEP)
    private fun canonicalGovernorate(row: JSONObject) = listOf(
        row.optString("code"), row.optString("geo_id", row.optString("code")), row.optString("name_ar"), row.optString("name_en"),
        row.optInt("sort_order", 0), row.optString("source"), row.optBoolean("is_official_seed", false), row.optBoolean("is_active", true)
    ).joinToString(SEP)

    private fun canonicalDistrict(row: DistrictEntity) = listOf(
        row.code, row.id, row.governorateId, row.nameAr, row.nameEn, row.sortOrder, row.source, row.isOfficialSeed, row.isActive
    ).joinToString(SEP)
    private fun canonicalDistrict(row: JSONObject) = listOf(
        row.optString("code"), row.optString("geo_id", row.optString("code")), row.optString("governorate_code"),
        row.optString("name_ar"), row.optString("name_en"), row.optInt("sort_order", 0), row.optString("source"),
        row.optBoolean("is_official_seed", false), row.optBoolean("is_active", true)
    ).joinToString(SEP)

    private fun canonicalArea(row: AreaEntity) = listOf(
        row.code, row.id, row.districtId, row.nameAr, row.nameEn, row.sortOrder, row.source, row.isOfficialSeed, row.isActive
    ).joinToString(SEP)
    private fun canonicalArea(row: JSONObject) = listOf(
        row.optString("code"), row.optString("geo_id", row.optString("code")), row.optString("district_code"),
        row.optString("name_ar"), row.optString("name_en"), row.optInt("sort_order", 0), row.optString("source"),
        row.optBoolean("is_official_seed", false), row.optBoolean("is_active", true)
    ).joinToString(SEP)

    private fun canonicalCustomer(row: CustomerEntity) = listOf(
        row.code, row.nameAr, row.nameEn, row.phone, row.address, row.province,
        row.governorateId ?: "", row.districtId ?: "", row.areaId ?: "", row.channel,
        row.classification, row.currencyCode, row.creditLimitBase, row.creditDays, row.allowCredit,
        row.salesRepName, row.isActive, row.createdAt
    ).joinToString(SEP)
    private fun canonicalCustomer(row: JSONObject) = listOf(
        row.optString("code"), row.optString("name_ar"), row.optString("name_en"), row.optString("phone"),
        row.optString("address"), row.optString("province"),
        row.optNullableString("governorate_id") ?: "", row.optNullableString("district_id") ?: "", row.optNullableString("area_id") ?: "",
        row.optString("channel", "RETAIL"), row.optString("classification", "C"), row.optString("currency_code", "YER_NEW"),
        row.optDouble("credit_limit_base", 0.0), row.optInt("credit_days", 0), row.optBoolean("allow_credit", false),
        row.optString("sales_rep_name"), row.optBoolean("is_active", true), row.optLong("created_at_ms", 0L)
    ).joinToString(SEP)

    private fun canonicalSupplier(row: SupplierEntity) = listOf(
        row.code, row.nameAr, row.nameEn, row.phone, row.address, row.currencyCode,
        row.paymentTermsDays, row.isActive, row.createdAt
    ).joinToString(SEP)
    private fun canonicalSupplier(row: JSONObject) = listOf(
        row.optString("code"), row.optString("name_ar"), row.optString("name_en"), row.optString("phone"),
        row.optString("address"), row.optString("currency_code", "YER_NEW"),
        row.optInt("payment_terms_days", 0), row.optBoolean("is_active", true), row.optLong("created_at_ms", 0L)
    ).joinToString(SEP)

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun request(
        method: String,
        path: String,
        body: String?,
        bearerToken: String,
        prefer: String?,
    ): HttpResult {
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        val connection = URL(base + path).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 25_000
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            connection.setRequestProperty("Authorization", "Bearer $bearerToken")
            connection.setRequestProperty("Accept", "application/json")
            prefer?.let { connection.setRequestProperty("Prefer", it) }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code in 200..299) HttpResult.Ok(code, responseBody) else HttpResult.Error(code, responseBody)
        } catch (t: Throwable) {
            HttpResult.Error(null, t.message ?: t.javaClass.simpleName)
        } finally {
            connection.disconnect()
        }
    }

    private fun apiErrorMessage(error: HttpResult.Error): String {
        if (error.code == null) return context.getString(R.string.cloud_error_network_data)
        val json = runCatching { JSONObject(error.body) }.getOrNull()
        val detail = json?.optString("message")?.takeIf { it.isNotBlank() }
            ?: json?.optString("details")?.takeIf { it.isNotBlank() }
            ?: json?.optString("hint")?.takeIf { it.isNotBlank() }
        val base = context.getString(R.string.cloud_error_data_http, error.code)
        return detail?.let { "$base $it" } ?: base
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
    private fun encodeConflictColumns(value: String): String = value.split(',').joinToString(",") { encode(it.trim()) }

    private fun JSONObject.optNullableInt(name: String): Int? =
        if (!has(name) || isNull(name)) null else optInt(name)

    private fun JSONObject.optNullableString(name: String): String? =
        if (!has(name) || isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

    private data class RemoteMasterData(
        val currencies: List<JSONObject>,
        val units: List<JSONObject>,
        val warehouses: List<JSONObject>,
        val items: List<JSONObject>,
        val conversions: List<JSONObject>,
        val governorates: List<JSONObject>,
        val districts: List<JSONObject>,
        val areas: List<JSONObject>,
        val customers: List<JSONObject>,
        val suppliers: List<JSONObject>,
    ) {
        fun totalRows(): Int = currencies.size + units.size + warehouses.size + items.size +
            conversions.size + governorates.size + districts.size + areas.size + customers.size + suppliers.size
    }

    private data class Counters(
        var uploaded: Int = 0,
        var downloaded: Int = 0,
        var unchanged: Int = 0,
        var conflicts: Int = 0,
        var skippedBaseline: Int = 0,
        var skippedUnauthorized: Int = 0,
    )

    private sealed interface HttpResult {
        data class Ok(val code: Int, val body: String) : HttpResult
        data class Error(val code: Int?, val body: String) : HttpResult
    }

    private companion object {
        const val SEP = "\u001f"
    }
}
