package com.fush.erp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.fush.erp.R
import com.fush.erp.data.AppContainer
import com.fush.erp.data.entity.UserEntity
import com.fush.erp.domain.AuthenticationResult
import com.fush.erp.ui.FushBrand
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun LoginScreen(
    container: AppContainer,
    onLogin: (UserEntity) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val loginStateDescription = if (busy) stringResource(R.string.login_checking_state) else stringResource(R.string.login_ready_state)

    fun submit() {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            when (val result = container.securityService.authenticate(
                username = username,
                password = password.toCharArray()
            )) {
                is AuthenticationResult.Success -> {
                    password = ""
                    onLogin(result.user)
                }
                is AuthenticationResult.Locked -> {
                    val until = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(result.until))
                    error = "تم قفل الحساب مؤقتًا بسبب محاولات دخول فاشلة. يمكن المحاولة بعد $until"
                    password = ""
                }
                is AuthenticationResult.Failure -> {
                    error = result.message
                    password = ""
                }
            }
            busy = false
        }
    }

    val background = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f),
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.28f),
        )
    )

    Box(
        modifier = Modifier.fillMaxSize().background(background).padding(horizontal = 20.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 5.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                FushBrand()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.login_welcome), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(R.string.login_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.login_username)) },
                    supportingText = { Text(stringResource(R.string.login_username_helper)) },
                    singleLine = true,
                    enabled = !busy,
                    shape = MaterialTheme.shapes.medium,
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.login_password)) },
                    singleLine = true,
                    enabled = !busy,
                    shape = MaterialTheme.shapes.medium,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); submit() }),
                    isError = error != null,
                    supportingText = {
                        error?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                            )
                        }
                    },
                )

                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                Button(
                    onClick = ::submit,
                    enabled = !busy && username.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .semantics { stateDescription = loginStateDescription },
                    shape = MaterialTheme.shapes.medium,
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(if (busy) stringResource(R.string.login_checking) else stringResource(R.string.login_action))
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    stringResource(R.string.login_security_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}
