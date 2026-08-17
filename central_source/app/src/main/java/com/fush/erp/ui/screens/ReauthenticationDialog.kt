package com.fush.erp.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.fush.erp.data.AppContainer
import com.fush.erp.domain.ReauthenticationPolicy
import com.fush.erp.domain.ReauthenticationResult
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun ReauthenticationDialog(
    container: AppContainer,
    userId: Long,
    requireMfa: Boolean,
    actionLabel: String,
    onDismiss: () -> Unit,
    onVerified: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var mfaCode by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("إعادة التحقق الأمني") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    "العملية الحساسة: $actionLabel. أدخل بياناتك الحالية لفتح نافذة تنفيذ مدتها ${ReauthenticationPolicy.WINDOW_MINUTES} دقائق فقط.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("كلمة المرور الحالية") },
                    singleLine = true,
                    enabled = !busy,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (requireMfa) {
                    OutlinedTextField(
                        value = mfaCode,
                        onValueChange = { mfaCode = it.uppercase().take(20) },
                        label = { Text("رمز MFA أو رمز استرداد") },
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && password.isNotBlank() && (!requireMfa || mfaCode.isNotBlank()),
                onClick = {
                    busy = true
                    message = null
                    scope.launch {
                        when (val result = container.securityService.reauthenticate(
                            userId = userId,
                            password = password.toCharArray(),
                            mfaCode = if (requireMfa) mfaCode else null
                        )) {
                            ReauthenticationResult.Success -> {
                                password = ""
                                mfaCode = ""
                                busy = false
                                onVerified()
                            }
                            ReauthenticationResult.MfaRequired -> {
                                message = "أدخل رمز التحقق الثنائي أو أحد رموز الاسترداد."
                                busy = false
                            }
                            is ReauthenticationResult.Locked -> {
                                message = "تم قفل الحساب حتى ${DateFormat.getDateTimeInstance().format(Date(result.until))}."
                                password = ""
                                mfaCode = ""
                                busy = false
                            }
                            is ReauthenticationResult.Failure -> {
                                message = result.message
                                password = ""
                                mfaCode = ""
                                busy = false
                            }
                        }
                    }
                }
            ) { Text("تحقق الآن") }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
