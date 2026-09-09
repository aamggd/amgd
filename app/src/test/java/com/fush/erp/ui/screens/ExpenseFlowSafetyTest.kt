package com.fush.erp.ui.screens

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ExpenseFlowSafetyTest {
    @Test
    fun `room refresh failure after an emitted value falls back instead of escaping`() = runBlocking {
        val failure = IllegalStateException("simulated Room refresh failure after expense post")
        var captured: Throwable? = null
        val values = flow {
            emit(listOf(10L))
            throw failure
        }.expenseScreenSafeFlow(
            fallback = emptyList(),
            source = "treasury-balances-after-expense-post",
            onError = { captured = it }
        ).toList()

        assertEquals(listOf(listOf(10L), emptyList<Long>()), values)
        assertSame(failure, captured)
    }

    @Test
    fun `initial room refresh failure emits safe fallback`() = runBlocking {
        val failure = IllegalArgumentException("simulated mapper failure")
        var captured: Throwable? = null
        val values = flow<List<Long>> {
            throw failure
        }.expenseScreenSafeFlow(
            fallback = emptyList(),
            source = "expense-report",
            onError = { captured = it }
        ).toList()

        assertEquals(listOf(emptyList<Long>()), values)
        assertSame(failure, captured)
    }
}
