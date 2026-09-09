package com.fush.erp.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShipmentFifoLotAutoselectV180ContractTest {
    private fun read(path: String): String {
        val direct = File(path)
        if (direct.isFile) return direct.readText()
        val fromModule = File("..", path)
        require(fromModule.isFile) { "Source contract file not found: $path" }
        return fromModule.readText()
    }

    @Test fun `shipment create UI no longer accepts manual lot entry`() {
        val screen = read("app/src/main/java/com/fush/erp/ui/screens/SalesScreens.kt")
        assertTrue(screen.contains("التشغيلة / Lot — تلقائي من أقدم مخزون"))
        assertTrue(screen.contains("availableShipmentLotsFifo"))
        assertTrue(screen.contains("allocateOldestAvailableLots"))
        assertFalse(screen.contains("OutlinedTextField(lotNo, { lotNo = it }, label = { Text(\"التشغيلة / Lot\")"))
    }

    @Test fun `fifo stock order and open shipment reservation are enforced`() {
        val shipmentDao = read("app/src/main/java/com/fush/erp/data/dao/ShipmentDao.kt")
        val service = read("app/src/main/java/com/fush/erp/domain/ShipmentService.kt")
        assertTrue(service.contains("lotBalancesAt(warehouseId, itemId, asOf)"))
        assertTrue(service.contains("lotMovementTimeline"))
        assertTrue(service.contains("firstMovementDate"))
        assertTrue(shipmentDao.contains("reservedShipmentQtyBaseAt"))
        assertTrue(shipmentDao.contains("sales_shipment_invoice_item_allocations"))
        assertTrue(service.contains("requireRequestUsesOldestAvailableLots(request)"))
    }

    @Test fun `stock lookup errors are not disguised as zero inventory`() {
        val screen = read("app/src/main/java/com/fush/erp/ui/screens/SalesScreens.kt")
        val lookupStart = screen.indexOf("LaunchedEffect(warehouse?.id, item?.id, dateText, drafts.size)")
        val lookupEnd = screen.indexOf("OutlinedTextField(", lookupStart)
        val lookupBlock = screen.substring(lookupStart, lookupEnd)
        assertTrue(lookupBlock.contains("lotLookupError"))
        assertTrue(lookupBlock.contains("catch (e: Exception)"))
        assertFalse(lookupBlock.contains(".getOrNull()"))
        assertTrue(screen.contains("خطأ قراءة المخزون:"))
    }
}
