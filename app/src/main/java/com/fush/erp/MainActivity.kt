package com.fush.erp

import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.fush.erp.ui.FushErpApp
import com.fush.erp.ui.FushSystemState
import com.fush.erp.ui.FushTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = application as FushErpApplication
            val systemDarkTheme = isSystemInDarkTheme()
            val uiPreferences = remember { getSharedPreferences("fush_ui_preferences", MODE_PRIVATE) }
            var darkTheme by remember {
                mutableStateOf(uiPreferences.getBoolean("dark_theme", systemDarkTheme))
            }
            var languageTag by remember {
                val stored = uiPreferences.getString("language_tag", null)
                val systemLanguage = resources.configuration.locales[0]?.language
                mutableStateOf(stored ?: if (systemLanguage == "en") "en" else "ar")
            }

            val localizedConfiguration = remember(languageTag) {
                Configuration(resources.configuration).apply {
                    val locale = Locale.forLanguageTag(languageTag)
                    setLocale(locale)
                    setLayoutDirection(locale)
                }
            }
            val localizedContext = remember(languageTag) {
                val localizedBase = createConfigurationContext(localizedConfiguration)
                object : ContextWrapper(this@MainActivity) {
                    override fun getResources(): Resources = localizedBase.resources
                }
            }
            val layoutDirection = if (languageTag == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr

            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides this@MainActivity,
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedConfiguration,
                LocalLayoutDirection provides layoutDirection,
            ) {
                FushTheme(darkTheme = darkTheme) {
                    val appContainer = app.container
                    if (appContainer == null) {
                        val failure = app.startupFailure
                        val errorType = failure?.javaClass?.simpleName ?: stringResource(R.string.startup_error_class)
                        val errorMessage = failure?.message ?: stringResource(R.string.startup_no_details)
                        Box(
                            modifier = androidx.compose.ui.Modifier.fillMaxSize().padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            FushSystemState(
                                title = stringResource(R.string.startup_database_error_title),
                                detail = stringResource(R.string.startup_database_error_detail, "$errorType: $errorMessage"),
                                modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                                isError = true,
                            )
                        }
                    } else {
                        FushErpApp(
                            container = appContainer,
                            darkTheme = darkTheme,
                            onToggleTheme = {
                                darkTheme = !darkTheme
                                uiPreferences.edit().putBoolean("dark_theme", darkTheme).apply()
                            },
                            languageTag = languageTag,
                            onLanguageChange = { requestedTag ->
                                val normalized = if (requestedTag == "en") "en" else "ar"
                                if (normalized != languageTag) {
                                    languageTag = normalized
                                    uiPreferences.edit().putString("language_tag", normalized).apply()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
