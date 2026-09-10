package com.fush.erp.domain

import com.fush.erp.data.FushDatabase
import androidx.room.withTransaction
import com.fush.erp.data.entity.AuditEventEntity
import com.fush.erp.data.entity.DemandForecastSnapshot
import com.fush.erp.data.entity.DemandPlanEntity
import com.fush.erp.data.entity.DemandSeasonalityEntity
import com.fush.erp.data.entity.InventoryPlanningPolicyEntity
import com.fush.erp.data.entity.ProductionPlanEntity
import com.fush.erp.data.entity.ProductionPlanMaterialEntity
import com.fush.erp.data.entity.ProductionPlanMaterialView
import com.fush.erp.data.entity.MonthlyDemandHistoryRow
import com.fush.erp.data.entity.SeasonalDemandAnalysis
import com.fush.erp.data.entity.ProvinceSeasonalityComparisonRow
import com.fush.erp.data.entity.SalesBudgetWeekEntity
import com.fush.erp.data.entity.WeeklySalesActualRow
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class PlanningService(private val db: FushDatabase) {
    fun observeWeeklySalesBudget(demandPlanId: Long): Flow<List<SalesBudgetWeekEntity>> =
        db.planningDao().observeWeeklySalesBudget(demandPlanId)

    suspend fun weeklySalesActual(plan: DemandPlanEntity): List<WeeklySalesActualRow> {
        val zone = ZoneId.systemDefault()
        val ym = YearMonth.of(plan.planYear, plan.planMonth)
        val fromDate = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val toDate = ym.atEndOfMonth().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return db.planningDao().weeklySalesActual(plan.itemId, plan.provinceCode, fromDate, toDate)
    }

    suspend fun autoDistributeWeeklySalesBudget(plan: DemandPlanEntity, userId: Long) = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.PLANNING_MANAGE)
        require(plan.status == "APPROVED") { "اعتمد خطة الطلب أولًا قبل إنشاء موازنة المبيعات." }
        val dao = db.planningDao()
        val old = dao.weeklySalesBudget(plan.id)
        val days = YearMonth.of(plan.planYear, plan.planMonth).lengthOfMonth()
        val targets = PlanningMath.distributeMonthlyTarget(plan.plannedQtyBase, days)
        dao.deleteWeeklySalesBudget(plan.id)
        val now = System.currentTimeMillis()
        dao.upsertWeeklySalesBudget(targets.mapIndexed { index, qty ->
            SalesBudgetWeekEntity(
                demandPlanId = plan.id,
                weekNo = index + 1,
                plannedQtyBase = qty,
                note = "توزيع تلقائي حسب عدد أيام الشهر",
                updatedBy = userId,
                updatedAt = now
            )
        })
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = userId,
                action = if (old.isEmpty()) "CREATE" else "REDISTRIBUTE",
                entityType = "SALES_BUDGET_WEEK",
                entityId = plan.id.toString(),
                oldValue = old.joinToString("|") { "${it.weekNo}:${it.plannedQtyBase}" },
                newValue = targets.mapIndexed { i, q -> "${i + 1}:$q" }.joinToString("|"),
                reason = "توزيع موازنة المبيعات الشهرية على الأسابيع"
            )
        )
    }

    suspend fun saveWeeklySalesBudget(
        plan: DemandPlanEntity,
        weeklyTargets: List<Double>,
        note: String,
        userId: Long
    ) = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.PLANNING_MANAGE)
        require(plan.status == "APPROVED") { "الخطة غير معتمدة. اعتمد خطة الطلب أولًا." }
        val days = YearMonth.of(plan.planYear, plan.planMonth).lengthOfMonth()
        val expectedWeeks = PlanningMath.activeBudgetWeeks(days)
        require(weeklyTargets.size == expectedWeeks) { "عدد الأسابيع لا يطابق الشهر المحدد." }
        PlanningMath.validateWeeklyBudget(plan.plannedQtyBase, weeklyTargets)
        val dao = db.planningDao()
        val old = dao.weeklySalesBudget(plan.id)
        val now = System.currentTimeMillis()
        dao.deleteWeeklySalesBudget(plan.id)
        dao.upsertWeeklySalesBudget(weeklyTargets.mapIndexed { index, qty ->
            SalesBudgetWeekEntity(
                demandPlanId = plan.id,
                weekNo = index + 1,
                plannedQtyBase = qty,
                note = note.trim(),
                updatedBy = userId,
                updatedAt = now
            )
        })
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = userId,
                action = "UPDATE",
                entityType = "SALES_BUDGET_WEEK",
                entityId = plan.id.toString(),
                oldValue = old.joinToString("|") { "${it.weekNo}:${it.plannedQtyBase}" },
                newValue = weeklyTargets.mapIndexed { i, q -> "${i + 1}:$q" }.joinToString("|"),
                reason = note.trim().ifBlank { "تعديل الموازنة الأسبوعية" }
            )
        )
    }
    fun observeDemandPlan(
        itemId: Long,
        provinceCode: String,
        planYear: Int,
        planMonth: Int
    ): Flow<DemandPlanEntity?> = db.planningDao().observeDemandPlan(itemId, provinceCode, planYear, planMonth)

    fun observeSeasonality(itemId: Long, provinceCode: String): Flow<List<DemandSeasonalityEntity>> =
        db.planningDao().observeSeasonality(itemId, provinceCode)

    suspend fun saveSeasonality(
        itemId: Long,
        provinceCode: String,
        month: Int,
        factor: Double,
        note: String,
        updatedBy: Long
    ) = db.withTransaction {
        db.requireUserPermission(updatedBy, SecurityPermissions.PLANNING_MANAGE)
        PlanningMath.validateMonth(month)
        PlanningMath.validateFactor(factor)
        val dao = db.planningDao()
        val existing = dao.seasonality(itemId, provinceCode, month)
        val now = System.currentTimeMillis()
        val id = dao.upsertSeasonality(
            DemandSeasonalityEntity(
                id = existing?.id ?: 0,
                itemId = itemId,
                provinceCode = provinceCode,
                month = month,
                demandFactor = factor,
                note = note.trim(),
                updatedBy = updatedBy,
                updatedAt = now
            )
        )
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = updatedBy,
                action = if (existing == null) "CREATE" else "UPDATE",
                entityType = "DEMAND_SEASONALITY",
                entityId = (existing?.id ?: id).toString(),
                oldValue = existing?.let { "${it.provinceCode}|m=${it.month}|factor=${it.demandFactor}|${it.note}" }.orEmpty(),
                newValue = "$provinceCode|m=$month|factor=$factor|${note.trim()}",
                reason = note.trim().ifBlank { "تحديث معامل الموسمية للمحافظة" }
            )
        )
    }

    suspend fun seasonalDemandAnalysis(
        itemId: Long,
        provinceCode: String,
        now: Long = System.currentTimeMillis()
    ): SeasonalDemandAnalysis {
        val zone = ZoneId.systemDefault()
        val currentMonth = YearMonth.from(java.time.Instant.ofEpochMilli(now).atZone(zone))
        val startMonth = currentMonth.minusMonths(23)
        val fromDate = startMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val toDate = currentMonth.atEndOfMonth().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val raw = db.planningDao().monthlyDemandHistory(itemId, provinceCode, fromDate, toDate)
        val historyMap = raw.associateBy { YearMonth.of(it.year, it.month) }
        val history = (0L..23L).map { offset ->
            val ym = startMonth.plusMonths(offset)
            historyMap[ym] ?: MonthlyDemandHistoryRow(ym.year, ym.monthValue, 0.0, 0.0, 0.0)
        }
        val last12 = history.takeLast(12)
        val baseline = PlanningMath.baseline(last12.map { it.netQtyBase })
        val factors = db.planningDao().seasonalityRows(itemId, provinceCode).associate { it.month to it.demandFactor }
        val summerFactor = PlanningMath.averageSeasonFactor(factors, summer = true)
        val winterFactor = PlanningMath.averageSeasonFactor(factors, summer = false)
        val monthValues = history.map { it.month to it.netQtyBase }
        return SeasonalDemandAnalysis(
            itemId = itemId,
            provinceCode = provinceCode,
            baselineQtyBase = baseline,
            historyMonths = history.size,
            summerActualAvgQtyBase = PlanningMath.seasonAverage(monthValues, summer = true),
            winterActualAvgQtyBase = PlanningMath.seasonAverage(monthValues, summer = false),
            summerFactorAvg = summerFactor,
            winterFactorAvg = winterFactor,
            summerForecastMonthlyQtyBase = PlanningMath.forecast(baseline, summerFactor),
            winterForecastMonthlyQtyBase = PlanningMath.forecast(baseline, winterFactor),
            summerSamples = history.count { PlanningMath.isSummerMonth(it.month) },
            winterSamples = history.count { !PlanningMath.isSummerMonth(it.month) }
        )
    }

    suspend fun provinceSeasonalityComparison(
        itemId: Long,
        provinceCodes: List<String>,
        now: Long = System.currentTimeMillis()
    ): List<ProvinceSeasonalityComparisonRow> = provinceCodes.distinct().map { provinceCode ->
        val analysis = seasonalDemandAnalysis(itemId, provinceCode, now)
        ProvinceSeasonalityComparisonRow(
            provinceCode = provinceCode,
            summerActualAvgQtyBase = analysis.summerActualAvgQtyBase,
            winterActualAvgQtyBase = analysis.winterActualAvgQtyBase,
            summerFactorAvg = analysis.summerFactorAvg,
            winterFactorAvg = analysis.winterFactorAvg,
            summerForecastMonthlyQtyBase = analysis.summerForecastMonthlyQtyBase,
            winterForecastMonthlyQtyBase = analysis.winterForecastMonthlyQtyBase
        )
    }

    suspend fun forecastNextMonth(
        itemId: Long,
        provinceCode: String,
        now: Long = System.currentTimeMillis()
    ): DemandForecastSnapshot {
        val zone = ZoneId.systemDefault()
        val currentMonth = YearMonth.from(java.time.Instant.ofEpochMilli(now).atZone(zone))
        val startMonth = currentMonth.minusMonths(11)
        val fromDate = startMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val toDate = currentMonth.atEndOfMonth().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val raw = db.planningDao().monthlyDemandHistory(itemId, provinceCode, fromDate, toDate)
        val historyMap = raw.associateBy { YearMonth.of(it.year, it.month) }
        val history = (0L..11L).map { offset ->
            val ym = startMonth.plusMonths(offset)
            historyMap[ym] ?: MonthlyDemandHistoryRow(
                year = ym.year,
                month = ym.monthValue,
                soldQtyBase = 0.0,
                returnedQtyBase = 0.0,
                netQtyBase = 0.0
            )
        }
        val next = currentMonth.plusMonths(1)
        val factor = db.planningDao().seasonality(itemId, provinceCode, next.monthValue)?.demandFactor ?: 1.0
        val baseline = PlanningMath.baseline(history.map { it.netQtyBase })
        return DemandForecastSnapshot(
            itemId = itemId,
            provinceCode = provinceCode,
            forecastYear = next.year,
            forecastMonth = next.monthValue,
            baselineQtyBase = baseline,
            seasonFactor = factor,
            forecastQtyBase = PlanningMath.forecast(baseline, factor),
            history = history
        )
    }
    suspend fun saveDemandPlanDraft(
        forecast: DemandForecastSnapshot,
        plannedQtyBase: Double,
        note: String,
        userId: Long
    ): Long = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.PLANNING_MANAGE)
        PlanningMath.validatePlannedQuantity(plannedQtyBase)
        if (kotlin.math.abs(plannedQtyBase - forecast.forecastQtyBase) > 0.001) {
            require(note.trim().isNotBlank()) { "سبب التعديل مطلوب عندما تختلف الخطة عن توقع النظام." }
        }
        val dao = db.planningDao()
        val existing = dao.demandPlan(
            forecast.itemId, forecast.provinceCode, forecast.forecastYear, forecast.forecastMonth
        )
        require(existing?.status != "APPROVED") { "الخطة معتمدة. أعد فتحها للتعديل أولًا." }
        val now = System.currentTimeMillis()
        val row = DemandPlanEntity(
            id = existing?.id ?: 0,
            itemId = forecast.itemId,
            provinceCode = forecast.provinceCode,
            planYear = forecast.forecastYear,
            planMonth = forecast.forecastMonth,
            baselineQtyBase = forecast.baselineQtyBase,
            seasonFactor = forecast.seasonFactor,
            systemForecastQtyBase = forecast.forecastQtyBase,
            plannedQtyBase = plannedQtyBase,
            manualAdjustmentQtyBase = PlanningMath.manualAdjustment(forecast.forecastQtyBase, plannedQtyBase),
            note = note.trim(),
            status = "DRAFT",
            revision = existing?.revision ?: 1,
            createdBy = existing?.createdBy ?: userId,
            createdAt = existing?.createdAt ?: now,
            updatedBy = userId,
            updatedAt = now,
            approvedBy = null,
            approvedAt = null,
            lastActionReason = note.trim()
        )
        val id = dao.upsertDemandPlan(row)
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = userId,
                action = if (existing == null) "CREATE_DRAFT" else "UPDATE_DRAFT",
                entityType = "DEMAND_PLAN",
                entityId = (if (row.id != 0L) row.id else id).toString(),
                oldValue = existing?.let { "${it.status}|${it.plannedQtyBase}" }.orEmpty(),
                newValue = "DRAFT|$plannedQtyBase",
                reason = note.trim()
            )
        )
        if (row.id != 0L) row.id else id
    }

    suspend fun approveDemandPlan(
        itemId: Long,
        provinceCode: String,
        planYear: Int,
        planMonth: Int,
        userId: Long
    ) = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.PLANNING_MANAGE)
        val dao = db.planningDao()
        val row = requireNotNull(dao.demandPlan(itemId, provinceCode, planYear, planMonth)) { "احفظ الخطة كمسودة أولًا." }
        require(row.status == "DRAFT") { "الخطة معتمدة بالفعل." }
        PlanningMath.validatePlannedQuantity(row.plannedQtyBase)
        val now = System.currentTimeMillis()
        dao.upsertDemandPlan(row.copy(
            status = "APPROVED",
            updatedBy = userId,
            updatedAt = now,
            approvedBy = userId,
            approvedAt = now,
            lastActionReason = "اعتماد خطة الطلب"
        ))
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = userId,
                action = "APPROVE",
                entityType = "DEMAND_PLAN",
                entityId = row.id.toString(),
                oldValue = "DRAFT|${row.plannedQtyBase}",
                newValue = "APPROVED|${row.plannedQtyBase}",
                reason = row.note
            )
        )
    }

    suspend fun reopenDemandPlan(
        itemId: Long,
        provinceCode: String,
        planYear: Int,
        planMonth: Int,
        reason: String,
        userId: Long
    ) = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.PLANNING_MANAGE)
        val cleanReason = reason.trim()
        require(cleanReason.isNotBlank()) { "سبب إعادة فتح الخطة مطلوب." }
        val dao = db.planningDao()
        val row = requireNotNull(dao.demandPlan(itemId, provinceCode, planYear, planMonth)) { "الخطة غير موجودة." }
        require(row.status == "APPROVED") { "يمكن إعادة فتح الخطة المعتمدة فقط." }
        val now = System.currentTimeMillis()
        dao.upsertDemandPlan(row.copy(
            status = "DRAFT",
            revision = row.revision + 1,
            updatedBy = userId,
            updatedAt = now,
            approvedBy = null,
            approvedAt = null,
            lastActionReason = cleanReason
        ))
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = userId,
                action = "REOPEN",
                entityType = "DEMAND_PLAN",
                entityId = row.id.toString(),
                oldValue = "APPROVED|rev=${row.revision}|${row.plannedQtyBase}",
                newValue = "DRAFT|rev=${row.revision + 1}|${row.plannedQtyBase}",
                reason = cleanReason
            )
        )
    }


    fun observeInventoryPlanningPolicy(itemId: Long): Flow<InventoryPlanningPolicyEntity?> =
        db.planningDao().observeInventoryPlanningPolicy(itemId)

    fun observeProductionPlan(itemId: Long, planYear: Int, planMonth: Int): Flow<ProductionPlanEntity?> =
        db.planningDao().observeProductionPlan(itemId, planYear, planMonth)

    fun observeProductionPlanMaterials(productionPlanId: Long): Flow<List<ProductionPlanMaterialView>> =
        db.planningDao().observeProductionPlanMaterialViews(productionPlanId)

    suspend fun approvedDemandPlans(itemId: Long, planYear: Int, planMonth: Int): List<DemandPlanEntity> =
        db.planningDao().approvedDemandPlans(itemId, planYear, planMonth)

    suspend fun saveInventoryPlanningPolicy(
        itemId: Long,
        safetyStockDays: Double,
        leadTimeDays: Double,
        note: String,
        userId: Long
    ) = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.PLANNING_MANAGE)
        PlanningMath.validatePlanningDays(safetyStockDays)
        PlanningMath.validatePlanningDays(leadTimeDays)
        requireNotNull(db.itemDao().byId(itemId)) { "الصنف غير موجود." }
        val dao = db.planningDao()
        val old = dao.inventoryPlanningPolicy(itemId)
        val now = System.currentTimeMillis()
        dao.upsertInventoryPlanningPolicy(
            InventoryPlanningPolicyEntity(
                itemId = itemId,
                safetyStockDays = safetyStockDays,
                leadTimeDays = leadTimeDays,
                note = note.trim(),
                updatedBy = userId,
                updatedAt = now
            )
        )
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = userId,
                action = if (old == null) "CREATE" else "UPDATE",
                entityType = "INVENTORY_PLANNING_POLICY",
                entityId = itemId.toString(),
                oldValue = old?.let { "safety=${it.safetyStockDays}|lead=${it.leadTimeDays}|${it.note}" }.orEmpty(),
                newValue = "safety=$safetyStockDays|lead=$leadTimeDays|${note.trim()}",
                reason = note.trim().ifBlank { "تحديث سياسة مخزون الأمان وزمن التوريد" }
            )
        )
    }

    suspend fun generateProductionPlan(
        itemId: Long,
        planYear: Int,
        planMonth: Int,
        userId: Long
    ): Long = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.PLANNING_MANAGE)
        PlanningMath.validateMonth(planMonth)
        require(planYear in 2000..2200) { "سنة الخطة غير صالحة." }
        val product = requireNotNull(db.itemDao().byId(itemId)) { "المنتج غير موجود." }
        require(product.category == "FINISHED_GOOD") { "خطة الإنتاج متاحة للمنتجات النهائية فقط." }
        val planningDao = db.planningDao()
        val existing = planningDao.productionPlan(itemId, planYear, planMonth)
        require(existing?.status != "APPROVED") { "خطة الإنتاج معتمدة. أعد فتحها قبل إعادة الحساب." }

        val approvedPlans = planningDao.approvedDemandPlans(itemId, planYear, planMonth)
        require(approvedPlans.isNotEmpty()) { "لا توجد خطط طلب معتمدة لهذا المنتج والشهر عبر المحافظات." }
        val approvedDemand = approvedPlans.sumOf { it.plannedQtyBase }.coerceAtLeast(0.0)
        val recipe = requireNotNull(db.recipeDao().activeForProduct(itemId)) { "لا توجد وصفة إنتاج نشطة لهذا المنتج." }
        require(recipe.targetOutputQtyBase > 0.0) { "الناتج القياسي للوصفة يجب أن يكون أكبر من صفر." }
        val components = db.recipeDao().components(recipe.id)
        require(components.isNotEmpty()) { "الوصفة النشطة لا تحتوي مواد." }

        val fgWarehouse = requireNotNull(db.warehouseDao().byCode("FG")) { "مخزن المنتج النهائي FG غير موجود." }
        val rmWarehouse = requireNotNull(db.warehouseDao().byCode("RM")) { "مخزن المواد الخام RM غير موجود." }
        val inventory = AdvancedInventoryService(db)
        val now = System.currentTimeMillis()
        val daysInMonth = YearMonth.of(planYear, planMonth).lengthOfMonth()
        val fgStock = inventory.usableBalance(fgWarehouse.id, itemId, now).coerceAtLeast(0.0)
        val fgPolicy = planningDao.inventoryPlanningPolicy(itemId)
        val finishedDailyDemand = PlanningMath.dailyRequirement(approvedDemand, daysInMonth)
        val finishedSafety = PlanningMath.safetyStockQty(finishedDailyDemand, fgPolicy?.safetyStockDays ?: 0.0)
        val finishedReorder = PlanningMath.reorderPointQty(
            finishedDailyDemand,
            fgPolicy?.leadTimeDays ?: 0.0,
            finishedSafety
        )
        val netNeed = PlanningMath.netProductionNeed(approvedDemand, finishedSafety, fgStock)
        val batches = PlanningMath.requiredBatchCount(netNeed, recipe.targetOutputQtyBase)
        val plannedOutput = batches.toDouble() * recipe.targetOutputQtyBase
        val projectedEnding = (fgStock + plannedOutput - approvedDemand).coerceAtLeast(0.0)

        val planRow = ProductionPlanEntity(
            id = existing?.id ?: 0,
            itemId = itemId,
            planYear = planYear,
            planMonth = planMonth,
            recipeId = recipe.id,
            recipeVersionNo = recipe.versionNo,
            recipeTargetOutputQtyBase = recipe.targetOutputQtyBase,
            approvedDemandQtyBase = approvedDemand,
            approvedProvinceCount = approvedPlans.size,
            finishedStockQtyBase = fgStock,
            finishedDailyDemandQtyBase = finishedDailyDemand,
            finishedSafetyStockQtyBase = finishedSafety,
            finishedReorderPointQtyBase = finishedReorder,
            netProductionNeedQtyBase = netNeed,
            plannedBatchCount = batches,
            plannedOutputQtyBase = plannedOutput,
            projectedEndingFinishedQtyBase = projectedEnding,
            status = "DRAFT",
            revision = existing?.revision ?: 1,
            generatedBy = existing?.generatedBy ?: userId,
            generatedAt = existing?.generatedAt ?: now,
            updatedBy = userId,
            updatedAt = now,
            approvedBy = null,
            approvedAt = null,
            lastActionReason = "إعادة حساب تلقائي من خطط الطلب المعتمدة والمخزون وسياسات الأمان"
        )
        val planId = if (existing == null) {
            planningDao.insertProductionPlan(planRow)
        } else {
            planningDao.updateProductionPlan(planRow)
            existing.id
        }
        planningDao.deleteProductionPlanMaterials(planId)

        val materialRows = components.mapIndexed { index, component ->
            val usableStock = inventory.usableBalance(rmWarehouse.id, component.itemId, now).coerceAtLeast(0.0)
            val reservedForOpenOrders = db.productionDao().reservedByOtherOrders(rmWarehouse.id, component.itemId, 0L).coerceAtLeast(0.0)
            val available = (usableStock - reservedForOpenOrders).coerceAtLeast(0.0)
            val required = PlanningMath.componentRequirement(
                perBatchQtyBase = component.quantityBase,
                expectedLossPct = component.expectedLossPct,
                batchCount = batches
            )
            val dailyUsage = PlanningMath.dailyRequirement(required, daysInMonth)
            val policy = planningDao.inventoryPlanningPolicy(component.itemId)
            val safety = PlanningMath.safetyStockQty(dailyUsage, policy?.safetyStockDays ?: 0.0)
            val reorder = PlanningMath.reorderPointQty(dailyUsage, policy?.leadTimeDays ?: 0.0, safety)
            val suggested = PlanningMath.suggestedPurchaseQty(required, safety, available)
            ProductionPlanMaterialEntity(
                productionPlanId = planId,
                itemId = component.itemId,
                sequenceNo = index + 1,
                perBatchQtyBase = component.quantityBase,
                expectedLossPct = component.expectedLossPct,
                requiredQtyBase = required,
                currentStockQtyBase = available,
                dailyUsageQtyBase = dailyUsage,
                safetyStockQtyBase = safety,
                reorderPointQtyBase = reorder,
                suggestedPurchaseQtyBase = suggested,
                projectedEndingQtyBase = (available + suggested - required).coerceAtLeast(0.0)
            )
        }
        if (materialRows.isNotEmpty()) planningDao.upsertProductionPlanMaterials(materialRows)

        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = userId,
                action = if (existing == null) "GENERATE" else "RECALCULATE",
                entityType = "PRODUCTION_PLAN",
                entityId = planId.toString(),
                oldValue = existing?.let { "${it.status}|demand=${it.approvedDemandQtyBase}|batches=${it.plannedBatchCount}|output=${it.plannedOutputQtyBase}" }.orEmpty(),
                newValue = "DRAFT|demand=$approvedDemand|fgStock=$fgStock|safety=$finishedSafety|batches=$batches|output=$plannedOutput|materials=${materialRows.size}",
                reason = "توليد خطة الإنتاج والمواد من خطط الطلب المعتمدة"
            )
        )
        planId
    }

    suspend fun approveProductionPlan(itemId: Long, planYear: Int, planMonth: Int, userId: Long) = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.PLANNING_MANAGE)
        val dao = db.planningDao()
        val row = requireNotNull(dao.productionPlan(itemId, planYear, planMonth)) { "أنشئ خطة الإنتاج أولًا." }
        require(row.status == "DRAFT") { "خطة الإنتاج معتمدة بالفعل." }
        val now = System.currentTimeMillis()
        dao.updateProductionPlan(
            row.copy(
                status = "APPROVED",
                updatedBy = userId,
                updatedAt = now,
                approvedBy = userId,
                approvedAt = now,
                lastActionReason = "اعتماد خطة الإنتاج والمواد"
            )
        )
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = userId,
                action = "APPROVE",
                entityType = "PRODUCTION_PLAN",
                entityId = row.id.toString(),
                oldValue = "DRAFT|batches=${row.plannedBatchCount}|output=${row.plannedOutputQtyBase}",
                newValue = "APPROVED|batches=${row.plannedBatchCount}|output=${row.plannedOutputQtyBase}",
                reason = "اعتماد خطة الإنتاج والمواد"
            )
        )
    }

    suspend fun reopenProductionPlan(
        itemId: Long,
        planYear: Int,
        planMonth: Int,
        reason: String,
        userId: Long
    ) = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.PLANNING_MANAGE)
        val cleanReason = reason.trim()
        require(cleanReason.isNotBlank()) { "سبب إعادة فتح خطة الإنتاج مطلوب." }
        val dao = db.planningDao()
        val row = requireNotNull(dao.productionPlan(itemId, planYear, planMonth)) { "خطة الإنتاج غير موجودة." }
        require(row.status == "APPROVED") { "يمكن إعادة فتح خطة إنتاج معتمدة فقط." }
        val now = System.currentTimeMillis()
        dao.updateProductionPlan(
            row.copy(
                status = "DRAFT",
                revision = row.revision + 1,
                updatedBy = userId,
                updatedAt = now,
                approvedBy = null,
                approvedAt = null,
                lastActionReason = cleanReason
            )
        )
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = userId,
                action = "REOPEN",
                entityType = "PRODUCTION_PLAN",
                entityId = row.id.toString(),
                oldValue = "APPROVED|rev=${row.revision}|output=${row.plannedOutputQtyBase}",
                newValue = "DRAFT|rev=${row.revision + 1}|output=${row.plannedOutputQtyBase}",
                reason = cleanReason
            )
        )
    }

}
