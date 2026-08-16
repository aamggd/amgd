package com.fush.erp.domain

import androidx.room.withTransaction
import com.fush.erp.data.FushDatabase
import com.fush.erp.data.entity.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

class AdvancedInventoryService(private val db: FushDatabase) {
    suspend fun startCount(warehouseId: Long, createdBy: Long, notes: String = ""): Long = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.INVENTORY_COUNT)
        require(db.warehouseDao().allActive().any { it.id == warehouseId }) { "المخزن غير موجود" }
        val countId = db.advancedInventoryDao().insertCount(
            InventoryCountEntity(
                countNo = documentNo("CNT"),
                warehouseId = warehouseId,
                countDate = System.currentTimeMillis(),
                notes = notes.trim(),
                createdBy = createdBy
            )
        )
        val snapshot = db.advancedInventoryDao().snapshot(warehouseId)
        val lines = snapshot.map { row ->
            val unitCost = if (abs(row.quantityBase) <= InventoryMath.EPS) 0.0 else row.inventoryValueBase / row.quantityBase
            InventoryCountLineEntity(
                countId = countId,
                itemId = row.itemId,
                lotNo = row.lotNo,
                expiryDate = row.expiryDate,
                lotKey = InventoryMath.lotKey(row.lotNo),
                expiryKey = InventoryMath.expiryKey(row.expiryDate),
                systemQtyBase = row.quantityBase,
                unitCostBase = unitCost.coerceAtLeast(0.0)
            )
        }
        if (lines.isNotEmpty()) db.advancedInventoryDao().insertCountLines(lines)
        countId
    }

    suspend fun setCountedQuantity(lineId: Long, countedQtyBase: Double, reason: String = "", userId: Long) = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.INVENTORY_COUNT)
        val line = requireNotNull(db.advancedInventoryDao().countLineById(lineId)) { "سطر الجرد غير موجود" }
        val count = requireNotNull(db.advancedInventoryDao().countById(line.countId)) { "محضر الجرد غير موجود" }
        require(count.status == "DRAFT") { "لا يمكن تعديل جرد مرحّل" }
        val variance = InventoryMath.variance(line.systemQtyBase, countedQtyBase)
        db.advancedInventoryDao().updateCountLine(line.copy(countedQtyBase = countedQtyBase, varianceQtyBase = variance, reason = reason.trim()))
    }

    suspend fun addMissingCountLine(
        countId: Long,
        itemId: Long,
        countedQtyBase: Double,
        unitCostBase: Double,
        lotNo: String? = null,
        expiryDate: Long? = null,
        reason: String = "",
        createdBy: Long
    ): Long = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.INVENTORY_COUNT)
        val count = requireNotNull(db.advancedInventoryDao().countById(countId)) { "محضر الجرد غير موجود" }
        require(count.status == "DRAFT") { "لا يمكن إضافة سطر إلى جرد مرحّل" }
        val item = requireNotNull(db.itemDao().byId(itemId)) { "الصنف غير موجود" }
        require(item.isActive) { "الصنف غير نشط" }

        InventoryCountMath.validateMissingLine(
            InventoryCountMath.MissingLineInput(
                countedQtyBase = countedQtyBase,
                unitCostBase = unitCostBase,
                lotTracked = item.lotTracked,
                expiryTracked = item.expiryTracked,
                lotNo = lotNo,
                expiryDate = expiryDate
            )
        )

        val normalizedLot = if (item.lotTracked) lotNo?.trim() else null
        val normalizedExpiry = if (item.expiryTracked) expiryDate else null
        val lotKey = InventoryMath.lotKey(normalizedLot)
        val expiryKey = InventoryMath.expiryKey(normalizedExpiry)
        val duplicate = db.advancedInventoryDao().countLines(countId).any {
            it.itemId == itemId && it.lotKey == lotKey && it.expiryKey == expiryKey
        }
        require(!duplicate) { "هذا الصنف/التشغيلة موجود بالفعل في محضر الجرد" }

        val row = InventoryCountLineEntity(
            countId = countId,
            itemId = itemId,
            lotNo = normalizedLot,
            expiryDate = normalizedExpiry,
            lotKey = lotKey,
            expiryKey = expiryKey,
            systemQtyBase = 0.0,
            countedQtyBase = countedQtyBase,
            varianceQtyBase = countedQtyBase,
            unitCostBase = unitCostBase,
            reason = reason.trim()
        )
        db.advancedInventoryDao().insertCountLines(listOf(row))
        val inserted = db.advancedInventoryDao().countLines(countId).firstOrNull {
            it.itemId == itemId && it.lotKey == lotKey && it.expiryKey == expiryKey
        } ?: error("تعذر إنشاء سطر الجرد")
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = createdBy,
                action = "CREATE",
                entityType = "INVENTORY_COUNT_MISSING_LINE",
                entityId = inserted.id.toString(),
                oldValue = "",
                newValue = "count=$countId|item=$itemId|system=0|counted=$countedQtyBase|lot=$lotKey|expiry=$expiryKey|cost=$unitCostBase",
                reason = reason.trim().ifBlank { "صنف/تشغيلة موجودة فعلياً وغير موجودة في لقطة بداية الجرد" }
            )
        )
        inserted.id
    }

    suspend fun postCount(countId: Long, createdBy: Long): Long? = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.INVENTORY_COUNT)
        val count = requireNotNull(db.advancedInventoryDao().countById(countId)) { "محضر الجرد غير موجود" }
        require(count.status == "DRAFT") { "الجرد مرحّل مسبقاً" }
        val lines = db.advancedInventoryDao().countLines(countId)
        require(lines.isNotEmpty()) { "لا توجد أرصدة في هذا الجرد" }
        require(lines.all { it.countedQtyBase != null }) { "يجب إدخال الكمية الفعلية لكل سطر قبل الترحيل" }

        var positiveValue = 0.0
        var negativeValue = 0.0
        val now = System.currentTimeMillis()
        lines.forEach { line ->
            val variance = line.varianceQtyBase
            if (abs(variance) <= InventoryMath.EPS) return@forEach
            val movementId = db.stockDao().insertMovement(
                StockMovementEntity(
                    movementDate = now,
                    warehouseId = count.warehouseId,
                    itemId = line.itemId,
                    movementType = "COUNT_ADJUSTMENT",
                    quantityBase = variance,
                    unitCostBase = line.unitCostBase,
                    referenceType = "INVENTORY_COUNT",
                    referenceId = countId,
                    lotNo = line.lotNo,
                    expiryDate = line.expiryDate
                )
            )
            require(movementId > 0) { "تعذر ترحيل فرق الجرد" }
            val value = abs(InventoryMath.varianceValue(variance, line.unitCostBase))
            if (variance > 0) positiveValue += value else negativeValue += value
        }

        val inventory = requireNotNull(db.accountDao().byCode("1200")) { "حساب المخزون غير موجود" }
        val loss = requireNotNull(db.accountDao().byCode("6300")) { "حساب فروق النقص غير موجود" }
        val gain = requireNotNull(db.accountDao().byCode("4200")) { "حساب فروق الزيادة غير موجود" }
        val draft = mutableListOf<DraftJournalLine>()
        if (positiveValue > InventoryMath.EPS) {
            draft += DraftJournalLine(inventory.id, positiveValue, 0.0)
            draft += DraftJournalLine(gain.id, 0.0, positiveValue)
        }
        if (negativeValue > InventoryMath.EPS) {
            draft += DraftJournalLine(loss.id, negativeValue, 0.0)
            draft += DraftJournalLine(inventory.id, 0.0, negativeValue)
        }
        val entryId = if (draft.isNotEmpty()) {
            AccountingValidator.validate(draft)
            val id = db.journalDao().insertEntry(
                JournalEntryEntity(
                    entryNo = "JE-${documentNo("CNT")}",
                    entryDate = now,
                    description = "فروق جرد ${count.countNo}",
                    currencyCode = "YER_NEW",
                    exchangeRate = 1.0,
                    sourceType = "INVENTORY_COUNT",
                    sourceId = countId.toString(),
                    createdBy = createdBy
                )
            )
            db.journalDao().insertLines(draft.map { JournalLineEntity(entryId = id, accountId = it.accountId, debit = it.debit, credit = it.credit) })
            id
        } else null
        db.advancedInventoryDao().updateCount(count.copy(status = "POSTED", postedAt = now))
        entryId
    }

    suspend fun assignLegacyLotAndExpiry(
        warehouseId: Long,
        itemId: Long,
        sourceLotNo: String?,
        sourceExpiryDate: Long?,
        quantityBase: Double,
        targetLotNo: String?,
        targetExpiryDate: Long?,
        changedBy: Long,
        reason: String = ""
    ) = db.withTransaction {
        db.requireUserPermission(changedBy, SecurityPermissions.INVENTORY_ADJUST)
        require(quantityBase > InventoryMath.EPS && quantityBase.isFinite()) { "الكمية المراد ربطها يجب أن تكون أكبر من صفر" }
        val item = requireNotNull(db.itemDao().byId(itemId)) { "الصنف غير موجود" }
        require(item.isActive) { "الصنف غير نشط" }
        require(item.lotTracked || item.expiryTracked) { "هذا الصنف لا يتطلب تتبع تشغيلة أو صلاحية" }
        require(db.warehouseDao().allActive().any { it.id == warehouseId }) { "المخزن غير موجود أو غير نشط" }

        val normalizedTargetLot = if (item.lotTracked) targetLotNo?.trim().orEmpty().also {
            require(it.isNotBlank()) { "رقم التشغيلة مطلوب للصنف ${item.nameAr}" }
        } else null
        val normalizedTargetExpiry = if (item.expiryTracked) targetExpiryDate.also {
            require(it != null && it > 0L) { "تاريخ الصلاحية مطلوب للصنف ${item.nameAr}" }
        } else null

        val sourceLotKey = InventoryMath.lotKey(sourceLotNo)
        val sourceExpiryKey = InventoryMath.expiryKey(sourceExpiryDate)
        val source = db.stockDao().lotBalances(warehouseId, itemId).firstOrNull {
            InventoryMath.lotKey(it.lotNo) == sourceLotKey && InventoryMath.expiryKey(it.expiryDate) == sourceExpiryKey
        } ?: error("لم يعد الرصيد القديم المحدد متاحاً؛ حدّث الشاشة وحاول مرة أخرى")

        val isLegacyForCurrentRules = (item.lotTracked && source.lotNo.isNullOrBlank()) ||
            (item.expiryTracked && source.expiryDate == null)
        require(isLegacyForCurrentRules) { "الرصيد المحدد مكتمل بالفعل من حيث التشغيلة والصلاحية" }
        require(quantityBase <= source.quantityBase + InventoryMath.EPS) { "الكمية المطلوبة أكبر من الرصيد القديم المتاح (${source.quantityBase})" }

        val targetLotKey = InventoryMath.lotKey(normalizedTargetLot)
        val targetExpiryKey = InventoryMath.expiryKey(normalizedTargetExpiry)
        require(sourceLotKey != targetLotKey || sourceExpiryKey != targetExpiryKey) { "يجب أن تكون التشغيلة/الصلاحية الجديدة مختلفة عن الرصيد القديم" }

        val unitCostBase = if (source.quantityBase <= InventoryMath.EPS) 0.0 else source.inventoryValueBase / source.quantityBase
        require(unitCostBase.isFinite() && unitCostBase >= 0.0) { "تعذر تحديد تكلفة الرصيد القديم" }
        val now = System.currentTimeMillis()
        val auditId = db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = changedBy,
                action = "RECLASSIFY",
                entityType = "LEGACY_INVENTORY_LOT",
                entityId = "$warehouseId:$itemId",
                oldValue = "qty=$quantityBase|lot=${source.lotNo ?: ""}|expiry=${source.expiryDate ?: -1}|unitCost=$unitCostBase",
                newValue = "qty=$quantityBase|lot=${normalizedTargetLot ?: ""}|expiry=${normalizedTargetExpiry ?: -1}|unitCost=$unitCostBase",
                reason = reason.trim().ifBlank { "ربط رصيد مخزون قديم بتشغيلة/صلاحية دون تغيير الكمية أو القيمة" }
            )
        )
        require(auditId > 0L) { "تعذر تسجيل عملية الربط في سجل التدقيق" }

        val outId = db.stockDao().insertMovement(
            StockMovementEntity(
                movementDate = now,
                warehouseId = warehouseId,
                itemId = itemId,
                movementType = "LEGACY_LOT_RECLASS_OUT",
                quantityBase = -quantityBase,
                unitCostBase = unitCostBase,
                referenceType = "LEGACY_LOT_ASSIGNMENT",
                referenceId = auditId,
                lotNo = source.lotNo,
                expiryDate = source.expiryDate
            )
        )
        val inId = db.stockDao().insertMovement(
            StockMovementEntity(
                movementDate = now,
                warehouseId = warehouseId,
                itemId = itemId,
                movementType = "LEGACY_LOT_RECLASS_IN",
                quantityBase = quantityBase,
                unitCostBase = unitCostBase,
                referenceType = "LEGACY_LOT_ASSIGNMENT",
                referenceId = auditId,
                lotNo = normalizedTargetLot,
                expiryDate = normalizedTargetExpiry
            )
        )
        require(outId > 0L && inId > 0L) { "تعذر ترحيل ربط الرصيد القديم" }
    }

    suspend fun setLotStatus(
        warehouseId: Long,
        itemId: Long,
        lotNo: String?,
        expiryDate: Long?,
        status: String,
        reason: String,
        changedBy: Long
    ) = db.withTransaction {
        db.requireUserPermission(changedBy, SecurityPermissions.INVENTORY_ADJUST)
        require(status in setOf("ACCEPTED", "QUARANTINE", "BLOCKED", "RETURNED")) { "حالة التشغيلة غير صالحة" }
        val key = InventoryMath.lotKey(lotNo)
        val expKey = InventoryMath.expiryKey(expiryDate)
        val existing = db.advancedInventoryDao().lotControl(warehouseId, itemId, key, expKey)
        db.advancedInventoryDao().upsertLotControl(
            InventoryLotControlEntity(
                id = existing?.id ?: 0,
                warehouseId = warehouseId,
                itemId = itemId,
                lotNo = lotNo,
                expiryDate = expiryDate,
                lotKey = key,
                expiryKey = expKey,
                status = status,
                reason = reason.trim(),
                changedBy = changedBy
            )
        )
    }

    suspend fun setWarehouseReorderPolicy(
        warehouseId: Long,
        itemId: Long,
        reorderLevel: Double,
        updatedBy: Long
    ): Long = db.withTransaction {
        db.requireUserPermission(updatedBy, SecurityPermissions.INVENTORY_ADJUST)
        val normalizedLevel = WarehouseReorderMath.validateLevel(reorderLevel)
        require(db.warehouseDao().allActive().any { it.id == warehouseId }) { "المخزن غير موجود أو غير نشط" }
        requireNotNull(db.itemDao().byId(itemId)) { "الصنف غير موجود" }
        val existing = db.advancedInventoryDao().reorderPolicy(warehouseId, itemId)
        val id = db.advancedInventoryDao().upsertReorderPolicy(
            WarehouseReorderPolicyEntity(
                id = existing?.id ?: 0,
                warehouseId = warehouseId,
                itemId = itemId,
                reorderLevel = normalizedLevel,
                updatedBy = updatedBy,
                updatedAt = System.currentTimeMillis()
            )
        )
        val policyId = if (existing != null) existing.id else id
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = updatedBy,
                action = if (existing == null) "CREATE" else "UPDATE",
                entityType = "WAREHOUSE_REORDER_POLICY",
                entityId = policyId.toString(),
                oldValue = existing?.reorderLevel?.toString() ?: "",
                newValue = normalizedLevel.toString(),
                reason = "warehouse=$warehouseId item=$itemId"
            )
        )
        policyId
    }

    suspend fun deleteWarehouseReorderPolicy(policyId: Long, userId: Long) = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.INVENTORY_ADJUST)
        val existing = requireNotNull(db.advancedInventoryDao().reorderPolicyById(policyId)) { "سياسة إعادة الطلب غير موجودة" }
        db.advancedInventoryDao().deleteReorderPolicy(policyId)
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = userId,
                action = "DELETE",
                entityType = "WAREHOUSE_REORDER_POLICY",
                entityId = policyId.toString(),
                oldValue = existing.reorderLevel.toString(),
                reason = "warehouse=${existing.warehouseId} item=${existing.itemId}"
            )
        )
    }

    suspend fun startWarehouseTransfer(
        fromWarehouseId: Long,
        toWarehouseId: Long,
        transferDate: Long,
        createdBy: Long,
        notes: String = ""
    ): Long = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.INVENTORY_TRANSFER)
        require(fromWarehouseId != toWarehouseId) { "يجب اختيار مخزنين مختلفين" }
        val warehouses = db.warehouseDao().allActive()
        val from = warehouses.firstOrNull { it.id == fromWarehouseId }
        val to = warehouses.firstOrNull { it.id == toWarehouseId }
        require(from != null) { "مخزن المصدر غير موجود أو غير نشط" }
        require(to != null) { "مخزن الوجهة غير موجود أو غير نشط" }
        require(transferDate > 0L) { "تاريخ التحويل غير صالح" }
        val id = db.advancedInventoryDao().insertTransfer(
            WarehouseTransferEntity(
                transferNo = documentNo("TRF"),
                transferDate = transferDate,
                fromWarehouseId = fromWarehouseId,
                toWarehouseId = toWarehouseId,
                notes = notes.trim(),
                createdBy = createdBy
            )
        )
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = createdBy,
                action = "CREATE",
                entityType = "WAREHOUSE_TRANSFER",
                entityId = id.toString(),
                newValue = "${from.code}->${to.code}",
                reason = notes.trim()
            )
        )
        id
    }

    suspend fun addWarehouseTransferLine(
        transferId: Long,
        itemId: Long,
        quantityBase: Double,
        lotNo: String?,
        expiryDate: Long?,
        userId: Long
    ): Long = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.INVENTORY_TRANSFER)
        val transfer = requireNotNull(db.advancedInventoryDao().transferById(transferId)) { "تحويل المخزون غير موجود" }
        require(transfer.status == "DRAFT") { "لا يمكن تعديل تحويل مرحّل أو ملغي" }
        requireNotNull(db.itemDao().byId(itemId)) { "الصنف غير موجود" }
        val lotKey = InventoryMath.lotKey(lotNo)
        val expiryKey = InventoryMath.expiryKey(expiryDate)
        val sourceLot = db.stockDao().lotBalancesAt(transfer.fromWarehouseId, itemId, transfer.transferDate).firstOrNull {
            InventoryMath.lotKey(it.lotNo) == lotKey && InventoryMath.expiryKey(it.expiryDate) == expiryKey
        } ?: error("لا يوجد رصيد مطابق للصنف/التشغيلة في مخزن المصدر")
        WarehouseTransferMath.validateQuantity(quantityBase, sourceLot.quantityBase)
        validateHistoricalTransferAvailability(transfer.fromWarehouseId, itemId, lotKey, expiryKey, transfer.transferDate, quantityBase)
        val unitCost = WarehouseTransferMath.unitCost(sourceLot.quantityBase, sourceLot.inventoryValueBase)
        try {
            db.advancedInventoryDao().insertTransferLine(
                WarehouseTransferLineEntity(
                    transferId = transferId,
                    itemId = itemId,
                    quantityBase = quantityBase,
                    unitCostBase = unitCost,
                    lotNo = lotNo,
                    expiryDate = expiryDate,
                    lotKey = lotKey,
                    expiryKey = expiryKey
                )
            )
        } catch (_: Exception) {
            throw IllegalArgumentException("الصنف/التشغيلة مضافة مسبقاً إلى هذا التحويل")
        }
    }

    suspend fun removeWarehouseTransferLine(lineId: Long, userId: Long) = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.INVENTORY_TRANSFER)
        val line = requireNotNull(db.advancedInventoryDao().transferLineById(lineId)) { "سطر التحويل غير موجود" }
        val transfer = requireNotNull(db.advancedInventoryDao().transferById(line.transferId)) { "تحويل المخزون غير موجود" }
        require(transfer.status == "DRAFT") { "لا يمكن حذف سطر من تحويل مرحّل أو ملغي" }
        db.advancedInventoryDao().deleteTransferLine(lineId)
    }

    suspend fun postWarehouseTransfer(transferId: Long, postedBy: Long): Unit = db.withTransaction {
        db.requireUserPermission(postedBy, SecurityPermissions.INVENTORY_TRANSFER)
        val transfer = requireNotNull(db.advancedInventoryDao().transferById(transferId)) { "تحويل المخزون غير موجود" }
        require(transfer.status == "DRAFT") { "التحويل مرحّل أو ملغي مسبقاً" }
        require(transfer.fromWarehouseId != transfer.toWarehouseId) { "مخزن المصدر والوجهة يجب أن يكونا مختلفين" }
        val lines = db.advancedInventoryDao().transferLines(transferId)
        require(lines.isNotEmpty()) { "أضف صنفاً واحداً على الأقل قبل الترحيل" }

        data class PreparedLine(val line: WarehouseTransferLineEntity, val unitCost: Double, val control: InventoryLotControlEntity?)
        val prepared = lines.map { line ->
            val sourceLot = db.stockDao().lotBalancesAt(transfer.fromWarehouseId, line.itemId, transfer.transferDate).firstOrNull {
                InventoryMath.lotKey(it.lotNo) == line.lotKey && InventoryMath.expiryKey(it.expiryDate) == line.expiryKey
            } ?: error("الرصيد لم يعد متاحاً للصنف رقم ${line.itemId}. حدّث التحويل ثم حاول مجدداً")
            WarehouseTransferMath.validateQuantity(line.quantityBase, sourceLot.quantityBase)
            validateHistoricalTransferAvailability(
                transfer.fromWarehouseId, line.itemId, line.lotKey, line.expiryKey, transfer.transferDate, line.quantityBase
            )
            val cost = WarehouseTransferMath.unitCost(sourceLot.quantityBase, sourceLot.inventoryValueBase)
            val control = db.advancedInventoryDao().lotControl(
                transfer.fromWarehouseId, line.itemId, line.lotKey, line.expiryKey
            )
            PreparedLine(line, cost, control)
        }

        prepared.forEach { p ->
            val line = p.line
            db.stockDao().insertMovement(
                StockMovementEntity(
                    movementDate = transfer.transferDate,
                    warehouseId = transfer.fromWarehouseId,
                    itemId = line.itemId,
                    movementType = "TRANSFER_OUT",
                    quantityBase = -line.quantityBase,
                    unitCostBase = p.unitCost,
                    referenceType = "WAREHOUSE_TRANSFER",
                    referenceId = transferId,
                    lotNo = line.lotNo,
                    expiryDate = line.expiryDate
                )
            )
            db.stockDao().insertMovement(
                StockMovementEntity(
                    movementDate = transfer.transferDate,
                    warehouseId = transfer.toWarehouseId,
                    itemId = line.itemId,
                    movementType = "TRANSFER_IN",
                    quantityBase = line.quantityBase,
                    unitCostBase = p.unitCost,
                    referenceType = "WAREHOUSE_TRANSFER",
                    referenceId = transferId,
                    lotNo = line.lotNo,
                    expiryDate = line.expiryDate
                )
            )
            db.advancedInventoryDao().updateTransferLine(line.copy(unitCostBase = p.unitCost))

            val destinationControl = db.advancedInventoryDao().lotControl(
                transfer.toWarehouseId, line.itemId, line.lotKey, line.expiryKey
            )
            if (destinationControl == null && p.control != null) {
                db.advancedInventoryDao().upsertLotControl(
                    p.control.copy(
                        id = 0,
                        warehouseId = transfer.toWarehouseId,
                        reason = if (p.control.reason.isBlank()) {
                            "نُقلت حالة التشغيلة مع التحويل ${transfer.transferNo}"
                        } else {
                            "${p.control.reason} • منقول عبر ${transfer.transferNo}"
                        },
                        changedBy = postedBy,
                        changedAt = System.currentTimeMillis()
                    )
                )
            }
        }

        val now = System.currentTimeMillis()
        db.advancedInventoryDao().updateTransfer(
            transfer.copy(status = "POSTED", postedBy = postedBy, postedAt = now)
        )
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = postedBy,
                action = "POST",
                entityType = "WAREHOUSE_TRANSFER",
                entityId = transferId.toString(),
                oldValue = "DRAFT",
                newValue = "POSTED",
                reason = transfer.notes
            )
        )
    }

    suspend fun reverseWarehouseTransfer(transferId: Long, reason: String, userId: Long) = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.INVENTORY_TRANSFER)
        val transfer = requireNotNull(db.advancedInventoryDao().transferById(transferId)) { "تحويل المخزون غير موجود" }
        require(transfer.status == "POSTED") { "يمكن عكس التحويلات المرحلة فقط" }
        require(reason.isNotBlank()) { "سبب العكس مطلوب" }
        val lines = db.advancedInventoryDao().transferLines(transferId)
        require(lines.isNotEmpty()) { "لا توجد أصناف في التحويل" }

        val reversalDate = maxOf(System.currentTimeMillis(), transfer.transferDate)
        lines.forEach { line ->
            val destinationLot = db.stockDao().lotBalancesAt(transfer.toWarehouseId, line.itemId, reversalDate).firstOrNull {
                InventoryMath.lotKey(it.lotNo) == line.lotKey && InventoryMath.expiryKey(it.expiryDate) == line.expiryKey
            } ?: error("لا يوجد رصيد كافٍ في مخزن الوجهة لعكس الصنف رقم ${line.itemId}")
            WarehouseTransferMath.validateReversalAvailability(line.quantityBase, destinationLot.quantityBase)
            validateHistoricalTransferAvailability(
                transfer.toWarehouseId, line.itemId, line.lotKey, line.expiryKey, reversalDate, line.quantityBase
            )
        }

        lines.forEach { line ->
            db.stockDao().insertMovement(
                StockMovementEntity(
                    movementDate = reversalDate,
                    warehouseId = transfer.toWarehouseId,
                    itemId = line.itemId,
                    movementType = "TRANSFER_REVERSAL_OUT",
                    quantityBase = -line.quantityBase,
                    unitCostBase = line.unitCostBase,
                    referenceType = "WAREHOUSE_TRANSFER_REVERSAL",
                    referenceId = transferId,
                    lotNo = line.lotNo,
                    expiryDate = line.expiryDate
                )
            )
            db.stockDao().insertMovement(
                StockMovementEntity(
                    movementDate = reversalDate,
                    warehouseId = transfer.fromWarehouseId,
                    itemId = line.itemId,
                    movementType = "TRANSFER_REVERSAL_IN",
                    quantityBase = line.quantityBase,
                    unitCostBase = line.unitCostBase,
                    referenceType = "WAREHOUSE_TRANSFER_REVERSAL",
                    referenceId = transferId,
                    lotNo = line.lotNo,
                    expiryDate = line.expiryDate
                )
            )
        }

        db.advancedInventoryDao().updateTransfer(
            transfer.copy(
                status = "REVERSED",
                reversalReason = reason.trim(),
                reversedBy = userId,
                reversedAt = reversalDate
            )
        )
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = userId,
                action = "REVERSE",
                entityType = "WAREHOUSE_TRANSFER",
                entityId = transferId.toString(),
                oldValue = "POSTED",
                newValue = "REVERSED",
                reason = reason.trim()
            )
        )
    }

    suspend fun cancelWarehouseTransfer(transferId: Long, reason: String, userId: Long) = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.INVENTORY_TRANSFER)
        val transfer = requireNotNull(db.advancedInventoryDao().transferById(transferId)) { "تحويل المخزون غير موجود" }
        require(transfer.status == "DRAFT") { "يمكن إلغاء التحويلات المسودة فقط" }
        require(reason.isNotBlank()) { "سبب الإلغاء مطلوب" }
        db.advancedInventoryDao().updateTransfer(transfer.copy(status = "CANCELLED", cancelReason = reason.trim()))
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = userId,
                action = "CANCEL",
                entityType = "WAREHOUSE_TRANSFER",
                entityId = transferId.toString(),
                oldValue = "DRAFT",
                newValue = "CANCELLED",
                reason = reason.trim()
            )
        )
    }

    private suspend fun validateHistoricalTransferAvailability(
        warehouseId: Long,
        itemId: Long,
        lotKey: String,
        expiryKey: Long,
        transferDate: Long,
        requestedQtyBase: Double
    ) {
        val timeline = db.stockDao().lotMovementTimeline(warehouseId, itemId, lotKey, expiryKey)
            .map { WarehouseTransferBalancePoint(it.movementDate, it.quantityBase) }
        val minimumAvailable = WarehouseTransferMath.minimumAvailableFrom(timeline, transferDate)
        WarehouseTransferMath.validateHistoricalQuantity(requestedQtyBase, minimumAvailable)
    }

    suspend fun usableLots(warehouseId: Long, itemId: Long, at: Long = System.currentTimeMillis()): List<LotBalanceRow> {
        return db.stockDao().lotBalances(warehouseId, itemId).filter { lot ->
            if (InventoryMath.isExpired(lot.expiryDate, at)) return@filter false
            val control = db.advancedInventoryDao().lotControl(
                warehouseId, itemId, InventoryMath.lotKey(lot.lotNo), InventoryMath.expiryKey(lot.expiryDate)
            )
            control == null || control.status == "ACCEPTED"
        }
    }

    suspend fun usableBalance(warehouseId: Long, itemId: Long, at: Long = System.currentTimeMillis()): Double =
        usableLots(warehouseId, itemId, at).sumOf { it.quantityBase }

    private fun documentNo(prefix: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "$prefix-$stamp-${UUID.randomUUID().toString().take(6)}"
    }
}
