package com.fush.erp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fush.erp.R
import com.fush.erp.backup.BackupRestoreManager
import com.fush.erp.data.AppContainer
import com.fush.erp.data.entity.UserEntity
import com.fush.erp.ui.screens.ChangePasswordScreen
import com.fush.erp.ui.screens.HomeShell
import com.fush.erp.ui.screens.InitialAdminSetupScreen
import com.fush.erp.ui.screens.InitialCloudJoinScreen
import com.fush.erp.ui.screens.LoginScreen
import kotlinx.coroutines.launch

@Composable
fun FushErpApp(
    container: AppContainer,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    languageTag: String,
    onLanguageChange: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var seeded by remember { mutableStateOf(false) }
    var user by remember { mutableStateOf<UserEntity?>(null) }
    var needsInitialAdmin by remember { mutableStateOf(false) }
    var initialCloudJoin by remember { mutableStateOf(false) }
    var startupErrorType by remember { mutableStateOf<String?>(null) }
    var startupErrorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            container.seedIfNeeded()
            BackupRestoreManager.recordAppliedRestoreAudit(context, container.db)
            needsInitialAdmin = container.db.userDao().count() == 0
            seeded = true
        } catch (t: Throwable) {
            startupErrorType = t::class.simpleName
            startupErrorMessage = t.message
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            startupErrorType != null || startupErrorMessage != null -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    val errorType = startupErrorType ?: stringResource(R.string.startup_error_class)
                    val errorMessage = startupErrorMessage ?: stringResource(R.string.startup_no_details)
                    FushSystemState(
                        title = stringResource(R.string.startup_database_error_title),
                        detail = stringResource(R.string.startup_database_error_detail, "$errorType: $errorMessage"),
                        modifier = Modifier.fillMaxSize(),
                        isError = true,
                    )
                }
            }
            !seeded -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    FushSystemState(
                        title = stringResource(R.string.startup_initializing_title),
                        detail = stringResource(R.string.startup_initializing_detail),
                        modifier = Modifier.fillMaxSize(),
                        loading = true,
                    )
                }
            }
            needsInitialAdmin && initialCloudJoin -> {
                InitialCloudJoinScreen(
                    container = container,
                    onCreated = { created ->
                        user = created
                        needsInitialAdmin = false
                        initialCloudJoin = false
                    },
                    onBack = { initialCloudJoin = false },
                )
            }
            needsInitialAdmin -> {
                InitialAdminSetupScreen(
                    container = container,
                    onCreated = { created ->
                        user = created
                        needsInitialAdmin = false
                    },
                    onJoinExisting = { initialCloudJoin = true },
                )
            }
            user == null -> {
                LoginScreen(
                    container = container,
                    onLogin = { user = it }
                )
            }
            user!!.mustChangePassword -> {
                ChangePasswordScreen(
                    container = container,
                    user = user!!,
                    forced = true,
                    onChanged = { user = it },
                    onLogout = {
                        val current = user
                        if (current == null) user = null else scope.launch {
                            runCatching { container.securityService.recordLogout(current.id) }
                            user = null
                        }
                    }
                )
            }
            else -> HomeShell(
                container = container,
                user = user!!,
                darkTheme = darkTheme,
                onToggleTheme = onToggleTheme,
                languageTag = languageTag,
                onLanguageChange = onLanguageChange,
                onLogout = {
                    val current = user
                    if (current == null) user = null else scope.launch {
                        runCatching { container.securityService.recordLogout(current.id) }
                        user = null
                    }
                },
            )
        }
    }
}
