package com.fush.erp.ui.export

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun ReportExportActions(
    document: ReportExportDocument,
    baseName: String,
    printJobName: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val context = LocalContext.current
    var shareMenu by remember { mutableStateOf(false) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("التصدير والمشاركة والطباعة", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = {
                    try {
                        ReportExportSupport.exportPdf(context, document, baseName)
                        Toast.makeText(context, "تم حفظ PDF في التنزيلات/FushERP", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, e.message ?: "تعذر تصدير PDF", Toast.LENGTH_LONG).show()
                    }
                }
            ) { Text("PDF") }
            OutlinedButton(
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = {
                    try {
                        ReportExportSupport.exportXlsx(context, document, baseName)
                        Toast.makeText(context, "تم حفظ Excel في التنزيلات/FushERP", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, e.message ?: "تعذر تصدير Excel", Toast.LENGTH_LONG).show()
                    }
                }
            ) { Text("Excel") }
            Column(Modifier.weight(1f)) {
                OutlinedButton(
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { shareMenu = true }
                ) { Text("مشاركة") }
                DropdownMenu(expanded = shareMenu, onDismissRequest = { shareMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("مشاركة PDF") },
                        onClick = {
                            shareMenu = false
                            try {
                                ReportExportSupport.sharePdf(context, document, baseName)
                            } catch (e: Exception) {
                                Toast.makeText(context, e.message ?: "تعذر مشاركة PDF", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("مشاركة Excel") },
                        onClick = {
                            shareMenu = false
                            try {
                                ReportExportSupport.shareXlsx(context, document, baseName)
                            } catch (e: Exception) {
                                Toast.makeText(context, e.message ?: "تعذر مشاركة Excel", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                }
            }
        }
        OutlinedButton(
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                try {
                    ReportExportSupport.printPreview(context, document, printJobName)
                } catch (e: Exception) {
                    Toast.makeText(context, e.message ?: "تعذر فتح معاينة الطباعة", Toast.LENGTH_LONG).show()
                }
            }
        ) { Text("معاينة قبل الطباعة / طباعة") }
        Text(
            "PDF وExcel يستخدمان نفس بيانات التقرير الحالية ونفس الفترة والفلاتر.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}
