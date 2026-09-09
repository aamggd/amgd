package com.fush.erp.domain

import androidx.room.withTransaction
import com.fush.erp.cloud.FxRateRemoteService
import com.fush.erp.data.FushDatabase
import com.fush.erp.data.entity.AuditEventEntity
import com.fush.erp.data.entity.ExchangeRateEntity
import com.fush.erp.data.entity.FxMarketRateEntity
import com.fush.erp.data.entity.FxRateSettingsEntity
import com.fush.erp.data.entity.FxSnapshotEntity
import kotlin.math.abs

private const val FX_HOUR_MS = 60L * 60L * 1000L

class FxRateService(
    private val db: FushDatabase,
    private val remote: FxRateRemoteService
) {
    suspend fun ensureSettings(): FxRateSettingsEntity = db.withTransaction {
        db.geographyDao().fxRateSettings() ?: FxRateSettingsEntity().also {
            db.geographyDao().upsertFxRateSettings(it)
        }
    }

    suspend fun refreshFromInternet(userId: Long): FxRefreshResult {
        db.requireUserPermission(userId, SecurityPermissions.EXCHANGE_RATE_REFRESH)
        val batch = remote.fetchLatest()
        val now = TrustedTimeService.now()
        require(batch.fetchedAt in 1..(now + FX_HOUR_MS)) { "وقت جلب الأسعار من الخدمة غير صالح" }
        val allowedMarkets = setOf("ADEN", "SANAA")
        val allowedCurrencies = setOf("USD", "SAR")
        val allowedTypes = setOf("BUY", "SELL")
        val normalized = batch.rates.map { row ->
            require(row.marketRegion in allowedMarkets) { "منطقة صرف غير معروفة: ${row.marketRegion}" }
            require(row.currencyCode in allowedCurrencies) { "عملة غير مدعومة في المحرك: ${row.currencyCode}" }
            require(row.rateType in allowedTypes) { "نوع سعر غير معروف: ${row.rateType}" }
            require(row.rateYer.isFinite() && row.rateYer > 0.0) { "سعر صرف غير صالح" }
            require(row.sourcePublishedAt in 1..(now + 24L * FX_HOUR_MS)) { "تاريخ نشر سعر الصرف غير صالح" }
            row.variancePercent?.let { require(it.isFinite() && it >= 0.0) { "نسبة اختلاف المصدر غير صالحة" } }
            FxMarketRateEntity(
                batchId = batch.batchId.take(160),
                marketRegion = row.marketRegion,
                currencyCode = row.currencyCode,
                rateType = row.rateType,
                rateYer = row.rateYer,
                primarySource = row.primarySource.take(160),
                primarySourceUrl = row.primarySourceUrl.take(500),
                sourcePublishedAt = row.sourcePublishedAt,
                comparisonRateYer = row.comparisonRateYer,
                comparisonSource = row.comparisonSource.take(160),
                comparisonSourceUrl = row.comparisonSourceUrl.take(500),
                comparisonPublishedAt = row.comparisonPublishedAt,
                variancePercent = row.variancePercent,
                sourceStatus = row.sourceStatus.take(80),
                fetchedAt = batch.fetchedAt,
                rawHash = row.rawHash.take(128)
            )
        }
        require(normalized.distinctBy { listOf(it.marketRegion, it.currencyCode, it.rateType) }.size == normalized.size) {
            "خدمة الأسعار أعادت صفوفًا مكررة لنفس المنطقة/العملة/النوع"
        }
        db.withTransaction {
            db.geographyDao().upsertFxMarketRates(normalized)
            db.governanceDao().insertAudit(
                AuditEventEntity(
                    userId = userId,
                    action = "FX_RATE_REFRESH",
                    entityType = "FX_MARKET_BATCH",
                    entityId = batch.batchId.take(160),
                    newValue = "rows=${normalized.size}|fallback=${batch.fallbackMode}|fetchedAt=${batch.fetchedAt}",
                    reason = "تحديث محرك أسعار الصرف عبر Supabase"
                )
            )
        }
        return FxRefreshResult(batch.batchId, batch.fetchedAt, batch.fallbackMode, normalized.size)
    }

    suspend fun approveLatest(
        effectiveAt: Long,
        rateType: String,
        userId: Long,
        overrideReason: String = ""
    ): FxApprovalResult = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.EXCHANGE_RATE_APPROVE)
        val settings = db.geographyDao().fxRateSettings() ?: FxRateSettingsEntity().also {
            db.geographyDao().upsertFxRateSettings(it)
        }
        val type = rateType.trim().uppercase()
        require(type in setOf("BUY", "SELL")) { "نوع سعر الاعتماد غير صالح" }
        require(db.geographyDao().latestFxSnapshotAt(effectiveAt)?.effectiveAt != effectiveAt) {
            "يوجد سعر صرف معتمد لهذا التاريخ بالفعل؛ لا يتم استبدال الاعتماد التاريخي"
        }
        val batchId = requireNotNull(db.geographyDao().latestFxMarketBatchId()) { "حدّث الأسعار من الإنترنت أولاً" }
        val rows = db.geographyDao().fxMarketRatesForBatch(batchId).filter { it.rateType == type }
        val byKey = rows.associateBy { "${it.marketRegion}:${it.currencyCode}" }
        val usdAden = requireNotNull(byKey["ADEN:USD"]) { "المصدر لا يحتوي USD عدن ($type)" }
        val usdSanaa = requireNotNull(byKey["SANAA:USD"]) { "المصدر لا يحتوي USD صنعاء ($type)" }
        val sarAden = requireNotNull(byKey["ADEN:SAR"]) { "المصدر لا يحتوي SAR عدن ($type)" }
        val sarSanaa = requireNotNull(byKey["SANAA:SAR"]) { "المصدر لا يحتوي SAR صنعاء ($type)" }
        val required = listOf(usdAden, usdSanaa, sarAden, sarSanaa)
        val now = TrustedTimeService.now()
        val staleCutoff = settings.staleAfterHours.toLong() * FX_HOUR_MS
        val isStale = required.any { now - it.sourcePublishedAt > staleCutoff }
        val maxVariance = required.mapNotNull { it.variancePercent }.maxOrNull()
        val isConflict = maxVariance?.let { it >= settings.highVariancePct } == true ||
            required.any { it.sourceStatus in setOf("CONFLICT", "SOURCE_ERROR", "UNAVAILABLE") }
        val needsOverride = isStale || isConflict
        if (needsOverride) {
            db.requireUserPermission(userId, SecurityPermissions.EXCHANGE_RATE_OVERRIDE)
            require(overrideReason.trim().length >= 5) {
                if (isStale) "السعر قديم ويحتاج سبب اعتماد استثنائي" else "يوجد تعارض مرتفع بين المصادر ويحتاج سبب اعتماد استثنائي"
            }
        }

        val oldToNew = GeographyMath.oldYerToNewYerRate(usdAden.rateYer, usdSanaa.rateYer)
        val primarySources = required.map { it.primarySource }.filter { it.isNotBlank() }.distinct().joinToString(" + ")
        val comparisonSources = required.map { it.comparisonSource }.filter { it.isNotBlank() }.distinct().joinToString(" + ")
        val sourceUrl = required.firstNotNullOfOrNull { it.primarySourceUrl.takeIf(String::isNotBlank) }.orEmpty()
        val publishedAt = required.maxOf { it.sourcePublishedAt }
        val fetchedAt = required.maxOf { it.fetchedAt }
        val sourceNote = buildString {
            append("ONLINE ")
            append(primarySources.ifBlank { "Supabase FX" })
            append(" • ")
            append(type)
            append(" • batch=")
            append(batchId)
            if (needsOverride) append(" • OVERRIDE")
        }
        val snapshotId = db.geographyDao().insertFxSnapshot(
            FxSnapshotEntity(
                effectiveAt = effectiveAt,
                usdNewYer = usdAden.rateYer,
                usdOldYer = usdSanaa.rateYer,
                oldYerToNewYer = oldToNew,
                sourceNote = sourceNote,
                createdBy = userId,
                sarNewYer = sarAden.rateYer,
                sarOldYer = sarSanaa.rateYer,
                approvedRateType = type,
                sourceBatchId = batchId,
                sourcePublishedAt = publishedAt,
                fetchedAt = fetchedAt,
                primarySource = primarySources,
                primarySourceUrl = sourceUrl,
                comparisonSource = comparisonSources,
                maxVariancePercent = maxVariance,
                approvalOverrideReason = overrideReason.trim()
            )
        )
        // Canonical accounting base is YER_NEW. Approved online rates are copied here once;
        // posted transactions continue to store their own exchangeRate and never re-price later.
        db.currencyDao().upsertRate(ExchangeRateEntity("USD", effectiveAt, usdAden.rateYer, "$sourceNote — USD/YER_NEW"))
        db.currencyDao().upsertRate(ExchangeRateEntity("SAR", effectiveAt, sarAden.rateYer, "$sourceNote — SAR/YER_NEW"))
        db.currencyDao().upsertRate(ExchangeRateEntity("YER_OLD", effectiveAt, oldToNew, "$sourceNote — YER_OLD/YER_NEW derived"))
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = userId,
                action = if (needsOverride) "FX_RATE_APPROVE_OVERRIDE" else "FX_RATE_APPROVE",
                entityType = "FX_SNAPSHOT",
                entityId = snapshotId.toString(),
                newValue = "batch=$batchId|type=$type|usdAden=${usdAden.rateYer}|usdSanaa=${usdSanaa.rateYer}|sarAden=${sarAden.rateYer}|sarSanaa=${sarSanaa.rateYer}|maxVariance=${maxVariance ?: "NA"}",
                reason = overrideReason.trim().ifBlank { "اعتماد سعر الصرف من المحرك المحلي" }
            )
        )
        FxApprovalResult(snapshotId, batchId, type, needsOverride, maxVariance, isStale)
    }

    suspend fun saveSettings(
        defaultMarketRegion: String,
        defaultRateType: String,
        staleAfterHours: Int,
        warningVariancePct: Double,
        highVariancePct: Double,
        userId: Long
    ) = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.EXCHANGE_RATE_SETTINGS)
        val region = defaultMarketRegion.trim().uppercase()
        val type = defaultRateType.trim().uppercase()
        require(region in setOf("ADEN", "SANAA")) { "منطقة الصرف الافتراضية غير صالحة" }
        require(type in setOf("BUY", "SELL")) { "نوع سعر الصرف الافتراضي غير صالح" }
        require(staleAfterHours in 1..720) { "صلاحية السعر يجب أن تكون بين ساعة و720 ساعة" }
        require(warningVariancePct.isFinite() && warningVariancePct > 0.0) { "حد التحذير غير صالح" }
        require(highVariancePct.isFinite() && highVariancePct > warningVariancePct) { "حد التعارض يجب أن يكون أكبر من حد التحذير" }
        val old = db.geographyDao().fxRateSettings()
        val row = FxRateSettingsEntity(
            defaultMarketRegion = region,
            defaultRateType = type,
            staleAfterHours = staleAfterHours,
            warningVariancePct = warningVariancePct,
            highVariancePct = highVariancePct,
            updatedBy = userId,
            updatedAt = TrustedTimeService.now()
        )
        db.geographyDao().upsertFxRateSettings(row)
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = userId,
                action = "FX_RATE_SETTINGS_UPDATE",
                entityType = "FX_RATE_SETTINGS",
                entityId = "1",
                oldValue = old?.let { "${it.defaultMarketRegion}|${it.defaultRateType}|${it.staleAfterHours}|${it.warningVariancePct}|${it.highVariancePct}" }.orEmpty(),
                newValue = "${row.defaultMarketRegion}|${row.defaultRateType}|${row.staleAfterHours}|${row.warningVariancePct}|${row.highVariancePct}",
                reason = "تعديل إعدادات محرك أسعار الصرف"
            )
        )
    }

    fun localStatus(row: FxMarketRateEntity, settings: FxRateSettingsEntity, now: Long = TrustedTimeService.now()): FxQuoteStatus {
        val ageMs = (now - row.sourcePublishedAt).coerceAtLeast(0L)
        if (ageMs > settings.staleAfterHours.toLong() * FX_HOUR_MS) return FxQuoteStatus.STALE
        val variance = row.variancePercent
        if (row.sourceStatus == "CONFLICT" || (variance != null && variance >= settings.highVariancePct)) return FxQuoteStatus.CONFLICT
        if (variance != null && variance >= settings.warningVariancePct) return FxQuoteStatus.WARNING
        if (row.comparisonRateYer == null) return FxQuoteStatus.PRIMARY_ONLY
        return FxQuoteStatus.FRESH
    }
}

data class FxRefreshResult(val batchId: String, val fetchedAt: Long, val fallbackMode: String, val rowCount: Int)
data class FxApprovalResult(
    val snapshotId: Long,
    val batchId: String,
    val rateType: String,
    val usedOverride: Boolean,
    val maxVariancePercent: Double?,
    val stale: Boolean
)

enum class FxQuoteStatus { FRESH, PRIMARY_ONLY, WARNING, CONFLICT, STALE }

internal fun fxVariancePercent(primary: Double, comparison: Double): Double {
    require(primary > 0.0 && comparison > 0.0)
    return abs(primary - comparison) / primary * 100.0
}
