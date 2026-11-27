package com.ramwiper.next.data

import android.content.SharedPreferences
import com.ramwiper.next.domain.*

class SettingsStore(private val prefs: SharedPreferences) : SettingsRepository {
    override fun load(): UserSettings = UserSettings(
        engine = runCatching { Engine.valueOf(prefs.getString("engine", "HYBRID")!!) }.getOrDefault(Engine.HYBRID),
        profile = runCatching { Profile.valueOf(prefs.getString("profile", "BALANCED")!!) }.getOrDefault(Profile.BALANCED),
        protected = prefs.getStringSet("protected", emptySet())?.toSet().orEmpty(),
        automation = AutomationSettings(prefs.getBoolean("auto_foreground", false),
            prefs.getInt("auto_threshold", 20), prefs.getInt("auto_interval", 5),
            prefs.getBoolean("background_alerts", false)).normalized(),
        nativeLimits = NativeLimits(prefs.getInt("native_cap_mib", 0), prefs.getInt("native_hold_ms", -1)).normalized()
    )
    override fun save(settings: UserSettings) {
        check(prefs.edit().putString("engine", settings.engine.name)
            .putString("profile", settings.profile.name)
            .putStringSet("protected", settings.protected.toSet())
            .putBoolean("auto_foreground", settings.automation.foregroundEnabled)
            .putInt("auto_threshold", settings.automation.normalized().thresholdPercent)
            .putInt("auto_interval", settings.automation.normalized().intervalMinutes)
            .putBoolean("background_alerts", settings.automation.backgroundAlerts)
            .putInt("native_cap_mib", settings.nativeLimits.normalized().capMiB)
            .putInt("native_hold_ms", settings.nativeLimits.normalized().holdMs).commit()) { "Не удалось сохранить настройки" }
    }
}
