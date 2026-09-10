package com.fush.erp.domain

import androidx.room.withTransaction
import com.fush.erp.data.FushDatabase
import com.fush.erp.data.entity.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.min

class ProductionService(private val db: FushDatabase) {
    private val numbering = AutoNumberService(db)


    data class CostSummary(
        val materialCostBase: Double,
        val laborCostBase: Double,
        val totalCostBase: Double,
        val acceptedQtyBase: Double,
        val unitCostBase: Double
    )

    data class IssueCorrectionResult(
        val returnedQtyBase: Double,
        val returnedCostBase: Double,
        val correctedIssuedQtyBase: Double,
        val correctedMaterialCostBase: Double,
        val correctedBatchUnitCostBase: Double?,
        val finishedInventoryReductionBase: Double,
        val cogsReductionBase: Double,
        val addedQtyBase: Double = 0.0,
        val addedCostBase: Double = 0.0,
        val linkedBottleLabelsAddedBase: Double = 0.0,
        val linkedPacksAddedBase: Double = 0.0,
        val linkedPackLabelsAddedBase: Double = 0.0,
        val finishedGoodsAddedBase: Double = 0.0
    )


    data class RecipeComponentInput(
        val itemId: Long,
        val quantityBase: Double,
        val stage: String = "PREPARATION",
        val expectedLossPct: Double = 0.0
    )

    data class MaterialAvailability(
        val itemId: Long,
        val itemCode: String,
        val itemName: String,
        val unitName: String,
        val requiredQtyBase: Double,
        val availableQtyBase: Double,
        val shortageQtyBase: Double
    ) {
        val isAvailable: Boolean get() = shortageQtyBase <= 1e-9
    }

