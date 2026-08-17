package com.fush.erp.domain

import androidx.room.withTransaction
import com.fush.erp.data.FushDatabase
import com.fush.erp.data.entity.*

class GeographyService(private val db: FushDatabase) {
    suspend fun recordFxSnapshot(
        effectiveAt: Long,
        usdNewYer: Double,
        usdOldYer: Double,
        sourceNote: String,
        createdBy: Long
    ): Long = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.GEOGRAPHY_MANAGE)
        val oldToNew = GeographyMath.oldYerToNewYerRate(usdNewYer, usdOldYer)
        val id = db.geographyDao().insertFxSnapshot(
            FxSnapshotEntity(
                effectiveAt = effectiveAt,
                usdNewYer = usdNewYer,
                usdOldYer = usdOldYer,
                oldYerToNewYer = oldToNew,
                sourceNote = sourceNote.trim(),
                createdBy = createdBy
            )
        )
        db.currencyDao().upsertRate(ExchangeRateEntity("USD", effectiveAt, usdNewYer, "${sourceNote.trim()} — USD/New YER"))
        db.currencyDao().upsertRate(ExchangeRateEntity("YER_OLD", effectiveAt, oldToNew, "${sourceNote.trim()} — Old YER/New YER derived"))
        id
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
