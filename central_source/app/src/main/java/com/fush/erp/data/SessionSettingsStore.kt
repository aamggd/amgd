package com.fush.erp.data

import android.content.Context
import com.fush.erp.domain.SessionPolicy
import com.fush.erp.domain.SessionTimeoutSettings

class SessionSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun current(): SessionTimeoutSettings {
        migrateLegacyForcedAutomaticLogout()
        return SessionPolicy.normalize(
            SessionTimeoutSettings(
                automaticLogoutEnabled = prefs.getBoolean(
                    KEY_AUTOMATIC_LOGOUT_ENABLED,
                    SessionPolicy.DEFAULT_AUTOMATIC_LOGOUT_ENABLED
                ),
                idleTimeoutMinutes = prefs.getLong(KEY_IDLE_TIMEOUT_MINUTES, SessionPolicy.DEFAULT_IDLE_MINUTES),
                maxSessionMinutes = prefs.getLong(KEY_MAX_SESSION_MINUTES, SessionPolicy.DEFAULT_ABSOLUTE_MINUTES)
            )
        )
    }

    fun save(settings: SessionTimeoutSettings) {
        val safe = SessionPolicy.normalize(settings)
        prefs.edit()
            .putBoolean(KEY_AUTOMATIC_LOGOUT_ENABLED, safe.automaticLogoutEnabled)
            .putLong(KEY_IDLE_TIMEOUT_MINUTES, safe.idleTimeoutMinutes)
            .putLong(KEY_MAX_SESSION_MINUTES, safe.maxSessionMinutes)
            .putInt(KEY_SETTINGS_VERSION, CURRENT_SETTINGS_VERSION)
            .apply()
    }

    private fun migrateLegacyForcedAutomaticLogout() {
        val version = prefs.getInt(KEY_SETTINGS_VERSION, 0)
        if (version >= CURRENT_SETTINGS_VERSION) return

        prefs.edit()
            .putBoolean(KEY_AUTOMATIC_LOGOUT_ENABLED, SessionPolicy.DEFAULT_AUTOMATIC_LOGOUT_ENABLED)
            .putInt(KEY_SETTINGS_VERSION, CURRENT_SETTINGS_VERSION)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "fush_security_session_settings"
        const val KEY_AUTOMATIC_LOGOUT_ENABLED = "automatic_logout_enabled"
        const val KEY_IDLE_TIMEOUT_MINUTES = "idle_timeout_minutes"
        const val KEY_MAX_SESSION_MINUTES = "max_session_minutes"
        const val KEY_SETTINGS_VERSION = "session_settings_version"
        const val CURRENT_SETTINGS_VERSION = 1
    }
}
