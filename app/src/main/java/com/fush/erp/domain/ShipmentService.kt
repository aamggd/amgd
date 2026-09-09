package com.fush.erp.domain

import androidx.room.withTransaction
import com.fush.erp.cloud.CloudOperationResult
import com.fush.erp.cloud.CloudSyncRepository
import com.fush.erp.data.FushDatabase
import com.fush.erp.data.entity.*
import java.util.Locale

/**
 * Shipment traceability and actual-cost allocation.
 *
 * Accounting invariant: actual shipment expense is posted ONCE through the treasury expense voucher
 * to the single distribution/transport account (6430). Invoice allocations never post a second expense.
 * When an expense is explicitly marked CUSTOMER, the exact automatically allocated share is recovered
 * through the sales invoice by crediting 6430; COMPANY expenses remain analytical profitability cost.
 * AdditionalCharges stay independent for separately priced fees/markups that are not the actual shipment cost.
 */
class ShipmentService(private val db: FushDatabase, private val cloudSyncRepository: CloudSyncRepository? = null) {
    data class ShipmentItemDraft(val itemId: Long, val lotNo: String, val quantityBase: Double)
    data class CreateShipmentRequest(
        val shipmentDate: Long,
        val fromWarehouseId: Long,
        val destinationProvince: String,
        val transportReference: String = "",
        val notes: String = "",
        val items: List<ShipmentItemDraft>,
        val createdBy: Long,
        val destinationGovernorateId: String? = null,
        val destinationDistrictId: String? = null,
        val destinationAreaId: String? = null
    )
    data class PostExpenseRequest(
        val shipmentId: Long,
        val expenseType: String,
        val description: String,
        val expenseDate: Long,
        val amountOriginal: Double,
        val currencyCode: String,
        val exchangeRate: Double,
        val treasuryAccountId: Long,
        val paymentReference: String = "",
        /** COMPANY or CUSTOMER. CUSTOMER recovers the allocated actual cost through later invoices. */
        val bearer: String = "COMPANY",
        /** Stable client operation id so a retry cannot create a second disbursement. */
        val operationId: String,
        val createdBy: Long
    )
    data class DeleteShipmentResult(
        val shipmentNo: String,
        val cloudTombstonePublished: Boolean,
    )

    data class AllocationResult(
        val invoiceId: Long,
        val shipmentId: Long,
        val allocatedQuantityBase: Double,
        val allocatedExpenseBase: Double,
        val shipmentTotalExpenseBase: Double,
        val shipmentRemainingExpenseBase: Double,
        val shipmentClosed: Boolean
    )

    data class ShipmentLotAvailability(
        val lotNo: String,
        val availableQtyBase: Double
    )

    data class ShipmentLotAllocation(
        val lotNo: String,
        val quantityBase: Double
    )

    data class SaleLineShipmentTake(
        val shipmentId: Long,
        val shipmentItemId: Long,
        val shipmentNo: String,
        val destinationProvince: String,
        val lotNo: String,
        val quantityBase: Double
    )

    data class PlannedExpenseTake(
        val shipmentId: Long,
        val expenseId: Long,
        val amountBase: Double,
        val customerChargeBase: Double
    )

    data class AutomaticExpensePreview(
        val rows: List<PlannedExpenseTake>,
        val actualCostBase: Double,
        val customerChargeBase: Double
    )

    private data class CustodyTransferTake(
        val itemId: Long,
        val lotNo: String?,
        val expiryDate: Long?,
        val quantityBase: Double,
        val unitCostBase: Double,
    )

    private data class SaleInventoryLayer(
        val warehouseId: Long,
        val lotNo: String?,
        val expiryDate: Long?,
        val quantityBase: Double,
        val unitCostBase: Double,
        val movementType: String,
    )

    suspend fun availableShipmentsForSale(warehouseId: Long, itemId: Long, province: String, governorateId: String? = null): List<ShipmentSaleOptionRow> =
        db.shipmentDao().availableShipmentsForSale(warehouseId, itemId, province, governorateId)

    /**
     * Preferred shipment is consumed first; any shortage is filled from the next oldest eligible
     * shipments. Province match is a preference, never a reason to fabricate or over-allocate stock.
     */
    suspend fun planSaleLineShipments(
        warehouseId: Long,
        itemId: Long,
        province: String,
        governorateId: String? = null,
        requiredQtyBase: Double,
        preferredShipmentId: Long?,
        additionalReservedByShipmentItem: Map<Long, Double> = emptyMap()
    ): List<SaleLineShipmentTake> {
        require(requiredQtyBase.isFinite() && requiredQtyBase > EPS) { "كمية البيع للشحنة غير صالحة" }
        val candidates = availableShipmentsForSale(warehouseId, itemId, province, governorateId)
        require(candidates.isNotEmpty()) { "لا توجد شحنة متاحة تحتوي رصيداً من الصنف المحدد" }
        if (preferredShipmentId != null) {
            require(candidates.any { it.shipmentId == preferredShipmentId }) { "الشحنة المختارة لا تحتوي رصيداً متاحاً من الصنف" }
        }
        val ordered = buildList {
            preferredShipmentId?.let { id -> candidates.firstOrNull { it.shipmentId == id }?.let(::add) }
            candidates.filter { it.shipmentId != preferredShipmentId }.forEach(::add)
        }
        data class AvailableShipmentItem(
            val shipment: SalesShipmentEntity,
            val item: SalesShipmentItemEntity,
            val availableQtyBase: Double,
        )
        val availableRows = mutableListOf<AvailableShipmentItem>()
        for (candidate in ordered) {
            val shipment = requireNotNull(db.shipmentDao().shipmentById(candidate.shipmentId)) { "الشحنة غير موجودة" }
            db.shipmentDao().itemsForShipment(candidate.shipmentId)
                .filter { it.itemId == itemId }
                .sortedBy { it.id }
                .forEach { shipmentItem ->
                    val alreadyPlanned = additionalReservedByShipmentItem[shipmentItem.id] ?: 0.0
                    val available = (shipmentItem.quantityBase - db.shipmentDao().allocatedQtyBase(shipmentItem.id) - alreadyPlanned).coerceAtLeast(0.0)
                    if (available > EPS) availableRows += AvailableShipmentItem(shipment, shipmentItem, available)
                }
        }
        val quantities = ShipmentAllocationMath.splitQuantityAcrossAvailability(
            requiredQtyBase = requiredQtyBase,
            availabilityQtyBase = availableRows.map { it.availableQtyBase },
        )
        return availableRows.zip(quantities).mapNotNull { (row, qty) ->
            qty.takeIf { it > EPS }?.let {
                SaleLineShipmentTake(
                    shipmentId = row.shipment.id,
                    shipmentItemId = row.item.id,
                    shipmentNo = row.shipment.shipmentNo,
                    destinationProvince = row.shipment.destinationProvince,
                    lotNo = row.item.lotNo,
                    quantityBase = it,
                )
            }
        }
    }

