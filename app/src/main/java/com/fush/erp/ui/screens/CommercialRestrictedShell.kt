package com.fush.erp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.fush.erp.backup.BackupRestoreManager
import com.fush.erp.data.AppContainer
import com.fush.erp.data.entity.UserEntity
import com.fush.erp.domain.CommercialLicenseAccess
import com.fush.erp.domain.CommercialLicenseManager
import kotlinx.coroutines.launch

private data class CommercialRestrictedTab(
    val key: String,
    val label: String,
    val icon: ImageVector,
)

private val commercialRestrictedTabs = listOf(
    CommercialRestrictedTab("license", "الترخيص", Icons.Filled.Lock),
    CommercialRestrictedTab("cloud", "السحابة", Icons.Filled.Cloud),
    CommercialRestrictedTab("reports", "التقارير", Icons.Filled.Description),
    CommercialRestrictedTab("backup", "تصدير نسخة", Icons.Filled.UploadFile),
    CommercialRestrictedTab("support", "الدعم", Icons.Filled.SupportAgent),
)

/**
 * v214 fail-closed commercial shell.
 * It deliberately exposes no sales, purchases, inventory, production, accounting or master-data
 * screens while the license cannot write. Backup is export-only: restore is not reachable here.
 */
@Composable
fun CommercialRestrictedShell(
    container: AppContainer,
    user: UserEntity,
    manager: CommercialLicenseManager,
    access: CommercialLicenseAccess,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    languageTag: String,
    onLanguageChange: (String) -> Unit,
    onAccessChanged: (CommercialLicenseAccess) -> Unit,
    onLogout: () -> Unit,
) {
    var tab by remember { mutableStateOf("license") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("FUSH Customer", style = MaterialTheme.typography.titleMedium)
                        Text(
                            access.message,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { onLanguageChange(if (languageTag == "ar") "en" else "ar") }) {
                        Text(if (languageTag == "ar") "EN" else "AR")
                    }
                    TextButton(onClick = onToggleTheme) {
                        Text(if (darkTheme) "فاتح" else "داكن")
                    }
                    TextButton(onClick = onLogout) { Text("خروج") }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                commercialRestrictedTabs.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item.key,
                        onClick = { tab = item.key },
                        icon = { androidx.compose.material3.Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label, maxLines = 1) },
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        when (tab) {
            "cloud" -> CloudSyncScreen(container, user, modifier)
            "reports" -> ReportsScreen(container, user, modifier)
            "backup" -> CommercialBackupExportOnly(container, user, modifier)
            "support" -> SupportCenterScreen(container, user, modifier)
            else -> CommercialLicenseScreen(manager, user, onAccessChanged, modifier)
        }
    }
}

@Composable
private fun CommercialBackupExportOnly(
    container: AppContainer,
    user: UserEntity,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("تصدير نسخة احتياطية", style = MaterialTheme.typography.headlineSmall)
        Text(
            "في وضع القراءة فقط يُسمح بإنشاء نسخة احتياطية مشفرة لحماية بيانات العميل، ولا يُسمح بالاستعادة.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it.take(128) },
            label = { Text("كلمة مرور النسخة الاحتياطية") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                enabled = !busy && password.length >= 10,
                onClick = {
                    busy = true
                    message = null
                    val secret = password.toCharArray()
                    scope.launch {
                        try {
                            val result = BackupRestoreManager.createBackup(context, container.db, user.id, secret)
                            isError = false
                            message = "تم إنشاء ${result.displayName} بنجاح."
                            password = ""
                        } catch (error: Throwable) {
                            isError = true
                            message = error.message ?: "تعذر إنشاء النسخة الاحتياطية"
                        } finally {
                            secret.fill('\u0000')
                            busy = false
                        }
                    }
                },
            ) { Text("إنشاء النسخة") }
            if (busy) CircularProgressIndicator()
        }
        message?.let {
            Text(
                it,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
    }
}
