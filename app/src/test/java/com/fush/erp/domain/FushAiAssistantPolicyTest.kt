package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FushAiAssistantPolicyTest {
    @Test fun detectsSalesToday() {
        assertEquals(FushAiIntent.SALES_TODAY, FushAiIntentParser.detect("كم مبيعات اليوم؟"))
    }

    @Test fun detectsCustomerBalance() {
        assertEquals(FushAiIntent.CUSTOMER_BALANCE, FushAiIntentParser.detect("كم مديونية العميل أحمد؟"))
    }

    @Test fun detectsStock() {
        assertEquals(FushAiIntent.STOCK_ITEM, FushAiIntentParser.detect("كم مخزون فوش؟"))
    }

    @Test fun detectsTreasury() {
        assertEquals(FushAiIntent.TREASURY_BALANCE, FushAiIntentParser.detect("اعرض أرصدة الخزينة"))
    }

    @Test fun detectsOverdue() {
        assertEquals(FushAiIntent.OVERDUE_INVOICES, FushAiIntentParser.detect("ما هي الفواتير المتأخرة؟"))
    }

    @Test fun detectsShipments() {
        assertEquals(FushAiIntent.SHIPMENTS_STATUS, FushAiIntentParser.detect("الشحنات غير المسواة"))
    }

    @Test fun detectsHelp() {
        assertEquals(FushAiIntent.HELP, FushAiIntentParser.detect("مساعدة"))
    }
}