    internal suspend fun saveSaleLineShipmentAllocationsInsideTransaction(
        invoiceId: Long,
        salesLineId: Long,
        takes: List<SaleLineShipmentTake>,
        userId: Long
    ) {
        val invoice = requireNotNull(db.salesDao().invoiceById(invoiceId)) { "فاتورة المبيعات غير موجودة" }
        val invoiceGovernorateId = requireNotNull(invoice.governorateId) {
            "الفاتورة ${invoice.invoiceNo} غير مربوطة بمعرف محافظة رسمي"
        }
        takes.forEach { take ->
            val shipment = requireNotNull(db.shipmentDao().shipmentById(take.shipmentId)) { "الشحنة غير موجودة" }
            val shipmentGovernorateId = requireNotNull(shipment.destinationGovernorateId) {
                "الشحنة ${shipment.shipmentNo} غير مربوطة بمعرف محافظة رسمي"
            }
            require(shipmentGovernorateId == invoiceGovernorateId) {
                "محافظة الفاتورة (${invoice.province}) لا تطابق وجهة الشحنة (${shipment.destinationProvince})"
            }
            require(db.shipmentDao().itemAllocationForSalesLine(take.shipmentItemId, salesLineId) == null) {
                "تم تخصيص سطر الفاتورة من سطر الشحنة نفسه مسبقاً"
            }
            val remaining = (requireNotNull(db.shipmentDao().itemById(take.shipmentItemId)).quantityBase -
                db.shipmentDao().allocatedQtyBase(take.shipmentItemId)).coerceAtLeast(0.0)
            require(take.quantityBase <= remaining + EPS) { "كمية الشحنة المتاحة تغيرت؛ أعد تحميل الفاتورة" }
            db.shipmentDao().insertItemAllocation(
                SalesShipmentInvoiceItemAllocationEntity(
                    shipmentItemId = take.shipmentItemId, invoiceId = invoiceId, salesLineId = salesLineId,
                    quantityBase = take.quantityBase, createdBy = userId
                )
            )
        }
    }

    /**
     * Predicts the exact automatic shipment-cost allocation before the invoice header is written.
     * This lets the customer-facing total include only costs explicitly marked CUSTOMER while the
     * actual cost itself remains linked to the original shipment expense. No second expense is posted.
     */
    internal suspend fun previewAutomaticInvoiceExpenses(
        shipmentPlans: List<List<SaleLineShipmentTake>>
    ): AutomaticExpensePreview {
        val qtyByShipment = linkedMapOf<Long, Double>()
        shipmentPlans.flatten().forEach { take ->
            qtyByShipment[take.shipmentId] = (qtyByShipment[take.shipmentId] ?: 0.0) + take.quantityBase
        }
        val rows = mutableListOf<PlannedExpenseTake>()
        for ((shipmentId, invoiceQty) in qtyByShipment) {
            val items = db.shipmentDao().itemsForShipment(shipmentId)
            val shipmentQty = items.sumOf { it.quantityBase }
            val currentRemaining = items.sumOf { (it.quantityBase - db.shipmentDao().allocatedQtyBase(it.id)).coerceAtLeast(0.0) }
            val remainingQtyAfter = (currentRemaining - invoiceQty).coerceAtLeast(0.0)
            for (expense in db.shipmentDao().expensesForShipment(shipmentId)) {
                val already = db.shipmentDao().allocatedExpenseBase(expense.id)
                val amount = ShipmentAllocationMath.automaticExpenseAllocation(
                    expense.amountBase, already, shipmentQty, invoiceQty, remainingQtyAfter
                )
                if (amount > EPS) {
                    rows += PlannedExpenseTake(
                        shipmentId = shipmentId, expenseId = expense.id, amountBase = amount,
                        customerChargeBase = if (expense.bearer == "CUSTOMER") amount else 0.0
                    )
                }
            }
        }
        return AutomaticExpensePreview(rows, rows.sumOf { it.amountBase }, rows.sumOf { it.customerChargeBase })
    }

    /** Allocates all actual shipment expenses for the invoice once, after all line links exist. */
    internal suspend fun allocateAutomaticInvoiceExpensesInsideTransaction(invoiceId: Long, userId: Long): AutomaticExpensePreview {
        val active = db.shipmentDao().itemAllocationsForInvoice(invoiceId)
        val shipmentQtyForInvoice = linkedMapOf<Long, Double>()
        active.forEach { alloc ->
            val shipmentItem = db.shipmentDao().itemById(alloc.shipmentItemId) ?: return@forEach
            shipmentQtyForInvoice[shipmentItem.shipmentId] = (shipmentQtyForInvoice[shipmentItem.shipmentId] ?: 0.0) + alloc.quantityBase
        }
        val rows = mutableListOf<PlannedExpenseTake>()
        for ((shipmentId, invoiceQty) in shipmentQtyForInvoice) {
            val shipment = db.shipmentDao().shipmentById(shipmentId) ?: continue
            val items = db.shipmentDao().itemsForShipment(shipmentId)
            val shipmentQty = items.sumOf { it.quantityBase }
            val remainingQtyAfter = items.sumOf { (it.quantityBase - db.shipmentDao().allocatedQtyBase(it.id)).coerceAtLeast(0.0) }
            for (expense in db.shipmentDao().expensesForShipment(shipmentId)) {
                if (db.shipmentDao().expenseAllocationLinkCount(expense.id, invoiceId) > 0) continue
                val already = db.shipmentDao().allocatedExpenseBase(expense.id)
                val amount = ShipmentAllocationMath.automaticExpenseAllocation(
                    expense.amountBase, already, shipmentQty, invoiceQty, remainingQtyAfter
                )
                if (amount > EPS) {
                    val customerCharge = if (expense.bearer == "CUSTOMER") amount else 0.0
                    db.shipmentDao().insertExpenseAllocation(
                        SalesShipmentExpenseInvoiceAllocationEntity(
                            shipmentExpenseId = expense.id, invoiceId = invoiceId, amountBase = amount,
                            customerChargeBase = customerCharge, allocationMethod = "QUANTITY_AUTO",
                            basisQuantityBase = invoiceQty, createdBy = userId
                        )
                    )
                    rows += PlannedExpenseTake(shipmentId, expense.id, amount, customerCharge)
                }
            }
            refreshShipmentStatus(shipment.id)
        }
        return AutomaticExpensePreview(rows, rows.sumOf { it.amountBase }, rows.sumOf { it.customerChargeBase })
    }