    suspend fun deleteRecipe(recipeId: Long, userId: Long) = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.PRODUCTION_POST)
        val recipe = requireNotNull(db.recipeDao().byId(recipeId)) { "الوصفة غير موجودة" }
        val usedByOrders = db.recipeDao().productionOrderCount(recipeId)
        require(usedByOrders == 0) {
            "لا يمكن حذف الوصفة ${recipe.code} إصدار ${recipe.versionNo} لأنها مستخدمة في $usedByOrders أمر إنتاج. يجب الاحتفاظ بها للتتبع والتكلفة."
        }
        val deleted = db.recipeDao().deleteById(recipeId)
        check(deleted == 1) { "تعذر حذف الوصفة" }
    }


    suspend fun deleteOrder(orderId: Long, userId: Long, reason: String = "حذف أمر إنتاج قبل الصرف") = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.PRODUCTION_POST)
        val order = requireNotNull(db.productionDao().orderById(orderId)) { "أمر الإنتاج غير موجود" }
        require(order.status in setOf("PLANNED", "MATERIALS_RESERVED")) {
            "لا يمكن حذف أمر الإنتاج بعد صرف المواد. بعد الصرف يجب استخدام التصحيح/الإلغاء للحفاظ على المخزون والمحاسبة."
        }
        require(db.productionDao().batchForOrder(orderId) == null) { "لا يمكن حذف أمر له دفعة إنتاج" }
        require(db.productionDao().issuesForOrder(orderId).isEmpty()) { "لا يمكن حذف أمر توجد عليه حركات صرف مواد" }
        val materials = db.productionDao().materialsForOrder(orderId)
        require(materials.all { it.issuedQtyBase <= 1e-9 && it.issueCostBase <= 1e-9 }) {
            "لا يمكن حذف أمر بعد تسجيل صرف أو تكلفة مواد"
        }
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = userId,
                action = "DELETE_PRODUCTION_ORDER",
                entityType = "PRODUCTION_ORDER",
                entityId = order.id.toString(),
                oldValue = "${order.orderNo}|status=${order.status}|planned=${order.plannedOutputQtyBase}",
                newValue = "DELETED",
                reason = reason.ifBlank { "حذف أمر إنتاج قبل الصرف" }
            )
        )
        val deleted = db.productionDao().deleteOrderById(order.id)
        check(deleted == 1) { "تعذر حذف أمر الإنتاج" }
    }


    suspend fun createRecipe(
        productItemId: Long,
        targetOutputQtyBase: Double,
        components: List<RecipeComponentInput>,
        notes: String = "",
        userId: Long
    ): Long = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.PRODUCTION_POST)
        require(targetOutputQtyBase > 0.0 && targetOutputQtyBase.isFinite()) { "الناتج القياسي يجب أن يكون أكبر من صفر" }
        val product = requireNotNull(db.itemDao().byId(productItemId)) { "المنتج النهائي غير موجود" }
        require(product.isActive && product.category == "FINISHED_GOOD") { "يجب اختيار منتج نهائي فعال" }
        require(db.recipeDao().activeForProduct(productItemId) == null) { "يوجد بالفعل إصدار فعال لهذا المنتج؛ استخدم إنشاء إصدار جديد" }
        require(components.isNotEmpty()) { "يجب إضافة مكون واحد على الأقل للوصفة" }
        require(components.map { it.itemId }.distinct().size == components.size) { "لا يمكن تكرار المكون نفسه داخل الوصفة" }
        components.forEach { component ->
            require(component.itemId != productItemId) { "لا يمكن استخدام المنتج النهائي كمكون لنفسه" }
            val item = requireNotNull(db.itemDao().byId(component.itemId)) { "أحد مكونات الوصفة غير موجود" }
            require(item.isActive && item.category != "FINISHED_GOOD") { "مكونات الوصفة يجب أن تكون مواد خام أو مواد تغليف فعالة" }
            require(component.quantityBase > 0.0 && component.quantityBase.isFinite()) { "كمية مكون الوصفة غير صالحة" }
            require(component.expectedLossPct in 0.0..100.0 && component.expectedLossPct.isFinite()) { "نسبة الفاقد غير صالحة" }
            require(component.stage in setOf("PREPARATION", "MIXING", "FILLING")) { "مرحلة مكون الوصفة غير صالحة" }
        }

        val code = "BOM-" + product.code.removePrefix("FG-")
        val versionNo = db.recipeDao().maxVersion(code) + 1
        val now = System.currentTimeMillis()
        val recipeId = db.recipeDao().insertRecipe(
            RecipeEntity(
                code = code,
                productItemId = product.id,
                versionNo = versionNo,
                effectiveFrom = now,
                targetOutputQtyBase = targetOutputQtyBase,
                status = "ACTIVE",
                notes = notes,
                createdAt = now
            )
        )
        db.recipeDao().insertComponents(
            components.mapIndexed { index, component ->
                RecipeComponentEntity(
                    recipeId = recipeId,
                    itemId = component.itemId,
                    quantityBase = component.quantityBase,
                    expectedLossPct = component.expectedLossPct,
                    stage = component.stage,
                    sequenceNo = index + 1
                )
            }
        )
        recipeId
    }


    suspend fun createRecipeVersion(
        sourceRecipeId: Long,
        componentQuantities: Map<Long, Double>,
        notes: String = "",
        userId: Long
    ): Long = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.PRODUCTION_POST)
        val source = requireNotNull(db.recipeDao().byId(sourceRecipeId)) { "الوصفة الأصلية غير موجودة" }
        val sourceComponents = db.recipeDao().components(sourceRecipeId)
        require(sourceComponents.isNotEmpty()) { "الوصفة الأصلية لا تحتوي مكونات" }
        sourceComponents.forEach { component ->
            val qty = componentQuantities[component.itemId] ?: component.quantityBase
            require(qty > 0.0 && qty.isFinite()) { "كمية مكون الوصفة غير صالحة" }
        }
        if (source.status == "ACTIVE") {
            val updated = db.recipeDao().updateRecipeStatus(source.id, "SUPERSEDED")
            check(updated == 1) { "تعذر تجميد إصدار الوصفة السابق" }
        }
        val newVersion = db.recipeDao().maxVersion(source.code) + 1
        val newId = db.recipeDao().insertRecipe(
            source.copy(
                id = 0,
                versionNo = newVersion,
                effectiveFrom = System.currentTimeMillis(),
                status = "ACTIVE",
                notes = if (notes.isBlank()) "إصدار جديد مشتق من الإصدار ${source.versionNo}" else notes,
                createdAt = System.currentTimeMillis()
            )
        )
        db.recipeDao().insertComponents(
            sourceComponents.map { component ->
                component.copy(
                    id = 0,
                    recipeId = newId,
                    quantityBase = componentQuantities[component.itemId] ?: component.quantityBase
                )
            }
        )
        newId
    }

    suspend fun createOrder(
        recipeId: Long,
        rawWarehouseId: Long,
        finishedWarehouseId: Long,
        plannedOutputQtyBase: Double,
        directLaborCostBase: Double,
        plannedDate: Long,
        createdBy: Long,
        primaryAssetId: Long? = null,
        operatorEmployeeId: Long? = null,
        notes: String = ""
    ): Long = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.PRODUCTION_POST)
        require(plannedOutputQtyBase > 0.0 && plannedOutputQtyBase.isFinite()) { "كمية الإنتاج المخططة يجب أن تكون أكبر من صفر" }
        ProductionMath.validateDirectLaborCost(directLaborCostBase)
        val recipe = requireNotNull(db.recipeDao().byId(recipeId)) { "الوصفة غير موجودة" }
        require(recipe.status == "ACTIVE") { "لا يمكن إنشاء أمر إنتاج من وصفة غير فعالة" }
        val components = db.recipeDao().components(recipeId)
        require(components.isNotEmpty()) { "الوصفة لا تحتوي مكونات" }
        require(db.warehouseDao().allActive().any { it.id == rawWarehouseId }) { "مخزن المواد الخام غير موجود" }
        require(db.warehouseDao().allActive().any { it.id == finishedWarehouseId }) { "مخزن المنتج النهائي غير موجود" }
        val productionEmployeeId = requireNotNull(operatorEmployeeId) { "يجب تحديد موظف الإنتاج الذي يستحق أجور/عمولة هذه الدفعة" }
        val productionEmployee = requireNotNull(db.employeeDao().employeeById(productionEmployeeId)) { "موظف الإنتاج غير موجود" }
        require(productionEmployee.status == "ACTIVE") { "موظف الإنتاج المحدد غير نشط" }
        if (primaryAssetId != null) {
            requireNotNull(db.maintenanceDao().assetById(primaryAssetId)) { "المعدة الرئيسية غير موجودة" }
            EmployeeService(db).assertEmployeeCanOperateAsset(productionEmployeeId, primaryAssetId)
        }

        val orderId = db.productionDao().insertOrder(
            ProductionOrderEntity(
                orderNo = numbering.nextDocumentNo("PROD", plannedDate),
                recipeId = recipe.id,
                productItemId = recipe.productItemId,
                plannedOutputQtyBase = plannedOutputQtyBase,
                rawWarehouseId = rawWarehouseId,
                finishedWarehouseId = finishedWarehouseId,
                plannedDate = plannedDate,
                directLaborCostBase = directLaborCostBase,
                primaryAssetId = primaryAssetId,
                notes = notes,
                createdBy = createdBy
            )
        )
        db.employeeDao().insertOperatorAssignment(
            ProductionOperatorAssignmentEntity(orderId = orderId, employeeId = productionEmployeeId, assignedBy = createdBy)
        )
        val frozenMaterials = components.map { component ->
            ProductionMaterialEntity(
                orderId = orderId,
                recipeComponentId = component.id,
                itemId = component.itemId,
                standardQtyBase = ProductionMath.fixedBatchComponentQuantity(
                    componentQty = component.quantityBase,
                    plannedOutputQty = plannedOutputQtyBase
                )
            )
        }
        db.productionDao().insertMaterials(frozenMaterials)
        ProductionMath.validateBomIntegrity(
            recipeComponents = components.map { ProductionBomLink(it.id, it.itemId) },
            orderMaterials = frozenMaterials.map { ProductionBomLink(it.recipeComponentId, it.itemId) }
        )
        orderId
    }

    suspend fun materialAvailability(orderId: Long): List<MaterialAvailability> {
        val order = requireNotNull(db.productionDao().orderById(orderId)) { "أمر الإنتاج غير موجود" }
        val materials = db.productionDao().materialsForOrder(orderId)
        require(materials.isNotEmpty()) { "لا توجد مواد في أمر الإنتاج" }
        return materials.map { material ->
            val item = requireNotNull(db.itemDao().byId(material.itemId)) { "الصنف رقم ${material.itemId} غير موجود" }
            val unit = db.unitDao().byId(item.baseUnitId)
            val balance = db.stockDao().balance(order.rawWarehouseId, material.itemId)
            val otherReservations = db.productionDao().reservedByOtherOrders(order.rawWarehouseId, material.itemId, orderId)
            val available = (balance - otherReservations).coerceAtLeast(0.0)
            val shortage = (material.standardQtyBase - available).coerceAtLeast(0.0)
            MaterialAvailability(
                itemId = item.id,
                itemCode = item.code,
                itemName = item.nameAr,
                unitName = unit?.nameAr ?: "وحدة",
                requiredQtyBase = material.standardQtyBase,
                availableQtyBase = available,
                shortageQtyBase = shortage
            )
        }
    }

    suspend fun reserveMaterials(orderId: Long, userId: Long) = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.PRODUCTION_POST)
        val order = requireNotNull(db.productionDao().orderById(orderId)) { "أمر الإنتاج غير موجود" }
        require(order.status == "PLANNED") { "يمكن حجز المواد لأمر مخطط فقط" }
        val materials = db.productionDao().materialsForOrder(orderId)
        require(materials.isNotEmpty()) { "لا توجد مواد في أمر الإنتاج" }
        validateOrderBomIntegrity(order, materials)
        val availability = materialAvailability(orderId)
        val shortages = availability.filterNot { it.isAvailable }
        require(shortages.isEmpty()) {
            val first = shortages.first()
            "المخزون المتاح لا يكفي للمادة ${first.itemName} (${first.itemCode}): المطلوب ${fmt(first.requiredQtyBase)} ${first.unitName} والمتاح ${fmt(first.availableQtyBase)} ${first.unitName} والناقص ${fmt(first.shortageQtyBase)} ${first.unitName}"
        }
        materials.forEach { material ->
            db.productionDao().updateMaterial(material.copy(reservedQtyBase = material.standardQtyBase))
        }
        db.productionDao().updateOrder(order.copy(status = "MATERIALS_RESERVED"))
    }

    suspend fun issueReservedMaterials(orderId: Long, createdBy: Long): Double = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.PRODUCTION_POST)
        val order = requireNotNull(db.productionDao().orderById(orderId)) { "أمر الإنتاج غير موجود" }
        require(order.status == "MATERIALS_RESERVED") { "يجب حجز المواد قبل الصرف" }
        val materials = db.productionDao().materialsForOrder(orderId)
        validateOrderBomIntegrity(order, materials)
        var totalIssueCost = 0.0

        materials.forEach { material ->
            val needed = material.reservedQtyBase - material.issuedQtyBase
            require(needed > 0.0) { "لا توجد كمية متبقية للصرف للصنف رقم ${material.itemId}" }
            val item = requireNotNull(db.itemDao().byId(material.itemId)) { "الصنف رقم ${material.itemId} غير موجود" }
            val lots = AdvancedInventoryService(db).usableLots(order.rawWarehouseId, material.itemId)
                .filter { it.quantityBase > 1e-9 }
            lots.forEach { lot ->
                ProductionMath.validateIssueLotTracking(
                    lotTracked = item.lotTracked,
                    expiryTracked = item.expiryTracked,
                    lotNo = lot.lotNo,
                    expiryDate = lot.expiryDate,
                    itemLabel = "${item.nameAr} (${item.code})"
                )
            }
            val balanceNow = lots.sumOf { it.quantityBase }
            require(balanceNow + 1e-9 >= needed) {
                "المخزون المقبول وغير المنتهي والمكتمل بيانات التتبع لا يكفي لإتمام صرف المادة ${item.nameAr}"
            }

            var remaining = needed
            var materialCost = 0.0
            for (lot in lots) {
                if (remaining <= 1e-9) break
                if (lot.quantityBase <= 1e-9) continue
                val allocated = min(remaining, lot.quantityBase)
                val unitCost = if (lot.quantityBase <= 1e-9) 0.0 else lot.inventoryValueBase / lot.quantityBase
                val cost = allocated * unitCost
                val issueId = db.productionDao().insertIssue(
                    ProductionIssueEntity(
                        orderId = order.id,
                        materialId = material.id,
                        itemId = material.itemId,
                        quantityBase = allocated,
                        unitCostBase = unitCost,
                        totalCostBase = cost,
                        lotNo = lot.lotNo,
                        expiryDate = lot.expiryDate,
                        createdBy = createdBy
                    )
                )
                db.stockDao().insertMovement(
                    StockMovementEntity(
                        movementDate = System.currentTimeMillis(),
                        warehouseId = order.rawWarehouseId,
                        itemId = material.itemId,
                        movementType = "PRODUCTION_ISSUE",
                        quantityBase = -allocated,
                        unitCostBase = unitCost,
                        referenceType = "PRODUCTION_ISSUE",
                        referenceId = issueId,
                        lotNo = lot.lotNo,
                        expiryDate = lot.expiryDate
                    )
                )
                remaining -= allocated
                materialCost += cost
            }
            require(remaining <= 1e-7) { "تعذر تخصيص كامل كمية الصنف من التشغيلات المتاحة" }
            totalIssueCost += materialCost
            db.productionDao().updateMaterial(
                material.copy(
                    issuedQtyBase = material.issuedQtyBase + needed,
                    issueCostBase = material.issueCostBase + materialCost
                )
            )
        }

        if (totalIssueCost > 0.0) {
            postMaterialIssueJournal(order, totalIssueCost, createdBy)
        }
        db.productionDao().updateOrder(order.copy(status = "MATERIALS_ISSUED"))
        totalIssueCost
    }

    suspend fun correctMaterialIssue(
        orderId: Long,
        materialId: Long,
        correctedIssuedQtyBase: Double,
        reason: String,
        createdBy: Long
    ): IssueCorrectionResult = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.PRODUCTION_POST)
        require(reason.trim().length >= 3) { "سبب التصحيح مطلوب (3 أحرف على الأقل)" }
        require(correctedIssuedQtyBase >= 0.0 && correctedIssuedQtyBase.isFinite()) { "الكمية الصحيحة غير صالحة" }

        val order = requireNotNull(db.productionDao().orderById(orderId)) { "أمر الإنتاج غير موجود" }
        require(order.status != "CANCELLED") { "لا يمكن تصحيح أمر إنتاج ملغي" }
        val material = requireNotNull(db.productionDao().materialsForOrder(orderId).firstOrNull { it.id == materialId }) {
            "مادة أمر الإنتاج غير موجودة"
        }
        val oldIssuedQty = material.issuedQtyBase
        require(oldIssuedQty > 1e-9) { "لا يوجد صرف مسجل لهذه المادة" }
        require(kotlin.math.abs(correctedIssuedQtyBase - oldIssuedQty) > 1e-9) { "الكمية الجديدة مطابقة للمصروف الحالي" }
        val item = requireNotNull(db.itemDao().byId(material.itemId)) { "الصنف رقم ${material.itemId} غير موجود" }
        if (correctedIssuedQtyBase > oldIssuedQty + 1e-9) {
            return@withTransaction increaseMaterialIssueCorrection(
                order = order,
                material = material,
                item = item,
                correctedIssuedQtyBase = correctedIssuedQtyBase,
                reason = reason.trim(),
                createdBy = createdBy
            )
        }

        val oldMaterialCost = db.productionDao().materialCostForOrder(order.id)
        val reductionQty = oldIssuedQty - correctedIssuedQtyBase
        var remaining = reductionQty
        var returnedCost = 0.0
        val now = System.currentTimeMillis()

        for (issue in db.productionDao().baseIssuesForMaterial(material.id)) {
            if (remaining <= 1e-9) break
            val alreadyCorrected = db.productionDao().correctedQtyForIssue(issue.id)
            val available = (issue.quantityBase - alreadyCorrected).coerceAtLeast(0.0)
            if (available <= 1e-9) continue
            val take = min(available, remaining)
            val cost = take * issue.unitCostBase
            val correctionIssueId = db.productionDao().insertIssue(
                ProductionIssueEntity(
                    orderId = order.id,
                    materialId = material.id,
                    itemId = material.itemId,
                    quantityBase = -take,
                    unitCostBase = issue.unitCostBase,
                    totalCostBase = -cost,
                    lotNo = issue.lotNo,
                    expiryDate = issue.expiryDate,
                    issueKind = "CORRECTION_RETURN",
                    correctionOfIssueId = issue.id,
                    reason = reason.trim(),
                    createdBy = createdBy,
                    issueDate = now
                )
            )
            db.stockDao().insertMovement(
                StockMovementEntity(
                    movementDate = now,
                    warehouseId = order.rawWarehouseId,
                    itemId = material.itemId,
                    movementType = "PRODUCTION_ISSUE_RETURN",
                    quantityBase = take,
                    unitCostBase = issue.unitCostBase,
                    referenceType = "PRODUCTION_ISSUE_CORRECTION",
                    referenceId = correctionIssueId,
                    lotNo = issue.lotNo,
                    expiryDate = issue.expiryDate
                )
            )
            returnedCost += cost
            remaining -= take
        }
        require(remaining <= 1e-7) { "تعذر ربط كامل كمية التصحيح بحركات الصرف الأصلية" }

        val netQty = db.productionDao().netIssuedQtyForMaterial(material.id)
        val netCost = db.productionDao().netIssueCostForMaterial(material.id)
        require(kotlin.math.abs(netQty - correctedIssuedQtyBase) <= 1e-6) { "تعذر مطابقة صافي الصرف بعد التصحيح" }
        require(netCost >= -1e-6) { "نتجت تكلفة مادة سالبة بعد التصحيح" }
        db.productionDao().updateMaterial(
            material.copy(
                issuedQtyBase = netQty.coerceAtLeast(0.0),
                issueCostBase = netCost.coerceAtLeast(0.0)
            )
        )

        val newMaterialCost = db.productionDao().materialCostForOrder(order.id)
        val actualReduction = (oldMaterialCost - newMaterialCost).coerceAtLeast(0.0)
        require(kotlin.math.abs(actualReduction - returnedCost) <= 0.01) { "قيمة التصحيح لا تطابق تكلفة الصرف المعادة" }

        var correctedBatchUnitCost: Double? = null
        var finishedInventoryReduction = 0.0
        var cogsReduction = 0.0
        val batch = db.productionDao().batchForOrder(order.id)

        when {
            order.status == "REJECTED" || batch?.status == "REJECTED" -> {
                postRejectedIssueCorrectionJournal(order, actualReduction, reason.trim(), createdBy)
            }
            order.status == "CLOSED" && batch?.status == "ACCEPTED" && batch.acceptedQtyBase > 0.0 -> {
                val acceptedQty = batch.acceptedQtyBase
                val oldTotalCost = oldMaterialCost + order.directLaborCostBase
                val newTotalCost = newMaterialCost + order.directLaborCostBase
                val oldUnitCost = oldTotalCost / acceptedQty
                val newUnitCost = newTotalCost / acceptedQty
                correctedBatchUnitCost = newUnitCost

                val allowedTypes = setOf(
                    "PRODUCTION_RECEIPT", "PRODUCTION_RECEIPT_CORRECTION", "SALE", "SALES_RETURN",
                    "PRODUCTION_COST_REVALUE_OUT", "PRODUCTION_COST_REVALUE_IN"
                )
                val movementTypes = db.stockDao().movementTypesForLot(order.finishedWarehouseId, order.productItemId, batch.batchNo)
                val unsupported = movementTypes.filterNot { it in allowedTypes }
                require(unsupported.isEmpty()) {
                    "تشغيلة ${batch.batchNo} تحتوي حركات مخزون أخرى (${unsupported.joinToString()})؛ يجب مراجعتها قبل تصحيح تكلفة الإنتاج"
                }

                val lot = db.stockDao().lotBalances(order.finishedWarehouseId, order.productItemId)
                    .firstOrNull { it.lotNo == batch.batchNo }
                val onHandQty = lot?.quantityBase ?: 0.0
                val split = ProductionMath.splitAcceptedBatchCostCorrection(actualReduction, acceptedQty, onHandQty)
                finishedInventoryReduction = split.inventoryReductionBase
                cogsReduction = split.cogsReductionBase

                if (onHandQty > 1e-9 && finishedInventoryReduction > 1e-9) {
                    val currentValue = requireNotNull(lot).inventoryValueBase
                    require(currentValue + 0.01 >= finishedInventoryReduction) { "قيمة رصيد الدفعة لا تكفي لإجراء إعادة التقييم" }
                    val currentUnit = currentValue / onHandQty
                    val correctedValue = (currentValue - finishedInventoryReduction).coerceAtLeast(0.0)
                    val correctedInventoryUnit = correctedValue / onHandQty
                    db.stockDao().insertMovement(
                        StockMovementEntity(
                            movementDate = now,
                            warehouseId = order.finishedWarehouseId,
                            itemId = order.productItemId,
                            movementType = "PRODUCTION_COST_REVALUE_OUT",
                            quantityBase = -onHandQty,
                            unitCostBase = currentUnit,
                            referenceType = "PRODUCTION_COST_CORRECTION",
                            referenceId = order.id,
                            lotNo = batch.batchNo,
                            expiryDate = batch.expiryDate
                        )
                    )
                    db.stockDao().insertMovement(
                        StockMovementEntity(
                            movementDate = now,
                            warehouseId = order.finishedWarehouseId,
                            itemId = order.productItemId,
                            movementType = "PRODUCTION_COST_REVALUE_IN",
                            quantityBase = onHandQty,
                            unitCostBase = correctedInventoryUnit,
                            referenceType = "PRODUCTION_COST_CORRECTION",
                            referenceId = order.id,
                            lotNo = batch.batchNo,
                            expiryDate = batch.expiryDate
                        )
                    )
                }

                revalueSalesLotCosts(order.productItemId, batch.batchNo, newUnitCost)
                postAcceptedIssueCorrectionJournal(
                    order = order,
                    batch = batch,
                    returnedRawCost = actualReduction,
                    finishedInventoryReduction = finishedInventoryReduction,
                    cogsReduction = cogsReduction,
                    oldUnitCost = oldUnitCost,
                    newUnitCost = newUnitCost,
                    reason = reason.trim(),
                    createdBy = createdBy
                )
            }
            else -> {
                postOpenIssueCorrectionJournal(order, actualReduction, reason.trim(), createdBy)
            }
        }

        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = createdBy,
                action = "CORRECT_PRODUCTION_ISSUE",
                entityType = "PRODUCTION_ORDER",
                entityId = order.id.toString(),
                oldValue = "materialId=${material.id}; issued=${fmt(oldIssuedQty)}; materialCost=${fmt(oldMaterialCost)}",
                newValue = "issued=${fmt(netQty)}; returned=${fmt(reductionQty)}; returnedCost=${fmt(actualReduction)}; materialCost=${fmt(newMaterialCost)}",
                reason = reason.trim()
            )
        )

        IssueCorrectionResult(
            returnedQtyBase = reductionQty,
            returnedCostBase = actualReduction,
            correctedIssuedQtyBase = netQty,
            correctedMaterialCostBase = newMaterialCost,
            correctedBatchUnitCostBase = correctedBatchUnitCost,
            finishedInventoryReductionBase = finishedInventoryReduction,
            cogsReductionBase = cogsReduction
        )
    }

    private data class AddedIssue(val quantityBase: Double, val costBase: Double)

    private suspend fun issueAdditionalMaterialToTarget(
        order: ProductionOrderEntity,
        material: ProductionMaterialEntity,
        targetQtyBase: Double,
        reason: String,
        createdBy: Long,
        now: Long
    ): AddedIssue {
        require(targetQtyBase.isFinite() && targetQtyBase >= material.issuedQtyBase - 1e-9) { "الكمية المستهدفة للصرف غير صالحة" }
        val additionalQty = (targetQtyBase - material.issuedQtyBase).coerceAtLeast(0.0)
        if (additionalQty <= 1e-9) return AddedIssue(0.0, 0.0)

        val item = requireNotNull(db.itemDao().byId(material.itemId)) { "الصنف رقم ${material.itemId} غير موجود" }
        val lots = AdvancedInventoryService(db).usableLots(order.rawWarehouseId, material.itemId)
            .filter { it.quantityBase > 1e-9 }
        lots.forEach { lot ->
            ProductionMath.validateIssueLotTracking(
                lotTracked = item.lotTracked,
                expiryTracked = item.expiryTracked,
                lotNo = lot.lotNo,
                expiryDate = lot.expiryDate,
                itemLabel = "${item.nameAr} (${item.code})"
            )
        }
        val available = lots.sumOf { it.quantityBase }
        require(available + 1e-9 >= additionalQty) {
            "المخزون المتاح لا يكفي لزيادة صرف ${item.nameAr}: المطلوب إضافيًا ${fmt(additionalQty)} والمتاح ${fmt(available)}"
        }

        var remaining = additionalQty
        var addedCost = 0.0
        for (lot in lots) {
            if (remaining <= 1e-9) break
            val take = min(remaining, lot.quantityBase)
            if (take <= 1e-9) continue
            val unitCost = if (lot.quantityBase <= 1e-9) 0.0 else lot.inventoryValueBase / lot.quantityBase
            val cost = take * unitCost
            val issueId = db.productionDao().insertIssue(
                ProductionIssueEntity(
                    orderId = order.id,
                    materialId = material.id,
                    itemId = material.itemId,
                    quantityBase = take,
                    unitCostBase = unitCost,
                    totalCostBase = cost,
                    lotNo = lot.lotNo,
                    expiryDate = lot.expiryDate,
                    issueKind = "CORRECTION_ISSUE",
                    reason = reason,
                    createdBy = createdBy,
                    issueDate = now
                )
            )
            db.stockDao().insertMovement(
                StockMovementEntity(
                    movementDate = now,
                    warehouseId = order.rawWarehouseId,
                    itemId = material.itemId,
                    movementType = "PRODUCTION_ISSUE_CORRECTION",
                    quantityBase = -take,
                    unitCostBase = unitCost,
                    referenceType = "PRODUCTION_ISSUE_CORRECTION",
                    referenceId = issueId,
                    lotNo = lot.lotNo,
                    expiryDate = lot.expiryDate
                )
            )
            remaining -= take
            addedCost += cost
        }
        require(remaining <= 1e-7) { "تعذر تخصيص كامل كمية الزيادة من التشغيلات المتاحة" }

        val netQty = db.productionDao().netIssuedQtyForMaterial(material.id)
        val netCost = db.productionDao().netIssueCostForMaterial(material.id)
        require(kotlin.math.abs(netQty - targetQtyBase) <= 1e-6) { "تعذر مطابقة صافي الصرف بعد الزيادة" }
        db.productionDao().updateMaterial(material.copy(issuedQtyBase = netQty, issueCostBase = netCost))
        return AddedIssue(additionalQty, addedCost)
    }

    private suspend fun increaseMaterialIssueCorrection(
        order: ProductionOrderEntity,
        material: ProductionMaterialEntity,
        item: ItemEntity,
        correctedIssuedQtyBase: Double,
        reason: String,
        createdBy: Long
    ): IssueCorrectionResult {
        require(order.status != "REJECTED") { "لا يمكن زيادة صرف مواد دفعة مرفوضة؛ راجع الدفعة أولاً" }
        val now = System.currentTimeMillis()
        val oldMaterialCost = db.productionDao().materialCostForOrder(order.id)
        val batchBefore = db.productionDao().batchForOrder(order.id)
        val isBottlePackaging = item.code.uppercase(Locale.US).startsWith("PK-BOTTLE-")

        if (isBottlePackaging) {
            ProductionMath.requireWholePieceQuantity(correctedIssuedQtyBase, "عدد العبوات النهائي")
        }

        val materials = db.productionDao().materialsForOrder(order.id)
        suspend fun materialByCode(code: String): ProductionMaterialEntity? {
            for (row in materials) {
                if (db.itemDao().byId(row.itemId)?.code?.equals(code, ignoreCase = true) == true) return row
            }
            return null
        }

        val selectedIncrease = issueAdditionalMaterialToTarget(
            order, material, correctedIssuedQtyBase, reason, createdBy, now
        )
        var totalAddedCost = selectedIncrease.costBase
        var bottleLabelsAdded = 0.0
        var packsAdded = 0.0
        var packLabelsAdded = 0.0
        var finishedGoodsAdded = 0.0

        if (isBottlePackaging) {
            val suffix = item.code.substringAfter("PK-BOTTLE-", "").trim()
            val labelCode = if (suffix.isBlank()) "PK-LABEL-60" else "PK-LABEL-$suffix"
            val labelMaterial = materialByCode(labelCode)
                ?: throw IllegalArgumentException("لا يوجد ملصق عبوة مرتبط ($labelCode) داخل مواد أمر الإنتاج")
            val labelAdd = issueAdditionalMaterialToTarget(order, labelMaterial, correctedIssuedQtyBase, reason, createdBy, now)
            bottleLabelsAdded = labelAdd.quantityBase
            totalAddedCost += labelAdd.costBase

            val targetPacks = ProductionMath.packagingPackCount(correctedIssuedQtyBase, 24)
            val packMaterial = materialByCode("PK-PACK")
                ?: throw IllegalArgumentException("لا يوجد باكيت تغليف PK-PACK داخل مواد أمر الإنتاج")
            val packAdd = issueAdditionalMaterialToTarget(order, packMaterial, targetPacks, reason, createdBy, now)
            packsAdded = packAdd.quantityBase
            totalAddedCost += packAdd.costBase

            materialByCode("PK-PACK-LABEL")?.let { packLabelMaterial ->
                val packLabelAdd = issueAdditionalMaterialToTarget(order, packLabelMaterial, targetPacks, reason, createdBy, now)
                packLabelsAdded = packLabelAdd.quantityBase
                totalAddedCost += packLabelAdd.costBase
            }

            // The bottle correction is the authoritative final piece count.
            // The actual finished-goods delta is calculated against the batch quantity below.
        }

        require(totalAddedCost >= -1e-7) { "تكلفة زيادة الصرف غير صالحة" }
        if (totalAddedCost > 1e-9) postMaterialIssueJournal(order, totalAddedCost, createdBy)

        val newMaterialCost = db.productionDao().materialCostForOrder(order.id)
        val actualAddedCost = newMaterialCost - oldMaterialCost
        require(kotlin.math.abs(actualAddedCost - totalAddedCost) <= 0.01) { "قيمة زيادة الصرف لا تطابق تكلفة المواد" }

        var correctedBatchUnitCost: Double? = null
        var finishedInventoryDelta = 0.0
        var cogsDelta = 0.0
        val batch = batchBefore

        if (batch != null && batch.status == "ACCEPTED" && order.status == "CLOSED") {
            require(batch.acceptedQtyBase > 0.0) { "الدفعة المقبولة لا تحتوي كمية صحيحة" }
            val oldAccepted = batch.acceptedQtyBase
            val newAccepted = if (isBottlePackaging) {
                require(batch.rejectedQtyBase <= 1e-9 && batch.scrapQtyBase <= 1e-9) {
                    "لا يمكن مزامنة المنتج النهائي تلقائيًا لأن الدفعة تحتوي كمية مرفوضة أو هالك؛ صحح بيانات الجودة أولاً"
                }
                require(correctedIssuedQtyBase + 1e-9 >= oldAccepted) { "عدد العبوات المصحح أقل من المنتج النهائي المقبول الحالي" }
                correctedIssuedQtyBase
            } else oldAccepted
            val newActual = if (isBottlePackaging) newAccepted else batch.actualOutputQtyBase
            finishedGoodsAdded = (newAccepted - oldAccepted).coerceAtLeast(0.0)
            ProductionMath.validateOutput(newActual, newAccepted, batch.rejectedQtyBase, batch.scrapQtyBase)

            val oldTotalCost = oldMaterialCost + order.directLaborCostBase
            val newTotalCost = newMaterialCost + order.directLaborCostBase
            val oldUnitCost = oldTotalCost / oldAccepted
            val newUnitCost = newTotalCost / newAccepted
            correctedBatchUnitCost = newUnitCost

            val allowedTypes = setOf(
                "PRODUCTION_RECEIPT", "PRODUCTION_RECEIPT_CORRECTION", "SALE", "SALES_RETURN",
                "PRODUCTION_COST_REVALUE_OUT", "PRODUCTION_COST_REVALUE_IN"
            )
            val movementTypes = db.stockDao().movementTypesForLot(order.finishedWarehouseId, order.productItemId, batch.batchNo)
            val unsupported = movementTypes.filterNot { it in allowedTypes }
            require(unsupported.isEmpty()) {
                "تشغيلة ${batch.batchNo} تحتوي حركات مخزون أخرى (${unsupported.joinToString()})؛ يجب مراجعتها قبل زيادة الإنتاج"
            }

            val oldLot = db.stockDao().lotBalances(order.finishedWarehouseId, order.productItemId)
                .firstOrNull { it.lotNo == batch.batchNo }
            val oldOnHandQty = oldLot?.quantityBase ?: 0.0
            val oldInventoryValue = oldLot?.inventoryValueBase ?: 0.0
            require(oldOnHandQty <= oldAccepted + 1e-7) { "رصيد الدفعة الحالي يتجاوز الكمية المقبولة قبل التصحيح" }

            if (finishedGoodsAdded > 1e-9) {
                db.stockDao().insertMovement(
                    StockMovementEntity(
                        movementDate = now,
                        warehouseId = order.finishedWarehouseId,
                        itemId = order.productItemId,
                        movementType = "PRODUCTION_RECEIPT_CORRECTION",
                        quantityBase = finishedGoodsAdded,
                        unitCostBase = newUnitCost,
                        referenceType = "PRODUCTION_OUTPUT_CORRECTION",
                        referenceId = batch.id,
                        lotNo = batch.batchNo,
                        expiryDate = batch.expiryDate
                    )
                )
            }

            val currentLot = db.stockDao().lotBalances(order.finishedWarehouseId, order.productItemId)
                .firstOrNull { it.lotNo == batch.batchNo }
            val correctedOnHandQty = currentLot?.quantityBase ?: 0.0
            if (correctedOnHandQty > 1e-9) {
                val currentValue = requireNotNull(currentLot).inventoryValueBase
                val currentUnit = currentValue / correctedOnHandQty
                if (kotlin.math.abs(currentUnit - newUnitCost) > 1e-7) {
                    db.stockDao().insertMovement(
                        StockMovementEntity(
                            movementDate = now,
                            warehouseId = order.finishedWarehouseId,
                            itemId = order.productItemId,
                            movementType = "PRODUCTION_COST_REVALUE_OUT",
                            quantityBase = -correctedOnHandQty,
                            unitCostBase = currentUnit,
                            referenceType = "PRODUCTION_OUTPUT_CORRECTION",
                            referenceId = batch.id,
                            lotNo = batch.batchNo,
                            expiryDate = batch.expiryDate
                        )
                    )
                    db.stockDao().insertMovement(
                        StockMovementEntity(
                            movementDate = now,
                            warehouseId = order.finishedWarehouseId,
                            itemId = order.productItemId,
                            movementType = "PRODUCTION_COST_REVALUE_IN",
                            quantityBase = correctedOnHandQty,
                            unitCostBase = newUnitCost,
                            referenceType = "PRODUCTION_OUTPUT_CORRECTION",
                            referenceId = batch.id,
                            lotNo = batch.batchNo,
                            expiryDate = batch.expiryDate
                        )
                    )
                }
            }

            revalueSalesLotCosts(order.productItemId, batch.batchNo, newUnitCost)
            finishedInventoryDelta = correctedOnHandQty * newUnitCost - oldInventoryValue
            cogsDelta = actualAddedCost - finishedInventoryDelta
            postAcceptedIssueIncreaseJournal(
                order = order,
                batch = batch,
                addedRawCost = actualAddedCost,
                finishedInventoryDelta = finishedInventoryDelta,
                cogsDelta = cogsDelta,
                oldUnitCost = oldUnitCost,
                newUnitCost = newUnitCost,
                reason = reason,
                createdBy = createdBy
            )
            if (finishedGoodsAdded > 1e-9) {
                db.productionDao().updateBatch(
                    batch.copy(
                        actualOutputQtyBase = newActual,
                        acceptedQtyBase = newAccepted
                    )
                )
            }
        } else if (batch != null && batch.status == "QC_HOLD" && isBottlePackaging) {
            require(correctedIssuedQtyBase + 1e-9 >= batch.actualOutputQtyBase) { "عدد العبوات المصحح أقل من الناتج الفعلي الحالي" }
            finishedGoodsAdded = (correctedIssuedQtyBase - batch.actualOutputQtyBase).coerceAtLeast(0.0)
            val newActual = correctedIssuedQtyBase
            ProductionMath.validateOutput(newActual, batch.acceptedQtyBase, batch.rejectedQtyBase, batch.scrapQtyBase)
            db.productionDao().updateBatch(batch.copy(actualOutputQtyBase = newActual))
        } else if (isBottlePackaging && batch?.status == "REJECTED") {
            throw IllegalArgumentException("لا يمكن زيادة الناتج النهائي لدفعة مرفوضة")
        }

        val updatedSelectedQty = db.productionDao().netIssuedQtyForMaterial(material.id)
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = createdBy,
                action = if (finishedGoodsAdded > 1e-9) "CORRECT_PRODUCTION_OUTPUT_UP" else "CORRECT_PRODUCTION_ISSUE_UP",
                entityType = "PRODUCTION_ORDER",
                entityId = order.id.toString(),
                oldValue = "materialId=${material.id}; issued=${fmt(material.issuedQtyBase)}; materialCost=${fmt(oldMaterialCost)}; accepted=${fmt(batchBefore?.acceptedQtyBase ?: 0.0)}",
                newValue = "issued=${fmt(updatedSelectedQty)}; added=${fmt(selectedIncrease.quantityBase)}; labelsAdded=${fmt(bottleLabelsAdded)}; packsAdded=${fmt(packsAdded)}; packLabelsAdded=${fmt(packLabelsAdded)}; finishedAdded=${fmt(finishedGoodsAdded)}; materialCost=${fmt(newMaterialCost)}",
                reason = reason
            )
        )

        return IssueCorrectionResult(
            returnedQtyBase = 0.0,
            returnedCostBase = 0.0,
            correctedIssuedQtyBase = updatedSelectedQty,
            correctedMaterialCostBase = newMaterialCost,
            correctedBatchUnitCostBase = correctedBatchUnitCost,
            finishedInventoryReductionBase = -finishedInventoryDelta,
            cogsReductionBase = -cogsDelta,
            addedQtyBase = selectedIncrease.quantityBase,
            addedCostBase = actualAddedCost,
            linkedBottleLabelsAddedBase = bottleLabelsAdded,
            linkedPacksAddedBase = packsAdded,
            linkedPackLabelsAddedBase = packLabelsAdded,
            finishedGoodsAddedBase = finishedGoodsAdded
        )
    }

    suspend fun beginPreparation(orderId: Long, userId: Long) {
        db.requireUserPermission(userId, SecurityPermissions.PRODUCTION_POST)
        moveOrder(orderId, "MATERIALS_ISSUED", "PREPARATION")
    }
    suspend fun beginMixing(orderId: Long, userId: Long) {
        db.requireUserPermission(userId, SecurityPermissions.PRODUCTION_POST)
        moveOrder(orderId, "PREPARATION", "MIXING")
    }
    suspend fun beginFilling(orderId: Long, userId: Long) {
        db.requireUserPermission(userId, SecurityPermissions.PRODUCTION_POST)
        val order = requireNotNull(db.productionDao().orderById(orderId)) { "أمر الإنتاج غير موجود" }
        order.primaryAssetId?.let { assetId ->
            MaintenanceService(db).assertAssetCanOperate(assetId)
            val operator = requireNotNull(db.employeeDao().operatorAssignment(orderId)) { "يجب تعيين مشغل مصرح له قبل بدء التعبئة" }
            EmployeeService(db).assertEmployeeCanOperateAsset(operator.employeeId, assetId)
        }
        moveOrder(orderId, "MIXING", "FILLING")
    }

    suspend fun submitForQuality(
        orderId: Long,
        actualOutputQtyBase: Double,
        scrapQtyBase: Double,
        notes: String = "",
        userId: Long
    ): Long = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.PRODUCTION_POST)
        require(actualOutputQtyBase > 0.0 && actualOutputQtyBase.isFinite()) { "الناتج الفعلي يجب أن يكون أكبر من صفر" }
        require(scrapQtyBase >= 0.0 && scrapQtyBase.isFinite()) { "التالف غير صالح" }
        val order = requireNotNull(db.productionDao().orderById(orderId)) { "أمر الإنتاج غير موجود" }
        require(order.status == "FILLING") { "يجب إكمال مرحلة التعبئة قبل إرسال الدفعة للجودة" }
        val product = requireNotNull(db.itemDao().allActive().firstOrNull { it.id == order.productItemId }) { "المنتج غير موجود" }
        val manufactureDate = System.currentTimeMillis()
        val shelfLifeDays = product.shelfLifeDays ?: 730
        require(shelfLifeDays > 0) { "يجب تحديد مدة صلاحية صحيحة للمنتج النهائي قبل إنشاء التشغيلة" }
        val expiryDate = manufactureDate + (shelfLifeDays.toLong() * 86_400_000L)
        require(expiryDate > manufactureDate) { "تاريخ انتهاء التشغيلة يجب أن يكون بعد تاريخ الإنتاج" }
        val batchNo = nextBatchNo(product, manufactureDate)
        val batchId = db.productionDao().insertBatch(
            ProductionBatchEntity(
                batchNo = batchNo,
                orderId = order.id,
                manufactureDate = manufactureDate,
                expiryDate = expiryDate,
                status = "QC_HOLD",
                actualOutputQtyBase = actualOutputQtyBase,
                scrapQtyBase = scrapQtyBase,
                notes = notes
            )
        )
        db.productionDao().updateOrder(order.copy(status = "QC_HOLD"))
        batchId
    }

    suspend fun saveQualitySpecification(
        existingId: Long? = null,
        productItemId: Long,
        stage: String = "FINAL",
        parameterName: String,
        unit: String,
        minValue: Double?,
        maxValue: Double?,
        targetValue: Double?,
        requiredSampleSize: Int,
        isRequired: Boolean,
        notes: String,
        userId: Long
    ): Long = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.QUALITY_DECIDE)
        require(stage in setOf("RECEIVING", "PREPARATION", "MIXING", "FILLING", "FINAL")) { "مرحلة مواصفة الجودة غير صالحة" }
        require(parameterName.isNotBlank()) { "اسم معيار الجودة مطلوب" }
        require(unit.isNotBlank()) { "وحدة قياس معيار الجودة مطلوبة" }
        ProductionMath.validateQualitySpecification(minValue, maxValue, targetValue, requiredSampleSize)
        val product = requireNotNull(db.itemDao().byId(productItemId)) { "المنتج غير موجود" }
        require(product.category == "FINISHED_GOOD") { "مواصفات الجودة الكمية في هذه الشاشة تخص المنتج النهائي" }
        val now = System.currentTimeMillis()
        if (existingId == null) {
            db.productionDao().insertQualitySpecification(
                QualitySpecificationEntity(
                    productItemId = productItemId,
                    stage = stage,
                    parameterName = parameterName.trim(),
                    unit = unit.trim(),
                    minValue = minValue,
                    maxValue = maxValue,
                    targetValue = targetValue,
                    requiredSampleSize = requiredSampleSize,
                    isRequired = isRequired,
                    notes = notes.trim(),
                    createdBy = userId,
                    createdAt = now,
                    updatedAt = now
                )
            )
        } else {
            val old = requireNotNull(db.productionDao().qualitySpecificationById(existingId)) { "مواصفة الجودة غير موجودة" }
            require(old.productItemId == productItemId) { "لا يمكن نقل مواصفة الجودة إلى منتج آخر" }
            db.productionDao().updateQualitySpecification(
                old.copy(
                    stage = stage,
                    parameterName = parameterName.trim(),
                    unit = unit.trim(),
                    minValue = minValue,
                    maxValue = maxValue,
                    targetValue = targetValue,
                    requiredSampleSize = requiredSampleSize,
                    isRequired = isRequired,
                    notes = notes.trim(),
                    updatedAt = now
                )
            )
            old.id
        }
    }

    suspend fun setQualitySpecificationActive(specificationId: Long, active: Boolean, userId: Long) = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.QUALITY_DECIDE)
        val old = requireNotNull(db.productionDao().qualitySpecificationById(specificationId)) { "مواصفة الجودة غير موجودة" }
        db.productionDao().updateQualitySpecification(old.copy(isActive = active, updatedAt = System.currentTimeMillis()))
    }

    suspend fun recordQuantitativeQualityCheck(
        batchId: Long,
        specificationId: Long,
        measuredValues: List<Double>,
        notes: String,
        checkedBy: Long
    ): Long = db.withTransaction {
        db.requireUserPermission(checkedBy, SecurityPermissions.QUALITY_DECIDE)
        val batch = requireNotNull(db.productionDao().batchById(batchId)) { "الدفعة غير موجودة" }
        require(batch.status == "QC_HOLD") { "يمكن تسجيل الفحص لدفعة تحت الفحص فقط" }
        val order = requireNotNull(db.productionDao().orderById(batch.orderId)) { "أمر الإنتاج غير موجود" }
        val spec = requireNotNull(db.productionDao().qualitySpecificationById(specificationId)) { "مواصفة الجودة غير موجودة" }
        require(spec.isActive) { "مواصفة الجودة غير نشطة" }
        require(spec.productItemId == order.productItemId) { "مواصفة الجودة لا تخص هذا المنتج" }
        ProductionMath.validateQualitySpecification(spec.minValue, spec.maxValue, spec.targetValue, spec.requiredSampleSize)
        ProductionMath.validateQualitySampleSize(measuredValues.size, spec.requiredSampleSize)
        val summary = ProductionMath.summarizeQualitySamples(measuredValues, spec.minValue, spec.maxValue)
        val decision = if (summary.failedCount == 0) "PASS" else "FAIL"
        val checkId = db.productionDao().insertQualityCheck(
            QualityCheckEntity(
                batchId = batch.id,
                stage = spec.stage,
                checkName = spec.parameterName,
                resultValue = measuredValues.joinToString(",") { it.toString() },
                specificationId = spec.id,
                measuredValue = summary.average,
                unit = spec.unit,
                minValue = spec.minValue,
                maxValue = spec.maxValue,
                targetValue = spec.targetValue,
                sampleSize = measuredValues.size,
                decision = decision,
                notes = notes.trim(),
                checkedBy = checkedBy
            )
        )
        db.productionDao().insertQualityCheckSamples(
            measuredValues.mapIndexed { index, value ->
                QualityCheckSampleEntity(
                    checkId = checkId,
                    sequenceNo = index + 1,
                    measuredValue = value
                )
            }
        )
        checkId
    }

    suspend fun recordQualityCheck(
        batchId: Long,
        stage: String,
        checkName: String,
        resultValue: String,
        decision: String,
        notes: String,
        checkedBy: Long
    ): Long = db.withTransaction {
        db.requireUserPermission(checkedBy, SecurityPermissions.QUALITY_DECIDE)
        require(decision in setOf("PASS", "FAIL")) { "قرار الفحص يجب أن يكون قبول أو رفض" }
        require(stage in setOf("RECEIVING", "PREPARATION", "MIXING", "FILLING", "FINAL")) { "مرحلة الفحص غير صالحة" }
        require(checkName.isNotBlank()) { "اسم الفحص مطلوب" }
        val batch = requireNotNull(db.productionDao().batchById(batchId)) { "الدفعة غير موجودة" }
        require(batch.status == "QC_HOLD") { "يمكن تسجيل الفحص لدفعة تحت الفحص فقط" }
        db.productionDao().insertQualityCheck(
            QualityCheckEntity(
                batchId = batch.id,
                stage = stage,
                checkName = checkName.trim(),
                resultValue = resultValue.trim(),
                decision = decision,
                notes = notes.trim(),
                checkedBy = checkedBy
            )
        )
    }

    suspend fun createNonConformance(
        batchId: Long,
        description: String,
        immediateAction: String,
        responsible: String,
        dueDate: Long?,
        createdBy: Long
    ): Long = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.QUALITY_DECIDE)
        require(description.isNotBlank()) { "وصف عدم المطابقة مطلوب" }
        requireNotNull(db.productionDao().batchById(batchId)) { "الدفعة غير موجودة" }
        db.productionDao().insertNonConformance(
            NonConformanceEntity(
                batchId = batchId,
                code = numbering.nextDocumentNo("NC"),
                description = description.trim(),
                immediateAction = immediateAction.trim(),
                responsible = responsible.trim(),
                dueDate = dueDate,
                createdBy = createdBy
            )
        )
    }

    suspend fun closeNonConformance(
        id: Long,
        rootCause: String,
        correctiveAction: String,
        preventiveAction: String,
        effectivenessVerified: Boolean,
        userId: Long
    ) = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.QUALITY_DECIDE)
        require(rootCause.isNotBlank()) { "السبب الجذري مطلوب" }
        require(correctiveAction.isNotBlank()) { "الإجراء التصحيحي مطلوب" }
        require(preventiveAction.isNotBlank()) { "الإجراء الوقائي مطلوب" }
        require(effectivenessVerified) { "يجب التحقق من فعالية الإجراء قبل الإغلاق" }
        val target = db.productionDao().nonConformanceById(id) ?: error("حالة عدم المطابقة غير موجودة")
        db.productionDao().updateNonConformance(
            target.copy(
                rootCause = rootCause.trim(),
                correctiveAction = correctiveAction.trim(),
                preventiveAction = preventiveAction.trim(),
                effectivenessVerified = true,
                status = "CLOSED",
                closedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun acceptBatch(batchId: Long, acceptedQtyBase: Double, createdBy: Long): CostSummary = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.QUALITY_DECIDE)
        val batch = requireNotNull(db.productionDao().batchById(batchId)) { "الدفعة غير موجودة" }
        require(batch.status == "QC_HOLD") { "الدفعة ليست تحت فحص الجودة" }
        require(db.productionDao().openNonConformanceCount(batchId) == 0) { "توجد حالة عدم مطابقة مفتوحة" }
        require(acceptedQtyBase > 0.0 && acceptedQtyBase <= batch.actualOutputQtyBase + 1e-9) { "الكمية المقبولة غير صالحة" }
        val order = requireNotNull(db.productionDao().orderById(batch.orderId)) { "أمر الإنتاج غير موجود" }
        require(order.status == "QC_HOLD") { "حالة أمر الإنتاج لا تسمح بالقبول" }
        val requiredSpecs = db.productionDao().activeQualitySpecificationsForProduct(order.productItemId, "FINAL").filter { it.isRequired }
        val checks = db.productionDao().checksForBatch(batchId)
        if (requiredSpecs.isNotEmpty()) {
            val incomplete = requiredSpecs.filter { spec ->
                checks.filter { it.specificationId == spec.id }
                    .maxWithOrNull(compareBy<QualityCheckEntity> { it.checkedAt }.thenBy { it.id })
                    ?.decision != "PASS"
            }
            require(incomplete.isEmpty()) {
                "لا يمكن قبول الدفعة قبل نجاح آخر قراءة لكل المواصفات الإلزامية: ${incomplete.joinToString("، ") { it.parameterName }}"
            }
        } else {
            require(db.productionDao().passedChecks(batchId) > 0) { "يجب تسجيل فحص جودة ناجح واحد على الأقل" }
            require(db.productionDao().failedChecks(batchId) == 0) { "لا يمكن قبول دفعة بها فحص مرفوض" }
        }

        val materialCost = db.productionDao().materialCostForOrder(order.id)
        val laborCost = order.directLaborCostBase
        val totalCost = materialCost + laborCost
        val unitCost = ProductionMath.actualUnitCost(materialCost, laborCost, acceptedQtyBase)
        val rejectedQty = (batch.actualOutputQtyBase - acceptedQtyBase).coerceAtLeast(0.0)
        ProductionMath.validateOutput(batch.actualOutputQtyBase, acceptedQtyBase, rejectedQty, batch.scrapQtyBase)

        postLaborToWip(order, laborCost, createdBy)
        val movementId = db.stockDao().insertMovement(
            StockMovementEntity(
                movementDate = System.currentTimeMillis(),
                warehouseId = order.finishedWarehouseId,
                itemId = order.productItemId,
                movementType = "PRODUCTION_RECEIPT",
                quantityBase = acceptedQtyBase,
                unitCostBase = unitCost,
                referenceType = "PRODUCTION_BATCH",
                referenceId = batch.id,
                lotNo = batch.batchNo,
                expiryDate = batch.expiryDate
            )
        )
        postFinishedGoodsJournal(order, batch, totalCost, createdBy, movementId)
        db.productionDao().updateBatch(
            batch.copy(
                status = "ACCEPTED",
                acceptedQtyBase = acceptedQtyBase,
                rejectedQtyBase = rejectedQty
            )
        )
        db.productionDao().updateOrder(order.copy(status = "CLOSED", closedAt = System.currentTimeMillis()))
        CostSummary(materialCost, laborCost, totalCost, acceptedQtyBase, unitCost)
    }

    suspend fun rejectBatch(batchId: Long, reason: String, createdBy: Long): CostSummary = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.QUALITY_DECIDE)
        require(reason.isNotBlank()) { "سبب الرفض مطلوب" }
        val batch = requireNotNull(db.productionDao().batchById(batchId)) { "الدفعة غير موجودة" }
        require(batch.status == "QC_HOLD") { "الدفعة ليست تحت فحص الجودة" }
        val order = requireNotNull(db.productionDao().orderById(batch.orderId)) { "أمر الإنتاج غير موجود" }
        val materialCost = db.productionDao().materialCostForOrder(order.id)
        val laborCost = order.directLaborCostBase
        val totalCost = materialCost + laborCost
        postLaborToWip(order, laborCost, createdBy)
        postRejectedBatchJournal(batch, totalCost, reason, createdBy)
        db.productionDao().updateBatch(
            batch.copy(status = "REJECTED", rejectedQtyBase = batch.actualOutputQtyBase, notes = listOf(batch.notes, reason).filter { it.isNotBlank() }.joinToString(" | "))
        )
        db.productionDao().updateOrder(order.copy(status = "REJECTED", closedAt = System.currentTimeMillis()))
        CostSummary(materialCost, laborCost, totalCost, 0.0, 0.0)
    }

    suspend fun orderCost(orderId: Long): CostSummary {
        val order = requireNotNull(db.productionDao().orderById(orderId)) { "أمر الإنتاج غير موجود" }
        val batch = db.productionDao().batchForOrder(orderId)
        val materialCost = db.productionDao().materialCostForOrder(orderId)
        val accepted = batch?.acceptedQtyBase ?: 0.0
        val total = materialCost + order.directLaborCostBase
        return CostSummary(
            materialCostBase = materialCost,
            laborCostBase = order.directLaborCostBase,
            totalCostBase = total,
            acceptedQtyBase = accepted,
            unitCostBase = if (accepted > 0) total / accepted else 0.0
        )
    }

    private suspend fun moveOrder(orderId: Long, expected: String, next: String) = db.withTransaction {
        val order = requireNotNull(db.productionDao().orderById(orderId)) { "أمر الإنتاج غير موجود" }
        require(order.status == expected) { "الانتقال غير مسموح من الحالة الحالية" }
        db.productionDao().updateOrder(order.copy(status = next))
    }

    private suspend fun revalueSalesLotCosts(itemId: Long, lotNo: String, newUnitCostBase: Double) {
        require(newUnitCostBase >= 0.0 && newUnitCostBase.isFinite()) { "تكلفة الدفعة المصححة غير صالحة" }
        val affectedReturnLineIds = linkedSetOf<Long>()
        val affectedReturnIds = linkedSetOf<Long>()
        db.salesDao().allocationsForLot(itemId, lotNo).forEach { allocation ->
            db.salesDao().updateAllocation(
                allocation.copy(
                    unitCostBase = newUnitCostBase,
                    costBase = allocation.quantityBase * newUnitCostBase
                )
            )
            db.salesDao().returnAllocationsForSalesAllocation(allocation.id).forEach { returned ->
                db.salesDao().updateReturnAllocation(
                    returned.copy(
                        unitCostBase = newUnitCostBase,
                        costBase = returned.quantityBase * newUnitCostBase
                    )
                )
                affectedReturnLineIds += returned.returnLineId
            }
        }
        affectedReturnLineIds.forEach { returnLineId ->
            val line = requireNotNull(db.salesDao().returnLineById(returnLineId)) { "سطر مرتجع مبيعات غير موجود أثناء إعادة التقييم" }
            val correctedCost = db.salesDao().returnAllocationsForLine(returnLineId).sumOf { it.costBase }
            db.salesDao().updateReturnLine(line.copy(costBase = correctedCost))
            affectedReturnIds += line.returnId
        }
        affectedReturnIds.forEach { returnId ->
            val row = requireNotNull(db.salesDao().returnById(returnId)) { "مرتجع مبيعات غير موجود أثناء إعادة التقييم" }
            val correctedCost = db.salesDao().returnLinesForReturn(returnId).sumOf { it.costBase }
            db.salesDao().updateReturn(row.copy(totalCostBase = correctedCost))
        }
    }

    private suspend fun postOpenIssueCorrectionJournal(order: ProductionOrderEntity, amount: Double, reason: String, createdBy: Long) {
        if (amount <= 1e-9) return
        val inventory = requireNotNull(db.accountDao().byCode("1200")) { "حساب المخزون 1200 غير موجود" }
        val wip = requireNotNull(db.accountDao().byCode("1210")) { "حساب الإنتاج تحت التشغيل 1210 غير موجود" }
        postJournal(
            sourceType = "PROD_ISSUE_CORR",
            sourceId = order.orderNo,
            description = "تصحيح صرف مواد لأمر الإنتاج ${order.orderNo}: $reason",
            createdBy = createdBy,
            lines = listOf(
                DraftJournalLine(inventory.id, amount, 0.0),
                DraftJournalLine(wip.id, 0.0, amount)
            )
        )
    }

    private suspend fun postRejectedIssueCorrectionJournal(order: ProductionOrderEntity, amount: Double, reason: String, createdBy: Long) {
        if (amount <= 1e-9) return
        val inventory = requireNotNull(db.accountDao().byCode("1200")) { "حساب المخزون 1200 غير موجود" }
        val loss = requireNotNull(db.accountDao().byCode("6300")) { "حساب خسائر الإنتاج 6300 غير موجود" }
        postJournal(
            sourceType = "PROD_REJECT_CORR",
            sourceId = order.orderNo,
            description = "تصحيح مواد دفعة إنتاج مرفوضة ${order.orderNo}: $reason",
            createdBy = createdBy,
            lines = listOf(
                DraftJournalLine(inventory.id, amount, 0.0),
                DraftJournalLine(loss.id, 0.0, amount)
            )
        )
    }

    private suspend fun postAcceptedIssueCorrectionJournal(
        order: ProductionOrderEntity,
        batch: ProductionBatchEntity,
        returnedRawCost: Double,
        finishedInventoryReduction: Double,
        cogsReduction: Double,
        oldUnitCost: Double,
        newUnitCost: Double,
        reason: String,
        createdBy: Long
    ) {
        if (returnedRawCost <= 1e-9) return
        val inventory = requireNotNull(db.accountDao().byCode("1200")) { "حساب المخزون 1200 غير موجود" }
        val cogs = requireNotNull(db.accountDao().byCode("5000")) { "حساب تكلفة المبيعات 5000 غير موجود" }
        val lines = mutableListOf(DraftJournalLine(inventory.id, returnedRawCost, 0.0))
        if (finishedInventoryReduction > 1e-9) lines += DraftJournalLine(inventory.id, 0.0, finishedInventoryReduction)
        if (cogsReduction > 1e-9) lines += DraftJournalLine(cogs.id, 0.0, cogsReduction)
        postJournal(
            sourceType = "PROD_COST_CORR",
            sourceId = order.orderNo,
            description = "تصحيح تكلفة الدفعة ${batch.batchNo} من ${fmt(oldUnitCost)} إلى ${fmt(newUnitCost)} ريال/وحدة: $reason",
            createdBy = createdBy,
            lines = lines
        )
    }

    private suspend fun postAcceptedIssueIncreaseJournal(
        order: ProductionOrderEntity,
        batch: ProductionBatchEntity,
        addedRawCost: Double,
        finishedInventoryDelta: Double,
        cogsDelta: Double,
        oldUnitCost: Double,
        newUnitCost: Double,
        reason: String,
        createdBy: Long
    ) {
        if (kotlin.math.abs(addedRawCost) <= 1e-9 && kotlin.math.abs(finishedInventoryDelta) <= 1e-9 && kotlin.math.abs(cogsDelta) <= 1e-9) return
        val inventory = requireNotNull(db.accountDao().byCode("1200")) { "حساب المخزون 1200 غير موجود" }
        val cogs = requireNotNull(db.accountDao().byCode("5000")) { "حساب تكلفة المبيعات 5000 غير موجود" }
        val wip = requireNotNull(db.accountDao().byCode("1210")) { "حساب الإنتاج تحت التشغيل 1210 غير موجود" }
        val lines = mutableListOf<DraftJournalLine>()
        fun addSigned(accountId: Long, amount: Double) {
            when {
                amount > 1e-9 -> lines += DraftJournalLine(accountId, amount, 0.0)
                amount < -1e-9 -> lines += DraftJournalLine(accountId, 0.0, -amount)
            }
        }
        addSigned(inventory.id, finishedInventoryDelta)
        addSigned(cogs.id, cogsDelta)
        if (addedRawCost > 1e-9) lines += DraftJournalLine(wip.id, 0.0, addedRawCost)
        postJournal(
            sourceType = "PROD_OUTPUT_CORR",
            sourceId = order.orderNo,
            description = "زيادة وتصحيح إنتاج الدفعة ${batch.batchNo} وتكلفتها من ${fmt(oldUnitCost)} إلى ${fmt(newUnitCost)} ريال/وحدة: $reason",
            createdBy = createdBy,
            lines = lines
        )
    }

    private suspend fun postMaterialIssueJournal(order: ProductionOrderEntity, amount: Double, createdBy: Long) {
        val wip = requireNotNull(db.accountDao().byCode("1210")) { "حساب الإنتاج تحت التشغيل 1210 غير موجود" }
        val inventory = requireNotNull(db.accountDao().byCode("1200")) { "حساب المخزون 1200 غير موجود" }
        postJournal(
            sourceType = "PRODUCTION_ISSUE",
            sourceId = order.orderNo,
            description = "صرف مواد لأمر الإنتاج ${order.orderNo}",
            createdBy = createdBy,
            lines = listOf(
                DraftJournalLine(wip.id, amount, 0.0),
                DraftJournalLine(inventory.id, 0.0, amount)
            )
        )
    }

    private suspend fun postLaborToWip(order: ProductionOrderEntity, amount: Double, createdBy: Long) {
        if (amount <= 0.0) return
        val assignment = requireNotNull(db.employeeDao().operatorAssignment(order.id)) {
            "لا يمكن ترحيل أجور الإنتاج دون موظف إنتاج مرتبط بالأمر ${order.orderNo}"
        }
        val employee = requireNotNull(db.employeeDao().employeeById(assignment.employeeId)) { "موظف الإنتاج المرتبط غير موجود" }
        val wip = requireNotNull(db.accountDao().byCode("1210")) { "حساب الإنتاج تحت التشغيل 1210 غير موجود" }
        val accrued = requireNotNull(db.accountDao().byCode("2200")) { "حساب أجور الإنتاج المستحقة 2200 غير موجود" }
        postJournal(
            sourceType = "PRODUCTION_LABOR",
            sourceId = order.orderNo,
            description = "استحقاق أجور/عمولة إنتاج للموظف ${employee.fullNameAr} عن أمر الإنتاج ${order.orderNo}",
            createdBy = createdBy,
            lines = listOf(
                DraftJournalLine(wip.id, amount, 0.0),
                DraftJournalLine(accrued.id, 0.0, amount)
            )
        )
    }

    private suspend fun postFinishedGoodsJournal(order: ProductionOrderEntity, batch: ProductionBatchEntity, amount: Double, createdBy: Long, movementId: Long) {
        val inventory = requireNotNull(db.accountDao().byCode("1200")) { "حساب المخزون 1200 غير موجود" }
        val wip = requireNotNull(db.accountDao().byCode("1210")) { "حساب الإنتاج تحت التشغيل 1210 غير موجود" }
        postJournal(
            sourceType = "PRODUCTION_RECEIPT",
            sourceId = movementId.toString(),
            description = "إدخال الدفعة المقبولة ${batch.batchNo} من الأمر ${order.orderNo}",
            createdBy = createdBy,
            lines = listOf(
                DraftJournalLine(inventory.id, amount, 0.0),
                DraftJournalLine(wip.id, 0.0, amount)
            )
        )
    }

    private suspend fun postRejectedBatchJournal(batch: ProductionBatchEntity, amount: Double, reason: String, createdBy: Long) {
        if (amount <= 0.0) return
        val loss = requireNotNull(db.accountDao().byCode("6300")) { "حساب خسائر الإنتاج 6300 غير موجود" }
        val wip = requireNotNull(db.accountDao().byCode("1210")) { "حساب الإنتاج تحت التشغيل 1210 غير موجود" }
        postJournal(
            sourceType = "PRODUCTION_REJECT",
            sourceId = batch.batchNo,
            description = "رفض دفعة ${batch.batchNo}: $reason",
            createdBy = createdBy,
            lines = listOf(
                DraftJournalLine(loss.id, amount, 0.0),
                DraftJournalLine(wip.id, 0.0, amount)
            )
        )
    }

    private suspend fun postJournal(
        sourceType: String,
        sourceId: String,
        description: String,
        createdBy: Long,
        lines: List<DraftJournalLine>
    ): Long {
        AccountingValidator.validate(lines)
        val entryId = db.journalDao().insertEntry(
            JournalEntryEntity(
                entryNo = "JE-${numbering.nextDocumentNo(sourceType.take(4))}",
                entryDate = System.currentTimeMillis(),
                description = description,
                currencyCode = "YER_NEW",
                exchangeRate = 1.0,
                sourceType = sourceType,
                sourceId = sourceId,
                createdBy = createdBy
            )
        )
        db.journalDao().insertLines(lines.map { JournalLineEntity(entryId = entryId, accountId = it.accountId, debit = it.debit, credit = it.credit) })
        return entryId
    }

    private suspend fun nextBatchNo(product: ItemEntity, manufactureDate: Long): String {
        val start = Calendar.getInstance().apply {
            timeInMillis = manufactureDate
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = start + 86_400_000L
        val seq = db.productionDao().countBatchesInRange(start, end) + 1
        val date = SimpleDateFormat("yyMMdd", Locale.US).format(Date(manufactureDate))
        val searchable = (product.code + " " + product.nameAr + " " + product.nameEn).uppercase(Locale.US)
        val prefix = when {
            searchable.contains("200") -> "F200"
            searchable.contains("60") -> "F60"
            else -> "F${product.id}"
        }
        return "$prefix-$date-${seq.toString().padStart(2, '0')}"
    }


    private suspend fun validateOrderBomIntegrity(
        order: ProductionOrderEntity,
        materials: List<ProductionMaterialEntity>
    ) {
        val components = db.recipeDao().components(order.recipeId)
        ProductionMath.validateBomIntegrity(
            recipeComponents = components.map { ProductionBomLink(it.id, it.itemId) },
            orderMaterials = materials.map { ProductionBomLink(it.recipeComponentId, it.itemId) }
        )
    }

    private fun fmt(value: Double): String = "%.3f".format(Locale.US, value)
}

