package com.fush.erp.ui.screens

import android.app.Activity
import android.content.Context
import android.os.Process
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fush.erp.backup.BackupRestoreManager
import com.fush.erp.backup.BackupResult
import com.fush.erp.backup.RestoreInspection
import com.fush.erp.data.AppContainer
import com.fush.erp.data.entity.UserEntity
import com.fush.erp.ui.*
import com.fush.erp.domain.SecurityPermissions
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun BackupRestoreScreen(container: AppContainer, user: UserEntity, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var lastBackup by remember { mutableStateOf<BackupResult?>(null) }
    var inspection by remember { mutableStateOf<RestoreInspection?>(null) }
    var pendingRestore by remember { mutableStateOf<RestoreInspection?>(null) }
    var restoreReady by remember { mutableStateOf(false) }
    val startupRestoreError = remember { BackupRestoreManager.consumeRestoreError(context) }
    val rolePermissions by container.db.securityDao().observePermissionCodesForRole(user.role).collectAsState(initial = emptyList())
    val permissionSet = remember(rolePermissions) { rolePermissions.toSet() }
    val canBackup = user.role == "ADMIN" || SecurityPermissions.BACKUP_CREATE in permissionSet
    val canRestore = user.role == "ADMIN" || SecurityPermissions.BACKUP_RESTORE in permissionSet

    fun stageSelectedRestore(info: RestoreInspection) {
        scope.launch {
            busy = true
            message = null
            val ok = runCatching {
                container.securityService.requirePermission(user.id, SecurityPermissions.BACKUP_RESTORE)
                container.securityService.requireRecentReauthentication(user.id, "BACKUP_RESTORE")
                BackupRestoreManager.stageRestore(context, info, user.id)
            }
                .onFailure { message = it.message ?: "تعذر تجهيز الاستعادة" }
                .isSuccess
            restoreReady = ok
            if (ok) message = "تم تجهيز الاستعادة بأمان. أغلق التطبيق الآن ثم افتحه من الأيقونة لتطبيقها."
            busy = false
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                message = null
                inspection = runCatching { BackupRestoreManager.inspectBackup(context, uri) }
                    .onFailure { message = it.message ?: "تعذر فحص النسخة الاحتياطية" }
                    .getOrNull()
                busy = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FushSectionHeader(
            title = "النسخ الاحتياطي والاستعادة",
            subtitle = "إنشاء نسخة كاملة، فحص سلامتها، وتجهيز الاستعادة بأمان قبل إعادة التشغيل.",
        )
        Text("قاعدة البيانات: schema ${BackupRestoreManager.CURRENT_SCHEMA_VERSION}. يتم فحص سلامة النسخة وبصمة SHA-256، وتطبق الاستعادة باستبدال ذري مع نسخة رجوع داخلية.")

        startupRestoreError?.let {
            FushErrorState(
                title = "لم تُطبق الاستعادة السابقة",
                detail = "تم الإبقاء على قاعدة البيانات الحالية دون استبدال. السبب: $it",
            )
        }
        if (!canBackup && !canRestore) {
            FushEmptyState(
                title = "لا توجد صلاحية للنسخ الاحتياطي أو الاستعادة",
                detail = "اطلب من مدير النظام منح صلاحية إنشاء النسخ أو الاستعادة حسب مسؤولياتك.",
            )
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("نسخة احتياطية كاملة", style = MaterialTheme.typography.titleMedium)
                Text("يشمل الملف قاعدة بيانات Fush ERP كاملة بعد تثبيت معاملات SQLite. تحفظ النسخة في Downloads/FushERP/Backups على Android الحديث.")
                Button(
                    enabled = canBackup && !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            message = null
                            lastBackup = runCatching {
                                container.securityService.requirePermission(user.id, SecurityPermissions.BACKUP_CREATE)
                                BackupRestoreManager.createBackup(context, container.db, user.id)
                            }
                                .onFailure { message = it.message ?: "فشل إنشاء النسخة الاحتياطية" }
                                .getOrNull()
                            if (lastBackup != null) message = "تم إنشاء النسخة الاحتياطية والتحقق منها."
                            busy = false
                        }
                    }
                ) { Text("إنشاء نسخة احتياطية الآن") }
                val shareUri = lastBackup?.uri ?: BackupRestoreManager.lastBackupUri(context)
                OutlinedButton(
                    enabled = canBackup && shareUri != null && !busy,
                    onClick = { shareUri?.let { BackupRestoreManager.shareBackup(context, it) } }
                ) { Text("مشاركة آخر نسخة") }
                lastBackup?.let { info ->
                    Text("الملف: ${info.displayName}")
                    Text("الحجم: ${formatBytes(info.sizeBytes)}")
                    Text("SHA-256: ${info.databaseSha256}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("استعادة نسخة احتياطية", style = MaterialTheme.typography.titleMedium)
                Text("لن يتم استبدال البيانات مباشرة. أولًا يتم فحص الملف، ثم تأكيدك، ثم تطبق الاستعادة عند تشغيل التطبيق التالي. ينظف النظام الملفات المؤقتة، ويثبت معاملات القاعدة الحالية، وينشئ نسخة رجوع داخلية قبل الاستبدال الذري.")
                Button(
                    enabled = canRestore && !busy,
                    onClick = { picker.launch(arrayOf("application/octet-stream", "application/zip", "*/*")) }
                ) { Text("اختيار ملف للاستعادة") }
            }
        }

        if (busy) {
            FushLoadingState(
                title = "جاري تنفيذ عملية النسخ الاحتياطي",
                detail = "لا تغلق التطبيق أثناء فحص الملف أو إنشاء النسخة.",
            )
        }
        FushOperationMessage(message, onConsumed = { message = null })
    }

    inspection?.let { info ->
        AlertDialog(
            onDismissRequest = { inspection = null },
            title = { Text("تأكيد استعادة قاعدة البيانات") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("إصدار التطبيق في النسخة: ${info.appVersion}")
                    Text("Schema: ${info.schemaVersion}")
                    Text("تاريخ النسخة: ${DateFormat.getDateTimeInstance().format(Date(info.createdAt))}")
                    Text("الحجم: ${formatBytes(info.sizeBytes)}")
                    Text("سيتم استبدال قاعدة البيانات الحالية بعد إعادة تشغيل التطبيق. لن يتم الحذف قبل إنشاء نسخة أمان داخلية.")
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        if (container.securityService.hasRecentReauthentication(user.id)) {
                            inspection = null
                            stageSelectedRestore(info)
                        } else {
                            inspection = null
                            pendingRestore = info
                        }
                    }
                }) { Text("تأكيد وتجهيز الاستعادة") }
            },
            dismissButton = { TextButton(onClick = { inspection = null }) { Text("إلغاء") } }
        )
    }

    pendingRestore?.let { info ->
        ReauthenticationDialog(
            container = container,
            userId = user.id,
            requireMfa = true,
            actionLabel = "استعادة نسخة احتياطية واستبدال قاعدة البيانات",
            onDismiss = { pendingRestore = null },
            onVerified = {
                pendingRestore = null
                stageSelectedRestore(info)
            }
        )
    }

    if (restoreReady) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("الاستعادة جاهزة") },
            text = { Text("سيتم تطبيق النسخة قبل فتح قاعدة البيانات في التشغيل القادم. اضغط إغلاق التطبيق، ثم افتح Fush ERP من الأيقونة.") },
            confirmButton = {
                Button(onClick = {
                    (context as? Activity)?.finishAffinity()
                    Process.killProcess(Process.myPid())
                }) { Text("إغلاق التطبيق لتطبيق الاستعادة") }
            },
            dismissButton = { TextButton(onClick = { restoreReady = false }) { Text("لاحقًا") } }
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.2f MB".format(bytes.toDouble() / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KB".format(bytes.toDouble() / 1024.0)
    else -> "$bytes B"
}
