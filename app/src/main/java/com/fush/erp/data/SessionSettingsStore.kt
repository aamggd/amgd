package com.fush.erp.data

import android.content.Context
import com.fush.erp.domain.SessionPolicy
import com.fush.erp.domain.SessionTimeoutSettings

class SessionSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun current(): SessionTimeoutSettings {
        migrateLegacyDefaultsIfNeeded()
        return SessionPolicy.normalize(
            SessionTimeoutSettings(
                automaticLogoutEnabled = true,
                idleTimeoutMinutes = prefs.getLong(KEY_IDLE_TIMEOUT_MINUTES, SessionPolicy.DEFAULT_IDLE_MINUTES),
                maxSessionMinutes = prefs.getLong(KEY_MAX_SESSION_MINUTES, SessionPolicy.DEFAULT_ABSOLUTE_MINUTES)
            )
        )
    }

    fun save(settings: SessionTimeoutSettings) {
        val safe = SessionPolicy.normalize(settings)
        prefs.edit()
            .putInt(KEY_SETTINGS_VERSION, SETTINGS_VERSION)
            .putBoolean(KEY_AUTOMATIC_LOGOUT_ENABLED, true)
            .putLong(KEY_IDLE_TIMEOUT_MINUTES, safe.idleTimeoutMinutes)
            .putLong(KEY_MAX_SESSION_MINUTES, safe.maxSessionMinutes)
            .apply()
    }

    private fun migrateLegacyDefaultsIfNeeded() {
        if (prefs.getInt(KEY_SETTINGS_VERSION, 1) >= SETTINGS_VERSION) return
        val oldIdle = prefs.getLong(KEY_IDLE_TIMEOUT_MINUTES, 5L)
        val oldAbsolute = prefs.getLong(KEY_MAX_SESSION_MINUTES, 480L)
        val editor = prefs.edit().putInt(KEY_SETTINGS_VERSION, SETTINGS_VERSION)
        if ((!prefs.contains(KEY_IDLE_TIMEOUT_MINUTES) && !prefs.contains(KEY_MAX_SESSION_MINUTES)) ||
            (oldIdle == 5L && oldAbsolute == 480L)) {
            editor
                .putLong(KEY_IDLE_TIMEOUT_MINUTES, SessionPolicy.DEFAULT_SESSION_MINUTES)
                .putLong(KEY_MAX_SESSION_MINUTES, SessionPolicy.DEFAULT_SESSION_MINUTES)
        }
        editor.apply()
    }

    private companion object {
        const val PREFS_NAME = "fush_security_session_settings"
        const val SETTINGS_VERSION = 2
        const val KEY_SETTINGS_VERSION = "settings_version"
        const val KEY_AUTOMATIC_LOGOUT_ENABLED = "automatic_logout_enabled"
        const val KEY_IDLE_TIMEOUT_MINUTES = "idle_timeout_minutes"
        const val KEY_MAX_SESSION_MINUTES = "max_session_minutes"
    }
}
