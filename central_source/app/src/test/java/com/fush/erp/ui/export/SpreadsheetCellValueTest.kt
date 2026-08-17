package com.fush.erp.ui.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpreadsheetCellValueTest {
    @Test
    fun parsesYemeniCurrencyAsNumericCell() {
        val parsed = parseSpreadsheetCell("125,000.50 ر.ي")
        assertEquals(SpreadsheetCellKind.CURRENCY, parsed.kind)
        assertEquals(125000.50, parsed.number ?: 0.0, 0.0001)
    }

    @Test
    fun parsesPlainQuantityAsNumber() {
        val parsed = parseSpreadsheetCell("360")
        assertEquals(SpreadsheetCellKind.NUMBER, parsed.kind)
        assertEquals(360.0, parsed.number ?: 0.0, 0.0001)
    }

    @Test
    fun preservesCodesAndBalanceLabelsAsText() {
        val code = parseSpreadsheetCell("00125")
        val balance = parseSpreadsheetCell("100,000.00 ر.ي مدين")
        assertEquals(SpreadsheetCellKind.TEXT, code.kind)
        assertNull(code.number)
        assertEquals(SpreadsheetCellKind.TEXT, balance.kind)
        assertNull(balance.number)
    }
}
