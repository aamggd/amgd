package com.fush.erp.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FushAiToolPolicyV210Test {
    @Test
    fun accountantGetsOnlyToolsBackedByItsPermissions() {
        val perms = setOf(SecurityPermissions.ACCOUNTING_VIEW, SecurityPermissions.SALES_VIEW)
        val tools = FushAiToolPolicy.allowedTools("ACCOUNTANT", perms)
        assertTrue(FushAiToolPolicy.TREASURY_BALANCES in tools)
        assertTrue(FushAiToolPolicy.SALES_SUMMARY in tools)
        assertTrue(FushAiToolPolicy.BUSINESS_SUMMARY in tools)
        assertFalse(FushAiToolPolicy.STOCK_ITEM in tools)
        assertFalse(FushAiToolPolicy.PRODUCTION_STATUS in tools)
    }

    @Test
    fun adminReceivesAllReadOnlyToolsAndNoWriteToolExists() {
        val tools = FushAiToolPolicy.allowedTools("ADMIN", emptySet())
        assertTrue(FushAiToolPolicy.CUSTOMER_BALANCE in tools)
        assertTrue(FushAiToolPolicy.STOCK_ITEM in tools)
        assertTrue(FushAiToolPolicy.SHIPMENTS_STATUS in tools)
        assertFalse(tools.any { it.contains("create") || it.contains("post") || it.contains("delete") || it.contains("update") })
    }
}