    /**
     * FIFO lot availability for shipment creation. Stock already committed to other shipments
     * (but not yet allocated/sold) is deducted so the same physical quantity cannot be selected
     * by two open shipments. [additionalReservedByLot] is used by the create dialog for lines
     * already drafted in the same new shipment.
     */
    suspend fun availableShipmentLotsFifo(
        warehouseId: Long,
        itemId: Long,
        shipmentDate: Long,
        additionalReservedByLot: Map<String, Double> = emptyMap()
    ): List<ShipmentLotAvailability> {
        val asOf = BusinessDatePolicy.endOfBusinessDay(shipmentDate)

        // Use the long-established historical lot balance query and derive FIFO order in Kotlin.
        // v180 used a dedicated aggregate ORDER BY query here; on some installed databases a
        // lookup failure was swallowed by the UI and incorrectly appeared as "no stock".
        // Keeping the balance source identical to the Inventory module prevents that false zero.
        val rawLots = db.stockDao().lotBalancesAt(warehouseId, itemId, asOf)
        val orderedLots = rawLots.map { row ->
            val firstMovementDate = db.stockDao().lotMovementTimeline(
                warehouseId = warehouseId,
                itemId = itemId,
                lotKey = InventoryMath.lotKey(row.lotNo),
                expiryKey = InventoryMath.expiryKey(row.expiryDate)
            ).firstOrNull()?.movementDate ?: Long.MAX_VALUE
            row to firstMovementDate
        }.sortedWith(
            compareBy<Pair<LotBalanceRow, Long>> { it.second }
                .thenBy { if (it.first.expiryDate == null) 1 else 0 }
                .thenBy { it.first.expiryDate ?: Long.MAX_VALUE }
                .thenBy { InventoryMath.lotKey(it.first.lotNo) }
        )

        // Shipment items store lotNo (not expiry) as the trace key. Keep FIFO order from the
        // first occurrence and merge any duplicate expiry rows for the same lot before reservations.
        val totalsByLot = linkedMapOf<String, Double>()
        orderedLots.forEach { (row, _) ->
            val key = InventoryMath.lotKey(row.lotNo)
            totalsByLot[key] = (totalsByLot[key] ?: 0.0) + row.quantityBase
        }

        val result = mutableListOf<ShipmentLotAvailability>()
        totalsByLot.forEach { (lotKey, physicalQty) ->
            val alreadyCommitted = db.shipmentDao().reservedShipmentQtyBaseAt(warehouseId, itemId, lotKey, asOf)
            val draftReserved = additionalReservedByLot[lotKey] ?: 0.0
            val available = (physicalQty - alreadyCommitted - draftReserved).coerceAtLeast(0.0)
            if (available > EPS) result += ShipmentLotAvailability(lotKey, available)
        }
        return result
    }

    private suspend fun requireRequestUsesOldestAvailableLots(request: CreateShipmentRequest) {
        request.items.groupBy { it.itemId }.forEach { (itemId, rows) ->
            val requestedQty = rows.sumOf { it.quantityBase }
            val availableLots = availableShipmentLotsFifo(request.fromWarehouseId, itemId, request.shipmentDate)
            val expected = allocateOldestAvailableLots(availableLots, requestedQty)
                .groupBy { it.lotNo }
                .mapValues { (_, values) -> values.sumOf { it.quantityBase } }
            val actual = rows.groupBy { it.lotNo.trim() }
                .mapValues { (_, values) -> values.sumOf { it.quantityBase } }
                .filterValues { it > EPS }

            require(expected.keys == actual.keys && expected.all { (lot, qty) -> kotlin.math.abs((actual[lot] ?: 0.0) - qty) <= EPS }) {
                "يجب استخدام أقدم تشغيلة متاحة تلقائياً للشحنة"
            }
        }
    }

    private suspend fun shipmentCustodyWarehouse(): WarehouseEntity {
        db.warehouseDao().byCode(SHIPMENT_CUSTODY_WAREHOUSE_CODE)?.let { return it }
        val id = db.warehouseDao().insert(
            WarehouseEntity(
                code = SHIPMENT_CUSTODY_WAREHOUSE_CODE,
                nameAr = "عهدة الشحنات (نظام)",
                nameEn = "Shipment Custody (System)",
                location = "SYSTEM",
                isActive = false,
            )
        )
        return requireNotNull(db.warehouseDao().byId(id)) { "تعذر إنشاء عهدة الشحنات النظامية" }
    }

    /**
     * Resolves the exact physical lot/expiry/cost layers moved out of the source warehouse.
     * Legacy (pre-v207) open shipments are treated as reservations and skipped first so their
     * stock cannot be stolen by a newly-created custody shipment.
     */
    private suspend fun prepareCustodyTransferTakes(request: CreateShipmentRequest): List<CustodyTransferTake> {
        val asOf = BusinessDatePolicy.endOfBusinessDay(request.shipmentDate)
        val out = mutableListOf<CustodyTransferTake>()
        request.items.groupBy { it.itemId }.forEach { (itemId, itemRows) ->
            val requestedByLot = linkedMapOf<String, Double>()
            itemRows.forEach { row ->
                val key = InventoryMath.lotKey(row.lotNo)
                requestedByLot[key] = (requestedByLot[key] ?: 0.0) + row.quantityBase
            }
            val orderedRows = db.stockDao().lotBalancesAt(request.fromWarehouseId, itemId, asOf)
                .map { row ->
                    val firstDate = db.stockDao().lotMovementTimeline(
                        warehouseId = request.fromWarehouseId,
                        itemId = itemId,
                        lotKey = InventoryMath.lotKey(row.lotNo),
                        expiryKey = InventoryMath.expiryKey(row.expiryDate),
                    ).firstOrNull()?.movementDate ?: Long.MAX_VALUE
                    row to firstDate
                }
                .sortedWith(
                    compareBy<Pair<LotBalanceRow, Long>> { it.second }
                        .thenBy { if (it.first.expiryDate == null) 1 else 0 }
                        .thenBy { it.first.expiryDate ?: Long.MAX_VALUE }
                        .thenBy { InventoryMath.lotKey(it.first.lotNo) }
                )

            requestedByLot.forEach { (lotKey, requestedQty) ->
                val layers = orderedRows.filter { InventoryMath.lotKey(it.first.lotNo) == lotKey }
                var legacyReserved = db.shipmentDao().reservedShipmentQtyBaseAt(
                    request.fromWarehouseId, itemId, lotKey, asOf
                ).coerceAtLeast(0.0)
                var remaining = requestedQty
                layers.forEach { (row, _) ->
                    if (remaining <= EPS) return@forEach
                    val skip = minOf(legacyReserved, row.quantityBase.coerceAtLeast(0.0))
                    legacyReserved = (legacyReserved - skip).coerceAtLeast(0.0)
                    val free = (row.quantityBase - skip).coerceAtLeast(0.0)
                    if (free <= EPS) return@forEach
                    val take = minOf(remaining, free)
                    val unitCost = if (row.quantityBase > EPS) row.inventoryValueBase / row.quantityBase else 0.0
                    require(unitCost.isFinite() && unitCost >= 0.0) { "تعذر تحديد تكلفة مخزون الشحنة" }
                    out += CustodyTransferTake(
                        itemId = itemId,
                        lotNo = row.lotNo,
                        expiryDate = row.expiryDate,
                        quantityBase = take,
                        unitCostBase = unitCost,
                    )
                    remaining -= take
                }
                require(remaining <= EPS) { "المخزون الفعلي المتاح للشحنة تغير؛ حدّث الشحنة وحاول مرة أخرى" }
            }
        }
        return out
    }

