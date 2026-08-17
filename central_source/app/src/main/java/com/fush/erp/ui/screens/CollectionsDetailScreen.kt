package com.fush.erp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fush.erp.data.AppContainer
import com.fush.erp.data.entity.CollectionDetailRow
import com.fush.erp.ui.FushEmptyState
import com.fush.erp.ui.FushErrorState
import com.fush.erp.ui.FushLoadingState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CollectionsDetailScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val now = remember { System.currentTimeMillis() }
    val from = remember(now) { now - 30L * 86_400_000L }
    var rows by remember { mutableStateOf<List<CollectionDetailRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("ALL") }

    LaunchedEffect(from, now) {
        loading = true
        val result = runCatching { container.db.reportDao().collectionDetails(from, now) }
        rows = result.getOrDefault(emptyList())
        error = result.exceptionOrNull()?.message
        loading = false
    }

    val receiptsBase = rows.filter { it.entryType in setOf("RECEIPT", "VOUCHER_RECEIPT") }.sumOf { it.amountBase }
    val refundsBase = -rows.filter { it.entryType in setOf("CASH_REFUND", "VOUCHER_PAYMENT") }.sumOf { it.amountBase }
    val netBase = rows.sumOf { it.amountBase }
    val visibleRows = when (filter) {
        "RECEIPT" -> rows.filter { it.entryType in setOf("RECEIPT", "VOUCHER_RECEIPT") }
        "CASH_REFUND" -> rows.filter { it.entryType in setOf("CASH_REFUND", "VOUCHER_PAYMENT") }
        else -> rows
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("تفاصيل صافي التحصيلات", style = MaterialTheme.typography.headlineSmall)
            Text("آخر 30 يومًا — نفس الفترة المستخدمة في لوحة الإدارة", style = MaterialTheme.typography.bodyMedium)
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CollectionSummaryCard("المقبوضات", receiptsBase, Modifier.weight(1f))
                CollectionSummaryCard("المسترد للعملاء", refundsBase, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            CollectionSummaryCard("صافي التحصيل", netBase, Modifier.fillMaxWidth(), emphasize = true)
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filter == "ALL", onClick = { filter = "ALL" }, label = { Text("الكل") })
                FilterChip(selected = filter == "RECEIPT", onClick = { filter = "RECEIPT" }, label = { Text("تحصيلات") })
                FilterChip(selected = filter == "CASH_REFUND", onClick = { filter = "CASH_REFUND" }, label = { Text("مبالغ مستردة") })
            }
        }

        if (loading) {
            item { FushLoadingState("جاري تحميل التحصيلات", "يتم تجهيز المقبوضات والمبالغ المستردة وصافي التحصيل لآخر 30 يومًا.") }
        }
        error?.let { msg ->
            item { FushErrorState(detail = "تعذر تحميل تفاصيل التحصيل: $msg") }
        }
        if (!loading && error == null && visibleRows.isEmpty()) {
            item {
                FushEmptyState(
                    title = "لا توجد حركات تحصيل ضمن الفترة",
                    detail = "غيّر المرشح أو راجع فترة آخر 30 يومًا في حال كنت تتوقع وجود حركة.",
                )
            }
        }

        itemsIndexed(visibleRows, key = { index, row -> "collection:${row.entryType}:${row.referenceNo}:${row.eventDate}:$index" }) { _, row ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        val isReceipt = row.entryType in setOf("RECEIPT", "VOUCHER_RECEIPT")
                        val movementTitle = when (row.entryType) {
                            "RECEIPT" -> "تحصيل فاتورة"
                            "VOUCHER_RECEIPT" -> "سند قبض عميل"
                            "VOUCHER_PAYMENT" -> "سند صرف للعميل"
                            else -> "مبلغ مسترد للعميل"
                        }
                        Text(
                            movementTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isReceipt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Text(collectionDate(row.eventDate), style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${row.referenceNo} • ${row.customerName}")
                    val placeAndInvoice = buildList {
                        if (row.province.isNotBlank()) add(row.province)
                        if (row.invoiceNo.isNotBlank()) add("فاتورة ${row.invoiceNo}")
                    }.joinToString(" • ")
                    if (placeAndInvoice.isNotBlank()) Text(placeAndInvoice, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${signedMoney(row.amountOriginal)} ${row.currencyCode} • ${signedMoney(row.amountBase)} ريال أساسي",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (row.notes.isNotBlank()) Text(row.notes, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun CollectionSummaryCard(title: String, amount: Double, modifier: Modifier, emphasize: Boolean = false) {
    ElevatedCard(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
            Text("${summaryMoney(amount)} ريال", style = MaterialTheme.typography.titleLarge)
        }
    }
}

private fun collectionDate(value: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(value))

private fun moneyAbs(value: Double): String = "%,.2f".format(Locale.US, value).removeSuffix(".00")

private fun summaryMoney(value: Double): String =
    if (value < -0.000001) "−" + moneyAbs(kotlin.math.abs(value)) else moneyAbs(value)

private fun signedMoney(value: Double): String {
    val sign = if (value < -0.000001) "−" else if (value > 0.000001) "+" else ""
    return sign + moneyAbs(kotlin.math.abs(value))
}
