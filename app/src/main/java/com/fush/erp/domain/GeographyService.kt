package com.fush.erp.domain

import androidx.room.withTransaction
import com.fush.erp.data.FushDatabase
import com.fush.erp.data.YemenGeographyHierarchy
import java.util.UUID
import com.fush.erp.data.entity.*

class GeographyService(private val db: FushDatabase) {

    data class ResolvedLocation(
        val governorate: GovernorateEntity,
        val district: DistrictEntity? = null,
        val area: AreaEntity? = null
    )

    suspend fun resolveLocation(
        governorateId: String?,
        districtId: String? = null,
        areaId: String? = null,
        legacyGovernorateText: String = ""
    ): ResolvedLocation {
        val resolvedGovernorateId = governorateId?.takeIf { it.isNotBlank() }
            ?: db.geographyDao().governorateIdForAlias(YemenGeographyHierarchy.normalizeAlias(legacyGovernorateText))
            ?: when {
                YemenGeographyHierarchy.normalizeAlias(legacyGovernorateText).startsWith("تعز") -> "YE15"
                YemenGeographyHierarchy.normalizeAlias(legacyGovernorateText) in setOf("الحوبان", "tauz") -> "YE15"
                else -> null
            }
        val governorate = requireNotNull(resolvedGovernorateId?.let { db.geographyDao().governorateById(it) }) {
            "اختر المحافظة من القائمة الرسمية"
        }
        require(governorate.isActive) { "المحافظة المحددة غير نشطة" }
        val district = districtId?.takeIf { it.isNotBlank() }?.let {
            requireNotNull(db.geographyDao().districtById(it)) { "المديرية المحددة غير موجودة" }.also { d ->
                require(d.isActive) { "المديرية المحددة غير نشطة" }
                require(d.governorateId == governorate.id) { "المديرية لا تتبع المحافظة المحددة" }
            }
        }
        val area = areaId?.takeIf { it.isNotBlank() }?.let {
            requireNotNull(db.geographyDao().areaById(it)) { "المنطقة المحددة غير موجودة" }.also { a ->
                require(a.isActive) { "المنطقة المحددة غير نشطة" }
                val d = requireNotNull(district) { "اختر المديرية قبل المنطقة" }
                require(a.districtId == d.id) { "المنطقة لا تتبع المديرية المحددة" }
            }
        }
        return ResolvedLocation(governorate, district, area)
    }

