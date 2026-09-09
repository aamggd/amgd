package com.fush.erp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.fush.erp.cloud.CloudOperationResult
import com.fush.erp.data.entity.UserEntity
import com.fush.erp.domain.BusinessTimeZone
import com.fush.erp.domain.CommercialLicenseAccess
import com.fush.erp.domain.CommercialLicenseManager
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter

@Composable
fun CommercialLicenseScreen(
    manager: CommercialLicenseManager,
    user: UserEntity,
    onAccessChanged: (CommercialLicenseAccess) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var access by remember { mutableStateOf(manager.currentAccess()) }
    var activationCode by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var resultError by remember { mutableStateOf(false) }

    fun publishAccess() {
        access = manager.currentAccess()
        onAccessChanged(access)
    }

    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("ترخيص FUSH Customer", style = MaterialTheme.typography.headlineSmall)
        Text(
            "الترخيص مرتبط بالشركة والجهاز. لا يتم تخزين رمز التفعيل على الهاتف.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("الحالة: ${modeLabel(access)}", style = MaterialTheme.typography.titleMedium)
                Text(access.message, style = MaterialTheme.typography.bodyMedium)
                access.snapshot?.let { snapshot ->
                    Text("الباقة: ${snapshot.planCode}")
                    Text("عدد الأجهزة المسموح: ${snapshot.maxDevices}")
                    Text("انتهاء الترخيص: ${formatEpoch(snapshot.expiresAt)}")
                    Text("نهاية فترة السماح: ${formatEpoch(snapshot.offlineGraceUntil)}")
                    if (snapshot.isTrial) Text("نوع الترخيص: تجريبي")
                }
            }
        }

        OutlinedTextField(
            value = activationCode,
            onValueChange = { activationCode = it.take(128) },
            label = { Text("رمز التفعيل") },
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                enabled = !busy && activationCode.trim().length >= 8,
                onClick = {
                    busy = true
                    resultMessage = null
                    scope.launch {
                        when (val result = manager.activate(user, activationCode)) {
                            is CloudOperationResult.Success -> {
                                activationCode = ""
                                resultError = false
                                resultMessage = "تم تفعيل الترخيص والتحقق من الجهاز."
                                publishAccess()
                            }
                            is CloudOperationResult.Failure -> {
                                resultError = true
                                resultMessage = result.message
                            }
                        }
                        busy = false
                    }
                },
            ) { Text("تفعيل الترخيص") }

            TextButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    resultMessage = null
                    scope.launch {
                        when (val result = manager.refresh(user)) {
                            is CloudOperationResult.Success -> {
                                resultError = false
                                resultMessage = "تم تحديث حالة الترخيص من الخادم."
                                publishAccess()
                            }
                            is CloudOperationResult.Failure -> {
                                resultError = true
                                resultMessage = result.message
                            }
                        }
                        busy = false
                    }
                },
            ) { Text("تحديث الحالة") }

            if (busy) CircularProgressIndicator()
        }

        resultMessage?.let { message ->
            Text(
                message,
                color = if (resultError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(Modifier.height(4.dp))
        Text(
            "إذا لم تسجل دخولك للسحابة بعد، افتح تبويب السحابة أولًا وسجل دخول حساب الشركة ثم ارجع للتفعيل.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!access.canWrite) {
            Text(
                "عند انتهاء أو إيقاف الترخيص لا تُحذف بياناتك. تبقى التقارير والتصدير والدعم والسحابة متاحة.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun modeLabel(access: CommercialLicenseAccess): String = when (access.mode.name) {
    "ACTIVE" -> "نشط"
    "TRIAL" -> "تجريبي"
    "OFFLINE_GRACE" -> "فترة سماح دون اتصال"
    "READ_ONLY" -> "قراءة فقط"
    else -> "غير مفعّل"
}

private fun formatEpoch(epochMillis: Long): String {
    if (epochMillis <= 0L) return "—"
    return runCatching {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(BusinessTimeZone.zoneId)
            .format(Instant.ofEpochMilli(epochMillis))
    }.getOrDefault("—")
}
