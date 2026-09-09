package com.fush.erp.ui.screens

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState as runtimeCollectAsState
import com.fush.erp.data.entity.ExpenseReportRow
import com.fush.erp.data.entity.SalesRepContributionRow
import com.fush.erp.data.entity.TreasuryBalanceRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/**
 * Expense-screen crash boundary for Room flows that can be invalidated while an expense is posted.
 *
 * Expense posting writes journal_entries/journal_lines and therefore immediately invalidates the
 * treasury-balance query in addition to the expense-specific report queries. A malformed legacy
 * row, mapper/query failure or stale database state must not cancel Compose's collection coroutine
 * and close the whole application. We keep the workspace alive with its safe initial value and
 * preserve the real exception in Logcat for diagnosis.
 */
internal fun <T> Flow<T>.expenseScreenSafeFlow(
    fallback: T,
    source: String,
    onError: (Throwable) -> Unit = {}
): Flow<T> = catch { error ->
    logExpenseFlowFailure(source, error)
    onError(error)
    emit(fallback)
}

@Composable
@JvmName("collectExpenseReportRowsAsStateSafely")
internal fun Flow<List<ExpenseReportRow>>.collectAsState(
    initial: List<ExpenseReportRow>
): State<List<ExpenseReportRow>> {
    val safeFlow = remember(this, initial) {
        expenseScreenSafeFlow(initial, "expense-report")
    }
    return safeFlow.runtimeCollectAsState(initial = initial)
}

@Composable
@JvmName("collectSalesRepContributionRowsAsStateSafely")
internal fun Flow<List<SalesRepContributionRow>>.collectAsState(
    initial: List<SalesRepContributionRow>
): State<List<SalesRepContributionRow>> {
    val safeFlow = remember(this, initial) {
        expenseScreenSafeFlow(initial, "sales-rep-contribution")
    }
    return safeFlow.runtimeCollectAsState(initial = initial)
}

/**
 * This overload is essential for Add Expense: journal posting invalidates treasury balances before
 * the dialog is dismissed. Without this boundary, a Room refresh failure escapes the save
 * try/catch because it runs in Compose's collector coroutine, producing a process-level crash.
 */
@Composable
@JvmName("collectTreasuryBalanceRowsForExpenseAsStateSafely")
internal fun Flow<List<TreasuryBalanceRow>>.collectAsState(
    initial: List<TreasuryBalanceRow>
): State<List<TreasuryBalanceRow>> {
    val safeFlow = remember(this, initial) {
        expenseScreenSafeFlow(initial, "treasury-balances-after-expense-post")
    }
    return safeFlow.runtimeCollectAsState(initial = initial)
}

private fun logExpenseFlowFailure(source: String, error: Throwable) {
    // Local JVM tests use the Android stub jar, where Log methods may be unimplemented.
    // Logging must never become a second failure while handling the original Room exception.
    runCatching { Log.e(TAG, "Expense workspace Room flow failed: $source", error) }
}

private const val TAG = "FushExpenseScreen"
