package com.fush.erp.domain

import androidx.room.withTransaction
import com.fush.erp.data.FushDatabase
import com.fush.erp.data.entity.*
import kotlin.math.min

class SalesService(private val db: FushDatabase) {
    private val numbering = AutoNumberService(db)


    data class PostSaleRequest(
        val customerId: Long,
        val warehouseId: Long,
        val currencyCode: String,
        val exchangeRate: Double,
        val paymentType: String,
        val creditDays: Int = 0,
        val discountPct: Double = 0.0,
        val transportOriginal: Double = 0.0,
        val feesOriginal: Double = 0.0,
        val riskMarginOriginal: Double = 0.0,
        val invoiceDate: Long = System.currentTimeMillis(),
        val notes: String = "",
        val belowFloorApprovedBy: Long? = null,
        val belowFloorReason: String = "",
        val salesRepId: Long? = null,
        val createdBy: Long,
        val lines: List<SalesDraftLine>,
        val treasuryAccountId: Long? = null
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
        val amountOriginal: Double
    )

    data class MultiReceiptResult(
        val receiptId: Long,
        val receiptNo: String,
        val allocationCount: Int,
        val totalOriginal: Double,
        val allocatedBase: Double,
        val cashBase: Double,
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
        province: String,
        channel: String,
        currencyCode: String,
        creditLimitBase: Double,
        creditDays: Int,
        allowCredit: Boolean,
        salesRepName: String = "",
        createdBy: Long = 0L,
        salesRepId: Long? = null
    ): CustomerEntity = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.SALES_POST)
        require(nameAr.isNotBlank()) { "اسم العميل مطلوب" }
        require(province.isNotBlank()) { "المحافظة مطلوبة" }
        require(channel in setOf("DIRECT", "RETAIL", "DISTRIBUTOR_CASH", "DISTRIBUTOR_CREDIT")) { "قناة البيع غير صالحة" }
        require(creditLimitBase >= 0.0 && creditLimitBase.isFinite()) { "السقف الائتماني غير صالح" }
        if (allowCredit) SalesMath.validateCreditDays(creditDays)
        require(db.currencyDao().allActive().any { it.code == currencyCode }) { "العملة غير موجودة" }
        val rep = salesRepId?.let { id ->
            requireNotNull(db.salesRepresentativeDao().byId(id)) { "مندوب المبيعات غير موجود" }.also {
                require(it.status == "ACTIVE") { "مندوب المبيعات غير نشط" }
            }
        }
        val code = numbering.nextCustomerCode()
        val row = CustomerEntity(
            code = code,
            nameAr = nameAr.trim(),
            phone = phone.trim(),
            province = province.trim(),
            channel = channel,
            currencyCode = currencyCode,
            creditLimitBase = creditLimitBase,
            creditDays = if (allowCredit) creditDays.coerceAtMost(SalesMath.MAX_CREDIT_DAYS) else 0,
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
        salesRepId: Long? = null
    ): CustomerEntity = db.withTransaction {
        db.requireUserPermission(updatedBy, SecurityPermissions.SALES_POST)
        require(nameAr.isNotBlank()) { "اسم العميل مطلوب" }
        require(province.isNotBlank()) { "المحافظة مطلوبة" }
        require(channel in setOf("DIRECT", "RETAIL", "DISTRIBUTOR_CASH", "DISTRIBUTOR_CREDIT")) { "قناة البيع غير صالحة" }
        require(classification in setOf("A", "B", "C")) { "تصنيف العميل غير صالح" }
        require(creditLimitBase >= 0.0 && creditLimitBase.isFinite()) { "السقف الائتماني غير صالح" }
        if (allowCredit) SalesMath.validateCreditDays(creditDays)
        require(db.currencyDao().allActive().any { it.code == currencyCode }) { "العملة غير موجودة" }
        val old = requireNotNull(db.customerDao().byId(customerId)) { "العميل غير موجود" }
        val requestedRepId = salesRepId ?: old.salesRepId?.takeIf { salesRepName.trim() == old.salesRepName.trim() }
        val rep = requestedRepId?.let { id ->
            requireNotNull(db.salesRepresentativeDao().byId(id)) { "مندوب المبيعات غير موجود" }.also {
                require(it.status == "ACTIVE") { "مندوب المبيعات غير نشط" }
            }
        }
        val row = old.copy(
            nameAr = nameAr.trim(),
            nameEn = nameEn.trim(),
            phone = phone.trim(),
            address = address.trim(),
            province = province.trim(),
            channel = channel,
            classification = classification,
            currencyCode = currencyCode,
            creditLimitBase = creditLimitBase,
            creditDays = if (allowCredit) creditDays.coerceAtMost(SalesMath.MAX_CREDIT_DAYS) else 0,
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
        val salesRepRatePct = salesRep?.commissionRatePct ?: SalesMath.DEFAULT_COMMISSION_PCT
        require(db.warehouseDao().allActive().any { it.id == request.warehouseId }) { "المخزن غير موجود" }
        require(request.currencyCode == customer.currencyCode || request.currencyCode in setOf("YER_NEW", "YER_OLD", "USD")) { "عملة الفاتورة غير صالحة" }
        val cashTreasury = if (request.paymentType == "CASH") resolveTreasury(request.treasuryAccountId, request.currencyCode) else null

        val grossOriginal = SalesMath.grossOriginal(request.lines)
        val totalBaseQty = SalesMath.totalBaseQuantity(request.lines)
        SalesMath.validateDiscount(request.paymentType, totalBaseQty, request.discountPct)
        val discountOriginal = SalesMath.discountOriginal(grossOriginal, request.discountPct)
        val totalOriginal = SalesMath.totalOriginal(
            grossOriginal,
            discountOriginal,
            request.transportOriginal,
            request.feesOriginal,
            request.riskMarginOriginal
        )
        val totalBase = SalesMath.toBaseAmount(totalOriginal, request.exchangeRate)

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
            if (item.code == "FG-FUSH-60") {
                val effective = SalesMath.effectiveBaseUnitPriceBase(line, request.discountPct, request.exchangeRate)
                if (effective + 1e-9 < SalesMath.FUSH_PRICE_FLOOR_BASE_PER_BOTTLE) {
                    require(request.belowFloorApprovedBy != null && request.belowFloorReason.isNotBlank()) {
                        "السعر أقل من حد الحماية 750 ريال للعبوة ويحتاج اعتماداً موثقاً"
                    }
                }
            }
        }

        val dueDate = if (request.paymentType == "CREDIT") {
            validateCredit(customer, totalBase, request.creditDays, request.invoiceDate)
            request.invoiceDate + request.creditDays.toLong() * 24L * 60L * 60L * 1000L
        } else null

        val invoiceNo = numbering.nextDocumentNo("SINV", request.invoiceDate)
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
                salesRepId = salesRep?.id,
                salesRepNameSnapshot = salesRepNameSnapshot,
                salesRepRatePct = salesRepRatePct,
                discountPct = request.discountPct,
                grossOriginal = grossOriginal,
                discountOriginal = discountOriginal,
                transportOriginal = request.transportOriginal,
                feesOriginal = request.feesOriginal,
                riskMarginOriginal = request.riskMarginOriginal,
                totalOriginal = totalOriginal,
                totalBase = totalBase,
                belowFloorApprovedBy = request.belowFloorApprovedBy,
                belowFloorReason = request.belowFloorReason.trim(),
                notes = request.notes.trim(),
                createdBy = request.createdBy
            )
        )

        var totalCostBase = 0.0
        request.lines.forEach { draft ->
            val lineGross = draft.grossOriginal
            val lineDiscount = SalesMath.discountOriginal(lineGross, request.discountPct)
            val lineNet = lineGross - lineDiscount
            val lineId = db.salesDao().insertLine(
                SalesLineEntity(
                    invoiceId = invoiceId,
                    itemId = draft.itemId,
                    unitId = draft.unitId,
                    quantity = draft.quantity,
                    factorToBase = draft.factorToBase,
                    baseQuantity = draft.baseQuantity,
                    unitPriceOriginal = draft.unitPriceOriginal,
                    grossOriginal = lineGross,
                    discountOriginal = lineDiscount,
                    netOriginal = lineNet
                )
            )
            totalCostBase += allocateStockForSale(request.warehouseId, lineId, draft.itemId, draft.baseQuantity, request.invoiceDate)
        }

        postSaleJournal(invoiceId, invoiceNo, request, totalBase, totalCostBase, cashTreasury?.accountId)
        if (request.paymentType == "CASH") {
            createCashReceiptAndCommission(invoiceId, customer, totalOriginal, totalBase, request)
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
        receiptDate: Long = System.currentTimeMillis(),
        treasuryAccountId: Long? = null
    ): ReceiptResult {
        val result = postReceiptAllocations(
            customerId = customerId,
            allocations = listOf(ReceiptAllocationRequest(invoiceId, amountOriginal)),
            currencyCode = currencyCode,
            exchangeRate = exchangeRate,
            notes = notes,
            createdBy = createdBy,
            receiptDate = receiptDate,
            treasuryAccountId = treasuryAccountId
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
        receiptDate: Long = System.currentTimeMillis(),
        treasuryAccountId: Long? = null
    ): MultiReceiptResult = db.withTransaction {
        val validatedCustomerId = CustomerMovementIdentity.requireId(customerId)
        val open = db.salesDao().openInvoiceSummaries(validatedCustomerId)
            .filter { it.currencyCode == currencyCode }
        val plan = SettlementAllocationMath.allocateOldest(
            amountOriginal,
            open.map { row ->
                val historicalRate = if (row.totalOriginal > 0.0) row.totalBase / row.totalOriginal else 0.0
                SettlementAllocationMath.InvoiceBalance(row.id, row.outstandingBase, historicalRate)
            }
        )
        postReceiptAllocationsInternal(
            customerId = validatedCustomerId,
            allocations = plan.allocations.map { ReceiptAllocationRequest(it.invoiceId, it.amountOriginal) },
            currencyCode = currencyCode,
            exchangeRate = exchangeRate,
            notes = notes,
            createdBy = createdBy,
            receiptDate = receiptDate,
            treasuryAccountId = treasuryAccountId
        )
    }

    suspend fun postReceiptAllocations(
        customerId: Long,
        allocations: List<ReceiptAllocationRequest>,
        currencyCode: String,
        exchangeRate: Double,
        notes: String,
        createdBy: Long,
        receiptDate: Long = System.currentTimeMillis(),
        treasuryAccountId: Long? = null
    ): MultiReceiptResult = db.withTransaction {
        postReceiptAllocationsInternal(
            customerId, allocations, currencyCode, exchangeRate, notes, createdBy, receiptDate, treasuryAccountId
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
        treasuryAccountId: Long?
    ): MultiReceiptResult {
        db.requireUserPermission(createdBy, SecurityPermissions.COLLECTION_POST)
        AccountingService(db).requirePostingPeriodOpen(receiptDate)
        SalesMath.validateExchangeRate(exchangeRate)
        require(allocations.isNotEmpty()) { "يجب تحديد فاتورة واحدة على الأقل للتحصيل" }
        require(allocations.map { it.invoiceId }.distinct().size == allocations.size) { "لا يجوز تكرار الفاتورة في نفس التحصيل" }
        val validatedCustomerId = CustomerMovementIdentity.requireId(customerId)
        val customer = requireNotNull(db.customerDao().byId(validatedCustomerId)) { "العميل غير موجود" }
        val treasury = resolveTreasury(treasuryAccountId, currencyCode)

        data class Prepared(val invoice: SalesInvoiceEntity, val request: ReceiptAllocationRequest, val split: CustomerArMath.ReceiptSplit)
        val prepared = allocations.map { allocation ->
            require(allocation.amountOriginal.isFinite() && allocation.amountOriginal > 0.0) { "مبلغ تخصيص الفاتورة يجب أن يكون أكبر من صفر" }
            val invoice = requireNotNull(db.salesDao().invoiceById(allocation.invoiceId)) { "فاتورة البيع غير موجودة" }
            require(invoice.customerId == customer.id) { "إحدى الفواتير لا تخص العميل المحدد" }
            require(invoice.status == "POSTED" && invoice.paymentType == "CREDIT") { "التحصيل متاح للفواتير الآجلة المرحلة فقط" }
            require(invoice.currencyCode == currencyCode) { "جميع الفواتير في التحصيل الواحد يجب أن تكون بنفس عملة التحصيل" }
            val split = CustomerArMath.receiptSplit(allocation.amountOriginal, invoice.exchangeRate, exchangeRate)
            val outstanding = invoiceOutstandingBase(invoice.id)
            require(split.allocatedBase <= outstanding + 1e-8) { "التحصيل يتجاوز الرصيد المستحق على الفاتورة ${invoice.invoiceNo}" }
            Prepared(invoice, allocation, split)
        }

        val totalOriginal = prepared.sumOf { it.request.amountOriginal }
        val totalAllocatedBase = prepared.sumOf { it.split.allocatedBase }
        val totalCashBase = prepared.sumOf { it.split.cashBase }
        val receiptNo = numbering.nextDocumentNo("RCPT", receiptDate)
        val receiptId = db.salesDao().insertReceipt(
            CustomerReceiptEntity(
                receiptNo = receiptNo,
                customerId = customer.id,
                receiptDate = receiptDate,
                currencyCode = currencyCode,
                exchangeRate = exchangeRate,
                amountOriginal = totalOriginal,
                amountBase = totalAllocatedBase,
                notes = notes.trim(),
                createdBy = createdBy
            )
        )

        var commissionBase = 0.0
        prepared.forEach { row ->
            val allocationId = db.salesDao().insertReceiptAllocation(
                CustomerReceiptAllocationEntity(
                    receiptId = receiptId,
                    invoiceId = row.invoice.id,
                    amountBase = row.split.allocatedBase
                )
            )
            commissionBase += accrueCommissionToTarget(row.invoice, customer, allocationId, createdBy, receiptDate)
        }
        postCollectionJournal(
            receiptId = receiptId,
            receiptNo = receiptNo,
            treasuryAccountId = treasury.accountId,
            allocatedBase = totalAllocatedBase,
            cashBase = totalCashBase,
            currencyCode = currencyCode,
            exchangeRate = exchangeRate,
            receiptDate = receiptDate,
            createdBy = createdBy
        )
        return MultiReceiptResult(
            receiptId = receiptId,
            receiptNo = receiptNo,
            allocationCount = prepared.size,
            totalOriginal = totalOriginal,
            allocatedBase = totalAllocatedBase,
            cashBase = totalCashBase,
            fxDifferenceBase = totalCashBase - totalAllocatedBase,
            commissionBase = commissionBase
        )
    }

    suspend fun reverseReceipt(
        receiptId: Long,
        reason: String,
        createdBy: Long,
        reversalDate: Long = System.currentTimeMillis()
    ): ReceiptReversalResult = db.withTransaction {
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
                notes = "عكس ${original.receiptNo}: ${reason.trim()}",
                reversalOfReceiptId = original.id,
                createdBy = createdBy
            )
        )
        allocations.forEach { allocation ->
            db.salesDao().insertReceiptAllocation(
                CustomerReceiptAllocationEntity(
                    receiptId = reversalId,
                    invoiceId = allocation.invoiceId,
                    amountBase = -allocation.amountBase
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
                oldValue = "${original.receiptNo}|${original.amountOriginal}|${original.amountBase}",
                newValue = "$reversalNo|journal=$reversalJournalId|commission=$reversedCommission",
                reason = reason.trim()
            )
        )
        ReceiptReversalResult(
            reversalReceiptId = reversalId,
            reversalReceiptNo = reversalNo,
            reversalJournalEntryId = reversalJournalId,
            restoredReceivableBase = allocations.sumOf { it.amountBase },
            reversedCommissionBase = reversedCommission
        )
    }

    suspend fun postReturn(
        salesLineId: Long,
        quantity: Double,
        settlementType: String,
        reason: String,
        createdBy: Long,
        returnDate: Long = System.currentTimeMillis(),
        treasuryAccountId: Long? = null
    ): ReturnResult = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.SALES_RETURN)
        AccountingService(db).requirePostingPeriodOpen(returnDate)
        require(settlementType in setOf("CUSTOMER_CREDIT", "CASH_REFUND")) { "نوع تسوية المرتجع غير صالح" }
        require(reason.isNotBlank()) { "سبب المرتجع مطلوب" }
        val line = requireNotNull(db.salesDao().lineById(salesLineId)) { "سطر البيع غير موجود" }
        val invoice = requireNotNull(db.salesDao().invoiceById(line.invoiceId)) { "فاتورة البيع غير موجودة" }
        val customerId = CustomerMovementIdentity.requireId(invoice.customerId)
        val customer = requireNotNull(db.customerDao().byId(customerId)) { "العميل غير موجود" }
        val refundTreasury = if (settlementType == "CASH_REFUND") resolveTreasury(treasuryAccountId, invoice.currencyCode) else null
        val alreadyReturned = db.salesDao().returnedQuantityForLine(line.id)
        SalesMath.validateReturn(quantity, line.quantity, alreadyReturned)
        val returnBaseQty = quantity * line.factorToBase
        val unitNetOriginal = if (line.quantity > 0.0) line.netOriginal / line.quantity else 0.0
        val totalOriginal = quantity * unitNetOriginal
        val totalBase = totalOriginal * invoice.exchangeRate

        val allocationPlan = mutableListOf<Pair<SalesAllocationEntity, Double>>()
        var remainingBase = returnBaseQty
        for (allocation in db.salesDao().allocationsForLine(line.id)) {
            if (remainingBase <= 1e-9) break
            val already = db.salesDao().returnedBaseForAllocation(allocation.id)
            val available = (allocation.quantityBase - already).coerceAtLeast(0.0)
            if (available <= 1e-9) continue
            val take = min(available, remainingBase)
            allocationPlan += allocation to take
            remainingBase -= take
        }
        require(remainingBase <= 1e-9) { "تعذر ربط المرتجع بالتشغيلات الأصلية بالكامل" }
        val totalCostBase = allocationPlan.sumOf { (a, qtyBase) -> qtyBase * a.unitCostBase }

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
                factorToBase = line.factorToBase,
                baseQuantity = returnBaseQty,
                unitPriceOriginal = unitNetOriginal,
                lineNetOriginal = totalOriginal,
                costBase = totalCostBase
            )
        )
        allocationPlan.forEach { (allocation, qtyBase) ->
            val cost = qtyBase * allocation.unitCostBase
            db.salesDao().insertReturnAllocation(
                SalesReturnAllocationEntity(
                    returnLineId = returnLineId,
                    salesAllocationId = allocation.id,
                    itemId = allocation.itemId,
                    lotNo = allocation.lotNo,
                    expiryDate = allocation.expiryDate,
                    quantityBase = qtyBase,
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
        val reversed = reverseCommissionForReturn(invoice.id, totalBase, createdBy, returnNo, returnDate)
        ReturnResult(returnId, returnNo, totalBase, totalCostBase, reversed)
    }

    suspend fun invoiceOutstandingBase(invoiceId: Long): Double {
        val invoice = requireNotNull(db.salesDao().invoiceById(invoiceId)) { "فاتورة البيع غير موجودة" }
        if (invoice.paymentType == "CASH") return 0.0
        val received = db.salesDao().receivedBaseForInvoice(invoiceId)
        val returned = db.salesDao().customerCreditReturnedBaseForInvoice(invoiceId)
        return (invoice.totalBase - received - returned).coerceAtLeast(0.0)
    }

    private suspend fun validateCredit(customer: CustomerEntity, totalBase: Double, requestedDays: Int, invoiceDate: Long) {
        require(customer.allowCredit) { "العميل غير مخول للبيع الآجل" }
        SalesMath.validateCreditDays(requestedDays)
        require(requestedDays <= customer.creditDays.coerceAtMost(SalesMath.MAX_CREDIT_DAYS)) { "مدة الائتمان تتجاوز المدة المعتمدة للعميل" }
        require(db.salesDao().overdueInvoiceCount(customer.id, invoiceDate) == 0) { "يوجد على العميل دين متأخر؛ تم إيقاف الآجل تلقائياً" }
        val outstanding = db.salesDao().customerOutstandingBase(customer.id).coerceAtLeast(0.0)
        require(outstanding + totalBase <= customer.creditLimitBase + 1e-9) { "الفاتورة تتجاوز السقف الائتماني للعميل" }
    }

    private suspend fun allocateStockForSale(
        warehouseId: Long,
        salesLineId: Long,
        itemId: Long,
        requiredBaseQty: Double,
        movementDate: Long
    ): Double {
        val advancedInventory = AdvancedInventoryService(db)
        val balance = advancedInventory.usableBalance(warehouseId, itemId, movementDate)
        require(balance + 1e-9 >= requiredBaseQty) { "المخزون المقبول وغير المنتهي لا يكفي لإتمام البيع" }
        val lots = advancedInventory.usableLots(warehouseId, itemId, movementDate)
        var remaining = requiredBaseQty
        var totalCost = 0.0
        for (lot in lots) {
            if (remaining <= 1e-9) break
            val take = min(lot.quantityBase, remaining)
            if (take <= 1e-9) continue
            val unitCost = if (lot.quantityBase > 0.0) lot.inventoryValueBase / lot.quantityBase else 0.0
            val cost = take * unitCost
            db.salesDao().insertAllocation(
                SalesAllocationEntity(
                    salesLineId = salesLineId,
                    itemId = itemId,
                    lotNo = lot.lotNo,
                    expiryDate = lot.expiryDate,
                    quantityBase = take,
                    unitCostBase = unitCost,
                    costBase = cost
                )
            )
            db.stockDao().insertMovement(
                StockMovementEntity(
                    movementDate = movementDate,
                    warehouseId = warehouseId,
                    itemId = itemId,
                    movementType = "SALE",
                    quantityBase = -take,
                    unitCostBase = unitCost,
                    referenceType = "SALES_LINE",
                    referenceId = salesLineId,
                    lotNo = lot.lotNo,
                    expiryDate = lot.expiryDate
                )
            )
            totalCost += cost
            remaining -= take
        }
        require(remaining <= 1e-9) { "تعذر تخصيص الكمية المطلوبة من التشغيلات المقبولة" }
        return totalCost
    }

    private suspend fun createCashReceiptAndCommission(
        invoiceId: Long,
        customer: CustomerEntity,
        amountOriginal: Double,
        amountBase: Double,
        request: PostSaleRequest
    ) {
        val receiptId = db.salesDao().insertReceipt(
            CustomerReceiptEntity(
                receiptNo = numbering.nextDocumentNo("CASH"),
                customerId = customer.id,
                receiptDate = request.invoiceDate,
                currencyCode = request.currencyCode,
                exchangeRate = request.exchangeRate,
                amountOriginal = amountOriginal,
                amountBase = amountBase,
                notes = "تحصيل نقدي تلقائي مع فاتورة البيع",
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

        db.salesDao().insertCommission(
            SalesCommissionEntity(
                invoiceId = invoice.id,
                receiptAllocationId = receiptAllocationId,
                salesRepId = invoice.salesRepId,
                beneficiary = invoice.salesRepNameSnapshot.ifBlank { customer.salesRepName.ifBlank { "مندوب غير محدد" } },
                ratePct = invoice.salesRepRatePct,
                earnedBase = delta
            )
        )
        postCommissionJournal(invoice, delta, "استحقاق عمولة بعد التحصيل", createdBy, commissionDate)
        return delta
    }

    private suspend fun reduceCommissionToCollectedTarget(
        invoice: SalesInvoiceEntity,
        createdBy: Long,
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
        postReceiptCommissionReversalJournal(invoice, originalNeeded, referenceNo, createdBy, journalDate)
        return originalNeeded
    }

    private suspend fun reverseCommissionForReturn(invoiceId: Long, returnedBase: Double, createdBy: Long, returnNo: String, reversalDate: Long): Double {
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
        postCommissionReversalJournal(invoice, originalNeeded, returnNo, createdBy, reversalDate)
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
            sourceId = invoice.id.toString(),
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
        totalBase: Double,
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
            DraftJournalLine(debitAccount.id, totalBase, 0.0),
            DraftJournalLine(sales.id, 0.0, totalBase)
        )
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
        allocatedBase: Double,
        cashBase: Double,
        currencyCode: String,
        exchangeRate: Double,
        receiptDate: Long,
        createdBy: Long
    ) {
        val treasuryAccount = requireNotNull(db.accountDao().byId(treasuryAccountId)) { "حساب الخزينة غير موجود" }
        val receivables = requireNotNull(db.accountDao().byCode("1300")) { "حساب العملاء 1300 غير موجود" }
        val lines = mutableListOf(
            DraftJournalLine(treasuryAccount.id, cashBase, 0.0),
            DraftJournalLine(receivables.id, 0.0, allocatedBase)
        )
        val fxDifference = cashBase - allocatedBase
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
        val salesReturns = requireNotNull(db.accountDao().byCode("4100")) { "حساب مردودات المبيعات 4100 غير موجود" }
        val settlement = if (settlementType == "CASH_REFUND") {
            requireNotNull(refundTreasuryGlAccountId?.let { db.accountDao().byId(it) }) { "خزينة رد المبلغ للعميل غير محددة" }
        } else {
            requireNotNull(db.accountDao().byCode("1300")) { "حساب العملاء 1300 غير موجود" }
        }
        val inventory = requireNotNull(db.accountDao().byCode("1200")) { "حساب المخزون 1200 غير موجود" }
        val cogs = requireNotNull(db.accountDao().byCode("5000")) { "حساب تكلفة المبيعات 5000 غير موجود" }
        val lines = mutableListOf(
            DraftJournalLine(salesReturns.id, salesBase, 0.0),
            DraftJournalLine(settlement.id, 0.0, salesBase)
        )
        if (costBase > 0.0) {
            lines += DraftJournalLine(inventory.id, costBase, 0.0)
            lines += DraftJournalLine(cogs.id, 0.0, costBase)
        }
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

    private suspend fun postCommissionJournal(invoice: SalesInvoiceEntity, amountBase: Double, description: String, createdBy: Long, journalDate: Long) {
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
            sourceId = invoice.id.toString(),
            createdBy = createdBy,
            lines = listOf(
                DraftJournalLine(expense.id, amountBase, 0.0),
                DraftJournalLine(payable.id, 0.0, amountBase)
            )
        )
    }

    private suspend fun postCommissionReversalJournal(invoice: SalesInvoiceEntity, amountBase: Double, returnNo: String, createdBy: Long, journalDate: Long) {
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
            sourceId = invoice.id.toString(),
            createdBy = createdBy,
            lines = listOf(
                DraftJournalLine(payable.id, amountBase, 0.0),
                DraftJournalLine(expense.id, 0.0, amountBase)
            )
        )
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