    private suspend fun custodyLayersForShipmentTake(
        take: SaleLineShipmentTake,
        itemId: Long,
    ): List<SaleInventoryLayer> {
        val custody = shipmentCustodyWarehouse()
        val shipmentItem = requireNotNull(db.shipmentDao().itemById(take.shipmentItemId)) { "سطر الشحنة غير موجود" }
        require(shipmentItem.itemId == itemId) { "صنف الشحنة لا يطابق سطر البيع" }
        val inbound = db.shipmentDao().shipmentCustodyInboundMovements(
            shipmentId = take.shipmentId,
            custodyWarehouseId = custody.id,
            itemId = itemId,
            lotKey = InventoryMath.lotKey(shipmentItem.lotNo),
        )
        require(inbound.isNotEmpty()) { "حركة عهدة الشحنة غير موجودة" }

        // Active shipment allocations represent quantity already consumed from these deterministic
        // inbound layers. Reversed links become available again by design.
        var skip = db.shipmentDao().allocatedQtyBase(shipmentItem.id).coerceAtLeast(0.0)
        var remaining = take.quantityBase
        val out = mutableListOf<SaleInventoryLayer>()
        inbound.forEach { movement ->
            if (remaining <= EPS) return@forEach
            val layerQty = movement.quantityBase.coerceAtLeast(0.0)
            val skipped = minOf(skip, layerQty)
            skip = (skip - skipped).coerceAtLeast(0.0)
            val available = (layerQty - skipped).coerceAtLeast(0.0)
            if (available <= EPS) return@forEach
            val qty = minOf(remaining, available)
            out += SaleInventoryLayer(
                warehouseId = custody.id,
                lotNo = movement.lotNo,
                expiryDate = movement.expiryDate,
                quantityBase = qty,
                unitCostBase = movement.unitCostBase,
                movementType = "SHIPMENT_SALE_OUT",
            )
            remaining -= qty
        }
        require(remaining <= EPS) { "رصيد عهدة الشحنة لم يعد كافياً؛ أعد المزامنة ثم حاول مرة أخرى" }
        return out
    }

    private suspend fun legacyWarehouseLayersForShipmentTake(
        take: SaleLineShipmentTake,
        itemId: Long,
        movementDate: Long,
    ): List<SaleInventoryLayer> {
        val shipment = requireNotNull(db.shipmentDao().shipmentById(take.shipmentId)) { "الشحنة غير موجودة" }
        val advanced = AdvancedInventoryService(db)
        val asOf = BusinessDatePolicy.endOfBusinessDay(movementDate)
        val lotKey = InventoryMath.lotKey(take.lotNo)
        val lots = advanced.usableLots(shipment.fromWarehouseId, itemId, asOf)
            .filter { InventoryMath.lotKey(it.lotNo) == lotKey }
        val capacities = lots.mapIndexed { index, lot ->
            val safe = advanced.historicalSafeLotOutflowQty(
                warehouseId = shipment.fromWarehouseId,
                itemId = itemId,
                lotNo = lot.lotNo,
                expiryDate = lot.expiryDate,
                movementDate = movementDate,
            )
            val unitCost = if (lot.quantityBase > EPS) lot.inventoryValueBase / lot.quantityBase else 0.0
            HistoricalLotCapacity(
                key = "$index|${InventoryMath.lotKey(lot.lotNo)}|${InventoryMath.expiryKey(lot.expiryDate)}",
                lotNo = lot.lotNo,
                quantityBase = lot.quantityBase,
                historicalSafeQtyBase = safe,
                unitCostBase = unitCost,
            )
        }
        val plan = SalesHistoricalAllocationMath.plan(take.quantityBase, capacities)
        require(plan.isComplete) { "المخزون التاريخي للشحنة القديمة لم يعد كافياً؛ راجع رصيد التشغيلة" }
        return plan.takes.map { planned ->
            val capacity = capacities.first { it.key == planned.key }
            val lotIndex = planned.key.substringBefore('|').toIntOrNull()
                ?: error("تعذر تحديد طبقة مخزون الشحنة القديمة")
            val lot = lots.getOrNull(lotIndex) ?: error("طبقة مخزون الشحنة القديمة غير موجودة")
            SaleInventoryLayer(
                warehouseId = shipment.fromWarehouseId,
                lotNo = lot.lotNo,
                expiryDate = lot.expiryDate,
                quantityBase = planned.quantityBase,
                unitCostBase = capacity.unitCostBase,
                movementType = "SALE",
            )
        }
    }

    /**
     * v207: a shipment-created after this release owns real inventory in the system custody warehouse.
     * Legacy shipments remain supported from the original warehouse. The line can therefore span
     * old and new shipments without double-deducting stock.
     */
    internal suspend fun allocateShipmentPlanStockForSaleInsideTransaction(
        salesLineId: Long,
        itemId: Long,
        soldBaseQty: Double,
        freeBaseQty: Double,
        movementDate: Long,
        takes: List<SaleLineShipmentTake>,
    ): Double {
        val required = soldBaseQty + freeBaseQty
        require(kotlin.math.abs(takes.sumOf { it.quantityBase } - required) <= 0.000001) {
            "كمية خطة الشحنة لا تطابق الكمية الفعلية في سطر البيع"
        }
        var soldRemaining = soldBaseQty
        var totalCost = 0.0
        takes.forEach { take ->
            val isCustody = db.shipmentDao().shipmentCustodyTransferCount(take.shipmentId) > 0
            val layers = if (isCustody) {
                custodyLayersForShipmentTake(take, itemId)
            } else {
                legacyWarehouseLayersForShipmentTake(take, itemId, movementDate)
            }
            layers.forEach { layer ->
                val paidTake = minOf(soldRemaining, layer.quantityBase)
                val freeTake = (layer.quantityBase - paidTake).coerceAtLeast(0.0)
                soldRemaining = (soldRemaining - paidTake).coerceAtLeast(0.0)
                val cost = layer.quantityBase * layer.unitCostBase
                db.salesDao().insertAllocation(
                    SalesAllocationEntity(
                        salesLineId = salesLineId,
                        itemId = itemId,
                        lotNo = layer.lotNo,
                        expiryDate = layer.expiryDate,
                        quantityBase = layer.quantityBase,
                        freeQuantityBase = freeTake,
                        unitCostBase = layer.unitCostBase,
                        costBase = cost,
                    )
                )
                db.stockDao().insertMovement(
                    StockMovementEntity(
                        movementDate = movementDate,
                        warehouseId = layer.warehouseId,
                        itemId = itemId,
                        movementType = layer.movementType,
                        quantityBase = -layer.quantityBase,
                        unitCostBase = layer.unitCostBase,
                        referenceType = "SALES_LINE",
                        referenceId = salesLineId,
                        lotNo = layer.lotNo,
                        expiryDate = layer.expiryDate,
                    )
                )
                totalCost += cost
            }
        }
        return totalCost
    }