    suspend fun saveGovernorate(
        id: String? = null, nameAr: String, nameEn: String = "", active: Boolean = true, userId: Long
    ): GovernorateEntity = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.GEOGRAPHY_MANAGE)
        require(nameAr.trim().isNotBlank()) { "اسم المحافظة مطلوب" }
        val old = id?.let { db.geographyDao().governorateById(it) }
        val stableId = old?.id ?: "USR-GOV-${UUID.randomUUID()}"
        val row = GovernorateEntity(
            id = stableId, code = old?.code ?: stableId, nameAr = nameAr.trim(), nameEn = nameEn.trim(),
            sortOrder = old?.sortOrder ?: 9999, source = old?.source ?: "USER", isOfficialSeed = old?.isOfficialSeed ?: false,
            isActive = active, updatedBy = userId
        )
        db.geographyDao().upsertGovernorate(row)
        db.geographyDao().upsertGovernorateAliases(listOf(
            GovernorateAliasEntity(YemenGeographyHierarchy.normalizeAlias(row.nameAr), row.id, row.nameAr, "USER"),
            GovernorateAliasEntity(YemenGeographyHierarchy.normalizeAlias(row.nameEn), row.id, row.nameEn, "USER")
        ).filter { it.normalizedAlias.isNotBlank() })
        row
    }

    suspend fun saveDistrict(
        id: String? = null, governorateId: String, nameAr: String, nameEn: String = "", active: Boolean = true, userId: Long
    ): DistrictEntity = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.GEOGRAPHY_MANAGE)
        val gov = requireNotNull(db.geographyDao().governorateById(governorateId)) { "المحافظة غير موجودة" }
        require(gov.isActive) { "المحافظة غير نشطة" }
        require(nameAr.trim().isNotBlank()) { "اسم المديرية مطلوب" }
        val old = id?.let { db.geographyDao().districtById(it) }
        if (old != null) require(old.governorateId == governorateId) { "لا يمكن نقل مديرية مستخدمة إلى محافظة أخرى؛ أنشئ مديرية جديدة" }
        val stableId = old?.id ?: "USR-DST-${UUID.randomUUID()}"
        val row = DistrictEntity(
            id = stableId, code = old?.code ?: stableId, governorateId = governorateId,
            nameAr = nameAr.trim(), nameEn = nameEn.trim(), sortOrder = old?.sortOrder ?: 9999,
            source = old?.source ?: "USER", isOfficialSeed = old?.isOfficialSeed ?: false,
            isActive = active, updatedBy = userId
        )
        db.geographyDao().upsertDistrict(row)
        row
    }

    suspend fun saveArea(
        id: String? = null, districtId: String, nameAr: String, nameEn: String = "", active: Boolean = true, userId: Long
    ): AreaEntity = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.GEOGRAPHY_MANAGE)
        val district = requireNotNull(db.geographyDao().districtById(districtId)) { "المديرية غير موجودة" }
        require(district.isActive) { "المديرية غير نشطة" }
        require(nameAr.trim().isNotBlank()) { "اسم المنطقة مطلوب" }
        val old = id?.let { db.geographyDao().areaById(it) }
        if (old != null) require(old.districtId == districtId) { "لا يمكن نقل منطقة مستخدمة إلى مديرية أخرى؛ أنشئ منطقة جديدة" }
        val stableId = old?.id ?: "USR-AREA-${UUID.randomUUID()}"
        val row = AreaEntity(
            id = stableId, code = old?.code ?: stableId, districtId = districtId,
            nameAr = nameAr.trim(), nameEn = nameEn.trim(), sortOrder = old?.sortOrder ?: 9999,
            source = old?.source ?: "USER", isOfficialSeed = old?.isOfficialSeed ?: false,
            isActive = active, updatedBy = userId
        )
        db.geographyDao().upsertArea(row)
        row
    }

    suspend fun recordFxSnapshot(
        effectiveAt: Long,
        usdNewYer: Double,
        usdOldYer: Double,
        sourceNote: String,
        createdBy: Long,
        sarNewYer: Double? = null,
        sarOldYer: Double? = null,
        approvedRateType: String = "SELL"
    ): Long = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.GEOGRAPHY_MANAGE)
        require((sarNewYer == null) == (sarOldYer == null)) { "أدخل سعري SAR لعدن وصنعاء معًا أو اتركهما معًا" }
        sarNewYer?.let { require(it.isFinite() && it > 0.0) { "سعر SAR عدن غير صالح" } }
        sarOldYer?.let { require(it.isFinite() && it > 0.0) { "سعر SAR صنعاء غير صالح" } }
        val type = approvedRateType.trim().uppercase()
        require(type in setOf("BUY", "SELL")) { "نوع سعر الصرف غير صالح" }
        val oldToNew = GeographyMath.oldYerToNewYerRate(usdNewYer, usdOldYer)
        val id = db.geographyDao().insertFxSnapshot(
            FxSnapshotEntity(
                effectiveAt = effectiveAt,
                usdNewYer = usdNewYer,
                usdOldYer = usdOldYer,
                oldYerToNewYer = oldToNew,
                sourceNote = sourceNote.trim(),
                createdBy = createdBy,
                sarNewYer = sarNewYer,
                sarOldYer = sarOldYer,
                approvedRateType = type,
                primarySource = "MANUAL"
            )
        )
        db.currencyDao().upsertRate(ExchangeRateEntity("USD", effectiveAt, usdNewYer, "${sourceNote.trim()} — USD/New YER"))
        if (sarNewYer != null) db.currencyDao().upsertRate(ExchangeRateEntity("SAR", effectiveAt, sarNewYer, "${sourceNote.trim()} — SAR/New YER"))
        db.currencyDao().upsertRate(ExchangeRateEntity("YER_OLD", effectiveAt, oldToNew, "${sourceNote.trim()} — Old YER/New YER derived"))
        id
    }


    suspend fun upsertProvincePolicy(
        code: String,
        nameAr: String,
        currencyCode: String,
        defaultTransportPerCartonBase: Double,
        requiresDailyFx: Boolean,
        requiresActualTransport: Boolean,
        requiresFeesAndCustoms: Boolean,
        notes: String,
        userId: Long
    ) = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.GEOGRAPHY_MANAGE)
        val normalizedCode = code.trim().uppercase()
        require(normalizedCode.matches(Regex("[A-Z0-9_-]{2,24}"))) { "رمز المحافظة يجب أن يكون 2–24 حرفًا/رقمًا إنجليزيًا" }
        require(nameAr.trim().isNotBlank()) { "اسم المحافظة مطلوب" }
        require(defaultTransportPerCartonBase >= 0.0 && defaultTransportPerCartonBase.isFinite()) { "النقل الافتراضي غير صالح" }
        require(db.currencyDao().allActive().any { it.code == currencyCode }) { "العملة غير موجودة" }
        val old = db.geographyDao().provincePolicy(normalizedCode)
        val row = ProvincePolicyEntity(
            code = normalizedCode,
            nameAr = nameAr.trim(),
            currencyCode = currencyCode,
            defaultTransportPerCartonBase = defaultTransportPerCartonBase,
            requiresDailyFx = requiresDailyFx,
            requiresActualTransport = requiresActualTransport,
            requiresFeesAndCustoms = requiresFeesAndCustoms,
            notes = notes.trim(),
            isActive = true
        )
        db.geographyDao().upsertProvincePolicies(listOf(row))
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = userId,
                action = if (old == null) "CREATE" else "UPDATE",
                entityType = "PROVINCE_POLICY",
                entityId = normalizedCode,
                oldValue = old?.let { "${it.nameAr}|${it.currencyCode}|${it.defaultTransportPerCartonBase}|${it.requiresDailyFx}|${it.requiresActualTransport}|${it.requiresFeesAndCustoms}" } ?: "",
                newValue = "${row.nameAr}|${row.currencyCode}|${row.defaultTransportPerCartonBase}|${row.requiresDailyFx}|${row.requiresActualTransport}|${row.requiresFeesAndCustoms}",
                reason = "إعداد سياسة محافظة من واجهة المستخدم"
            )
        )
    }

    suspend fun historicalRate(currencyCode: String, at: Long): Double {
        if (currencyCode == "YER_NEW") return 1.0
        return requireNotNull(db.currencyDao().latestRateAt(currencyCode, at)) {
            "لا يوجد سعر صرف تاريخي للعملة $currencyCode في هذا التاريخ"
        }.rateToBase
    }

    suspend fun setProvincePrice(
        itemId: Long,
        channel: String,
        province: String,
        currencyCode: String,
        baseUnitPriceOriginal: Double,
        effectiveFrom: Long,
        effectiveTo: Long?,
        isActive: Boolean,
        note: String,
        userId: Long
    ): Long = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.GEOGRAPHY_MANAGE)
        require(channel in setOf("DIRECT", "RETAIL", "DISTRIBUTOR_CASH", "DISTRIBUTOR_CREDIT")) { "قناة البيع غير صالحة" }
        val normalizedProvince = province.trim()
        require(normalizedProvince.isNotBlank()) { "المحافظة مطلوبة" }
        require(baseUnitPriceOriginal > 0.0 && baseUnitPriceOriginal.isFinite()) { "سعر البيع غير صالح" }
        SalesMath.validatePricePeriod(effectiveFrom, effectiveTo)
        require(db.itemDao().byId(itemId)?.category == "FINISHED_GOOD") { "الصنف النهائي غير موجود" }
        require(db.currencyDao().allActive().any { it.code == currencyCode }) { "العملة غير موجودة" }

        if (isActive) {
            val previous = db.salesDao().latestActivePriceBefore(itemId, channel, normalizedProvince, currencyCode, effectiveFrom)
            if (previous != null && previous.effectiveTo == null) {
                db.salesDao().updatePrice(previous.copy(effectiveTo = effectiveFrom - 1L))
            }
            require(
                db.salesDao().overlappingActivePriceCount(
                    itemId = itemId,
                    channel = channel,
                    province = normalizedProvince,
                    currencyCode = currencyCode,
                    effectiveFrom = effectiveFrom,
                    effectiveTo = effectiveTo,
                    excludeId = -1L
                ) == 0
            ) { "توجد قائمة أسعار فعالة تتداخل مع هذه الفترة لنفس المحافظة والقناة والعملة" }
        }

        db.salesDao().insertPrice(
            SalesPriceEntity(
                itemId = itemId,
                channel = channel,
                province = normalizedProvince,
                currencyCode = currencyCode,
                baseUnitPriceOriginal = baseUnitPriceOriginal,
                effectiveFrom = effectiveFrom,
                effectiveTo = effectiveTo,
                isActive = isActive,
                note = note.trim()
            )
        )
    }

    suspend fun setProvincePriceActive(priceId: Long, active: Boolean, userId: Long) = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.GEOGRAPHY_MANAGE)
        val old = requireNotNull(db.salesDao().priceById(priceId)) { "قائمة السعر غير موجودة" }
        if (active) {
            SalesMath.validatePricePeriod(old.effectiveFrom, old.effectiveTo)
            require(
                db.salesDao().overlappingActivePriceCount(
                    itemId = old.itemId,
                    channel = old.channel,
                    province = old.province,
                    currencyCode = old.currencyCode,
                    effectiveFrom = old.effectiveFrom,
                    effectiveTo = old.effectiveTo,
                    excludeId = old.id
                ) == 0
            ) { "لا يمكن تفعيل القائمة لأنها تتداخل مع قائمة أسعار فعالة أخرى" }
        }
        db.salesDao().updatePrice(old.copy(isActive = active))
    }

    suspend fun calculateQuote(
        provinceCode: String,
        cartons: Double,
        productAmountNewBase: Double,
        transportOverrideOriginal: Double?,
        feesOriginal: Double,
        riskMarginOriginal: Double,
        at: Long
    ): GeographicQuoteResult {
        val policy = requireNotNull(db.geographyDao().provincePolicy(provinceCode)) { "سياسة المحافظة غير موجودة" }
        val snapshot = if (policy.requiresDailyFx || policy.currencyCode == "YER_OLD") {
            requireNotNull(db.geographyDao().latestFxSnapshotAt(at)) { "سجل سعر صرف يومي أولاً" }
        } else null
        return GeographyMath.quote(
            policy = policy,
            cartons = cartons,
            productAmountNewBase = productAmountNewBase,
            usdNewYer = snapshot?.usdNewYer,
            usdOldYer = snapshot?.usdOldYer,
            transportOverrideOriginal = transportOverrideOriginal,
            feesOriginal = feesOriginal,
            riskMarginOriginal = riskMarginOriginal
        )
    }

    suspend fun recordInvoiceGeographicCost(
        invoiceId: Long,
        cartonsEquivalent: Double,
        transportCostBase: Double,
        feesCustomsCostBase: Double,
        otherDirectCostBase: Double,
        notes: String,
        recordedBy: Long
    ): Long = db.withTransaction {
        db.requireUserPermission(recordedBy, SecurityPermissions.GEOGRAPHY_MANAGE)
        val invoice = requireNotNull(db.salesDao().invoiceById(invoiceId)) { "فاتورة البيع غير موجودة" }
        listOf(cartonsEquivalent, transportCostBase, feesCustomsCostBase, otherDirectCostBase).forEach {
            require(it >= 0.0 && it.isFinite()) { "أحد مبالغ التكلفة الجغرافية غير صالح" }
        }
        val existing = db.geographyDao().invoiceGeographicCost(invoiceId)
        db.geographyDao().upsertInvoiceGeographicCost(
            InvoiceGeographicCostEntity(
                id = existing?.id ?: 0,
                invoiceId = invoiceId,
                province = invoice.province,
                cartonsEquivalent = cartonsEquivalent,
                transportCostBase = transportCostBase,
                feesCustomsCostBase = feesCustomsCostBase,
                otherDirectCostBase = otherDirectCostBase,
                notes = notes.trim(),
                recordedBy = recordedBy
            )
        )
    }

    suspend fun invoiceProfitability(from: Long, to: Long): List<InvoiceProfitabilityRow> {
        require(from <= to) { "الفترة غير صالحة" }
        return db.geographyDao().invoiceProfitability(from, to)
    }

    suspend fun provinceProfitability(from: Long, to: Long): List<ProvinceProfitabilityRow> {
        require(from <= to) { "الفترة غير صالحة" }
        return db.geographyDao().provinceProfitability(from, to)
    }
}
