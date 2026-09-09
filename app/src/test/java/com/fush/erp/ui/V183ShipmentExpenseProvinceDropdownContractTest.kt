package com.fush.erp.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class V183ShipmentExpenseProvinceDropdownContractTest {
    @Test
    fun shipmentExpenseUsesValidTreasuryOperationUuid() {
        val source = File("src/main/java/com/fush/erp/ui/screens/SalesScreens.kt").readText()
        assertTrue(source.contains("TreasuryVoucherOperationIdentity.newOperationId()"))
        assertFalse(source.contains("SHIP-EXP-${'$'}{UUID.randomUUID()}"))
    }

    @Test
    fun shipmentProvinceIsDropdownAndSupportsAddingMasterData() {
        val sales = File("src/main/java/com/fush/erp/ui/screens/SalesScreens.kt").readText()
        val dao = File("src/main/java/com/fush/erp/data/dao/GeographyDao.kt").readText()
        assertTrue(sales.contains("label = \"إلى المحافظة\""))
        assertTrue(sales.contains("Text(\"إضافة محافظة جديدة\")"))
        assertTrue(sales.contains("geographyService.upsertProvincePolicy"))
        assertTrue(dao.contains("suspend fun knownProvinceNames(): List<String>"))
        assertTrue(dao.contains("SELECT TRIM(destinationProvince) AS name FROM sales_shipments"))
    }
}
