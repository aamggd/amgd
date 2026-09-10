package com.fush.erp.domain

import androidx.room.withTransaction
import com.fush.erp.data.FushDatabase
import com.fush.erp.data.entity.AuditEventEntity
import com.fush.erp.data.entity.ItemEntity
import com.fush.erp.data.entity.ItemUnitConversionEntity
import com.fush.erp.data.entity.UnitEntity
import com.fush.erp.data.entity.WarehouseEntity

class MasterDataService(private val db: FushDatabase) {
    private val numbering = AutoNumberService(db)

    private suspend fun audit(
        userId: Long,
        action: String,
        entityType: String,
        entityId: String,
        oldValue: String = "",
        newValue: String = "",
        reason: String
    ) {
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = userId,
                action = action,
                entityType = entityType,
                entityId = entityId,
                oldValue = oldValue,
                newValue = newValue,
                reason = reason
            )
        )
    }

    suspend fun createUnit(nameAr: String, nameEn: String = "", createdBy: Long): UnitEntity = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.MASTER_DATA_MANAGE)
        require(nameAr.isNotBlank()) { "اسم الوحدة مطلوب" }
        val code = numbering.nextUnitCode()
        val row = UnitEntity(code = code, nameAr = nameAr.trim(), nameEn = nameEn.trim())
        val id = db.unitDao().insert(row)
        val saved = row.copy(id = id)
        audit(createdBy, "CREATE", "UNIT", id.toString(), newValue = "${saved.code}|${saved.nameAr}|${saved.nameEn}|${saved.isActive}", reason = "إنشاء وحدة قياس")
        saved
    }

    suspend fun updateUnit(unitId: Long, nameAr: String, nameEn: String = "", updatedBy: Long): UnitEntity = db.withTransaction {
        db.requireUserPermission(updatedBy, SecurityPermissions.MASTER_DATA_MANAGE)
        require(nameAr.isNotBlank()) { "اسم الوحدة مطلوب" }
        val old = requireNotNull(db.unitDao().byId(unitId)) { "الوحدة غير موجودة" }
        val row = old.copy(nameAr = nameAr.trim(), nameEn = nameEn.trim())
        db.unitDao().update(row)
        audit(updatedBy, "UPDATE", "UNIT", unitId.toString(), "${old.code}|${old.nameAr}|${old.nameEn}|${old.isActive}", "${row.code}|${row.nameAr}|${row.nameEn}|${row.isActive}", "تعديل بيانات الوحدة")
        row
    }

    suspend fun setUnitActive(unitId: Long, active: Boolean, updatedBy: Long): UnitEntity = db.withTransaction {
        db.requireUserPermission(updatedBy, SecurityPermissions.MASTER_DATA_MANAGE)
        val old = requireNotNull(db.unitDao().byId(unitId)) { "الوحدة غير موجودة" }
        if (!active) {
            require(db.unitDao().activeBaseItemCount(unitId) == 0) { "لا يمكن إيقاف الوحدة لأنها وحدة أساسية لصنف نشط" }
            require(db.itemUnitConversionDao().activeCountForUnit(unitId) == 0) { "لا يمكن إيقاف الوحدة لأنها مستخدمة في تحويل وحدة نشط" }
        }
        val row = old.copy(isActive = active)
        db.unitDao().update(row)
        audit(updatedBy, if (active) "ACTIVATE" else "DEACTIVATE", "UNIT", unitId.toString(), old.isActive.toString(), row.isActive.toString(), if (active) "إعادة تفعيل الوحدة" else "إيقاف الوحدة")
        row
    }

    suspend fun createWarehouse(nameAr: String, nameEn: String = "", location: String = "", createdBy: Long): WarehouseEntity = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.MASTER_DATA_MANAGE)
        require(nameAr.isNotBlank()) { "اسم المخزن مطلوب" }
        val code = numbering.nextWarehouseCode()
        val row = WarehouseEntity(code = code, nameAr = nameAr.trim(), nameEn = nameEn.trim(), location = location.trim())
        val id = db.warehouseDao().insert(row)
        val saved = row.copy(id = id)
        audit(createdBy, "CREATE", "WAREHOUSE", id.toString(), newValue = "${saved.code}|${saved.nameAr}|${saved.nameEn}|${saved.location}|${saved.isActive}", reason = "إنشاء مخزن")
        saved
    }

    suspend fun updateWarehouse(warehouseId: Long, nameAr: String, nameEn: String, location: String, updatedBy: Long): WarehouseEntity = db.withTransaction {
        db.requireUserPermission(updatedBy, SecurityPermissions.MASTER_DATA_MANAGE)
        require(nameAr.isNotBlank()) { "اسم المخزن مطلوب" }
        val old = requireNotNull(db.warehouseDao().byId(warehouseId)) { "المخزن غير موجود" }
        val row = old.copy(nameAr = nameAr.trim(), nameEn = nameEn.trim(), location = location.trim())
        db.warehouseDao().update(row)
        audit(updatedBy, "UPDATE", "WAREHOUSE", warehouseId.toString(), "${old.code}|${old.nameAr}|${old.nameEn}|${old.location}|${old.isActive}", "${row.code}|${row.nameAr}|${row.nameEn}|${row.location}|${row.isActive}", "تعديل بيانات المخزن")
        row
    }

    suspend fun setWarehouseActive(warehouseId: Long, active: Boolean, updatedBy: Long): WarehouseEntity = db.withTransaction {
        db.requireUserPermission(updatedBy, SecurityPermissions.MASTER_DATA_MANAGE)
        val old = requireNotNull(db.warehouseDao().byId(warehouseId)) { "المخزن غير موجود" }
        if (!active) {
            MasterDataMath.requireEmptyBalanceForDeactivation(db.stockDao().absoluteWarehouseBalance(warehouseId), "المخزن")
        }
        val row = old.copy(isActive = active)
        db.warehouseDao().update(row)
        audit(updatedBy, if (active) "ACTIVATE" else "DEACTIVATE", "WAREHOUSE", warehouseId.toString(), old.isActive.toString(), row.isActive.toString(), if (active) "إعادة تفعيل المخزن" else "إيقاف مخزن فارغ")
        row
    }

    suspend fun createItem(
        nameAr: String,
        nameEn: String,
        category: String,
        baseUnitId: Long,
        reorderLevel: Double = 0.0,
        shelfLifeDays: Int? = null,
        lotTracked: Boolean = false,
        expiryTracked: Boolean = false,
        createdBy: Long
    ): ItemEntity = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.MASTER_DATA_MANAGE)
        require(nameAr.isNotBlank()) { "اسم الصنف مطلوب" }
        require(category in setOf("RAW_MATERIAL", "PACKAGING", "FINISHED_GOOD")) { "فئة الصنف غير صالحة" }
        require(reorderLevel >= 0.0 && reorderLevel.isFinite()) { "حد إعادة الطلب غير صالح" }
        require(shelfLifeDays == null || shelfLifeDays > 0) { "مدة الصلاحية يجب أن تكون أكبر من صفر" }
        val baseUnit = requireNotNull(db.unitDao().byId(baseUnitId)) { "الوحدة الأساسية غير موجودة" }
        require(baseUnit.isActive) { "الوحدة الأساسية موقوفة" }

        val code = numbering.nextItemCode(category)
        val effectiveShelfLife = if (category == "FINISHED_GOOD") (shelfLifeDays ?: 730) else shelfLifeDays
        val row = ItemEntity(
            code = code,
            nameAr = nameAr.trim(),
            nameEn = nameEn.trim(),
            category = category,
            baseUnitId = baseUnitId,
            reorderLevel = reorderLevel,
            shelfLifeDays = effectiveShelfLife,
            lotTracked = if (category == "FINISHED_GOOD") true else lotTracked,
            expiryTracked = if (category == "FINISHED_GOOD") true else expiryTracked
        )
        val id = db.itemDao().insert(row)
        db.itemUnitConversionDao().insert(
            ItemUnitConversionEntity(
                itemId = id,
                unitId = baseUnitId,
                factorToBase = 1.0,
                allowPurchase = category != "FINISHED_GOOD",
                allowSale = category == "FINISHED_GOOD"
            )
        )
        val saved = row.copy(id = id)
        audit(createdBy, "CREATE", "ITEM", id.toString(), newValue = "${saved.code}|${saved.nameAr}|${saved.category}|${saved.baseUnitId}|${saved.isActive}", reason = "إنشاء مادة/صنف")
        saved
    }

    suspend fun updateItem(
        itemId: Long,
        nameAr: String,
        nameEn: String,
        reorderLevel: Double,
        shelfLifeDays: Int?,
        lotTracked: Boolean,
        expiryTracked: Boolean,
        updatedBy: Long
    ): ItemEntity = db.withTransaction {
        db.requireUserPermission(updatedBy, SecurityPermissions.MASTER_DATA_MANAGE)
        require(nameAr.isNotBlank()) { "اسم الصنف مطلوب" }
        require(reorderLevel >= 0.0 && reorderLevel.isFinite()) { "حد إعادة الطلب غير صالح" }
        require(shelfLifeDays == null || shelfLifeDays > 0) { "مدة الصلاحية يجب أن تكون أكبر من صفر" }
        val old = requireNotNull(db.itemDao().byId(itemId)) { "الصنف غير موجود" }
        val effectiveShelfLife = if (old.category == "FINISHED_GOOD") (shelfLifeDays ?: old.shelfLifeDays ?: 730) else shelfLifeDays
        val row = old.copy(
            nameAr = nameAr.trim(),
            nameEn = nameEn.trim(),
            reorderLevel = reorderLevel,
            shelfLifeDays = effectiveShelfLife,
            lotTracked = if (old.category == "FINISHED_GOOD") true else lotTracked,
            expiryTracked = if (old.category == "FINISHED_GOOD") true else expiryTracked
        )
        db.itemDao().update(row)
        audit(updatedBy, "UPDATE", "ITEM", itemId.toString(), "${old.code}|${old.nameAr}|${old.nameEn}|${old.reorderLevel}|${old.shelfLifeDays}|${old.lotTracked}|${old.expiryTracked}|${old.isActive}", "${row.code}|${row.nameAr}|${row.nameEn}|${row.reorderLevel}|${row.shelfLifeDays}|${row.lotTracked}|${row.expiryTracked}|${row.isActive}", "تعديل بيانات المادة/الصنف")
        row
    }

    suspend fun setItemActive(itemId: Long, active: Boolean, updatedBy: Long): ItemEntity = db.withTransaction {
        db.requireUserPermission(updatedBy, SecurityPermissions.MASTER_DATA_MANAGE)
        val old = requireNotNull(db.itemDao().byId(itemId)) { "الصنف غير موجود" }
        if (!active) {
            MasterDataMath.requireEmptyBalanceForDeactivation(db.stockDao().absoluteItemBalance(itemId), "الصنف")
            require(db.itemDao().activeRecipeProductCount(itemId) == 0) { "لا يمكن إيقاف الصنف لأنه منتج في وصفة إنتاج نشطة" }
            require(db.itemDao().activeRecipeComponentCount(itemId) == 0) { "لا يمكن إيقاف الصنف لأنه مكوّن في وصفة إنتاج نشطة" }
        }
        val row = old.copy(isActive = active)
        db.itemDao().update(row)
        audit(updatedBy, if (active) "ACTIVATE" else "DEACTIVATE", "ITEM", itemId.toString(), old.isActive.toString(), row.isActive.toString(), if (active) "إعادة تفعيل الصنف" else "إيقاف صنف بدون رصيد أو وصفة نشطة")
        row
    }

    suspend fun saveConversion(
        conversionId: Long?,
        itemId: Long,
        unitId: Long,
        factorToBase: Double,
        allowPurchase: Boolean,
        allowSale: Boolean,
        barcode: String?,
        isActive: Boolean = true,
        updatedBy: Long
    ): ItemUnitConversionEntity = db.withTransaction {
        db.requireUserPermission(updatedBy, SecurityPermissions.MASTER_DATA_MANAGE)
        val item = requireNotNull(db.itemDao().byId(itemId)) { "الصنف غير موجود" }
        val unit = requireNotNull(db.unitDao().byId(unitId)) { "الوحدة غير موجودة" }
        require(item.isActive) { "الصنف موقوف" }
        require(unit.isActive) { "الوحدة موقوفة" }
        val isBase = item.baseUnitId == unitId
        MasterDataMath.validateConversionFactor(factorToBase, isBase)
        val normalizedBarcode = MasterDataMath.normalizeBarcode(barcode)

        val old = when {
            conversionId != null -> requireNotNull(db.itemUnitConversionDao().byId(conversionId)) { "تحويل الوحدة غير موجود" }
            else -> db.itemUnitConversionDao().byItemAndUnitAny(itemId, unitId)
        }
        if (old != null) {
            require(old.itemId == itemId && old.unitId == unitId) { "لا يمكن تغيير الصنف أو الوحدة لتحويل موجود" }
        }
        if (normalizedBarcode != null) {
            require(db.itemUnitConversionDao().barcodeConflictCount(normalizedBarcode, old?.id ?: 0L) == 0) { "الباركود مستخدم لتحويل وحدة آخر" }
        }
        val activeValue = if (isBase) true else isActive
        val row = ItemUnitConversionEntity(
            id = old?.id ?: 0,
            itemId = itemId,
            unitId = unitId,
            factorToBase = factorToBase,
            allowPurchase = allowPurchase,
            allowSale = allowSale,
            barcode = normalizedBarcode,
            isActive = activeValue
        )
        val saved = if (old == null) {
            val id = db.itemUnitConversionDao().insert(row)
            row.copy(id = id)
        } else {
            db.itemUnitConversionDao().update(row)
            row
        }
        audit(
            updatedBy,
            if (old == null) "CREATE" else "UPDATE",
            "ITEM_UNIT_CONVERSION",
            saved.id.toString(),
            old?.let { "${it.itemId}|${it.unitId}|${it.factorToBase}|${it.allowPurchase}|${it.allowSale}|${it.barcode}|${it.isActive}" } ?: "",
            "${saved.itemId}|${saved.unitId}|${saved.factorToBase}|${saved.allowPurchase}|${saved.allowSale}|${saved.barcode}|${saved.isActive}",
            if (old == null) "إنشاء تحويل وحدة للصنف" else "تعديل تحويل وحدة للصنف"
        )
        saved
    }

    suspend fun setConversionActive(conversionId: Long, active: Boolean, updatedBy: Long): ItemUnitConversionEntity = db.withTransaction {
        db.requireUserPermission(updatedBy, SecurityPermissions.MASTER_DATA_MANAGE)
        val old = requireNotNull(db.itemUnitConversionDao().byId(conversionId)) { "تحويل الوحدة غير موجود" }
        val item = requireNotNull(db.itemDao().byId(old.itemId)) { "الصنف غير موجود" }
        if (!active) require(old.unitId != item.baseUnitId) { "لا يمكن إيقاف تحويل الوحدة الأساسية" }
        if (active) {
            require(item.isActive) { "لا يمكن تفعيل التحويل لأن الصنف موقوف" }
            require(db.unitDao().byId(old.unitId)?.isActive == true) { "لا يمكن تفعيل التحويل لأن الوحدة موقوفة" }
        }
        val row = old.copy(isActive = active)
        db.itemUnitConversionDao().update(row)
        audit(updatedBy, if (active) "ACTIVATE" else "DEACTIVATE", "ITEM_UNIT_CONVERSION", conversionId.toString(), old.isActive.toString(), row.isActive.toString(), if (active) "إعادة تفعيل تحويل الوحدة" else "إيقاف تحويل الوحدة")
        row
    }
}