    companion object {
        private const val EPS = 0.000000001
        private const val SHIPMENT_CUSTODY_WAREHOUSE_CODE = "SHIP-CUSTODY"

        fun allocateOldestAvailableLots(
            lots: List<ShipmentLotAvailability>,
            requestedQtyBase: Double
        ): List<ShipmentLotAllocation> {
            require(requestedQtyBase.isFinite() && requestedQtyBase > 0.0) { "الكمية يجب أن تكون أكبر من صفر" }
            var remaining = requestedQtyBase
            val out = mutableListOf<ShipmentLotAllocation>()
            lots.forEach { lot ->
                if (remaining <= EPS) return@forEach
                if (!lot.availableQtyBase.isFinite() || lot.availableQtyBase <= EPS) return@forEach
                val take = minOf(remaining, lot.availableQtyBase)
                out += ShipmentLotAllocation(lot.lotNo, take)
                remaining -= take
            }
            require(remaining <= EPS) { "المخزون المتاح غير كافٍ للكمية المطلوبة" }
            return out
        }
    }

    private suspend fun reserveNextShipmentNo(createdBy: Long, shipmentDate: Long): String {
        val numbering = AutoNumberService(db)
        repeat(100) {
            val candidate = numbering.nextShipmentNo(shipmentDate)
            // Local reconciliation already skips persisted rows. The cloud reservation closes the
            // connected multi-device race where two phones allocate the next SHP number together.
            val guard = cloudSyncRepository ?: return candidate
            when (val reservation = guard.reserve(createdBy, "SALE_SHIPMENT", candidate)) {
                is DocumentNumberReservationResult.Reserved, DocumentNumberReservationResult.LocalOnly -> return candidate
                is DocumentNumberReservationResult.InUse -> Unit // advance and try the next suffix
                is DocumentNumberReservationResult.Failure -> error("تعذر حجز رقم الشحنة سحابياً: ${reservation.message}")
            }
        }
        error("تعذر تخصيص رقم شحنة فريد بعد عدة محاولات")
    }

