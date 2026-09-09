package com.fush.erp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fush.erp.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ShipmentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertShipment(row: SalesShipmentEntity): Long
    @Update suspend fun updateShipment(row: SalesShipmentEntity)
    @Query("SELECT * FROM sales_shipments WHERE id=:id LIMIT 1") suspend fun shipmentById(id: Long): SalesShipmentEntity?
    @Query("SELECT * FROM sales_shipments WHERE shipmentNo=:no LIMIT 1") suspend fun shipmentByNo(no: String): SalesShipmentEntity?
    @Query("SELECT COALESCE(MAX(CAST(SUBSTR(shipmentNo, LENGTH(:prefix) + 1) AS INTEGER)), 0) FROM sales_shipments WHERE shipmentNo LIKE :prefix || '%'") suspend fun maxAutomaticShipmentSequence(prefix: String): Long
    @Query("SELECT * FROM sales_shipments ORDER BY shipmentDate DESC,id DESC") fun observeShipments(): Flow<List<SalesShipmentEntity>>

    @Query("SELECT COUNT(*) FROM sales_shipment_expenses WHERE shipmentId=:shipmentId")
    suspend fun shipmentExpenseCountAll(shipmentId: Long): Int

    @Query("""
        SELECT COUNT(*) FROM sales_shipment_invoice_item_allocations a
        JOIN sales_shipment_items i ON i.id=a.shipmentItemId
        WHERE i.shipmentId=:shipmentId
    """)
    suspend fun shipmentItemAllocationCountAll(shipmentId: Long): Int

    @Query("""
        SELECT COUNT(*) FROM sales_shipment_expense_invoice_allocations a
        JOIN sales_shipment_expenses e ON e.id=a.shipmentExpenseId
        WHERE e.shipmentId=:shipmentId
    """)
    suspend fun shipmentExpenseAllocationCountAll(shipmentId: Long): Int

    @Query("DELETE FROM sales_shipments WHERE id=:shipmentId")
    suspend fun deleteShipmentById(shipmentId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertItem(row: SalesShipmentItemEntity): Long
    @Query("SELECT * FROM sales_shipment_items WHERE shipmentId=:shipmentId ORDER BY id") suspend fun itemsForShipment(shipmentId: Long): List<SalesShipmentItemEntity>
    @Query("SELECT * FROM sales_shipment_items WHERE id=:id LIMIT 1") suspend fun itemById(id: Long): SalesShipmentItemEntity?

    /**
     * Quantity from this warehouse/item/lot already committed to shipments and not yet sold,
     * evaluated as-of the shipment business date. This keeps a second shipment from reusing
     * the same physical lot while its first shipment still has unallocated quantity.
     */
    @Query("""
        SELECT COALESCE(SUM(
            i.quantityBase - COALESCE((
                SELECT SUM(a.quantityBase)
                FROM sales_shipment_invoice_item_allocations a
                WHERE a.shipmentItemId=i.id
                  AND a.createdAt <= :asOf
                  AND (a.status='ACTIVE' OR (a.status='REVERSED' AND COALESCE(a.reversedAt, 9223372036854775807) > :asOf))
            ), 0)
        ), 0)
        FROM sales_shipment_items i
        JOIN sales_shipments s ON s.id=i.shipmentId
        WHERE s.fromWarehouseId=:warehouseId
          AND i.itemId=:itemId
          AND COALESCE(i.lotNo, '')=:lotKey
          AND s.shipmentDate <= :asOf
          AND NOT (s.status='CANCELLED' AND COALESCE(s.cancelledAt, 0) <= :asOf)
          AND NOT EXISTS (
              SELECT 1 FROM stock_movements sm
              WHERE sm.referenceType='SALES_SHIPMENT_CUSTODY'
                AND sm.referenceId=s.id
                AND sm.movementType='SHIPMENT_TRANSFER_OUT'
          )
    """)
    suspend fun reservedShipmentQtyBaseAt(warehouseId: Long, itemId: Long, lotKey: String, asOf: Long): Double

    @Query("""
        SELECT COUNT(*) FROM stock_movements
        WHERE referenceType='SALES_SHIPMENT_CUSTODY'
          AND referenceId=:shipmentId
          AND movementType='SHIPMENT_TRANSFER_IN'
    """)
    suspend fun shipmentCustodyTransferCount(shipmentId: Long): Int

    @Query("""
        SELECT * FROM stock_movements
        WHERE referenceType='SALES_SHIPMENT_CUSTODY'
          AND referenceId=:shipmentId
          AND movementType='SHIPMENT_TRANSFER_IN'
          AND warehouseId=:custodyWarehouseId
          AND itemId=:itemId
          AND TRIM(COALESCE(lotNo,''))=:lotKey
        ORDER BY movementDate ASC, id ASC
    """)
    suspend fun shipmentCustodyInboundMovements(
        shipmentId: Long,
        custodyWarehouseId: Long,
        itemId: Long,
        lotKey: String
    ): List<StockMovementEntity>

    @Query("""
        SELECT * FROM stock_movements
        WHERE referenceType='SALES_SHIPMENT_CUSTODY'
          AND referenceId=:shipmentId
          AND movementType='SHIPMENT_TRANSFER_IN'
        ORDER BY movementDate ASC, id ASC
    """)
    suspend fun allShipmentCustodyInboundMovements(shipmentId: Long): List<StockMovementEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertExpense(row: SalesShipmentExpenseEntity): Long
    @Query("SELECT * FROM sales_shipment_expenses WHERE shipmentId=:shipmentId AND status='POSTED' ORDER BY expenseDate,id") suspend fun expensesForShipment(shipmentId: Long): List<SalesShipmentExpenseEntity>
    @Query("SELECT * FROM sales_shipment_expenses WHERE id=:id LIMIT 1") suspend fun expenseById(id: Long): SalesShipmentExpenseEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertItemAllocation(row: SalesShipmentInvoiceItemAllocationEntity): Long
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertExpenseAllocation(row: SalesShipmentExpenseInvoiceAllocationEntity): Long

    @Query("SELECT COALESCE(SUM(quantityBase),0) FROM sales_shipment_invoice_item_allocations WHERE shipmentItemId=:shipmentItemId AND status='ACTIVE'")
    suspend fun allocatedQtyBase(shipmentItemId: Long): Double

    @Query("SELECT COALESCE(SUM(amountBase),0) FROM sales_shipment_expense_invoice_allocations WHERE shipmentExpenseId=:expenseId AND status='ACTIVE'")
    suspend fun allocatedExpenseBase(expenseId: Long): Double

    @Query("SELECT COALESCE(SUM(customerChargeBase),0) FROM sales_shipment_expense_invoice_allocations WHERE invoiceId=:invoiceId AND status='ACTIVE'")
    suspend fun customerShipmentChargeBaseForInvoice(invoiceId: Long): Double

    @Query("SELECT COUNT(*) FROM sales_shipment_invoice_item_allocations WHERE shipmentItemId=:shipmentItemId AND invoiceId=:invoiceId AND status='ACTIVE'")
    suspend fun itemAllocationLinkCount(shipmentItemId: Long, invoiceId: Long): Int

    @Query("SELECT COUNT(*) FROM sales_shipment_expense_invoice_allocations WHERE shipmentExpenseId=:expenseId AND invoiceId=:invoiceId AND status='ACTIVE'")
    suspend fun expenseAllocationLinkCount(expenseId: Long, invoiceId: Long): Int

    @Query("SELECT * FROM sales_shipment_invoice_item_allocations WHERE invoiceId=:invoiceId AND status='ACTIVE'")
    suspend fun itemAllocationsForInvoice(invoiceId: Long): List<SalesShipmentInvoiceItemAllocationEntity>

    @Query("SELECT * FROM sales_shipment_expense_invoice_allocations WHERE invoiceId=:invoiceId AND status='ACTIVE'")
    suspend fun expenseAllocationsForInvoice(invoiceId: Long): List<SalesShipmentExpenseInvoiceAllocationEntity>

    @Query("SELECT * FROM sales_shipment_invoice_item_allocations WHERE shipmentItemId=:shipmentItemId AND status='ACTIVE'")
    suspend fun itemAllocationsForShipmentItem(shipmentItemId: Long): List<SalesShipmentInvoiceItemAllocationEntity>

    @Query("SELECT * FROM sales_shipment_expense_invoice_allocations WHERE shipmentExpenseId=:expenseId AND status='ACTIVE'")
    suspend fun expenseAllocationsForExpense(expenseId: Long): List<SalesShipmentExpenseInvoiceAllocationEntity>

    @Query("UPDATE sales_shipment_invoice_item_allocations SET status='REVERSED', reversedAt=:at, reversalReason=:reason WHERE invoiceId=:invoiceId AND status='ACTIVE'")
    suspend fun reverseItemAllocationsForInvoice(invoiceId: Long, at: Long, reason: String)

    @Query("UPDATE sales_shipment_expense_invoice_allocations SET status='REVERSED', reversedAt=:at, reversalReason=:reason WHERE invoiceId=:invoiceId AND status='ACTIVE'")
    suspend fun reverseExpenseAllocationsForInvoice(invoiceId: Long, at: Long, reason: String)

    @Query("""
        SELECT s.id AS id,s.shipmentNo AS shipmentNo,s.shipmentDate AS shipmentDate,w.nameAr AS warehouseName,
               s.destinationProvince AS destinationProvince,s.status AS status,s.transportReference AS transportReference,
               (SELECT COUNT(*) FROM sales_shipment_items i WHERE i.shipmentId=s.id) AS itemLineCount,
               COALESCE((SELECT SUM(e.amountBase) FROM sales_shipment_expenses e WHERE e.shipmentId=s.id AND e.status='POSTED'),0) AS totalExpenseBase,
               COALESCE((SELECT SUM(a.amountBase) FROM sales_shipment_expense_invoice_allocations a JOIN sales_shipment_expenses e2 ON e2.id=a.shipmentExpenseId WHERE e2.shipmentId=s.id AND a.status='ACTIVE' AND e2.status='POSTED'),0) AS allocatedExpenseBase,
               COALESCE((SELECT SUM(e3.amountBase) FROM sales_shipment_expenses e3 WHERE e3.shipmentId=s.id AND e3.status='POSTED'),0)
                 - COALESCE((SELECT SUM(a2.amountBase) FROM sales_shipment_expense_invoice_allocations a2 JOIN sales_shipment_expenses e4 ON e4.id=a2.shipmentExpenseId WHERE e4.shipmentId=s.id AND a2.status='ACTIVE' AND e4.status='POSTED'),0) AS remainingExpenseBase
        FROM sales_shipments s JOIN warehouses w ON w.id=s.fromWarehouseId
        ORDER BY s.shipmentDate DESC,s.id DESC
    """) suspend fun shipmentSummaries(): List<ShipmentSummaryRow>

    @Query("""
        SELECT i.id AS shipmentItemId,i.itemId AS itemId,it.code AS itemCode,it.nameAr AS itemName,i.lotNo AS lotNo,
               i.quantityBase AS shippedQtyBase,
               COALESCE((SELECT SUM(a.quantityBase) FROM sales_shipment_invoice_item_allocations a WHERE a.shipmentItemId=i.id AND a.status='ACTIVE'),0) AS allocatedQtyBase,
               i.quantityBase-COALESCE((SELECT SUM(a2.quantityBase) FROM sales_shipment_invoice_item_allocations a2 WHERE a2.shipmentItemId=i.id AND a2.status='ACTIVE'),0) AS remainingQtyBase
        FROM sales_shipment_items i JOIN items it ON it.id=i.itemId WHERE i.shipmentId=:shipmentId ORDER BY i.id
    """) suspend fun shipmentItemAllocationRows(shipmentId: Long): List<ShipmentItemAllocationRow>

    @Query("""
        SELECT s.id AS shipmentId,s.shipmentNo AS shipmentNo,s.shipmentDate AS shipmentDate,s.destinationProvince AS destinationProvince,
               s.transportReference AS transportReference,e.expenseType AS expenseType,e.id AS expenseId,e.amountBase AS expenseAmountBase,
               a.amountBase AS allocatedBase,e.bearer AS bearer,a.customerChargeBase AS customerChargeBase,
               e.paymentVoucherNo AS paymentVoucherNo,e.paymentReference AS paymentReference
        FROM sales_shipment_expense_invoice_allocations a
        JOIN sales_shipment_expenses e ON e.id=a.shipmentExpenseId
        JOIN sales_shipments s ON s.id=e.shipmentId
        WHERE a.invoiceId=:invoiceId AND a.status='ACTIVE' AND e.status='POSTED'
        ORDER BY s.shipmentDate,s.id,e.id
    """) suspend fun invoiceShipmentCosts(invoiceId: Long): List<ShipmentInvoiceCostRow>

    @Query("""
        SELECT COALESCE(SUM(a.amountBase),0)
        FROM sales_shipment_expense_invoice_allocations a
        JOIN sales_shipment_expenses e ON e.id=a.shipmentExpenseId
        WHERE a.invoiceId=:invoiceId AND a.status='ACTIVE' AND e.status='POSTED'
    """) suspend fun invoiceAllocatedShipmentCostBase(invoiceId: Long): Double
    @Query("""
        SELECT si.id AS invoiceId,si.invoiceNo AS invoiceNo,si.invoiceDate AS invoiceDate,c.nameAr AS customerName,
               COALESCE((SELECT SUM(ia.quantityBase) FROM sales_shipment_invoice_item_allocations ia
                         JOIN sales_shipment_items shi ON shi.id=ia.shipmentItemId
                         WHERE shi.shipmentId=:shipmentId AND ia.invoiceId=si.id AND ia.status='ACTIVE'),0) AS allocatedQuantityBase,
               COALESCE((SELECT SUM(ea.amountBase) FROM sales_shipment_expense_invoice_allocations ea
                         JOIN sales_shipment_expenses she ON she.id=ea.shipmentExpenseId
                         WHERE she.shipmentId=:shipmentId AND ea.invoiceId=si.id AND ea.status='ACTIVE' AND she.status='POSTED'),0) AS allocatedCostBase
        FROM sales_invoices si JOIN customers c ON c.id=si.customerId
        WHERE si.id IN (
          SELECT ia2.invoiceId FROM sales_shipment_invoice_item_allocations ia2 JOIN sales_shipment_items shi2 ON shi2.id=ia2.shipmentItemId
          WHERE shi2.shipmentId=:shipmentId AND ia2.status='ACTIVE'
          UNION
          SELECT ea2.invoiceId FROM sales_shipment_expense_invoice_allocations ea2 JOIN sales_shipment_expenses she2 ON she2.id=ea2.shipmentExpenseId
          WHERE she2.shipmentId=:shipmentId AND ea2.status='ACTIVE'
        )
        ORDER BY si.invoiceDate,si.id
    """) suspend fun shipmentInvoiceLinks(shipmentId: Long): List<ShipmentInvoiceLinkRow>

    @Query("""
        SELECT s.id AS shipmentId, s.shipmentNo AS shipmentNo, s.shipmentDate AS shipmentDate,
               s.destinationProvince AS destinationProvince, si.itemId AS itemId,
               SUM(si.quantityBase - COALESCE((SELECT SUM(a.quantityBase)
                   FROM sales_shipment_invoice_item_allocations a
                   WHERE a.shipmentItemId=si.id AND a.status='ACTIVE'),0)) AS remainingQtyBase
        FROM sales_shipments s
        JOIN sales_shipment_items si ON si.shipmentId=s.id
        WHERE si.itemId=:itemId AND s.fromWarehouseId=:warehouseId AND s.status!='CANCELLED'
          AND (
              (:governorateId IS NOT NULL AND s.destinationGovernorateId=:governorateId)
              OR (:governorateId IS NULL AND s.destinationGovernorateId IS NULL AND TRIM(s.destinationProvince)=TRIM(:province))
          )
        GROUP BY s.id,s.shipmentNo,s.shipmentDate,s.destinationProvince,si.itemId
        HAVING remainingQtyBase > 0.000000001
        ORDER BY s.shipmentDate, s.id
    """)
    suspend fun availableShipmentsForSale(warehouseId: Long, itemId: Long, province: String, governorateId: String?): List<ShipmentSaleOptionRow>

    @Query("""
        SELECT sl.id AS salesLineId, s.id AS shipmentId, s.shipmentNo AS shipmentNo,
               s.destinationProvince AS destinationProvince, SUM(a.quantityBase) AS quantityBase
        FROM sales_shipment_invoice_item_allocations a
        JOIN sales_shipment_items si ON si.id=a.shipmentItemId
        JOIN sales_shipments s ON s.id=si.shipmentId
        JOIN sales_lines sl ON sl.id=a.salesLineId
        WHERE a.salesLineId=:salesLineId AND a.status='ACTIVE'
        GROUP BY sl.id,s.id,s.shipmentNo,s.destinationProvince
        ORDER BY s.shipmentDate,s.id
    """)
    suspend fun shipmentAllocationsForSalesLine(salesLineId: Long): List<SalesLineShipmentAllocationRow>

    @Query("SELECT * FROM sales_shipment_invoice_item_allocations WHERE shipmentItemId=:shipmentItemId AND salesLineId=:salesLineId AND status='ACTIVE' LIMIT 1")
    suspend fun itemAllocationForSalesLine(shipmentItemId: Long, salesLineId: Long): SalesShipmentInvoiceItemAllocationEntity?

    // v179 cloud hydration/export. Direct DAO hydration prevents replaying treasury/GL side effects.
    @Query("SELECT * FROM sales_shipments ORDER BY shipmentDate,id") suspend fun allShipmentsForCloudSync(): List<SalesShipmentEntity>
    @Query("SELECT * FROM sales_shipment_items ORDER BY shipmentId,id") suspend fun allItemsForCloudSync(): List<SalesShipmentItemEntity>
    @Query("SELECT * FROM sales_shipment_expenses ORDER BY expenseDate,id") suspend fun allExpensesForCloudSync(): List<SalesShipmentExpenseEntity>
    @Query("SELECT * FROM sales_shipment_invoice_item_allocations ORDER BY shipmentItemId,invoiceId,id") suspend fun allItemAllocationsForCloudSync(): List<SalesShipmentInvoiceItemAllocationEntity>
    @Query("SELECT * FROM sales_shipment_expense_invoice_allocations ORDER BY shipmentExpenseId,invoiceId,id") suspend fun allExpenseAllocationsForCloudSync(): List<SalesShipmentExpenseInvoiceAllocationEntity>

    @Update suspend fun updateItem(row: SalesShipmentItemEntity)
    @Update suspend fun updateExpense(row: SalesShipmentExpenseEntity)
    @Update suspend fun updateItemAllocation(row: SalesShipmentInvoiceItemAllocationEntity)
    @Update suspend fun updateExpenseAllocation(row: SalesShipmentExpenseInvoiceAllocationEntity)

    @Query("SELECT * FROM sales_shipment_items WHERE shipmentId=:shipmentId AND itemId=:itemId AND lotNo=:lotNo LIMIT 1")
    suspend fun itemByNaturalKey(shipmentId: Long, itemId: Long, lotNo: String): SalesShipmentItemEntity?

    @Query("SELECT * FROM sales_shipment_expenses WHERE shipmentId=:shipmentId AND paymentVoucherNo=:voucherNo LIMIT 1")
    suspend fun expenseByNaturalKey(shipmentId: Long, voucherNo: String): SalesShipmentExpenseEntity?

    @Query("""SELECT * FROM sales_shipment_invoice_item_allocations
              WHERE shipmentItemId=:shipmentItemId AND invoiceId=:invoiceId
                AND ((:salesLineId IS NULL AND salesLineId IS NULL) OR salesLineId=:salesLineId) LIMIT 1""")
    suspend fun itemAllocationByNaturalKey(shipmentItemId: Long, invoiceId: Long, salesLineId: Long?): SalesShipmentInvoiceItemAllocationEntity?

    @Query("SELECT * FROM sales_shipment_expense_invoice_allocations WHERE shipmentExpenseId=:shipmentExpenseId AND invoiceId=:invoiceId LIMIT 1")
    suspend fun expenseAllocationByNaturalKey(shipmentExpenseId: Long, invoiceId: Long): SalesShipmentExpenseInvoiceAllocationEntity?

}
