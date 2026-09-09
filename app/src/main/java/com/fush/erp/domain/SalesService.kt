package com.fush.erp.domain

import androidx.room.withTransaction
import com.fush.erp.data.FushDatabase
import com.fush.erp.data.entity.*
import kotlin.math.abs
import kotlin.math.min

class SalesService(
    private val db: FushDatabase,
    private val documentNumberGuard: DocumentNumberGuard = LocalOnlyDocumentNumberGuard,
) {
    private val numbering = AutoNumberService(db)

    private suspend fun requireUniqueDocumentNo(
        requestedRaw: String,
        label: String,
        exists: suspend (String) -> Boolean,
    ): String {
        val manual = SalesDocumentNumberPolicy.requireManual(requestedRaw, label)
        require(!exists(manual)) { "$label مستخدم مسبقاً: $manual" }
        return manual
    }

    data class PostSaleRequest(
        val customerId: Long,
        val warehouseId: Long,
        val currencyCode: String,
        val exchangeRate: Double,
        /** Required only when the invoice intentionally uses a rate different from the approved historical rate. */
        val exchangeRateOverrideReason: String = "",
        val paymentType: String,
        val creditDays: Int = 0,
        val discountPct: Double = 0.0,
        val transportOriginal: Double = 0.0,
        val feesOriginal: Double = 0.0,
        val riskMarginOriginal: Double = 0.0,
        val invoiceDate: Long = com.fush.erp.domain.TrustedTimeService.now(),
        val invoiceNo: String = "",
        val cashReceiptNo: String = "",
        val notes: String = "",
        val belowFloorApprovedBy: Long? = null,
        val belowFloorReason: String = "",
        val salesRepId: Long? = null,
        val freeQtyApprovalReason: String = "",
        val createdBy: Long,
        val lines: List<SalesDraftLine>,
        val treasuryAccountId: Long? = null,
        /** New charge rows created atomically with the invoice. */
        val additionalCharges: List<AdditionalChargesService.ChargeDraft> = emptyList(),
        /** Existing pre-invoice charges allocated to this invoice (many-to-many). */
        val additionalChargeAllocations: List<AdditionalChargesService.ExistingChargeAllocation> = emptyList()
    )

    data class SalePostResult(
        val invoiceId: Long,
        val invoiceNo: String,
        val totalBase: Double,
        val costBase: Double
    )

    data class ReceiptResult(
        val receiptId: Long,
        val receiptNo: String,
        val commissionBase: Double
    )

    data class ReceiptAllocationRequest(
        val invoiceId: Long,
        /** Actual cash allocated to this invoice in document currency. */
        val amountOriginal: Double,
        /** Non-cash settlement discount allocated to this invoice in document currency. */
        val discountOriginal: Double = 0.0
    )

    data class MultiReceiptResult(
        val receiptId: Long,
        val receiptNo: String,
        val allocationCount: Int,
        /** Actual cash collected in receipt currency. */
        val totalOriginal: Double,
        val discountOriginal: Double,
        /** Total A/R cleared (cash allocation + discount) in base currency. */
        val allocatedBase: Double,
        /** Actual treasury cash in base currency at receipt rate. */
        val cashBase: Double,
        val discountBase: Double,
        val fxDifferenceBase: Double,
        val commissionBase: Double
    )

    data class ReceiptReversalResult(
        val reversalReceiptId: Long,
        val reversalReceiptNo: String,
        val reversalJournalEntryId: Long,
        val restoredReceivableBase: Double,
        val reversedCommissionBase: Double
    )

    data class ReturnResult(
        val returnId: Long,
        val returnNo: String,
        val totalBase: Double,
        val costBase: Double,
        val commissionReversedBase: Double
    )

    suspend fun createCustomer(
        nameAr: String,
        phone: String,
        address: String = "",
        province: String,
        channel: String,
        currencyCode: String,
        creditLimitBase: Double,
        creditDays: Int,
        allowCredit: Boolean,
        salesRepName: String = "",
        createdBy: Long = 0L,
        salesRepId: Long? = null,
        governorateId: String? = null,
        districtId: String? = null,
        areaId: String? = null
    ): CustomerEntity = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.CUSTOMERS_CREATE)
        require(nameAr.isNotBlank()) { "اسم العميل مطلوب" }
        val location = GeographyService(db).resolveLocation(governorateId, districtId, areaId, province)
        require(channel in setOf("DIRECT", "RETAIL", "DISTRIBUTOR_CASH", "DISTRIBUTOR_CREDIT")) { "قناة البيع غير صالحة" }
        require(creditLimitBase >= 0.0 && creditLimitBase.isFinite()) { "السقف الائتماني غير صالح" }
        if (allowCredit) SalesMath.validateCreditDays(creditDays)
        require(db.currencyDao().allActive().any { it.code == currencyCode }) { "العملة غير موجودة" }
        val rep = salesRepId?.let { id ->
            requireNotNull(db.salesRepresentativeDao().byId(id)) { "مندوب المبيعات غير موجود" }.also {
                require(it.status == "ACTIVE") { "مندوب المبيعات غير نشط" }
            }
        }
        val code = allocateCompanySafeCustomerCode(createdBy)
        val row = CustomerEntity(
            code = code,
            nameAr = nameAr.trim(),
            phone = phone.trim(),
            address = address.trim(),
            province = location.governorate.nameAr,
            governorateId = location.governorate.id,
            districtId = location.district?.id,
            areaId = location.area?.id,
            channel = channel,
            currencyCode = currencyCode,
            creditLimitBase = creditLimitBase,
            creditDays = if (allowCredit) creditDays else 0,
            allowCredit = allowCredit,
            salesRepName = rep?.fullNameAr ?: salesRepName.trim(),
            salesRepId = rep?.id
        )
        val id = db.customerDao().insert(row)
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = createdBy,
                action = "CREATE",
                entityType = "CUSTOMER",
                entityId = id.toString(),
                newValue = "${row.code}|${row.nameAr}|${row.currencyCode}|${row.province}",
                reason = "إنشاء عميل"
            )
        )
        row.copy(id = id)
    }

    private suspend fun allocateCompanySafeCustomerCode(createdBy: Long): String {
        repeat(100) {
            val candidate = numbering.nextCustomerCode()
            // nextCustomerCode() already reconciles with every locally hydrated customer.
            // The cloud reservation additionally prevents two connected phones from allocating
            // the same new customer code concurrently. Offline-only installations keep working.
            when (val reservation = documentNumberGuard.reserve(createdBy, "CUSTOMER_MASTER", candidate)) {
                is DocumentNumberReservationResult.Reserved, DocumentNumberReservationResult.LocalOnly -> return candidate
                is DocumentNumberReservationResult.InUse -> Unit // advance the local sequence and retry
                is DocumentNumberReservationResult.Failure -> error("تعذر حجز كود العميل سحابياً: ${reservation.message}")
            }
        }
        error("تعذر توليد كود عميل فريد بعد عدة محاولات")
    }

    suspend fun updateCustomer(
        customerId: Long,
        nameAr: String,
        nameEn: String,
        phone: String,
        address: String,
        province: String,
        channel: String,
        classification: String,
        currencyCode: String,
        creditLimitBase: Double,
        creditDays: Int,
        allowCredit: Boolean,
        salesRepName: String,
        updatedBy: Long,
        salesRepId: Long? = null,
        governorateId: String? = null,
        districtId: String? = null,
        areaId: String? = null
    ): CustomerEntity = db.withTransaction {
        db.requireUserPermission(updatedBy, SecurityPermissions.CUSTOMERS_EDIT)
        require(nameAr.isNotBlank()) { "اسم العميل مطلوب" }
        val old = requireNotNull(db.customerDao().byId(customerId)) { "العميل غير موجود" }
        val location = GeographyService(db).resolveLocation(
            governorateId ?: old.governorateId, districtId ?: old.districtId, areaId ?: old.areaId, province.ifBlank { old.province }
        )
        require(channel in setOf("DIRECT", "RETAIL", "DISTRIBUTOR_CASH", "DISTRIBUTOR_CREDIT")) { "قناة البيع غير صالحة" }
        require(classification in setOf("A", "B", "C")) { "تصنيف العميل غير صالح" }
        require(creditLimitBase >= 0.0 && creditLimitBase.isFinite()) { "السقف الائتماني غير صالح" }
        if (allowCredit) SalesMath.validateCreditDays(creditDays)
        require(db.currencyDao().allActive().any { it.code == currencyCode }) { "العملة غير موجودة" }
        val requestedRepId = salesRepId ?: old.salesRepId?.takeIf { salesRepName.trim() == old.salesRepName.trim() }
        val rep = requestedRepId?.let { id ->
            requireNotNull(db.salesRepresentativeDao().byId(id)) { "مندوب المبيعات غير موجود" }.also { currentRep ->
                val unchangedAssignment = id == old.salesRepId && salesRepId == null && salesRepName.trim() == old.salesRepName.trim()
                if (!unchangedAssignment) require(currentRep.status == "ACTIVE") { "مندوب المبيعات غير نشط" }
            }
        }
        val row = old.copy(
            nameAr = nameAr.trim(),
            nameEn = nameEn.trim(),
            phone = phone.trim(),
            address = address.trim(),
            province = location.governorate.nameAr,
            governorateId = location.governorate.id,
            districtId = location.district?.id,
            areaId = location.area?.id,
            channel = channel,
            classification = classification,
            currencyCode = currencyCode,
            creditLimitBase = creditLimitBase,
            creditDays = if (allowCredit) creditDays else 0,
            allowCredit = allowCredit,
            salesRepName = rep?.fullNameAr ?: salesRepName.trim(),
            salesRepId = rep?.id
        )
        db.customerDao().update(row)
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = updatedBy,
                action = "UPDATE",
                entityType = "CUSTOMER",
                entityId = customerId.toString(),
                oldValue = "${old.code}|${old.nameAr}|${old.phone}|${old.province}|${old.channel}|${old.classification}|${old.currencyCode}|${old.creditLimitBase}|${old.creditDays}|${old.allowCredit}|${old.salesRepName}",
                newValue = "${row.code}|${row.nameAr}|${row.phone}|${row.province}|${row.channel}|${row.classification}|${row.currencyCode}|${row.creditLimitBase}|${row.creditDays}|${row.allowCredit}|${row.salesRepName}",
                reason = "تعديل بيانات العميل والائتمان"
            )
        )
        row
    }

    suspend fun postSale(request: PostSaleRequest): SalePostResult = db.withTransaction {
        db.requireUserPermission(request.createdBy, SecurityPermissions.SALES_POST)
        requireEffectiveDateNotFuture(request.invoiceDate, "تاريخ الفاتورة")
        AccountingService(db).requirePostingPeriodOpen(request.invoiceDate)
        require(request.paymentType in setOf("CASH", "CREDIT")) { "نوع البيع غير صالح" }
        SalesMath.validateExchangeRate(request.exchangeRate)
        val customerId = CustomerMovementIdentity.requireId(request.customerId)
        val customer = requireNotNull(db.customerDao().byId(customerId)) { "العميل غير موجود" }
        val salesRep = (request.salesRepId ?: customer.salesRepId)?.let { id ->
            requireNotNull(db.salesRepresentativeDao().byId(id)) { "مندوب المبيعات غير موجود" }.also {
                require(it.status == "ACTIVE") { "مندوب المبيعات غير نشط" }
            }
        }
        val salesRepNameSnapshot = salesRep?.fullNameAr ?: customer.salesRepName.trim()
        val salesRepRatePct = salesRep?.commissionRatePct ?: 0.0
        val freeQtyLimitPct = salesRep?.freeQtyLimitPct ?: 0.0
        require(db.warehouseDao().allActive().any { it.id == request.warehouseId }) { "المخزن غير موجود" }
        require(request.currencyCode == customer.currencyCode || request.currencyCode in setOf("YER_NEW", "YER_OLD", "USD")) { "عملة الفاتورة غير صالحة" }
        val historicalRate = exchangeRateAt(request.currencyCode, request.invoiceDate)
        val canonicalHistoricalRate = SalesExchangeRatePolicy.canonical(historicalRate)
        val canonicalRequestedRate = SalesExchangeRatePolicy.canonical(request.exchangeRate)
        val exchangeRateOverride = SalesExchangeRatePolicy.requiresOverride(canonicalRequestedRate, canonicalHistoricalRate)
        if (exchangeRateOverride) {
            db.requireUserPermission(request.createdBy, SecurityPermissions.EXCHANGE_RATE_OVERRIDE)
            require(request.exchangeRateOverrideReason.trim().length >= 5) {
                "تغيير سعر الصرف المعتمد يحتاج سبباً واضحاً لا يقل عن 5 أحرف. السعر المعتمد: $canonicalHistoricalRate"
            }
        }
        val cashTreasury = if (request.paymentType == "CASH") resolveTreasury(request.treasuryAccountId, request.currencyCode) else null

        val additionalChargesService = AdditionalChargesService(db)
        val newlyCreatedCharges = request.additionalCharges.map { draft ->
            additionalChargesService.createChargeInsideTransaction(customer, request.invoiceDate, draft, request.createdBy).charge
        }
        val requestedChargeAllocations = buildList {
            addAll(request.additionalChargeAllocations)
            newlyCreatedCharges.forEach { add(AdditionalChargesService.ExistingChargeAllocation(it.id, it.amountOriginal)) }
        }
        require(requestedChargeAllocations.map { it.chargeId }.distinct().size == requestedChargeAllocations.size) {
            "لا يجوز ربط نفس الرسم الإضافي أكثر من مرة في الفاتورة نفسها"
        }
        data class PreparedChargeAllocation(
            val charge: SalesAdditionalChargeEntity,
            val amountChargeOriginal: Double,
            val amountInvoiceOriginal: Double,
            val amountBase: Double
        )
        val preparedChargeAllocations = requestedChargeAllocations.map { allocation ->
            require(allocation.amountChargeOriginal > 0.0 && allocation.amountChargeOriginal.isFinite()) { "مبلغ تسوية الرسم يجب أن يكون أكبر من صفر" }
            val charge = requireNotNull(db.additionalChargesDao().chargeById(allocation.chargeId)) { "الرسم الإضافي غير موجود" }
            require(charge.status == "POSTED") { "لا يمكن ربط رسم إضافي ملغى" }
            require(charge.customerId == customer.id) { "الرسم الإضافي لا يخص عميل الفاتورة" }
            val alreadySettled = db.additionalChargesDao().settledOriginalForCharge(charge.id)
            val remainingOriginal = (charge.amountOriginal - alreadySettled).coerceAtLeast(0.0)
            require(allocation.amountChargeOriginal <= remainingOriginal + 1e-9) {
                "الرسم ${charge.chargeNo} لا يملك رصيداً كافياً للتسوية. المتبقي المتاح: %.2f %s".format(remainingOriginal, charge.currencyCode)
            }
            val base = allocation.amountChargeOriginal * charge.exchangeRate
            PreparedChargeAllocation(charge, allocation.amountChargeOriginal, base / request.exchangeRate, base)
        }
        val shipmentService = ShipmentService(db)
        // Plan every invoice line against one shared in-memory reservation map. This prevents two
        // draft lines from consuming the same remaining shipment quantity before allocations are persisted.
        val plannedShipmentQtyByItem = mutableMapOf<Long, Double>()
        val shipmentPlans = request.lines.map { line ->
            if (!line.useShipmentTracking) {
                // v186: explicit direct warehouse sale. Stock is still allocated by normal inventory FIFO,
                // but no shipment quantity/cost allocation is created and no shipment charge reaches the customer.
                emptyList()
            } else {
                val candidates = shipmentService.availableShipmentsForSale(request.warehouseId, line.itemId, customer.province, customer.governorateId)
                val preferred = line.preferredShipmentId ?: candidates.firstOrNull()?.shipmentId
                requireNotNull(preferred) { "لا توجد شحنة متاحة للصنف في سطر فاتورة البيع؛ اختر بدون شحنة للبيع المباشر من المخزن" }
                val plan = shipmentService.planSaleLineShipments(
                    warehouseId = request.warehouseId, itemId = line.itemId, province = customer.province, governorateId = customer.governorateId,
                    requiredQtyBase = line.totalBaseQuantity, preferredShipmentId = preferred,
                    additionalReservedByShipmentItem = plannedShipmentQtyByItem
                )
                plan.forEach { take ->
                    plannedShipmentQtyByItem[take.shipmentItemId] =
                        (plannedShipmentQtyByItem[take.shipmentItemId] ?: 0.0) + take.quantityBase
                }
                plan
            }
        }
        val shipmentExpensePreview = shipmentService.previewAutomaticInvoiceExpenses(shipmentPlans)
        val shipmentCustomerChargeOriginal = shipmentExpensePreview.customerChargeBase / request.exchangeRate

        val customerChargeOriginal = preparedChargeAllocations.filter { it.charge.bearer == "CUSTOMER" }.sumOf { it.amountInvoiceOriginal }
        val externalDirectOriginal = preparedChargeAllocations.filter { it.charge.bearer == "CUSTOMER" && it.charge.paidBy == "CUSTOMER_DIRECT" }.sumOf { it.amountInvoiceOriginal }
        val externalDirectBase = preparedChargeAllocations.filter { it.charge.bearer == "CUSTOMER" && it.charge.paidBy == "CUSTOMER_DIRECT" }.sumOf { it.amountBase }

        val grossOriginal = SalesMath.grossOriginal(request.lines)
        val totalBaseQty = SalesMath.totalBaseQuantity(request.lines)
        val totalFreeBaseQty = SalesMath.totalFreeBaseQuantity(request.lines)
        val totalPhysicalBaseQty = SalesMath.totalPhysicalBaseQuantity(request.lines)
        val freeQtyApprovalRequired = request.lines.any { line ->
            SalesFreeQuantityPolicy.requiresApproval(line.baseQuantity, line.freeBaseQuantity, freeQtyLimitPct)
        }
        if (freeQtyApprovalRequired) {
            db.requireUserPermission(request.createdBy, SecurityPermissions.SALES_FREE_QTY_APPROVE)
            require(request.freeQtyApprovalReason.isNotBlank()) { "سبب اعتماد تجاوز حد الكمية المجانية مطلوب" }
        }
        SalesMath.validateDiscount(request.paymentType, totalBaseQty, request.discountPct)
        val discountOriginal = SalesMath.discountOriginal(grossOriginal, request.discountPct)
        val legacyTotalOriginal = SalesMath.totalOriginal(
            grossOriginal,
            discountOriginal,
            request.transportOriginal,
            request.feesOriginal,
            request.riskMarginOriginal
        )
        val totalOriginal = legacyTotalOriginal + customerChargeOriginal + shipmentCustomerChargeOriginal
        require(totalOriginal > 0.0) { "إجمالي فاتورة البيع يجب أن يكون أكبر من صفر" }
        val totalBase = SalesMath.toBaseAmount(totalOriginal, request.exchangeRate)
        require(totalBase > 0.0) { "إجمالي فاتورة البيع بالعملة الأساسية يجب أن يكون أكبر من صفر" }
        val collectibleOriginal = (totalOriginal - externalDirectOriginal).coerceAtLeast(0.0)
        val collectibleBase = (totalBase - externalDirectBase).coerceAtLeast(0.0)
        require(collectibleBase > 0.0) { "المبلغ المطلوب تحصيله بعد الدفعات المباشرة يجب أن يكون أكبر من صفر" }

        request.lines.forEach { line ->
            SalesMath.validateLine(line)
            val item = requireNotNull(db.itemDao().byId(line.itemId)) { "الصنف غير موجود" }
            require(item.isActive) { "الصنف ${item.nameAr} موقوف ولا يمكن البيع عليه" }
            require(item.category == "FINISHED_GOOD") { "المبيعات في هذه المرحلة مخصصة للمنتج النهائي" }
            val conversion = requireNotNull(db.itemUnitConversionDao().byItemAndUnit(line.itemId, line.unitId)) { "وحدة البيع غير معرفة للصنف" }
            require(conversion.allowSale) { "هذه الوحدة غير مسموحة للبيع" }
            require(kotlin.math.abs(conversion.factorToBase - line.factorToBase) < 1e-9) { "عامل التحويل تغير، أعد تحميل الوحدة" }
            // Phase 14.5.18: manual sales pricing is allowed.
            // A valid price list is only an optional reference/default, not a posting prerequisite.
            // The entered unit price is preserved on the invoice line as the historical snapshot.
        }

        val dueDate = if (request.paymentType == "CREDIT") {
            validateCredit(customer, collectibleBase, request.creditDays, request.invoiceDate)
            request.invoiceDate + request.creditDays.toLong() * 24L * 60L * 60L * 1000L
        } else null

        val invoiceNo = requireUniqueDocumentNo(
            requestedRaw = request.invoiceNo,
            label = "رقم فاتورة البيع",
        ) { db.salesDao().invoiceNoCount(it) > 0 }
        when (val reservation = documentNumberGuard.reserve(request.createdBy, "SALE_INVOICE", invoiceNo)) {
            is DocumentNumberReservationResult.Reserved, DocumentNumberReservationResult.LocalOnly -> Unit
            is DocumentNumberReservationResult.InUse -> error("رقم فاتورة البيع مستخدم مسبقاً على جهاز آخر داخل الشركة: $invoiceNo")
            is DocumentNumberReservationResult.Failure -> error("تعذر حجز رقم فاتورة البيع سحابياً: ${reservation.message}")
        }
        val cashReceiptNo = if (request.paymentType == "CASH") {
            requireUniqueDocumentNo(
                requestedRaw = request.cashReceiptNo,
                label = "رقم سند التحصيل النقدي",
            ) { db.salesDao().receiptNoCount(it) > 0 }.also { receiptNo ->
                when (val reservation = documentNumberGuard.reserve(request.createdBy, "CUSTOMER_RECEIPT", receiptNo)) {
                    is DocumentNumberReservationResult.Reserved, DocumentNumberReservationResult.LocalOnly -> Unit
                    is DocumentNumberReservationResult.InUse -> error("رقم سند التحصيل مستخدم مسبقاً على جهاز آخر داخل الشركة: $receiptNo")
                    is DocumentNumberReservationResult.Failure -> error("تعذر حجز رقم سند التحصيل سحابياً: ${reservation.message}")
                }
            }
        } else null
        val invoiceId = db.salesDao().insertInvoice(
            SalesInvoiceEntity(
                invoiceNo = invoiceNo,
                customerId = customer.id,
                invoiceDate = request.invoiceDate,
                dueDate = dueDate,
                warehouseId = request.warehouseId,
                currencyCode = request.currencyCode,
                exchangeRate = request.exchangeRate,
                paymentType = request.paymentType,
                channel = customer.channel,
                province = customer.province,
                governorateId = customer.governorateId,
                districtId = customer.districtId,
                areaId = customer.areaId,
                salesRepId = salesRep?.id,
                salesRepNameSnapshot = salesRepNameSnapshot,
                salesRepRatePct = salesRepRatePct,
                freeQtyLimitPctSnapshot = freeQtyLimitPct,
                freeQtyApprovedBy = if (freeQtyApprovalRequired) request.createdBy else null,
                freeQtyApprovalReason = if (freeQtyApprovalRequired) request.freeQtyApprovalReason.trim() else "",
                discountPct = request.discountPct,
                grossOriginal = grossOriginal,
                discountOriginal = discountOriginal,
                transportOriginal = request.transportOriginal,
                feesOriginal = request.feesOriginal,
                riskMarginOriginal = request.riskMarginOriginal,
                totalOriginal = totalOriginal,
                totalBase = totalBase,
                treasuryAccountId = cashTreasury?.id,
                belowFloorApprovedBy = request.belowFloorApprovedBy,
                belowFloorReason = request.belowFloorReason.trim(),
                notes = request.notes.trim(),
                createdBy = request.createdBy
            )
        )

        preparedChargeAllocations.forEach { prepared ->
            require(db.additionalChargesDao().linkCount(prepared.charge.id, invoiceId) == 0) { "تم ربط الرسم ${prepared.charge.chargeNo} بهذه الفاتورة مسبقاً" }
            db.additionalChargesDao().insertSettlement(
                SalesAdditionalChargeSettlementEntity(
                    chargeId = prepared.charge.id,
                    invoiceId = invoiceId,
                    amountChargeOriginal = prepared.amountChargeOriginal,
                    amountInvoiceOriginal = prepared.amountInvoiceOriginal,
                    amountBase = prepared.amountBase,
                    settlementDate = request.invoiceDate,
                    createdBy = request.createdBy
                )
            )
        }

        var totalCostBase = 0.0
        request.lines.forEachIndexed { lineIndex, draft ->
            val lineGross = draft.grossOriginal
            val lineDiscount = SalesMath.discountOriginal(lineGross, request.discountPct)
            val lineNet = lineGross - lineDiscount
            val lineId = db.salesDao().insertLine(
                SalesLineEntity(
                    invoiceId = invoiceId,
                    itemId = draft.itemId,
                    unitId = draft.unitId,
                    quantity = draft.quantity,
                    freeQuantity = draft.freeQuantity,
                    factorToBase = draft.factorToBase,
                    baseQuantity = draft.baseQuantity,
                    freeBaseQuantity = draft.freeBaseQuantity,
                    unitPriceOriginal = draft.unitPriceOriginal,
                    grossOriginal = lineGross,
                    discountOriginal = lineDiscount,
                    netOriginal = lineNet
                )
            )
            val shipmentPlan = shipmentPlans[lineIndex]
            totalCostBase += if (shipmentPlan.isNotEmpty()) {
                shipmentService.allocateShipmentPlanStockForSaleInsideTransaction(
                    salesLineId = lineId,
                    itemId = draft.itemId,
                    soldBaseQty = draft.baseQuantity,
                    freeBaseQty = draft.freeBaseQuantity,
                    movementDate = request.invoiceDate,
                    takes = shipmentPlan,
                )
            } else {
                allocateStockForSale(
                    request.warehouseId,
                    lineId,
                    draft.itemId,
                    draft.baseQuantity,
                    draft.freeBaseQuantity,
                    request.invoiceDate,
                )
            }
            if (shipmentPlan.isNotEmpty()) {
                shipmentService.saveSaleLineShipmentAllocationsInsideTransaction(invoiceId, lineId, shipmentPlan, request.createdBy)
            }
        }
        val actualShipmentExpenses = shipmentService.allocateAutomaticInvoiceExpensesInsideTransaction(invoiceId, request.createdBy)
        require(kotlin.math.abs(actualShipmentExpenses.customerChargeBase - shipmentExpensePreview.customerChargeBase) <= 0.01) {
            "تغيرت حصة رسوم الشحنة أثناء ترحيل الفاتورة؛ أعد المحاولة"
        }

        val itemAndLegacyRevenueBase = SalesMath.toBaseAmount(legacyTotalOriginal, request.exchangeRate)
        val chargeCreditsByAccount = preparedChargeAllocations
            .filter { it.charge.bearer == "CUSTOMER" && it.charge.paidBy != "CUSTOMER_DIRECT" }
            .groupBy { prepared ->
                when (prepared.charge.accountingTreatment) {
                    "RECOVERABLE" -> prepared.charge.recoverableAccountId
                    "SERVICE_REVENUE" -> prepared.charge.revenueAccountId
                    else -> error("معالجة رسم العميل غير مدعومة في الفاتورة")
                }
            }
            .mapValues { (_, rows) -> rows.sumOf { it.amountBase } }
            .toMutableMap()
        if (actualShipmentExpenses.customerChargeBase > 1e-9) {
            val transportExpense = requireNotNull(db.accountDao().byCode("6430")) { "حساب مصاريف النقل والتوزيع 6430 غير موجود" }
            chargeCreditsByAccount[transportExpense.id] = (chargeCreditsByAccount[transportExpense.id] ?: 0.0) + actualShipmentExpenses.customerChargeBase
        }
        postSaleJournal(invoiceId, invoiceNo, request, collectibleBase, itemAndLegacyRevenueBase, chargeCreditsByAccount, totalCostBase, cashTreasury?.accountId)
        if (request.paymentType == "CASH") {
            createCashReceiptAndCommission(invoiceId, customer, collectibleOriginal, collectibleBase, request, requireNotNull(cashReceiptNo), requireNotNull(cashTreasury).id)
        }
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = request.createdBy,
                action = "POST",
                entityType = "SALES_INVOICE",
                entityId = invoiceId.toString(),
                newValue = "invoiceNo=$invoiceNo|customer=${customer.code}|soldBase=$totalBaseQty|freeBase=$totalFreeBaseQty|physicalBase=$totalPhysicalBaseQty|documentTotalBase=$totalBase|collectibleBase=$collectibleBase|externalDirectBase=$externalDirectBase|charges=${preparedChargeAllocations.size}|shipmentCostBase=${actualShipmentExpenses.actualCostBase}|shipmentCustomerChargeBase=${actualShipmentExpenses.customerChargeBase}|cogsBase=$totalCostBase",
                reason = "ترحيل فاتورة بيع"
            )
        )
        if (exchangeRateOverride) {
            db.governanceDao().insertAudit(
                AuditEventEntity(
                    userId = request.createdBy,
                    action = "SALES_FX_RATE_OVERRIDE",
                    entityType = "SALES_INVOICE",
                    entityId = invoiceId.toString(),
                    oldValue = "approvedRate=$canonicalHistoricalRate|currency=${request.currencyCode}|invoiceDate=${request.invoiceDate}",
                    newValue = "invoiceRate=$canonicalRequestedRate",
                    reason = request.exchangeRateOverrideReason.trim()
                )
            )
        }
        if (freeQtyApprovalRequired) {
            db.governanceDao().insertAudit(
                AuditEventEntity(
                    userId = request.createdBy,
                    action = "FREE_QTY_OVERRIDE_APPROVED",
                    entityType = "SALES_INVOICE",
                    entityId = invoiceId.toString(),
                    oldValue = "limitPct=$freeQtyLimitPct|policy=PER_LINE",
                    newValue = "freeBase=$totalFreeBaseQty|lines=${request.lines.joinToString(";") { "item=${it.itemId},sold=${it.baseQuantity},free=${it.freeBaseQuantity},excess=${SalesFreeQuantityPolicy.excessFreeBase(it.baseQuantity, it.freeBaseQuantity, freeQtyLimitPct)}" }}",
                    reason = request.freeQtyApprovalReason.trim()
                )
            )
        }
        SalePostResult(invoiceId, invoiceNo, totalBase, totalCostBase)
    }

    suspend fun postReceipt(
        customerId: Long,
        invoiceId: Long,
        amountOriginal: Double,
        currencyCode: String,
        exchangeRate: Double,
        notes: String,
        createdBy: Long,
        receiptDate: Long = com.fush.erp.domain.TrustedTimeService.now(),
        treasuryAccountId: Long? = null,
        receiptNo: String,
        discountOriginal: Double = 0.0,
        discountReason: String = ""
    ): ReceiptResult {
        val result = postReceiptAllocations(
            customerId = customerId,
            allocations = listOf(ReceiptAllocationRequest(invoiceId, amountOriginal, discountOriginal)),
            currencyCode = currencyCode,
            exchangeRate = exchangeRate,
            notes = notes,
            createdBy = createdBy,
            receiptDate = receiptDate,
            treasuryAccountId = treasuryAccountId,
            receiptNo = receiptNo,
            discountReason = discountReason
        )
        return ReceiptResult(result.receiptId, result.receiptNo, result.commissionBase)
    }

    suspend fun postReceiptAutoAllocate(
        customerId: Long,
        amountOriginal: Double,
        currencyCode: String,
        exchangeRate: Double,
        notes: String,
        createdBy: Long,
        receiptDate: Long = com.fush.erp.domain.TrustedTimeService.now(),
        treasuryAccountId: Long? = null,
        receiptNo: String,
        discountOriginal: Double = 0.0,
        discountReason: String = ""
    ): MultiReceiptResult = db.withTransaction {
        FutureDocumentDatePolicy.requireNotFuture(receiptDate, "تاريخ التحصيل")
        require(amountOriginal.isFinite() && amountOriginal > 0.0) { "المبلغ المحصل نقداً يجب أن يكون أكبر من صفر" }
        require(discountOriginal.isFinite() && discountOriginal >= 0.0) { "خصم التحصيل غير صالح" }
        val totalSettlementOriginal = amountOriginal + discountOriginal
        val validatedCustomerId = CustomerMovementIdentity.requireId(customerId)
        val open = db.salesDao().openInvoiceSummariesAsOf(validatedCustomerId, BusinessDatePolicy.endOfBusinessDay(receiptDate))
            .filter { it.currencyCode == currencyCode }
        val plan = SettlementAllocationMath.allocateOldest(
            totalSettlementOriginal,
            open.map { row ->
                val historicalRate = if (row.totalOriginal > 0.0) row.totalBase / row.totalOriginal else 0.0
                SettlementAllocationMath.InvoiceBalance(row.id, row.outstandingBase, historicalRate)
            }
        )

        // Split the total settlement deterministically across the oldest-invoice plan.
        // Cash and discount keep the exact caller totals; any floating-point residue goes
        // to the final allocation.
        val cashRatio = amountOriginal / totalSettlementOriginal
        var cashRemaining = amountOriginal
        var discountRemaining = discountOriginal
        val requests = plan.allocations.mapIndexed { index, allocation ->
            val isLast = index == plan.allocations.lastIndex
            val cashPart = if (isLast) cashRemaining else (allocation.amountOriginal * cashRatio).coerceAtMost(cashRemaining)
            val discountPart = if (isLast) discountRemaining else (allocation.amountOriginal - cashPart).coerceAtLeast(0.0).coerceAtMost(discountRemaining)
            cashRemaining = (cashRemaining - cashPart).coerceAtLeast(0.0)
            discountRemaining = (discountRemaining - discountPart).coerceAtLeast(0.0)
            ReceiptAllocationRequest(allocation.invoiceId, cashPart, discountPart)
        }

        postReceiptAllocationsInternal(
            customerId = validatedCustomerId,
            allocations = requests,
            currencyCode = currencyCode,
            exchangeRate = exchangeRate,
            notes = notes,
            createdBy = createdBy,
            receiptDate = receiptDate,
            treasuryAccountId = treasuryAccountId,
            requestedReceiptNo = receiptNo,
            discountReason = discountReason
        )
    }

    suspend fun postReceiptAllocations(
        customerId: Long,
        allocations: List<ReceiptAllocationRequest>,
        currencyCode: String,
        exchangeRate: Double,
        notes: String,
        createdBy: Long,
        receiptDate: Long = com.fush.erp.domain.TrustedTimeService.now(),
        treasuryAccountId: Long? = null,
        receiptNo: String,
        discountReason: String = ""
    ): MultiReceiptResult = db.withTransaction {
        postReceiptAllocationsInternal(
            customerId = customerId,
            allocations = allocations,
            currencyCode = currencyCode,
            exchangeRate = exchangeRate,
            notes = notes,
            createdBy = createdBy,
            receiptDate = receiptDate,
            treasuryAccountId = treasuryAccountId,
            requestedReceiptNo = receiptNo,
            discountReason = discountReason
        )
    }

    private suspend fun postReceiptAllocationsInternal(
        customerId: Long,
        allocations: List<ReceiptAllocationRequest>,
        currencyCode: String,
        exchangeRate: Double,
        notes: String,
        createdBy: Long,
        receiptDate: Long,
        treasuryAccountId: Long?,
        requestedReceiptNo: String,
        discountReason: String
    ): MultiReceiptResult {
        db.requireUserPermission(createdBy, SecurityPermissions.COLLECTION_POST)
        FutureDocumentDatePolicy.requireNotFuture(receiptDate, "تاريخ التحصيل")
        AccountingService(db).requirePostingPeriodOpen(receiptDate)
        SalesMath.validateExchangeRate(exchangeRate)
        require(allocations.isNotEmpty()) { "يجب تحديد فاتورة واحدة على الأقل للتحصيل" }
        require(allocations.map { it.invoiceId }.distinct().size == allocations.size) { "لا يجوز تكرار الفاتورة في نفس التحصيل" }
        require(allocations.sumOf { it.amountOriginal } > 0.0) { "المبلغ المحصل نقداً يجب أن يكون أكبر من صفر" }
        val totalDiscountOriginalRequested = allocations.sumOf { it.discountOriginal }
        if (totalDiscountOriginalRequested > 1e-9) {
            db.requireUserPermission(createdBy, SecurityPermissions.COLLECTION_DISCOUNT_POST)
            require(discountReason.trim().isNotBlank()) { "سبب خصم التحصيل مطلوب" }
        }
        val validatedCustomerId = CustomerMovementIdentity.requireId(customerId)
        val customer = requireNotNull(db.customerDao().byId(validatedCustomerId)) { "العميل غير موجود" }
        val treasury = resolveTreasury(treasuryAccountId, currencyCode)

        data class Prepared(
            val invoice: SalesInvoiceEntity,
            val request: ReceiptAllocationRequest,
            val split: CustomerArMath.SettlementSplit
        )
        val prepared = allocations.map { allocation ->
            require(allocation.amountOriginal.isFinite() && allocation.amountOriginal >= 0.0) { "مبلغ النقد المخصص للفـاتورة غير صالح" }
            require(allocation.discountOriginal.isFinite() && allocation.discountOriginal >= 0.0) { "خصم التحصيل المخصص للفـاتورة غير صالح" }
            require(allocation.amountOriginal + allocation.discountOriginal > 0.0) { "إجمالي تسوية الفاتورة يجب أن يكون أكبر من صفر" }
            val invoice = requireNotNull(db.salesDao().invoiceById(allocation.invoiceId)) { "فاتورة البيع غير موجودة" }
            require(invoice.customerId == customer.id) { "إحدى الفواتير لا تخص العميل المحدد" }
            require(invoice.status == "POSTED" && invoice.paymentType == "CREDIT") { "التحصيل متاح للفواتير الآجلة المرحلة فقط" }
            val sameCurrency = invoice.currencyCode == currencyCode
            if (!sameCurrency) {
                require(allocation.discountOriginal <= 1e-9) {
                    "لا يمكن جمع خصم تحصيل مع دفع بعملة مختلفة عن عملة الفاتورة؛ رحّل الدفع أولاً ثم عالج الخصم بعملة الفاتورة"
                }
            }
            TransactionChronology.requireOnOrAfter(
                eventDate = receiptDate,
                sourceDate = invoice.invoiceDate,
                eventLabel = "تاريخ التحصيل",
                sourceLabel = "تاريخ الفاتورة ${invoice.invoiceNo}",
            )
            val split = CustomerArMath.settlementSplit(
                cashOriginal = allocation.amountOriginal,
                discountOriginal = allocation.discountOriginal,
                invoiceExchangeRate = invoice.exchangeRate,
                receiptExchangeRate = exchangeRate,
                sameCurrency = sameCurrency,
            )
            val outstanding = invoiceOutstandingBaseAsOf(invoice.id, BusinessDatePolicy.endOfBusinessDay(receiptDate))
            require(split.settledReceivableBase <= outstanding + 1e-8) { "إجمالي التحصيل والخصم يتجاوز الرصيد المستحق على الفاتورة ${invoice.invoiceNo} في تاريخ التحصيل" }
            val currentOutstanding = invoiceOutstandingBase(invoice.id)
            require(split.settledReceivableBase <= currentOutstanding + 1e-8) {
                "التحصيل التاريخي سيجعل الفاتورة ${invoice.invoiceNo} مفرطة التخصيص في تاريخ لاحق؛ اعكس/أعد تخصيص التحصيلات اللاحقة أولاً"
            }
            Prepared(invoice, allocation, split)
        }

        val totalCashOriginal = prepared.sumOf { it.request.amountOriginal }
        val totalDiscountOriginal = prepared.sumOf { it.request.discountOriginal }
        val totalCashReceivableBase = prepared.sumOf { it.split.cashReceivableBase }
        val totalDiscountBase = prepared.sumOf { it.split.discountBase }
        val totalSettledReceivableBase = prepared.sumOf { it.split.settledReceivableBase }
        val totalTreasuryCashBase = prepared.sumOf { it.split.treasuryCashBase }
        val receiptNo = requireUniqueDocumentNo(
            requestedRaw = requestedReceiptNo,
            label = "رقم سند التحصيل",
        ) { db.salesDao().receiptNoCount(it) > 0 }
        val receiptId = db.salesDao().insertReceipt(
            CustomerReceiptEntity(
                receiptNo = receiptNo,
                customerId = customer.id,
                receiptDate = receiptDate,
                currencyCode = currencyCode,
                exchangeRate = exchangeRate,
                amountOriginal = totalCashOriginal,
                amountBase = totalCashReceivableBase,
                discountOriginal = totalDiscountOriginal,
                discountBase = totalDiscountBase,
                discountReason = discountReason.trim(),
                notes = notes.trim(),
                treasuryAccountId = treasury.id,
                createdBy = createdBy
            )
        )

        var commissionBase = 0.0
        prepared.forEach { row ->
            val allocationId = db.salesDao().insertReceiptAllocation(
                CustomerReceiptAllocationEntity(
                    receiptId = receiptId,
                    invoiceId = row.invoice.id,
                    amountBase = row.split.cashReceivableBase,
                    discountOriginal = row.request.discountOriginal,
                    discountBase = row.split.discountBase
                )
            )
            // Commission is based on actual cash collection only; discountBase is intentionally excluded.
            commissionBase += accrueCommissionToTarget(row.invoice, customer, allocationId, createdBy, receiptDate)
        }
        postCollectionJournal(
            receiptId = receiptId,
            receiptNo = receiptNo,
            treasuryAccountId = treasury.accountId,
            settledReceivableBase = totalSettledReceivableBase,
            cashReceivableBase = totalCashReceivableBase,
            treasuryCashBase = totalTreasuryCashBase,
            discountBase = totalDiscountBase,
            currencyCode = currencyCode,
            exchangeRate = exchangeRate,
            receiptDate = receiptDate,
            createdBy = createdBy
        )
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = createdBy,
                action = "POST",
                entityType = "CUSTOMER_RECEIPT",
                entityId = receiptId.toString(),
                newValue = "receipt=$receiptNo|customer=${customer.code}|receiptCurrency=$currencyCode|cashOriginal=$totalCashOriginal|discountOriginal=$totalDiscountOriginal|invoices=${prepared.joinToString(",") { "${it.invoice.invoiceNo}:${it.invoice.currencyCode}" }}",
                reason = notes.trim().ifBlank { "ترحيل تحصيل عميل" }
            )
        )
        if (totalDiscountOriginal > 1e-9) {
            db.governanceDao().insertAudit(
                AuditEventEntity(
                    userId = createdBy,
                    action = "CUSTOMER_COLLECTION_DISCOUNT",
                    entityType = "CUSTOMER_RECEIPT",
                    entityId = receiptId.toString(),
                    newValue = "receipt=$receiptNo;customer=${customer.id};cashOriginal=$totalCashOriginal;discountOriginal=$totalDiscountOriginal;discountBase=$totalDiscountBase;invoices=${prepared.joinToString(",") { it.invoice.invoiceNo }}",
                    reason = discountReason.trim()
                )
            )
        }
        return MultiReceiptResult(
            receiptId = receiptId,
            receiptNo = receiptNo,
            allocationCount = prepared.size,
            totalOriginal = totalCashOriginal,
            discountOriginal = totalDiscountOriginal,
            allocatedBase = totalSettledReceivableBase,
            cashBase = totalTreasuryCashBase,
            discountBase = totalDiscountBase,
            fxDifferenceBase = totalTreasuryCashBase - totalCashReceivableBase,
            commissionBase = commissionBase
        )
    }

    suspend fun reverseReceipt(
        receiptId: Long,
        reason: String,
        createdBy: Long,
        reversalDate: Long = com.fush.erp.domain.TrustedTimeService.now()
    ): ReceiptReversalResult = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.COLLECTION_POST)
        FutureDocumentDatePolicy.requireNotFuture(reversalDate, "تاريخ عكس التحصيل")
        require(reason.trim().isNotBlank()) { "سبب عكس التحصيل مطلوب" }
        AccountingService(db).requirePostingPeriodOpen(reversalDate)
        val original = requireNotNull(db.salesDao().receiptById(receiptId)) { "التحصيل غير موجود" }
        val customerId = CustomerMovementIdentity.requireId(original.customerId)
        require(original.reversalOfReceiptId == null) { "لا يمكن عكس مستند عكس" }
        require(reversalDate >= original.receiptDate) { "تاريخ العكس لا يمكن أن يسبق تاريخ التحصيل" }
        require(db.salesDao().reversalForReceipt(original.id) == null) { "تم عكس هذا التحصيل مسبقاً" }
        val allocations = db.salesDao().receiptAllocations(original.id)
        require(allocations.isNotEmpty()) { "التحصيل لا يحتوي تخصيصاً لفاتورة" }
        val invoices = allocations.map { allocation ->
            requireNotNull(db.salesDao().invoiceById(allocation.invoiceId)) { "فاتورة التحصيل غير موجودة" }.also { invoice ->
                val invoiceCustomerId = CustomerMovementIdentity.requireId(invoice.customerId)
                require(invoiceCustomerId == customerId) {
                    "فاتورة التحصيل لا تخص العميل المرتبط بالتحصيل"
                }
            }
        }
        require(invoices.all { it.paymentType == "CREDIT" }) {
            "التحصيل النقدي التلقائي لفاتورة نقدية يعكس من عملية البيع الأصلية، وليس كتحصيل مستقل"
        }
        val originalJournal = requireNotNull(db.journalDao().bySource("CUSTOMER_RECEIPT", original.id.toString())) {
            "لا يوجد قيد تحصيل مستقل لهذا المستند؛ يجب عكس العملية الأصلية"
        }
        require(db.journalDao().reversalCount(originalJournal.id) == 0) { "تم عكس قيد هذا التحصيل مسبقاً" }

        allocations.forEach { allocation ->
            val invoice = requireNotNull(db.salesDao().invoiceById(allocation.invoiceId)) { "فاتورة التحصيل غير موجودة" }
            requireSalesCashRefundTimelineCovered(
                invoice = invoice,
                proposedCashDeltaBase = -allocation.amountBase,
                eventDate = reversalDate
            )
        }

        val reversalNo = numbering.nextDocumentNo("RCRV", reversalDate)
        val reversalId = db.salesDao().insertReceipt(
            CustomerReceiptEntity(
                receiptNo = reversalNo,
                customerId = customerId,
                receiptDate = reversalDate,
                currencyCode = original.currencyCode,
                exchangeRate = original.exchangeRate,
                amountOriginal = -original.amountOriginal,
                amountBase = -original.amountBase,
                discountOriginal = -original.discountOriginal,
                discountBase = -original.discountBase,
                discountReason = original.discountReason,
                notes = "عكس ${original.receiptNo}: ${reason.trim()}",
                treasuryAccountId = original.treasuryAccountId,
                reversalOfReceiptId = original.id,
                createdBy = createdBy
            )
        )
        allocations.forEach { allocation ->
            db.salesDao().insertReceiptAllocation(
                CustomerReceiptAllocationEntity(
                    receiptId = reversalId,
                    invoiceId = allocation.invoiceId,
                    amountBase = -allocation.amountBase,
                    discountOriginal = -allocation.discountOriginal,
                    discountBase = -allocation.discountBase
                )
            )
        }
        val reversalJournalId = reverseOperationalJournal(
            originalJournal = originalJournal,
            reason = reason,
            createdBy = createdBy,
            reversalDate = reversalDate
        )

        var reversedCommission = 0.0
        invoices.distinctBy { it.id }.forEach { invoice ->
            reversedCommission += reduceCommissionToCollectedTarget(
                invoice = invoice,
                createdBy = createdBy,
                reversalReceiptId = reversalId,
                referenceNo = reversalNo,
                journalDate = reversalDate
            )
        }
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = createdBy,
                action = "REVERSE",
                entityType = "CUSTOMER_RECEIPT",
                entityId = original.id.toString(),
                oldValue = "${original.receiptNo}|cash=${original.amountOriginal}|cashBase=${original.amountBase}|discount=${original.discountOriginal}|discountBase=${original.discountBase}",
                newValue = "$reversalNo|journal=$reversalJournalId|commission=$reversedCommission",
                reason = reason.trim()
            )
        )
        ReceiptReversalResult(
            reversalReceiptId = reversalId,
            reversalReceiptNo = reversalNo,
            reversalJournalEntryId = reversalJournalId,
            restoredReceivableBase = allocations.sumOf { it.amountBase + it.discountBase },
            reversedCommissionBase = reversedCommission
        )
    }

    suspend fun postReturn(
        salesLineId: Long,
        quantity: Double,
        freeQuantity: Double = 0.0,
        settlementType: String,
        reason: String,
        createdBy: Long,
        returnDate: Long = com.fush.erp.domain.TrustedTimeService.now(),
        treasuryAccountId: Long? = null
    ): ReturnResult = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.SALES_RETURN)
        FutureDocumentDatePolicy.requireNotFuture(returnDate, "تاريخ مرتجع البيع")
        AccountingService(db).requirePostingPeriodOpen(returnDate)
        require(settlementType in setOf("CUSTOMER_CREDIT", "CASH_REFUND", "NO_FINANCIAL")) { "نوع تسوية المرتجع غير صالح" }
        require(quantity >= 0.0 && freeQuantity >= 0.0 && quantity + freeQuantity > 0.0) { "يجب إدخال كمية مرتجع مباعة أو مجانية" }
        require(if (quantity <= SalesFreeQuantityPolicy.EPS) settlementType == "NO_FINANCIAL" else settlementType != "NO_FINANCIAL") {
            "مرتجع المجاني فقط يجب أن يكون بدون تسوية مالية، والمرتجع المدفوع يحتاج تسوية مالية"
        }
        require(reason.isNotBlank()) { "سبب المرتجع مطلوب" }
        val line = requireNotNull(db.salesDao().lineById(salesLineId)) { "سطر البيع غير موجود" }
        val invoice = requireNotNull(db.salesDao().invoiceById(line.invoiceId)) { "فاتورة البيع غير موجودة" }
        TransactionChronology.requireOnOrAfter(
            eventDate = returnDate,
            sourceDate = invoice.invoiceDate,
            eventLabel = "تاريخ مرتجع البيع",
            sourceLabel = "تاريخ الفاتورة ${invoice.invoiceNo}",
        )
        val customerId = CustomerMovementIdentity.requireId(invoice.customerId)
        val customer = requireNotNull(db.customerDao().byId(customerId)) { "العميل غير موجود" }
        val refundTreasury = if (settlementType == "CASH_REFUND") resolveTreasury(treasuryAccountId, invoice.currencyCode) else null
        val alreadyReturned = db.salesDao().returnedQuantityForLine(line.id)
        val alreadyFreeReturned = db.salesDao().returnedFreeQuantityForLine(line.id)
        SalesMath.validateReturnComponent(quantity, line.quantity, alreadyReturned, "كمية المرتجع المباعة")
        SalesMath.validateReturnComponent(freeQuantity, line.freeQuantity, alreadyFreeReturned, "كمية المرتجع المجانية")
        val returnBaseQty = quantity * line.factorToBase
        val freeReturnBaseQty = freeQuantity * line.factorToBase
        val unitNetOriginal = if (line.quantity > 0.0) line.netOriginal / line.quantity else 0.0
        val totalOriginal = quantity * unitNetOriginal
        val totalBase = totalOriginal * invoice.exchangeRate
        if (settlementType == "CASH_REFUND") {
            requireSalesCashRefundTimelineCovered(
                invoice = invoice,
                proposedRefundBase = totalBase,
                eventDate = returnDate
            )
        }

        data class ReturnAllocationPart(val allocation: SalesAllocationEntity, val paidBase: Double, val freeBase: Double) {
            val totalBase: Double get() = paidBase + freeBase
        }
        val allocationPlan = mutableListOf<ReturnAllocationPart>()
        var remainingPaidBase = returnBaseQty
        var remainingFreeBase = freeReturnBaseQty
        for (allocation in db.salesDao().allocationsForLine(line.id)) {
            if (remainingPaidBase <= 1e-9 && remainingFreeBase <= 1e-9) break
            val originalFree = allocation.freeQuantityBase.coerceIn(0.0, allocation.quantityBase)
            val originalPaid = (allocation.quantityBase - originalFree).coerceAtLeast(0.0)
            val alreadyPaid = db.salesDao().returnedPaidBaseForAllocation(allocation.id)
            val alreadyFree = db.salesDao().returnedFreeBaseForAllocation(allocation.id)
            val paidTake = min((originalPaid - alreadyPaid).coerceAtLeast(0.0), remainingPaidBase)
            val freeTake = min((originalFree - alreadyFree).coerceAtLeast(0.0), remainingFreeBase)
            if (paidTake + freeTake > 1e-9) allocationPlan += ReturnAllocationPart(allocation, paidTake, freeTake)
            remainingPaidBase -= paidTake
            remainingFreeBase -= freeTake
        }
        require(remainingPaidBase <= 1e-9 && remainingFreeBase <= 1e-9) { "تعذر ربط المرتجع بالكميات المباعة/المجانية الأصلية بالكامل" }
        val totalCostBase = allocationPlan.sumOf { it.totalBase * it.allocation.unitCostBase }

        val returnNo = numbering.nextDocumentNo("SRET", returnDate)
        val returnId = db.salesDao().insertReturn(
            SalesReturnEntity(
                returnNo = returnNo,
                salesInvoiceId = invoice.id,
                customerId = customer.id,
                returnDate = returnDate,
                warehouseId = invoice.warehouseId,
                currencyCode = invoice.currencyCode,
                exchangeRate = invoice.exchangeRate,
                settlementType = settlementType,
                totalOriginal = totalOriginal,
                totalBase = totalBase,
                totalCostBase = totalCostBase,
                treasuryAccountId = refundTreasury?.id,
                reason = reason.trim(),
                createdBy = createdBy
            )
        )
        val returnLineId = db.salesDao().insertReturnLine(
            SalesReturnLineEntity(
                returnId = returnId,
                salesLineId = line.id,
                itemId = line.itemId,
                unitId = line.unitId,
                quantity = quantity,
                freeQuantity = freeQuantity,
                factorToBase = line.factorToBase,
                baseQuantity = returnBaseQty,
                freeBaseQuantity = freeReturnBaseQty,
                unitPriceOriginal = unitNetOriginal,
                lineNetOriginal = totalOriginal,
                costBase = totalCostBase
            )
        )
        allocationPlan.forEach { part ->
            val allocation = part.allocation
            val qtyBase = part.totalBase
            val cost = qtyBase * allocation.unitCostBase
            db.salesDao().insertReturnAllocation(
                SalesReturnAllocationEntity(
                    returnLineId = returnLineId,
                    salesAllocationId = allocation.id,
                    itemId = allocation.itemId,
                    lotNo = allocation.lotNo,
                    expiryDate = allocation.expiryDate,
                    quantityBase = qtyBase,
                    freeQuantityBase = part.freeBase,
                    unitCostBase = allocation.unitCostBase,
                    costBase = cost
                )
            )
            db.stockDao().insertMovement(
                StockMovementEntity(
                    movementDate = returnDate,
                    warehouseId = invoice.warehouseId,
                    itemId = allocation.itemId,
                    movementType = "SALES_RETURN",
                    quantityBase = qtyBase,
                    unitCostBase = allocation.unitCostBase,
                    referenceType = "SALES_RETURN",
                    referenceId = returnId,
                    lotNo = allocation.lotNo,
                    expiryDate = allocation.expiryDate
                )
            )
        }
        postSalesReturnJournal(returnId, returnNo, invoice, totalBase, totalCostBase, settlementType, returnDate, createdBy, refundTreasury?.accountId)
        val reversed = reverseCommissionForReturn(invoice.id, totalBase, createdBy, returnId, returnNo, returnDate)
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = createdBy,
                action = "POST",
                entityType = "SALES_RETURN",
                entityId = returnId.toString(),
                oldValue = "invoice=${invoice.invoiceNo}",
                newValue = "returnNo=$returnNo|paidQty=$quantity|freeQty=$freeQuantity|refundBase=$totalBase|restoredCostBase=$totalCostBase",
                reason = reason.trim()
            )
        )
        ReturnResult(returnId, returnNo, totalBase, totalCostBase, reversed)
    }

    suspend fun invoiceOutstandingBase(invoiceId: Long): Double {
        val invoice = requireNotNull(db.salesDao().invoiceById(invoiceId)) { "فاتورة البيع غير موجودة" }
        if (invoice.paymentType == "CASH") return 0.0
        val received = db.salesDao().settledBaseForInvoice(invoiceId)
        val returned = db.salesDao().customerCreditReturnedBaseForInvoice(invoiceId)
        val externalDirect = db.additionalChargesDao().externallySettledBaseForInvoice(invoiceId)
        return (invoice.totalBase - externalDirect - received - returned).coerceAtLeast(0.0)
    }

    private suspend fun invoiceOutstandingBaseAsOf(invoiceId: Long, asOf: Long): Double {
        val invoice = requireNotNull(db.salesDao().invoiceById(invoiceId)) { "فاتورة البيع غير موجودة" }
        if (invoice.paymentType == "CASH") return 0.0
        TransactionChronology.requireOnOrAfter(
            eventDate = asOf,
            sourceDate = invoice.invoiceDate,
            eventLabel = "تاريخ التسوية",
            sourceLabel = "تاريخ الفاتورة ${invoice.invoiceNo}",
        )
        val received = db.salesDao().settledBaseForInvoiceAsOf(invoiceId, asOf)
        val returned = db.salesDao().customerCreditReturnedBaseForInvoiceAsOf(invoiceId, asOf)
        val externalDirect = db.additionalChargesDao().externallySettledBaseForInvoiceAsOf(invoiceId, asOf)
        return (invoice.totalBase - externalDirect - received - returned).coerceAtLeast(0.0)
    }

    private suspend fun validateCredit(customer: CustomerEntity, totalBase: Double, requestedDays: Int, invoiceDate: Long) {
        require(customer.allowCredit) { "العميل غير مخول للبيع الآجل" }
        SalesMath.validateCreditDays(requestedDays)
        require(requestedDays <= customer.creditDays) { "مدة الائتمان تتجاوز المدة المعتمدة للعميل" }
        val asOf = BusinessDatePolicy.endOfBusinessDay(invoiceDate)
        require(db.salesDao().overdueInvoiceCountAsOf(customer.id, asOf) == 0) { "يوجد على العميل دين متأخر في تاريخ الفاتورة؛ تم إيقاف الآجل تلقائياً" }
        val outstanding = db.salesDao().customerOutstandingBaseAsOf(customer.id, asOf).coerceAtLeast(0.0)
        require(outstanding + totalBase <= customer.creditLimitBase + 1e-9) { "الفاتورة تتجاوز السقف الائتماني للعميل في تاريخ الفاتورة" }
    }

    suspend fun exchangeRateAt(currencyCode: String, invoiceDate: Long): Double {
        require(invoiceDate > 0L) { "تاريخ الفاتورة غير صالح" }
        if (currencyCode == "YER_NEW") return 1.0
        val rate = requireNotNull(db.currencyDao().latestRateAt(currencyCode, BusinessDatePolicy.endOfBusinessDay(invoiceDate))) {
            "لا يوجد سعر صرف للعملة $currencyCode في تاريخ الفاتورة"
        }.rateToBase
        require(rate.isFinite() && rate > 0.0) { "سعر الصرف المعتمد غير صالح" }
        return rate
    }

    private fun requireEffectiveDateNotFuture(value: Long, label: String) {
        require(value > 0L) { "$label غير صالح" }
        require(value <= BusinessDatePolicy.endOfBusinessDay(TrustedTimeService.requireTrustedNow())) { "$label لا يمكن أن يكون في المستقبل" }
    }

    private suspend fun allocateStockForSale(
        warehouseId: Long,
        salesLineId: Long,
        itemId: Long,
        soldBaseQty: Double,
        freeBaseQty: Double,
        movementDate: Long,
        preferredShipmentLots: List<Pair<String, Double>> = emptyList()
    ): Double {
        val requiredBaseQty = soldBaseQty + freeBaseQty
        val advancedInventory = AdvancedInventoryService(db)
        val businessDayAsOf = BusinessDatePolicy.endOfBusinessDay(movementDate)
        val balance = advancedInventory.usableBalance(warehouseId, itemId, businessDayAsOf)
        require(balance + WarehouseTransferMath.EPS >= requiredBaseQty) {
            "المخزون المقبول وغير المنتهي لا يكفي لإتمام البيع"
        }

        val lots = advancedInventory.usableLots(warehouseId, itemId, businessDayAsOf)
        data class PreparedLot(
            val key: String,
            val lot: com.fush.erp.data.entity.LotBalanceRow,
            val safeQty: Double,
            val unitCost: Double,
        )

        val prepared = lots.mapIndexed { index, lot ->
            val safeQty = advancedInventory.historicalSafeLotOutflowQty(
                warehouseId = warehouseId,
                itemId = itemId,
                lotNo = lot.lotNo,
                expiryDate = lot.expiryDate,
                movementDate = movementDate
            )
            val unitCost = if (lot.quantityBase > WarehouseTransferMath.EPS) {
                lot.inventoryValueBase / lot.quantityBase
            } else 0.0
            PreparedLot(
                key = "$index|${InventoryMath.lotKey(lot.lotNo)}|${InventoryMath.expiryKey(lot.expiryDate)}",
                lot = lot,
                safeQty = safeQty,
                unitCost = unitCost
            )
        }

        val capacities = prepared.map { p ->
            HistoricalLotCapacity(
                key = p.key, lotNo = p.lot.lotNo, quantityBase = p.lot.quantityBase,
                historicalSafeQtyBase = p.safeQty, unitCostBase = p.unitCost
            )
        }
        val plan = if (preferredShipmentLots.isEmpty()) {
            SalesHistoricalAllocationMath.plan(requiredBaseQty, capacities)
        } else {
            val desiredByLot = linkedMapOf<String, Double>()
            preferredShipmentLots.forEach { (lot, qty) ->
                val key = InventoryMath.lotKey(lot)
                desiredByLot[key] = (desiredByLot[key] ?: 0.0) + qty
            }
            val ordered = buildList {
                desiredByLot.keys.forEach { lotKey -> capacities.filter { InventoryMath.lotKey(it.lotNo) == lotKey }.forEach(::add) }
            }
            val planned = mutableListOf<HistoricalLotTake>()
            var shortage = 0.0
            desiredByLot.forEach { (lotKey, requiredForLot) ->
                val lotPlan = SalesHistoricalAllocationMath.plan(requiredForLot, ordered.filter { InventoryMath.lotKey(it.lotNo) == lotKey })
                planned += lotPlan.takes
                shortage += lotPlan.shortageQtyBase
            }
            HistoricalAllocationPlan(requiredBaseQty, planned.sumOf { it.quantityBase }, planned)
        }

        require(plan.isComplete) {
            val details = prepared.joinToString("؛ ") { p ->
                val safe = minOf(p.lot.quantityBase.coerceAtLeast(0.0), p.safeQty.coerceAtLeast(0.0))
                "${p.lot.lotNo ?: "بدون تشغيلة"}: ${formatHistoricalQty(safe)}"
            }
            "لا يمكن ترحيل فاتورة البيع بتاريخ ${BusinessDatePolicy.businessDay(movementDate)}؛ " +
                "الكمية المطلوبة ${formatHistoricalQty(requiredBaseQty)} بينما إجمالي الكمية الآمنة تاريخياً عبر التشغيلات " +
                "هو ${formatHistoricalQty(plan.totalSafeQtyBase)}. العجز ${formatHistoricalQty(plan.shortageQtyBase)}. " +
                "التشغيلات: $details"
        }

        var totalCost = 0.0
        var soldRemaining = soldBaseQty
        plan.takes.forEach { take ->
            val p = prepared.first { it.key == take.key }
            val lot = p.lot
            val paidTake = min(soldRemaining, take.quantityBase)
            val freeTake = (take.quantityBase - paidTake).coerceAtLeast(0.0)
            soldRemaining = (soldRemaining - paidTake).coerceAtLeast(0.0)
            val cost = take.quantityBase * p.unitCost
            db.salesDao().insertAllocation(
                SalesAllocationEntity(
                    salesLineId = salesLineId,
                    itemId = itemId,
                    lotNo = lot.lotNo,
                    expiryDate = lot.expiryDate,
                    quantityBase = take.quantityBase,
                    freeQuantityBase = freeTake,
                    unitCostBase = p.unitCost,
                    costBase = cost
                )
            )
            db.stockDao().insertMovement(
                StockMovementEntity(
                    movementDate = movementDate,
                    warehouseId = warehouseId,
                    itemId = itemId,
                    movementType = "SALE",
                    quantityBase = -take.quantityBase,
                    unitCostBase = p.unitCost,
                    referenceType = "SALES_LINE",
                    referenceId = salesLineId,
                    lotNo = lot.lotNo,
                    expiryDate = lot.expiryDate
                )
            )
            totalCost += cost
        }
        return totalCost
    }

    private fun formatHistoricalQty(value: Double): String =
        if (kotlin.math.abs(value - value.toLong().toDouble()) <= WarehouseTransferMath.EPS) {
            value.toLong().toString()
        } else {
            "%.6f".format(java.util.Locale.US, value).trimEnd('0').trimEnd('.')
        }

    private suspend fun createCashReceiptAndCommission(
        invoiceId: Long,
        customer: CustomerEntity,
        amountOriginal: Double,
        amountBase: Double,
        request: PostSaleRequest,
        cashReceiptNo: String,
        cashTreasuryId: Long
    ) {
        val receiptId = db.salesDao().insertReceipt(
            CustomerReceiptEntity(
                receiptNo = cashReceiptNo,
                customerId = customer.id,
                receiptDate = request.invoiceDate,
                currencyCode = request.currencyCode,
                exchangeRate = request.exchangeRate,
                amountOriginal = amountOriginal,
                amountBase = amountBase,
                notes = "تحصيل نقدي تلقائي مع فاتورة البيع",
                treasuryAccountId = cashTreasuryId,
                createdBy = request.createdBy
            )
        )
        val allocationId = db.salesDao().insertReceiptAllocation(
            CustomerReceiptAllocationEntity(receiptId = receiptId, invoiceId = invoiceId, amountBase = amountBase)
        )
        val invoice = requireNotNull(db.salesDao().invoiceById(invoiceId))
        accrueCommissionToTarget(invoice, customer, allocationId, request.createdBy, request.invoiceDate)
    }

    private suspend fun accrueCommissionToTarget(
        invoice: SalesInvoiceEntity,
        customer: CustomerEntity,
        receiptAllocationId: Long,
        createdBy: Long,
        commissionDate: Long
    ): Double {
        val totalCollected = db.salesDao().receivedBaseForInvoice(invoice.id)
        val totalReturned = db.salesDao().returnedBaseForInvoice(invoice.id)
        val netSaleBase = (invoice.totalBase - totalReturned).coerceAtLeast(0.0)
        val eligibleCollected = (totalCollected - totalReturned).coerceAtLeast(0.0).coerceAtMost(netSaleBase)
        val targetNetCommission = SalesMath.commissionBase(eligibleCollected, invoice.salesRepRatePct)
        val currentNetCommission = db.salesDao().netCommissionBaseForInvoice(invoice.id)
        val delta = (targetNetCommission - currentNetCommission).coerceAtLeast(0.0)
        if (delta <= 1e-9) return 0.0

        val commissionId = db.salesDao().insertCommission(
            SalesCommissionEntity(
                invoiceId = invoice.id,
                receiptAllocationId = receiptAllocationId,
                salesRepId = invoice.salesRepId,
                beneficiary = invoice.salesRepNameSnapshot.ifBlank { customer.salesRepName.ifBlank { "مندوب غير محدد" } },
                ratePct = invoice.salesRepRatePct,
                earnedBase = delta
            )
        )
        postCommissionJournal(commissionId, invoice, delta, "استحقاق عمولة بعد التحصيل", createdBy, commissionDate)
        return delta
    }

    private suspend fun reduceCommissionToCollectedTarget(
        invoice: SalesInvoiceEntity,
        createdBy: Long,
        reversalReceiptId: Long,
        referenceNo: String,
        journalDate: Long
    ): Double {
        val totalCollected = db.salesDao().receivedBaseForInvoice(invoice.id)
        val totalReturned = db.salesDao().returnedBaseForInvoice(invoice.id)
        val netSaleBase = (invoice.totalBase - totalReturned).coerceAtLeast(0.0)
        val eligibleCollected = (totalCollected - totalReturned).coerceAtLeast(0.0).coerceAtMost(netSaleBase)
        val targetNetCommission = SalesMath.commissionBase(eligibleCollected, invoice.salesRepRatePct)
        val currentNetCommission = db.salesDao().netCommissionBaseForInvoice(invoice.id).coerceAtLeast(0.0)
        var needed = OperationalReversalMath.commissionReduction(currentNetCommission, targetNetCommission)
        if (needed <= 1e-9) return 0.0
        val originalNeeded = needed
        for (commission in db.salesDao().commissionsForInvoice(invoice.id)) {
            if (needed <= 1e-9) break
            val available = (commission.earnedBase - commission.reversedBase).coerceAtLeast(0.0)
            if (available <= 1e-9) continue
            val reverse = min(available, needed)
            val newReversed = commission.reversedBase + reverse
            db.salesDao().updateCommission(
                commission.copy(
                    reversedBase = newReversed,
                    status = if (newReversed + 1e-9 >= commission.earnedBase) "REVERSED" else "PARTIAL_REVERSED"
                )
            )
            needed -= reverse
        }
        postReceiptCommissionReversalJournal(reversalReceiptId, invoice, originalNeeded, referenceNo, createdBy, journalDate)
        return originalNeeded
    }

    private suspend fun reverseCommissionForReturn(invoiceId: Long, returnedBase: Double, createdBy: Long, returnId: Long, returnNo: String, reversalDate: Long): Double {
        val invoice = requireNotNull(db.salesDao().invoiceById(invoiceId))
        var needed = min(
            SalesMath.commissionReversalBase(returnedBase, invoice.salesRepRatePct),
            db.salesDao().netCommissionBaseForInvoice(invoiceId).coerceAtLeast(0.0)
        )
        if (needed <= 1e-9) return 0.0
        val originalNeeded = needed
        for (commission in db.salesDao().commissionsForInvoice(invoiceId)) {
            if (needed <= 1e-9) break
            val available = (commission.earnedBase - commission.reversedBase).coerceAtLeast(0.0)
            if (available <= 1e-9) continue
            val reverse = min(available, needed)
            val newReversed = commission.reversedBase + reverse
            db.salesDao().updateCommission(
                commission.copy(
                    reversedBase = newReversed,
                    status = if (newReversed + 1e-9 >= commission.earnedBase) "REVERSED" else "PARTIAL_REVERSED"
                )
            )
            needed -= reverse
        }
        postCommissionReversalJournal(returnId, invoice, originalNeeded, returnNo, createdBy, reversalDate)
        return originalNeeded
    }

    private suspend fun reverseOperationalJournal(
        originalJournal: JournalEntryEntity,
        reason: String,
        createdBy: Long,
        reversalDate: Long
    ): Long {
        val originalLines = db.journalDao().linesForEntry(originalJournal.id)
        require(originalLines.isNotEmpty()) { "قيد العملية لا يحتوي سطوراً" }
        val reversed = OperationalReversalMath.reverseJournalLines(
            originalLines.map { DraftJournalLine(it.accountId, it.debit, it.credit) }
        )
        AccountingValidator.validate(reversed)
        val reversalId = db.journalDao().insertEntry(
            JournalEntryEntity(
                entryNo = "JE-${numbering.nextDocumentNo("REV", reversalDate)}",
                entryDate = reversalDate,
                description = "عكس ${originalJournal.entryNo}: ${reason.trim()}",
                currencyCode = originalJournal.currencyCode,
                exchangeRate = originalJournal.exchangeRate,
                sourceType = "REVERSAL",
                sourceId = originalJournal.id.toString(),
                createdBy = createdBy
            )
        )
        db.journalDao().insertLines(
            originalLines.map {
                JournalLineEntity(
                    entryId = reversalId,
                    accountId = it.accountId,
                    debit = it.credit,
                    credit = it.debit,
                    memo = "عكس: ${it.memo}"
                )
            }
        )
        return reversalId
    }

    private suspend fun postReceiptCommissionReversalJournal(
        reversalReceiptId: Long,
        invoice: SalesInvoiceEntity,
        amountBase: Double,
        referenceNo: String,
        createdBy: Long,
        journalDate: Long
    ) {
        if (amountBase <= 0.0) return
        val expense = requireNotNull(db.accountDao().byCode("6400")) { "حساب مصروف عمولات البيع 6400 غير موجود" }
        val payable = requireNotNull(db.accountDao().byCode("2300")) { "حساب عمولات مستحقة 2300 غير موجود" }
        postJournal(
            entryNo = "JE-${numbering.nextDocumentNo("CMR", journalDate)}",
            date = journalDate,
            description = "إلغاء عمولة بسبب عكس تحصيل $referenceNo — ${invoice.invoiceNo}",
            currencyCode = "YER_NEW",
            exchangeRate = 1.0,
            sourceType = "RECEIPT_COMMISSION_REVERSAL",
            sourceId = SalesCommissionAccountingEventIdentity.receiptReversal(reversalReceiptId, invoice.id),
            createdBy = createdBy,
            lines = listOf(
                DraftJournalLine(payable.id, amountBase, 0.0),
                DraftJournalLine(expense.id, 0.0, amountBase)
            )
        )
    }

    private suspend fun postSaleJournal(
        invoiceId: Long,
        invoiceNo: String,
        request: PostSaleRequest,
        collectibleBase: Double,
        itemAndLegacyRevenueBase: Double,
        chargeCreditsByAccount: Map<Long, Double>,
        costBase: Double,
        cashTreasuryGlAccountId: Long?
    ) {
        val debitAccount = if (request.paymentType == "CASH") {
            requireNotNull(cashTreasuryGlAccountId?.let { db.accountDao().byId(it) }) { "خزينة البيع النقدي غير محددة" }
        } else {
            requireNotNull(db.accountDao().byCode("1300")) { "حساب العملاء 1300 غير موجود" }
        }
        val sales = requireNotNull(db.accountDao().byCode("4000")) { "حساب المبيعات 4000 غير موجود" }
        val cogs = requireNotNull(db.accountDao().byCode("5000")) { "حساب تكلفة المبيعات 5000 غير موجود" }
        val inventory = requireNotNull(db.accountDao().byCode("1200")) { "حساب المخزون 1200 غير موجود" }
        val lines = mutableListOf(
            DraftJournalLine(debitAccount.id, collectibleBase, 0.0),
            DraftJournalLine(sales.id, 0.0, itemAndLegacyRevenueBase)
        )
        chargeCreditsByAccount.forEach { (accountId, amountBase) ->
            if (amountBase > 1e-9) lines += DraftJournalLine(accountId, 0.0, amountBase)
        }
        if (costBase > 0.0) {
            lines += DraftJournalLine(cogs.id, costBase, 0.0)
            lines += DraftJournalLine(inventory.id, 0.0, costBase)
        }
        postJournal(
            entryNo = "JE-$invoiceNo",
            date = request.invoiceDate,
            description = "فاتورة بيع $invoiceNo",
            currencyCode = request.currencyCode,
            exchangeRate = request.exchangeRate,
            sourceType = "SALE",
            sourceId = invoiceId.toString(),
            createdBy = request.createdBy,
            lines = lines
        )
    }

    private suspend fun postCollectionJournal(
        receiptId: Long,
        receiptNo: String,
        treasuryAccountId: Long,
        settledReceivableBase: Double,
        cashReceivableBase: Double,
        treasuryCashBase: Double,
        discountBase: Double,
        currencyCode: String,
        exchangeRate: Double,
        receiptDate: Long,
        createdBy: Long
    ) {
        val treasuryAccount = requireNotNull(db.accountDao().byId(treasuryAccountId)) { "حساب الخزينة غير موجود" }
        val receivables = requireNotNull(db.accountDao().byCode("1300")) { "حساب العملاء 1300 غير موجود" }
        val lines = mutableListOf(
            DraftJournalLine(treasuryAccount.id, treasuryCashBase, 0.0)
        )
        if (discountBase > 1e-9) {
            val settlementDiscount = requireNotNull(db.accountDao().byCode("4110")) { "حساب خصومات تسوية العملاء 4110 غير موجود" }
            lines += DraftJournalLine(settlementDiscount.id, discountBase, 0.0)
        }
        lines += DraftJournalLine(receivables.id, 0.0, settledReceivableBase)

        // FX belongs to the cash leg only. The settlement discount clears A/R at the
        // invoice historical rate and must never create a synthetic FX gain/loss.
        val fxDifference = treasuryCashBase - cashReceivableBase
        if (fxDifference > 1e-9) {
            val fxGain = requireNotNull(db.accountDao().byCode("4250")) { "حساب أرباح فروق العملة 4250 غير موجود" }
            lines += DraftJournalLine(fxGain.id, 0.0, fxDifference)
        } else if (fxDifference < -1e-9) {
            val fxLoss = requireNotNull(db.accountDao().byCode("6750")) { "حساب خسائر فروق العملة 6750 غير موجود" }
            lines += DraftJournalLine(fxLoss.id, -fxDifference, 0.0)
        }
        postJournal(
            entryNo = "JE-$receiptNo",
            date = receiptDate,
            description = "تحصيل عميل $receiptNo",
            currencyCode = currencyCode,
            exchangeRate = exchangeRate,
            sourceType = "CUSTOMER_RECEIPT",
            sourceId = receiptId.toString(),
            createdBy = createdBy,
            lines = lines
        )
    }

    private suspend fun postSalesReturnJournal(
        returnId: Long,
        returnNo: String,
        invoice: SalesInvoiceEntity,
        salesBase: Double,
        costBase: Double,
        settlementType: String,
        returnDate: Long,
        createdBy: Long,
        refundTreasuryGlAccountId: Long?
    ) {
        val inventory = requireNotNull(db.accountDao().byCode("1200")) { "حساب المخزون 1200 غير موجود" }
        val cogs = requireNotNull(db.accountDao().byCode("5000")) { "حساب تكلفة المبيعات 5000 غير موجود" }
        val lines = mutableListOf<DraftJournalLine>()
        if (salesBase > 1e-9) {
            val salesReturns = requireNotNull(db.accountDao().byCode("4100")) { "حساب مردودات المبيعات 4100 غير موجود" }
            val settlement = if (settlementType == "CASH_REFUND") {
                requireNotNull(refundTreasuryGlAccountId?.let { db.accountDao().byId(it) }) { "خزينة رد المبلغ للعميل غير محددة" }
            } else {
                requireNotNull(db.accountDao().byCode("1300")) { "حساب العملاء 1300 غير موجود" }
            }
            lines += DraftJournalLine(salesReturns.id, salesBase, 0.0)
            lines += DraftJournalLine(settlement.id, 0.0, salesBase)
        }
        if (costBase > 0.0) {
            lines += DraftJournalLine(inventory.id, costBase, 0.0)
            lines += DraftJournalLine(cogs.id, 0.0, costBase)
        }
        if (lines.isEmpty()) return
        postJournal(
            entryNo = "JE-$returnNo",
            date = returnDate,
            description = "مرتجع مبيعات $returnNo للفاتورة ${invoice.invoiceNo}",
            currencyCode = invoice.currencyCode,
            exchangeRate = invoice.exchangeRate,
            sourceType = "SALES_RETURN",
            sourceId = returnId.toString(),
            createdBy = createdBy,
            lines = lines
        )
    }

    private suspend fun postCommissionJournal(commissionId: Long, invoice: SalesInvoiceEntity, amountBase: Double, description: String, createdBy: Long, journalDate: Long) {
        if (amountBase <= 0.0) return
        val expense = requireNotNull(db.accountDao().byCode("6400")) { "حساب مصروف عمولات البيع 6400 غير موجود" }
        val payable = requireNotNull(db.accountDao().byCode("2300")) { "حساب عمولات مستحقة 2300 غير موجود" }
        postJournal(
            entryNo = "JE-${numbering.nextDocumentNo("COM")}",
            date = journalDate,
            description = "$description — ${invoice.invoiceNo}",
            currencyCode = "YER_NEW",
            exchangeRate = 1.0,
            sourceType = "SALES_COMMISSION",
            sourceId = SalesCommissionAccountingEventIdentity.commission(commissionId),
            createdBy = createdBy,
            lines = listOf(
                DraftJournalLine(expense.id, amountBase, 0.0),
                DraftJournalLine(payable.id, 0.0, amountBase)
            )
        )
    }

    private suspend fun postCommissionReversalJournal(returnId: Long, invoice: SalesInvoiceEntity, amountBase: Double, returnNo: String, createdBy: Long, journalDate: Long) {
        if (amountBase <= 0.0) return
        val expense = requireNotNull(db.accountDao().byCode("6400")) { "حساب مصروف عمولات البيع 6400 غير موجود" }
        val payable = requireNotNull(db.accountDao().byCode("2300")) { "حساب عمولات مستحقة 2300 غير موجود" }
        postJournal(
            entryNo = "JE-${numbering.nextDocumentNo("CMR")}",
            date = journalDate,
            description = "إلغاء عمولة بسبب مرتجع $returnNo — ${invoice.invoiceNo}",
            currencyCode = "YER_NEW",
            exchangeRate = 1.0,
            sourceType = "COMMISSION_REVERSAL",
            sourceId = SalesCommissionAccountingEventIdentity.returnReversal(returnId),
            createdBy = createdBy,
            lines = listOf(
                DraftJournalLine(payable.id, amountBase, 0.0),
                DraftJournalLine(expense.id, 0.0, amountBase)
            )
        )
    }

    private suspend fun requireSalesCashRefundTimelineCovered(
        invoice: SalesInvoiceEntity,
        proposedRefundBase: Double = 0.0,
        proposedCashDeltaBase: Double = 0.0,
        eventDate: Long
    ) {
        val relevantDates = buildSet {
            add(eventDate)
            db.salesDao().receiptsForInvoice(invoice.id)
                .filter { it.receiptDate >= eventDate }
                .forEach { add(it.receiptDate) }
            db.salesDao().returnsForInvoice(invoice.id)
                .filter { it.status == "POSTED" && it.settlementType == "CASH_REFUND" && it.returnDate >= eventDate }
                .forEach { add(it.returnDate) }
        }.sorted()

        for (date in relevantDates) {
            val actualCashBase = if (invoice.paymentType == "CASH") {
                (invoice.totalBase - db.additionalChargesDao().externallySettledBaseForInvoiceAsOf(invoice.id, date)).coerceAtLeast(0.0)
            } else {
                db.salesDao().receivedBaseForInvoiceAsOf(invoice.id, date)
            } + proposedCashDeltaBase
            val refundedBase = db.salesDao().cashRefundedBaseForInvoiceAsOf(invoice.id, date)
            CashRefundCoveragePolicy.requireCovered(
                actualCashBase = actualCashBase,
                alreadyRefundedBase = refundedBase,
                requestedRefundBase = proposedRefundBase,
                context = "فاتورة البيع ${invoice.invoiceNo}: الرد النقدي يجب أن يكون مغطى بالنقد المحصل فعلياً"
            )
        }
    }

    private suspend fun resolveTreasury(treasuryAccountId: Long?, currencyCode: String): TreasuryAccountEntity {
        val treasury = if (treasuryAccountId != null) {
            requireNotNull(db.accountingDao().treasuryById(treasuryAccountId)) { "الخزينة/البنك غير موجود" }
        } else {
            requireNotNull(db.accountingDao().allActiveTreasury().firstOrNull { it.currencyCode == currencyCode }) {
                "لا توجد خزينة/حساب بنكي نشط بعملة $currencyCode"
            }
        }
        require(treasury.isActive) { "الخزينة/البنك غير نشط" }
        require(treasury.currencyCode == currencyCode) { "عملة الخزينة/البنك يجب أن تطابق عملة العملية" }
        return treasury
    }

    private suspend fun postJournal(
        entryNo: String,
        date: Long,
        description: String,
        currencyCode: String,
        exchangeRate: Double,
        sourceType: String,
        sourceId: String,
        createdBy: Long,
        lines: List<DraftJournalLine>
    ): Long {
        AccountingValidator.validate(lines)
        val entryId = db.journalDao().insertEntry(
            JournalEntryEntity(
                entryNo = entryNo,
                entryDate = date,
                description = description,
                currencyCode = currencyCode,
                exchangeRate = exchangeRate,
                sourceType = sourceType,
                sourceId = sourceId,
                createdBy = createdBy
            )
        )
        db.journalDao().insertLines(lines.map { JournalLineEntity(entryId = entryId, accountId = it.accountId, debit = it.debit, credit = it.credit) })
        return entryId
    }

}