    suspend fun createShipment(request: CreateShipmentRequest): Long = db.withTransaction {
        db.requireUserPermission(request.createdBy, SecurityPermissions.SALES_POST)
        FutureDocumentDatePolicy.requireNotFuture(request.shipmentDate, "تاريخ الشحنة")
        val location = GeographyService(db).resolveLocation(
            request.destinationGovernorateId, request.destinationDistrictId, request.destinationAreaId, request.destinationProvince
        )
        require(request.items.isNotEmpty()) { "أضف صنفاً واحداً على الأقل للشحنة" }
        requireNotNull(db.warehouseDao().byId(request.fromWarehouseId)) { "مخزن الشحنة غير موجود" }
        request.items.forEach {
            require(it.quantityBase.isFinite() && it.quantityBase > 0.0) { "كمية الشحنة يجب أن تكون أكبر من صفر" }
            requireNotNull(db.itemDao().byId(it.itemId)) { "أحد أصناف الشحنة غير موجود" }
        }
        requireRequestUsesOldestAvailableLots(request)
        val custodyTakes = prepareCustodyTransferTakes(request)
        val custodyWarehouse = shipmentCustodyWarehouse()
        require(custodyWarehouse.id != request.fromWarehouseId) { "مخزن المصدر لا يمكن أن يكون عهدة الشحنات النظامية" }
        val shipmentNo = reserveNextShipmentNo(request.createdBy, request.shipmentDate)
        val id = db.shipmentDao().insertShipment(
            SalesShipmentEntity(
                shipmentNo = shipmentNo,
                shipmentDate = request.shipmentDate,
                fromWarehouseId = request.fromWarehouseId,
                destinationProvince = location.governorate.nameAr,
                destinationGovernorateId = location.governorate.id,
                destinationDistrictId = location.district?.id,
                destinationAreaId = location.area?.id,
                status = "IN_TRANSIT",
                transportReference = request.transportReference.trim(),
                notes = request.notes.trim(),
                createdBy = request.createdBy
            )
        )
        custodyTakes.forEach { take ->
            db.stockDao().insertMovement(
                StockMovementEntity(
                    movementDate = request.shipmentDate,
                    warehouseId = request.fromWarehouseId,
                    itemId = take.itemId,
                    movementType = "SHIPMENT_TRANSFER_OUT",
                    quantityBase = -take.quantityBase,
                    unitCostBase = take.unitCostBase,
                    referenceType = "SALES_SHIPMENT_CUSTODY",
                    referenceId = id,
                    lotNo = take.lotNo,
                    expiryDate = take.expiryDate,
                )
            )
            db.stockDao().insertMovement(
                StockMovementEntity(
                    movementDate = request.shipmentDate,
                    warehouseId = custodyWarehouse.id,
                    itemId = take.itemId,
                    movementType = "SHIPMENT_TRANSFER_IN",
                    quantityBase = take.quantityBase,
                    unitCostBase = take.unitCostBase,
                    referenceType = "SALES_SHIPMENT_CUSTODY",
                    referenceId = id,
                    lotNo = take.lotNo,
                    expiryDate = take.expiryDate,
                )
            )
        }
        request.items.groupBy { it.itemId to it.lotNo.trim() }.forEach { (key, rows) ->
            db.shipmentDao().insertItem(
                SalesShipmentItemEntity(
                    shipmentId = id,
                    itemId = key.first,
                    lotNo = key.second,
                    quantityBase = rows.sumOf { it.quantityBase },
                    createdBy = request.createdBy
                )
            )
        }
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = request.createdBy, action = "POST", entityType = "SALES_SHIPMENT", entityId = id.toString(),
                newValue = "$shipmentNo|${location.governorate.id}|${location.governorate.nameAr}|items=${request.items.size}|custodyQty=${custodyTakes.sumOf { it.quantityBase }}", reason = "إنشاء شحنة مبيعات ونقل البضاعة إلى عهدة الشحنة"
            )
        )
        id
    }

    private suspend fun requireUnusedShipmentForDelete(shipmentId: Long): SalesShipmentEntity {
        val dao = db.shipmentDao()
        val shipment = requireNotNull(dao.shipmentById(shipmentId)) { "الشحنة غير موجودة" }
        val expenses = dao.shipmentExpenseCountAll(shipmentId)
        val itemAllocations = dao.shipmentItemAllocationCountAll(shipmentId)
        val expenseAllocations = dao.shipmentExpenseAllocationCountAll(shipmentId)
        require(expenses == 0) {
            "لا يمكن حذف ${shipment.shipmentNo}: لديها $expenses مصروف/سند شحنة. احذف/عالج المصاريف أولاً."
        }
        require(itemAllocations == 0) {
            "لا يمكن حذف ${shipment.shipmentNo}: مرتبطة بفواتير مبيعات ($itemAllocations تخصيص)."
        }
        require(expenseAllocations == 0) {
            "لا يمكن حذف ${shipment.shipmentNo}: لديها تخصيصات تكلفة على فواتير ($expenseAllocations)."
        }
        return shipment
    }

    /**
     * v201 hard delete is intentionally limited to ADMIN and to a shipment that was never used by
     * an invoice or shipment expense.  Cloud tombstones are published first so bidirectional sync
     * cannot resurrect the deleted shipment on another device.
     */
    suspend fun deleteUnusedShipment(shipmentId: Long, userId: Long): DeleteShipmentResult {
        db.requireUserPermission(userId, SecurityPermissions.SALES_POST)
        val actor = requireNotNull(db.userDao().byId(userId)) { "المستخدم غير موجود" }
        require(actor.role == "ADMIN") { "حذف الشحنات متاح للـ ADMIN فقط" }
        val before = requireUnusedShipmentForDelete(shipmentId)
        val reason = "حذف شحنة غير مستخدمة ${before.shipmentNo}"
        val cloudPublished = cloudSyncRepository?.let { repository ->
            when (val cloud = repository.publishUnusedShipmentDeletionTombstones(userId, shipmentId, reason)) {
                is CloudOperationResult.Success -> cloud.value
                is CloudOperationResult.Failure -> error(cloud.message)
            }
        } ?: false

        return db.withTransaction {
            val row = requireUnusedShipmentForDelete(shipmentId)
            val custodyWarehouse = shipmentCustodyWarehouse()
            val custodyInbound = db.shipmentDao().allShipmentCustodyInboundMovements(shipmentId)
            val returnedAt = TrustedTimeService.now()
            custodyInbound.forEach { movement ->
                db.stockDao().insertMovement(
                    StockMovementEntity(
                        movementDate = returnedAt,
                        warehouseId = custodyWarehouse.id,
                        itemId = movement.itemId,
                        movementType = "SHIPMENT_DELETE_RETURN_OUT",
                        quantityBase = -movement.quantityBase,
                        unitCostBase = movement.unitCostBase,
                        referenceType = "SALES_SHIPMENT_CUSTODY_RETURN",
                        referenceId = shipmentId,
                        lotNo = movement.lotNo,
                        expiryDate = movement.expiryDate,
                    )
                )
                db.stockDao().insertMovement(
                    StockMovementEntity(
                        movementDate = returnedAt,
                        warehouseId = row.fromWarehouseId,
                        itemId = movement.itemId,
                        movementType = "SHIPMENT_DELETE_RETURN_IN",
                        quantityBase = movement.quantityBase,
                        unitCostBase = movement.unitCostBase,
                        referenceType = "SALES_SHIPMENT_CUSTODY_RETURN",
                        referenceId = shipmentId,
                        lotNo = movement.lotNo,
                        expiryDate = movement.expiryDate,
                    )
                )
            }
            db.governanceDao().insertAudit(
                AuditEventEntity(
                    userId = userId,
                    action = "DELETE_UNUSED_SHIPMENT",
                    entityType = "SALES_SHIPMENT",
                    entityId = row.shipmentNo,
                    oldValue = "id=${row.id};date=${row.shipmentDate};warehouse=${row.fromWarehouseId};province=${row.destinationProvince};status=${row.status}",
                    newValue = "DELETED;cloudTombstone=$cloudPublished;returnedCustodyQty=${custodyInbound.sumOf { it.quantityBase }}",
                    reason = reason,
                    deviceInfo = "ANDROID_V201",
                )
            )
            require(db.shipmentDao().deleteShipmentById(shipmentId) == 1) { "تعذر حذف الشحنة" }
            DeleteShipmentResult(row.shipmentNo, cloudPublished)
        }
    }

    suspend fun markDelivered(shipmentId: Long, userId: Long) = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.SALES_POST)
        val row = requireNotNull(db.shipmentDao().shipmentById(shipmentId)) { "الشحنة غير موجودة" }
        require(row.status !in setOf("CANCELLED", "CLOSED")) { "الشحنة مغلقة أو ملغاة" }
        db.shipmentDao().updateShipment(row.copy(status = "DELIVERED"))
    }

    suspend fun postActualExpense(request: PostExpenseRequest): Long {
        db.requireUserPermission(request.createdBy, SecurityPermissions.TREASURY_POST)
        val shipment = requireNotNull(db.shipmentDao().shipmentById(request.shipmentId)) { "الشحنة غير موجودة" }
        require(shipment.status != "CANCELLED") { "لا يمكن إضافة مصروف لشحنة ملغاة" }
        require(request.expenseType.uppercase(Locale.US) in setOf("TRANSPORT", "LOADING", "CUSTOMS", "OTHER")) { "نوع مصروف الشحنة غير صالح" }
        require(request.amountOriginal.isFinite() && request.amountOriginal > 0.0) { "مبلغ المصروف يجب أن يكون أكبر من صفر" }
        require(request.exchangeRate.isFinite() && request.exchangeRate > 0.0) { "سعر الصرف غير صالح" }
        require(request.bearer in setOf("COMPANY", "CUSTOMER")) { "الطرف المتحمل لمصروف الشحنة غير صالح" }
        val expenseAccount = requireNotNull(db.accountDao().byCode("6430")) { "حساب مصاريف النقل والتوزيع 6430 غير موجود" }
        require(expenseAccount.isPosting && expenseAccount.isActive && expenseAccount.type == "EXPENSE") { "حساب 6430 غير صالح للترحيل" }
        val treasury = requireNotNull(db.accountingDao().treasuryById(request.treasuryAccountId)) { "حساب الدفع غير موجود" }
        require(treasury.isActive) { "حساب الدفع غير نشط" }
        require(treasury.currencyCode == request.currencyCode) { "عملة المصروف يجب أن تطابق عملة الصندوق/البنك" }
        val entryId = AccountingService(db).postVoucher(
            AccountingService.VoucherRequest(
                type = "EXPENSE",
                treasuryAccountId = treasury.id,
                offsetAccountId = expenseAccount.id,
                amountOriginal = request.amountOriginal,
                currencyCode = request.currencyCode,
                exchangeRate = request.exchangeRate,
                description = request.description.trim().ifBlank { expenseTypeLabel(request.expenseType) + " — ${shipment.shipmentNo}" },
                referenceNo = shipment.shipmentNo,
                voucherDate = request.expenseDate,
                createdBy = request.createdBy,
                operationId = request.operationId,
                expenseContext = AccountingService.ExpenseContext(
                    costCenterCode = "DISTRIBUTION",
                    organizationUnit = shipment.destinationProvince,
                    referenceType = "SHIPMENT",
                    referenceId = shipment.id,
                    referenceNo = shipment.shipmentNo,
                    referenceLabel = "شحنة ${shipment.shipmentNo} إلى ${shipment.destinationProvince}"
                )
            )
        )
        val voucher = requireNotNull(db.partyDao().voucherByJournalEntryId(entryId)) { "تعذر العثور على سند صرف الشحنة بعد الترحيل" }
        db.shipmentDao().expensesForShipment(shipment.id).firstOrNull { it.partyVoucherId == voucher.id }?.let { return it.id }
        return db.shipmentDao().insertExpense(
            SalesShipmentExpenseEntity(
                shipmentId = shipment.id,
                expenseType = request.expenseType.uppercase(Locale.US),
                description = request.description.trim(),
                expenseDate = request.expenseDate,
                amountOriginal = request.amountOriginal,
                currencyCode = request.currencyCode,
                exchangeRate = request.exchangeRate,
                amountBase = request.amountOriginal * request.exchangeRate,
                bearer = request.bearer,
                paymentMethod = treasury.kind,
                partyVoucherId = voucher.id,
                paymentVoucherNo = voucher.voucherNo,
                paymentReference = request.paymentReference.trim(),
                createdBy = request.createdBy
            )
        )
    }

    /**
     * Analytical only: the allocation below never posts a second journal.
     * Allocates shipment item quantities to the invoice by item+lot trace. This is many-to-many and
     * fail-closed against duplicated quantities on either side.
     */
    suspend fun allocateShipmentItemsToInvoice(shipmentId: Long, invoiceId: Long, userId: Long): Double = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.SALES_POST)
        val shipment = requireNotNull(db.shipmentDao().shipmentById(shipmentId)) { "الشحنة غير موجودة" }
        val invoice = requireNotNull(db.salesDao().invoiceById(invoiceId)) { "فاتورة المبيعات غير موجودة" }
        require(shipment.status != "CANCELLED") { "الشحنة ملغاة" }
        require(invoice.status == "POSTED") { "الفاتورة غير مرحلة" }
        val shipmentGovernorateId = requireNotNull(shipment.destinationGovernorateId) {
            "الشحنة ${shipment.shipmentNo} قديمة وغير مربوطة بمعرف محافظة؛ عدّل موقعها أولاً"
        }
        val invoiceGovernorateId = requireNotNull(invoice.governorateId) {
            "الفاتورة ${invoice.invoiceNo} قديمة وغير مربوطة بمعرف محافظة؛ حدّث موقع العميل/الفاتورة أولاً"
        }
        require(shipmentGovernorateId == invoiceGovernorateId) {
            "محافظة الفاتورة (${invoice.province}) لا تطابق وجهة الشحنة (${shipment.destinationProvince})"
        }
        val lines = db.salesDao().linesForInvoice(invoiceId)
        val salesAlloc = lines.flatMap { line -> db.salesDao().allocationsForLine(line.id).map { line.itemId to it } }
        val shipmentItems = db.shipmentDao().itemsForShipment(shipmentId)
        var totalLinked = 0.0
        for (shipItem in shipmentItems) {
            if (db.shipmentDao().itemAllocationLinkCount(shipItem.id, invoiceId) > 0) continue
            val lotKey = shipItem.lotNo.trim()
            val invoicePhysical = salesAlloc
                .filter { (itemId, a) -> itemId == shipItem.itemId && (lotKey.isBlank() || (a.lotNo ?: "").trim() == lotKey) }
                .sumOf { (_, a) -> a.quantityBase + a.freeQuantityBase }
            if (invoicePhysical <= 0.000001) continue
            val alreadyToOtherShipments = invoiceAllocatedQtyForItemLot(invoiceId, shipItem.itemId, lotKey)
            val invoiceRemaining = (invoicePhysical - alreadyToOtherShipments).coerceAtLeast(0.0)
            val shipmentRemaining = (shipItem.quantityBase - db.shipmentDao().allocatedQtyBase(shipItem.id)).coerceAtLeast(0.0)
            val qty = minOf(invoiceRemaining, shipmentRemaining)
            if (qty > 0.000001) {
                db.shipmentDao().insertItemAllocation(
                    SalesShipmentInvoiceItemAllocationEntity(
                        shipmentItemId = shipItem.id, invoiceId = invoiceId, quantityBase = qty, createdBy = userId
                    )
                )
                totalLinked += qty
            }
        }
        require(totalLinked > 0.000001) { "لا توجد كمية متاحة للربط؛ تحقق من الصنف/التشغيلة أو من أنها لم تُربط بشحنة أخرى" }
        totalLinked
    }

    /** Auto cost allocation is safe for a one-item shipment (the user's 10→5+5 case). */
    suspend fun allocateExpensesByQuantity(shipmentId: Long, invoiceId: Long, userId: Long): AllocationResult = db.withTransaction {
        val linkedQty = ensureInvoiceItemLinkInsideTransaction(shipmentId, invoiceId, userId)
        val items = db.shipmentDao().itemsForShipment(shipmentId)
        val distinctItems = items.map { it.itemId }.distinct()
        require(distinctItems.size == 1) {
            "التوزيع التلقائي بالكمية متاح للشحنة ذات الصنف الواحد. للشحنات متعددة الأصناف استخدم التوزيع اليدوي للمصاريف."
        }
        val shipmentQty = items.sumOf { it.quantityBase }
        require(shipmentQty > 0.0) { "كمية الشحنة غير صالحة" }
        val expenseMap = db.shipmentDao().expensesForShipment(shipmentId).associate { expense ->
            val already = db.shipmentDao().allocatedExpenseBase(expense.id)
            expense.id to ShipmentAllocationMath.proportionalExpenseAllocation(
                expenseTotalBase = expense.amountBase,
                alreadyAllocatedBase = already,
                shipmentQuantityBase = shipmentQty,
                invoiceShipmentQuantityBase = linkedQty
            )
        }
        allocateExpenseMapInsideTransaction(shipmentId, invoiceId, expenseMap, "QUANTITY", linkedQty, userId)
    }

    /** Manual allocation supports mixed-item shipments while retaining the same no-duplication guards. */
    suspend fun allocateExpensesManually(shipmentId: Long, invoiceId: Long, amountsBaseByExpenseId: Map<Long, Double>, userId: Long): AllocationResult = db.withTransaction {
        val linkedQty = ensureInvoiceItemLinkInsideTransaction(shipmentId, invoiceId, userId)
        allocateExpenseMapInsideTransaction(shipmentId, invoiceId, amountsBaseByExpenseId, "MANUAL", linkedQty, userId)
    }

    suspend fun reopenInvoiceAllocations(invoiceId: Long, reason: String, userId: Long, at: Long = TrustedTimeService.now()) = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.SALES_POST)
        require(reason.trim().isNotBlank()) { "سبب إعادة فتح تخصيص الشحنة مطلوب" }
        db.shipmentDao().reverseItemAllocationsForInvoice(invoiceId, at, reason.trim())
        db.shipmentDao().reverseExpenseAllocationsForInvoice(invoiceId, at, reason.trim())
        refreshClosedShipments()
    }

    private suspend fun ensureInvoiceItemLinkInsideTransaction(shipmentId: Long, invoiceId: Long, userId: Long): Double {
        val existing = db.shipmentDao().itemAllocationsForInvoice(invoiceId).filter { alloc ->
            db.shipmentDao().itemById(alloc.shipmentItemId)?.shipmentId == shipmentId
        }.sumOf { it.quantityBase }
        if (existing > 0.000001) return existing
        return allocateShipmentItemsToInvoice(shipmentId, invoiceId, userId)
    }

    private suspend fun allocateExpenseMapInsideTransaction(
        shipmentId: Long,
        invoiceId: Long,
        requested: Map<Long, Double>,
        method: String,
        basisQty: Double,
        userId: Long
    ): AllocationResult {
        db.requireUserPermission(userId, SecurityPermissions.SALES_POST)
        val shipment = requireNotNull(db.shipmentDao().shipmentById(shipmentId)) { "الشحنة غير موجودة" }
        val invoice = requireNotNull(db.salesDao().invoiceById(invoiceId)) { "الفاتورة غير موجودة" }
        require(invoice.status == "POSTED") { "الفاتورة غير مرحلة" }
        val expenses = db.shipmentDao().expensesForShipment(shipmentId)
        var allocatedNow = 0.0
        for ((expenseId, amount) in requested) {
            if (amount <= 0.000001) continue
            require(amount.isFinite()) { "مبلغ التخصيص غير صالح" }
            val expense = requireNotNull(expenses.firstOrNull { it.id == expenseId }) { "مصروف لا يتبع هذه الشحنة" }
            require(expense.bearer != "CUSTOMER") {
                "المصروف المحمل على العميل يجب توزيعه تلقائياً أثناء ترحيل فاتورة البيع المرتبطة بالشحنة، حتى تتطابق قيمة الفاتورة والقيد المحاسبي. لا تستخدم التخصيص اليدوي لهذا المصروف."
            }
            require(db.shipmentDao().expenseAllocationLinkCount(expense.id, invoiceId) == 0) {
                "تم ربط مصروف ${expense.paymentVoucherNo} بهذه الفاتورة مسبقاً"
            }
            val already = db.shipmentDao().allocatedExpenseBase(expense.id)
            val remaining = (expense.amountBase - already).coerceAtLeast(0.0)
            require(amount <= remaining + 0.01) {
                "المبلغ المطلوب ${money(amount)} يتجاوز المتبقي المتاح للتوزيع ${money(remaining)} لمصروف ${expense.paymentVoucherNo}"
            }
            db.shipmentDao().insertExpenseAllocation(
                SalesShipmentExpenseInvoiceAllocationEntity(
                    shipmentExpenseId = expense.id,
                    invoiceId = invoiceId,
                    amountBase = amount.coerceAtMost(remaining),
                    customerChargeBase = if (expense.bearer == "CUSTOMER") amount.coerceAtMost(remaining) else 0.0,
                    allocationMethod = method,
                    basisQuantityBase = basisQty,
                    createdBy = userId
                )
            )
            allocatedNow += amount.coerceAtMost(remaining)
        }
        require(allocatedNow > 0.000001 || expenses.isEmpty()) { "لم يتم تخصيص أي تكلفة شحن جديدة لهذه الفاتورة" }
        refreshShipmentStatus(shipment.id)
        val summary = db.shipmentDao().shipmentSummaries().first { it.id == shipment.id }
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = userId, action = "ALLOCATE", entityType = "SHIPMENT_INVOICE_COST", entityId = "${shipment.id}:$invoiceId",
                newValue = "shipment=${shipment.shipmentNo}|invoice=${invoice.invoiceNo}|method=$method|qty=$basisQty|cost=$allocatedNow|remaining=${summary.remainingExpenseBase}",
                reason = "تخصيص تكلفة شحنة على فاتورة"
            )
        )
        return AllocationResult(invoiceId, shipment.id, basisQty, allocatedNow, summary.totalExpenseBase, summary.remainingExpenseBase, summary.status == "CLOSED")
    }

    private suspend fun invoiceAllocatedQtyForItemLot(invoiceId: Long, itemId: Long, lotNo: String): Double {
        var total = 0.0
        db.shipmentDao().itemAllocationsForInvoice(invoiceId).forEach { alloc ->
            val item = db.shipmentDao().itemById(alloc.shipmentItemId) ?: return@forEach
            if (item.itemId == itemId && (lotNo.isBlank() || item.lotNo.trim() == lotNo)) total += alloc.quantityBase
        }
        return total
    }

    private suspend fun refreshShipmentStatus(shipmentId: Long) {
        val row = db.shipmentDao().shipmentById(shipmentId) ?: return
        if (row.status == "CANCELLED") return
        val itemsDone = db.shipmentDao().shipmentItemAllocationRows(shipmentId).all { it.remainingQtyBase <= 0.000001 }
        val summary = db.shipmentDao().shipmentSummaries().firstOrNull { it.id == shipmentId } ?: return
        val costsDone = summary.remainingExpenseBase <= 0.01
        val newStatus = if (itemsDone && costsDone) "CLOSED" else if (row.status == "DRAFT") "IN_TRANSIT" else row.status
        if (newStatus != row.status) db.shipmentDao().updateShipment(row.copy(status = newStatus, closedAt = if (newStatus == "CLOSED") TrustedTimeService.now() else null))
    }

    private suspend fun refreshClosedShipments() {
        db.shipmentDao().shipmentSummaries().forEach { summary ->
            val row = db.shipmentDao().shipmentById(summary.id) ?: return@forEach
            if (row.status == "CANCELLED") return@forEach
            val itemsDone = db.shipmentDao().shipmentItemAllocationRows(row.id).all { it.remainingQtyBase <= 0.000001 }
            val shouldClose = itemsDone && summary.remainingExpenseBase <= 0.01
            val status = if (shouldClose) "CLOSED" else if (row.status == "CLOSED") "DELIVERED" else row.status
            if (status != row.status) db.shipmentDao().updateShipment(row.copy(status=status, closedAt=if (status=="CLOSED") TrustedTimeService.now() else null))
        }
    }

    private fun money(v: Double) = String.format(Locale.US, "%.2f", v)
    private fun expenseTypeLabel(code: String) = when(code.uppercase(Locale.US)) {
        "TRANSPORT" -> "نقل"; "LOADING" -> "تحميل وتنزيل"; "CUSTOMS" -> "جمارك"; else -> "مصروف شحنة"
    }
}
