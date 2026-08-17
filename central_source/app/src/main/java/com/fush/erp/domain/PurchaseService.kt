package com.fush.erp.domain

import androidx.room.withTransaction
import com.fush.erp.data.FushDatabase
import com.fush.erp.data.entity.*

class PurchaseService(private val db: FushDatabase) {
    private val numbering = AutoNumberService(db)

    private data class PreparedPurchaseReturnLine(
        val line: PurchaseLineEntity,
        val quantity: Double,
        val baseQuantity: Double,
        val totalOriginal: Double
    )


    suspend fun createSupplier(
        nameAr: String,
        phone: String,
        currencyCode: String,
        paymentTermsDays: Int,
        nameEn: String = "",
        address: String = "",
        createdBy: Long = 0L
    ): SupplierEntity = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.PURCHASE_POST)
        require(nameAr.isNotBlank()) { "اسم المورد مطلوب" }
        require(paymentTermsDays >= 0) { "مدة السداد غير صالحة" }
        require(db.currencyDao().allActive().any { it.code == currencyCode }) { "العملة غير موجودة" }
        val code = numbering.nextSupplierCode()
        val row = SupplierEntity(
            code = code,
            nameAr = nameAr.trim(),
            nameEn = nameEn.trim(),
            phone = phone.trim(),
            address = address.trim(),
            currencyCode = currencyCode,
            paymentTermsDays = paymentTermsDays
        )
        val id = db.supplierDao().insert(row)
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = createdBy,
                action = "CREATE",
                entityType = "SUPPLIER",
                entityId = id.toString(),
                newValue = "${row.code}|${row.nameAr}|${row.currencyCode}",
                reason = "إنشاء مورد"
            )
        )
        row.copy(id = id)
    }

    data class PostPurchaseRequest(
        val supplierId: Long,
        val warehouseId: Long,
        val currencyCode: String,
        val exchangeRate: Double,
        val paymentType: String,
        val supplierInvoiceNo: String = "",
        val notes: String = "",
        val invoiceDate: Long = System.currentTimeMillis(),
        val createdBy: Long,
        val lines: List<PurchaseDraftLine>,
        val treasuryAccountId: Long? = null
    )

    suspend fun postPurchase(request: PostPurchaseRequest): Long = db.withTransaction {
        db.requireUserPermission(request.createdBy, SecurityPermissions.PURCHASE_POST)
        AccountingService(db).requirePostingPeriodOpen(request.invoiceDate)
        require(request.paymentType in setOf("CASH", "CREDIT")) { "نوع السداد غير صالح" }
        PurchaseMath.validateExchangeRate(request.exchangeRate)
        val supplierId = SupplierMovementIdentity.requireId(request.supplierId)
        val supplier = requireNotNull(db.supplierDao().byId(supplierId)) { "المورد غير موجود" }
        require(db.warehouseDao().allActive().any { it.id == request.warehouseId }) { "المخزن غير موجود" }
        val cashTreasury = if (request.paymentType == "CASH") resolveTreasury(request.treasuryAccountId, request.currencyCode) else null
        val totalOriginal = PurchaseMath.totalOriginal(request.lines)
        val totalBase = PurchaseMath.toBaseAmount(totalOriginal, request.exchangeRate)
        val invoiceNo = numbering.nextDocumentNo("PINV", request.invoiceDate)

        val invoiceId = db.purchaseDao().insertInvoice(
            PurchaseInvoiceEntity(
                invoiceNo = invoiceNo,
                supplierInvoiceNo = request.supplierInvoiceNo,
                supplierId = supplier.id,
                invoiceDate = request.invoiceDate,
                dueDate = if (request.paymentType == "CREDIT") request.invoiceDate + supplier.paymentTermsDays.toLong() * 86_400_000L else null,
                warehouseId = request.warehouseId,
                currencyCode = request.currencyCode,
                exchangeRate = request.exchangeRate,
                paymentType = request.paymentType,
                subtotalOriginal = totalOriginal,
                totalOriginal = totalOriginal,
                totalBase = totalBase,
                notes = request.notes,
                createdBy = request.createdBy
            )
        )

        request.lines.forEach { line ->
            PurchaseMath.validateLine(line)
            val item = requireNotNull(db.itemDao().byId(line.itemId)) { "الصنف غير موجود" }
            require(item.isActive) { "الصنف ${item.nameAr} موقوف ولا يمكن الشراء عليه" }
            val conversion = requireNotNull(db.itemUnitConversionDao().byItemAndUnit(line.itemId, line.unitId)) {
                "وحدة الشراء غير معرفة للصنف"
            }
            require(conversion.allowPurchase) { "هذه الوحدة غير مسموحة للشراء" }
            require(kotlin.math.abs(conversion.factorToBase - line.factorToBase) < 1e-9) { "عامل التحويل تغير، أعد تحميل الوحدة" }
            val unitCostBase = PurchaseMath.unitCostBase(line, request.exchangeRate)
            val lineId = db.purchaseDao().insertLine(
                PurchaseLineEntity(
                    invoiceId = invoiceId,
                    itemId = line.itemId,
                    unitId = line.unitId,
                    quantity = line.quantity,
                    factorToBase = line.factorToBase,
                    baseQuantity = line.baseQuantity,
                    unitPriceOriginal = line.unitPriceOriginal,
                    lineTotalOriginal = line.lineTotalOriginal,
                    unitCostBase = unitCostBase,
                    lotNo = line.lotNo,
                    expiryDate = line.expiryDate
                )
            )
            db.stockDao().insertMovement(
                StockMovementEntity(
                    movementDate = request.invoiceDate,
                    warehouseId = request.warehouseId,
                    itemId = line.itemId,
                    movementType = "PURCHASE",
                    quantityBase = line.baseQuantity,
                    unitCostBase = unitCostBase,
                    referenceType = "PURCHASE_LINE",
                    referenceId = lineId,
                    lotNo = line.lotNo,
                    expiryDate = line.expiryDate
                )
            )
        }

        postPurchaseJournal(invoiceId, invoiceNo, totalBase, request, cashTreasury?.accountId)
        invoiceId
    }

    data class PostPurchaseReturnRequest(
        val purchaseInvoiceId: Long,
        val settlementType: String,
        val reason: String,
        val createdBy: Long,
        val returnDate: Long = System.currentTimeMillis(),
        val lines: List<PurchaseReturnDraftLine>,
        val treasuryAccountId: Long? = null
    )

    suspend fun postPurchaseReturn(request: PostPurchaseReturnRequest): Long = db.withTransaction {
        db.requireUserPermission(request.createdBy, SecurityPermissions.PURCHASE_RETURN)
        AccountingService(db).requirePostingPeriodOpen(request.returnDate)
        require(request.settlementType in setOf("SUPPLIER_CREDIT", "CASH_REFUND")) { "نوع تسوية المرتجع غير صالح" }
        require(request.reason.isNotBlank()) { "سبب المرتجع مطلوب" }
        PurchaseMath.validateReturnDraft(request.lines)

        val invoice = requireNotNull(db.purchaseDao().invoiceById(request.purchaseInvoiceId)) { "فاتورة الشراء غير موجودة" }
        SupplierMovementIdentity.requireId(invoice.supplierId)
        require(invoice.status == "POSTED") { "يمكن إرجاع الأصناف من فاتورة مرحلة فقط" }
        val refundTreasury = if (request.settlementType == "CASH_REFUND") resolveTreasury(request.treasuryAccountId, invoice.currencyCode) else null

        val prepared = request.lines.map { draft ->
            val line = requireNotNull(db.purchaseDao().lineById(draft.purchaseLineId)) { "سطر الشراء غير موجود" }
            require(line.invoiceId == invoice.id) { "أحد أسطر المرتجع لا يتبع فاتورة الشراء المحددة" }
            val alreadyReturned = db.purchaseDao().returnedQuantityForLine(line.id)
            PurchaseMath.validateReturn(draft.quantity, line.quantity, alreadyReturned)
            PreparedPurchaseReturnLine(
                line = line,
                quantity = draft.quantity,
                baseQuantity = draft.quantity * line.factorToBase,
                totalOriginal = draft.quantity * line.unitPriceOriginal
            )
        }

        // Validate the exact original lot/expiry in aggregate so multiple invoice lines
        // cannot jointly overdraw the same physical lot.
        prepared.groupBy { Triple(it.line.itemId, it.line.lotNo?.trim().orEmpty(), it.line.expiryDate ?: -1L) }
            .forEach { (key, selectedLines) ->
                val (itemId, lotKey, expiryKey) = key
                val requiredBase = selectedLines.sumOf { it.baseQuantity }
                val availableBase = db.stockDao().lotMovementTimeline(
                    warehouseId = invoice.warehouseId,
                    itemId = itemId,
                    lotKey = lotKey,
                    expiryKey = expiryKey
                ).sumOf { it.quantityBase }
                require(availableBase + 1e-9 >= requiredBase) {
                    "المخزون المتاح من التشغيلة الأصلية لا يكفي لتنفيذ المرتجع"
                }
            }

        val totalOriginal = prepared.sumOf { it.totalOriginal }
        val totalBase = totalOriginal * invoice.exchangeRate
        if (request.settlementType == "CASH_REFUND") {
            val priorCashRefundBase = db.purchaseDao().returnsForInvoice(invoice.id)
                .filter { it.settlementType == "CASH_REFUND" }
                .sumOf { it.totalBase }
            val netPaidBase = if (invoice.paymentType == "CREDIT") db.purchaseDao().paidBaseForInvoice(invoice.id) else invoice.totalBase
            val refundableBase = SupplierApMath.cashRefundableBase(
                paymentType = invoice.paymentType,
                invoiceBase = invoice.totalBase,
                netPaidBase = netPaidBase,
                priorCashRefundBase = priorCashRefundBase
            )
            require(totalBase <= refundableBase + 1e-8) { "الاسترداد النقدي يتجاوز المبلغ المدفوع والقابل للاسترداد فعلياً لهذه الفاتورة" }
        }
        val returnNo = numbering.nextDocumentNo("PRET", request.returnDate)
        val returnId = db.purchaseDao().insertReturn(
            PurchaseReturnEntity(
                returnNo = returnNo,
                purchaseInvoiceId = invoice.id,
                supplierId = invoice.supplierId,
                returnDate = request.returnDate,
                warehouseId = invoice.warehouseId,
                currencyCode = invoice.currencyCode,
                exchangeRate = invoice.exchangeRate,
                settlementType = request.settlementType,
                totalOriginal = totalOriginal,
                totalBase = totalBase,
                reason = request.reason.trim(),
                createdBy = request.createdBy
            )
        )

        prepared.forEach { preparedLine ->
            val line = preparedLine.line
            db.purchaseDao().insertReturnLine(
                PurchaseReturnLineEntity(
                    returnId = returnId,
                    purchaseLineId = line.id,
                    itemId = line.itemId,
                    unitId = line.unitId,
                    quantity = preparedLine.quantity,
                    factorToBase = line.factorToBase,
                    baseQuantity = preparedLine.baseQuantity,
                    unitPriceOriginal = line.unitPriceOriginal,
                    lineTotalOriginal = preparedLine.totalOriginal,
                    unitCostBase = line.unitCostBase
                )
            )
            db.stockDao().insertMovement(
                StockMovementEntity(
                    movementDate = request.returnDate,
                    warehouseId = invoice.warehouseId,
                    itemId = line.itemId,
                    movementType = "PURCHASE_RETURN",
                    quantityBase = -preparedLine.baseQuantity,
                    unitCostBase = line.unitCostBase,
                    referenceType = "PURCHASE_RETURN",
                    referenceId = returnId,
                    lotNo = line.lotNo,
                    expiryDate = line.expiryDate
                )
            )
        }

        postReturnJournal(
            returnId = returnId,
            returnNo = returnNo,
            totalBase = totalBase,
            settlementType = request.settlementType,
            invoice = invoice,
            createdBy = request.createdBy,
            returnDate = request.returnDate,
            refundTreasuryGlAccountId = refundTreasury?.accountId
        )
        returnId
    }

    suspend fun postFullLineReturn(
        purchaseLineId: Long,
        quantity: Double,
        settlementType: String,
        reason: String,
        createdBy: Long
    ): Long {
        val line = requireNotNull(db.purchaseDao().lineById(purchaseLineId)) { "سطر الشراء غير موجود" }
        return postPurchaseReturn(
            PostPurchaseReturnRequest(
                purchaseInvoiceId = line.invoiceId,
                settlementType = settlementType,
                reason = reason,
                createdBy = createdBy,
                lines = listOf(PurchaseReturnDraftLine(purchaseLineId, quantity))
            )
        )
    }



    data class SupplierPaymentResult(
        val paymentId: Long,
        val paymentNo: String,
        val allocatedBase: Double,
        val cashBase: Double,
        val fxDifferenceBase: Double
    )

    data class SupplierPaymentAllocationRequest(
        val invoiceId: Long,
        val amountOriginal: Double
    )

    data class MultiSupplierPaymentResult(
        val paymentId: Long,
        val paymentNo: String,
        val allocationCount: Int,
        val totalOriginal: Double,
        val allocatedBase: Double,
        val cashBase: Double,
        val fxDifferenceBase: Double
    )

    data class SupplierPaymentReversalResult(
        val reversalPaymentId: Long,
        val reversalPaymentNo: String,
        val reversalJournalEntryId: Long,
        val restoredPayableBase: Double,
        val restoredCashBase: Double
    )

    suspend fun postSupplierPayment(
        supplierId: Long,
        invoiceId: Long,
        treasuryAccountId: Long,
        amountOriginal: Double,
        paymentExchangeRate: Double,
        notes: String,
        createdBy: Long,
        paymentDate: Long = System.currentTimeMillis()
    ): SupplierPaymentResult {
        val invoice = requireNotNull(db.purchaseDao().invoiceById(invoiceId)) { "فاتورة الشراء غير موجودة" }
        val result = postSupplierPaymentAllocations(
            supplierId = supplierId,
            treasuryAccountId = treasuryAccountId,
            allocations = listOf(SupplierPaymentAllocationRequest(invoiceId, amountOriginal)),
            currencyCode = invoice.currencyCode,
            paymentExchangeRate = paymentExchangeRate,
            notes = notes,
            createdBy = createdBy,
            paymentDate = paymentDate
        )
        return SupplierPaymentResult(result.paymentId, result.paymentNo, result.allocatedBase, result.cashBase, result.fxDifferenceBase)
    }

    suspend fun postSupplierPaymentAutoAllocate(
        supplierId: Long,
        treasuryAccountId: Long,
        amountOriginal: Double,
        currencyCode: String,
        paymentExchangeRate: Double,
        notes: String,
        createdBy: Long,
        paymentDate: Long = System.currentTimeMillis()
    ): MultiSupplierPaymentResult = db.withTransaction {
        val open = db.purchaseDao().openSupplierInvoices(supplierId).filter { it.currencyCode == currencyCode }
        val plan = SettlementAllocationMath.allocateOldest(
            amountOriginal,
            open.map { SettlementAllocationMath.InvoiceBalance(it.invoiceId, it.outstandingBase, it.invoiceExchangeRate) }
        )
        postSupplierPaymentAllocationsInternal(
            supplierId = supplierId,
            treasuryAccountId = treasuryAccountId,
            allocations = plan.allocations.map { SupplierPaymentAllocationRequest(it.invoiceId, it.amountOriginal) },
            currencyCode = currencyCode,
            paymentExchangeRate = paymentExchangeRate,
            notes = notes,
            createdBy = createdBy,
            paymentDate = paymentDate
        )
    }

    suspend fun postSupplierPaymentAllocations(
        supplierId: Long,
        treasuryAccountId: Long,
        allocations: List<SupplierPaymentAllocationRequest>,
        currencyCode: String,
        paymentExchangeRate: Double,
        notes: String,
        createdBy: Long,
        paymentDate: Long = System.currentTimeMillis()
    ): MultiSupplierPaymentResult = db.withTransaction {
        postSupplierPaymentAllocationsInternal(
            supplierId, treasuryAccountId, allocations, currencyCode, paymentExchangeRate, notes, createdBy, paymentDate
        )
    }

    private suspend fun postSupplierPaymentAllocationsInternal(
        supplierId: Long,
        treasuryAccountId: Long,
        allocations: List<SupplierPaymentAllocationRequest>,
        currencyCode: String,
        paymentExchangeRate: Double,
        notes: String,
        createdBy: Long,
        paymentDate: Long
    ): MultiSupplierPaymentResult {
        db.requireUserPermission(createdBy, SecurityPermissions.SUPPLIER_PAYMENT_POST)
        AccountingService(db).requirePostingPeriodOpen(paymentDate)
        require(allocations.isNotEmpty()) { "يجب تحديد فاتورة واحدة على الأقل للدفع" }
        require(allocations.map { it.invoiceId }.distinct().size == allocations.size) { "لا يجوز تكرار الفاتورة في نفس الدفعة" }

        PurchaseMath.validateExchangeRate(paymentExchangeRate)
        val validatedSupplierId = SupplierMovementIdentity.requireId(supplierId)
        val supplier = requireNotNull(db.supplierDao().byId(validatedSupplierId)) { "المورد غير موجود" }
        val treasury = requireNotNull(db.accountingDao().treasuryById(treasuryAccountId)) { "الخزينة/البنك غير موجود" }
        require(treasury.isActive) { "الخزينة/البنك غير نشط" }
        require(treasury.currencyCode == currencyCode) { "عملة الخزينة يجب أن تطابق عملة الدفعة" }

        data class Prepared(val invoice: PurchaseInvoiceEntity, val request: SupplierPaymentAllocationRequest, val split: SupplierApMath.PaymentSplit)
        val prepared = allocations.map { allocation ->
            require(allocation.amountOriginal.isFinite() && allocation.amountOriginal > 0.0) { "مبلغ تخصيص الفاتورة يجب أن يكون أكبر من صفر" }
            val invoice = requireNotNull(db.purchaseDao().invoiceById(allocation.invoiceId)) { "فاتورة الشراء غير موجودة" }
            require(invoice.supplierId == supplier.id) { "إحدى الفواتير لا تخص المورد المحدد" }
            require(invoice.status == "POSTED" && invoice.paymentType == "CREDIT") { "الدفع متاح للفواتير الآجلة المرحلة فقط" }
            require(invoice.currencyCode == currencyCode) { "جميع الفواتير في الدفعة الواحدة يجب أن تكون بنفس عملة الدفع" }
            val returnedCreditBase = db.purchaseDao().returnsForInvoice(invoice.id)
                .filter { it.status == "POSTED" && it.settlementType == "SUPPLIER_CREDIT" }
                .sumOf { it.totalBase }
            val alreadyPaidBase = db.purchaseDao().paidBaseForInvoice(invoice.id)
            val outstandingBase = SupplierApMath.outstandingBase(invoice.totalBase, returnedCreditBase, alreadyPaidBase)
            require(outstandingBase > 1e-8) { "لا يوجد رصيد مستحق على الفاتورة ${invoice.invoiceNo}" }
            val split = SupplierApMath.paymentSplit(allocation.amountOriginal, invoice.exchangeRate, paymentExchangeRate)
            require(split.allocatedBase <= outstandingBase + 1e-8) { "الدفعة تتجاوز الرصيد المتبقي على الفاتورة ${invoice.invoiceNo}" }
            Prepared(invoice, allocation, split)
        }

        val totalOriginal = prepared.sumOf { it.request.amountOriginal }
        val totalAllocatedBase = prepared.sumOf { it.split.allocatedBase }
        val totalCashBase = prepared.sumOf { it.split.cashBase }
        val paymentNo = numbering.nextDocumentNo("SPAY", paymentDate)
        val paymentId = db.purchaseDao().insertSupplierPayment(
            SupplierPaymentEntity(
                paymentNo = paymentNo,
                supplierId = supplier.id,
                treasuryAccountId = treasury.id,
                paymentDate = paymentDate,
                currencyCode = currencyCode,
                exchangeRate = paymentExchangeRate,
                amountOriginal = totalOriginal,
                cashAmountBase = totalCashBase,
                notes = notes.trim(),
                createdBy = createdBy
            )
        )
        prepared.forEach { row ->
            db.purchaseDao().insertSupplierPaymentAllocation(
                SupplierPaymentAllocationEntity(
                    paymentId = paymentId,
                    invoiceId = row.invoice.id,
                    amountOriginal = row.request.amountOriginal,
                    allocatedBase = row.split.allocatedBase
                )
            )
        }
        postSupplierPaymentJournalMulti(
            paymentId = paymentId,
            paymentNo = paymentNo,
            invoiceNos = prepared.map { it.invoice.invoiceNo },
            currencyCode = currencyCode,
            treasuryAccountId = treasury.accountId,
            allocatedBase = totalAllocatedBase,
            cashBase = totalCashBase,
            paymentExchangeRate = paymentExchangeRate,
            paymentDate = paymentDate,
            createdBy = createdBy
        )
        return MultiSupplierPaymentResult(
            paymentId = paymentId,
            paymentNo = paymentNo,
            allocationCount = prepared.size,
            totalOriginal = totalOriginal,
            allocatedBase = totalAllocatedBase,
            cashBase = totalCashBase,
            fxDifferenceBase = totalCashBase - totalAllocatedBase
        )
    }

    suspend fun reverseSupplierPayment(
        paymentId: Long,
        reason: String,
        createdBy: Long,
        reversalDate: Long = System.currentTimeMillis()
    ): SupplierPaymentReversalResult = db.withTransaction {
        require(reason.trim().isNotBlank()) { "سبب عكس دفعة المورد مطلوب" }
        AccountingService(db).requirePostingPeriodOpen(reversalDate)
        val original = requireNotNull(db.purchaseDao().supplierPaymentById(paymentId)) { "دفعة المورد غير موجودة" }
        SupplierMovementIdentity.requireId(original.supplierId)
        require(original.reversalOfPaymentId == null) { "لا يمكن عكس مستند عكس" }
        require(reversalDate >= original.paymentDate) { "تاريخ العكس لا يمكن أن يسبق تاريخ الدفع" }
        require(db.purchaseDao().reversalForSupplierPayment(original.id) == null) { "تم عكس هذه الدفعة مسبقاً" }
        val allocations = db.purchaseDao().supplierPaymentAllocations(original.id)
        require(allocations.isNotEmpty()) { "دفعة المورد لا تحتوي تخصيصاً لفاتورة" }
        val originalJournal = requireNotNull(db.journalDao().bySource("SUPPLIER_PAYMENT", original.id.toString())) {
            "لا يوجد قيد محاسبي لدفعة المورد"
        }
        require(db.journalDao().reversalCount(originalJournal.id) == 0) { "تم عكس قيد هذه الدفعة مسبقاً" }

        allocations.forEach { allocation ->
            val invoice = requireNotNull(db.purchaseDao().invoiceById(allocation.invoiceId)) { "فاتورة دفعة المورد غير موجودة" }
            val priorCashRefundBase = db.purchaseDao().returnsForInvoice(invoice.id)
                .filter { it.settlementType == "CASH_REFUND" }
                .sumOf { it.totalBase }
            val netPaidBase = db.purchaseDao().paidBaseForInvoice(invoice.id)
            require(
                SupplierApMath.canReverseSupplierPayment(
                    netPaidBase = netPaidBase,
                    reversedAllocationBase = allocation.allocatedBase,
                    priorCashRefundBase = priorCashRefundBase
                )
            ) { "لا يمكن عكس دفعة المورد لأن جزءاً منها أصبح أساساً لاسترداد نقدي لمشتريات مرتجعة" }
        }

        val reversalNo = numbering.nextDocumentNo("SPRV", reversalDate)
        val reversalId = db.purchaseDao().insertSupplierPayment(
            SupplierPaymentEntity(
                paymentNo = reversalNo,
                supplierId = original.supplierId,
                treasuryAccountId = original.treasuryAccountId,
                paymentDate = reversalDate,
                currencyCode = original.currencyCode,
                exchangeRate = original.exchangeRate,
                amountOriginal = -original.amountOriginal,
                cashAmountBase = -original.cashAmountBase,
                notes = "عكس ${original.paymentNo}: ${reason.trim()}",
                reversalOfPaymentId = original.id,
                createdBy = createdBy
            )
        )
        allocations.forEach { allocation ->
            db.purchaseDao().insertSupplierPaymentAllocation(
                SupplierPaymentAllocationEntity(
                    paymentId = reversalId,
                    invoiceId = allocation.invoiceId,
                    amountOriginal = -allocation.amountOriginal,
                    allocatedBase = -allocation.allocatedBase
                )
            )
        }
        val reversalJournalId = reverseOperationalJournal(
            originalJournal = originalJournal,
            reason = reason,
            createdBy = createdBy,
            reversalDate = reversalDate
        )
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = createdBy,
                action = "REVERSE",
                entityType = "SUPPLIER_PAYMENT",
                entityId = original.id.toString(),
                oldValue = "${original.paymentNo}|${original.amountOriginal}|${original.cashAmountBase}",
                newValue = "$reversalNo|journal=$reversalJournalId",
                reason = reason.trim()
            )
        )
        SupplierPaymentReversalResult(
            reversalPaymentId = reversalId,
            reversalPaymentNo = reversalNo,
            reversalJournalEntryId = reversalJournalId,
            restoredPayableBase = allocations.sumOf { it.allocatedBase },
            restoredCashBase = original.cashAmountBase
        )
    }

    private suspend fun postSupplierPaymentJournalMulti(
        paymentId: Long,
        paymentNo: String,
        invoiceNos: List<String>,
        currencyCode: String,
        treasuryAccountId: Long,
        allocatedBase: Double,
        cashBase: Double,
        paymentExchangeRate: Double,
        paymentDate: Long,
        createdBy: Long
    ) {
        val payable = requireNotNull(db.accountDao().byCode("2100")) { "حساب الموردين 2100 غير موجود" }
        val lines = mutableListOf(
            DraftJournalLine(payable.id, allocatedBase, 0.0),
            DraftJournalLine(treasuryAccountId, 0.0, cashBase)
        )
        val diff = cashBase - allocatedBase
        if (diff > 1e-8) {
            val fxLoss = requireNotNull(db.accountDao().byCode("6750")) { "حساب خسائر فروق العملة 6750 غير موجود" }
            lines += DraftJournalLine(fxLoss.id, diff, 0.0)
        } else if (diff < -1e-8) {
            val fxGain = requireNotNull(db.accountDao().byCode("4250")) { "حساب أرباح فروق العملة 4250 غير موجود" }
            lines += DraftJournalLine(fxGain.id, 0.0, -diff)
        }
        AccountingValidator.validate(lines)
        val label = if (invoiceNos.size == 1) invoiceNos.first() else "${invoiceNos.size} فواتير: ${invoiceNos.take(3).joinToString(", ")}"
        val entryId = db.journalDao().insertEntry(
            JournalEntryEntity(
                entryNo = "JE-$paymentNo",
                entryDate = paymentDate,
                description = "دفعة مورد $paymentNo — $label",
                currencyCode = currencyCode,
                exchangeRate = paymentExchangeRate,
                sourceType = "SUPPLIER_PAYMENT",
                sourceId = paymentId.toString(),
                createdBy = createdBy
            )
        )
        db.journalDao().insertLines(lines.map { JournalLineEntity(entryId = entryId, accountId = it.accountId, debit = it.debit, credit = it.credit) })
    }

    private suspend fun postSupplierPaymentJournal(
        paymentId: Long,
        paymentNo: String,
        invoice: PurchaseInvoiceEntity,
        treasuryAccountId: Long,
        allocatedBase: Double,
        cashBase: Double,
        paymentExchangeRate: Double,
        paymentDate: Long,
        createdBy: Long
    ) {
        val payable = requireNotNull(db.accountDao().byCode("2100")) { "حساب الموردين 2100 غير موجود" }
        val lines = mutableListOf(
            DraftJournalLine(payable.id, allocatedBase, 0.0),
            DraftJournalLine(treasuryAccountId, 0.0, cashBase)
        )
        val diff = cashBase - allocatedBase
        if (diff > 0.000000001) {
            val fxLoss = requireNotNull(db.accountDao().byCode("6750")) { "حساب خسائر فروق العملة 6750 غير موجود" }
            lines.add(DraftJournalLine(fxLoss.id, diff, 0.0))
        } else if (diff < -0.000000001) {
            val fxGain = requireNotNull(db.accountDao().byCode("4250")) { "حساب أرباح فروق العملة 4250 غير موجود" }
            lines.add(DraftJournalLine(fxGain.id, 0.0, -diff))
        }
        AccountingValidator.validate(lines)
        val entryId = db.journalDao().insertEntry(
            JournalEntryEntity(
                entryNo = "JE-$paymentNo",
                entryDate = paymentDate,
                description = "دفعة مورد $paymentNo للفاتورة ${invoice.invoiceNo}",
                currencyCode = invoice.currencyCode,
                exchangeRate = paymentExchangeRate,
                sourceType = "SUPPLIER_PAYMENT",
                sourceId = paymentId.toString(),
                createdBy = createdBy
            )
        )
        db.journalDao().insertLines(
            lines.map { JournalLineEntity(entryId = entryId, accountId = it.accountId, debit = it.debit, credit = it.credit) }
        )
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

    private suspend fun postPurchaseJournal(
        invoiceId: Long,
        invoiceNo: String,
        totalBase: Double,
        request: PostPurchaseRequest,
        cashTreasuryGlAccountId: Long?
    ) {
        val inventory = requireNotNull(db.accountDao().byCode("1200")) { "حساب المخزون 1200 غير موجود" }
        val creditAccount = if (request.paymentType == "CASH") {
            requireNotNull(cashTreasuryGlAccountId?.let { db.accountDao().byId(it) }) { "خزينة الشراء النقدي غير محددة" }
        } else {
            requireNotNull(db.accountDao().byCode("2100")) { "حساب الموردين 2100 غير موجود" }
        }
        val draft = listOf(
            DraftJournalLine(inventory.id, totalBase, 0.0),
            DraftJournalLine(creditAccount.id, 0.0, totalBase)
        )
        AccountingValidator.validate(draft)
        val entryId = db.journalDao().insertEntry(
            JournalEntryEntity(
                entryNo = "JE-$invoiceNo",
                entryDate = request.invoiceDate,
                description = "فاتورة شراء $invoiceNo",
                currencyCode = request.currencyCode,
                exchangeRate = request.exchangeRate,
                sourceType = "PURCHASE",
                sourceId = invoiceId.toString(),
                createdBy = request.createdBy
            )
        )
        db.journalDao().insertLines(
            draft.map { JournalLineEntity(entryId = entryId, accountId = it.accountId, debit = it.debit, credit = it.credit) }
        )
    }

    private suspend fun postReturnJournal(
        returnId: Long,
        returnNo: String,
        totalBase: Double,
        settlementType: String,
        invoice: PurchaseInvoiceEntity,
        createdBy: Long,
        returnDate: Long,
        refundTreasuryGlAccountId: Long?
    ) {
        val inventory = requireNotNull(db.accountDao().byCode("1200")) { "حساب المخزون 1200 غير موجود" }
        val debitAccount = if (settlementType == "CASH_REFUND") {
            requireNotNull(refundTreasuryGlAccountId?.let { db.accountDao().byId(it) }) { "خزينة استلام رد المورد غير محددة" }
        } else {
            requireNotNull(db.accountDao().byCode("2100")) { "حساب الموردين 2100 غير موجود" }
        }
        val draft = listOf(
            DraftJournalLine(debitAccount.id, totalBase, 0.0),
            DraftJournalLine(inventory.id, 0.0, totalBase)
        )
        AccountingValidator.validate(draft)
        val entryId = db.journalDao().insertEntry(
            JournalEntryEntity(
                entryNo = "JE-$returnNo",
                entryDate = returnDate,
                description = "مرتجع مشتريات $returnNo",
                currencyCode = invoice.currencyCode,
                exchangeRate = invoice.exchangeRate,
                sourceType = "PURCHASE_RETURN",
                sourceId = returnId.toString(),
                createdBy = createdBy
            )
        )
        db.journalDao().insertLines(
            draft.map { JournalLineEntity(entryId = entryId, accountId = it.accountId, debit = it.debit, credit = it.credit) }
        )
    }

}
