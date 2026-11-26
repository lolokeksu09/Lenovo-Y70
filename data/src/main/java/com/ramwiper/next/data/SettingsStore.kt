package com.ramwiper.next.data

import android.content.SharedPreferences
import com.ramwiper.next.domain.*

class SettingsStore(private val prefs: SharedPreferences) : SettingsRepository {
    override fun load(): UserSettings = UserSettings(
        engine = runCatching { Engine.valueOf(prefs.getString("engine", "HYBRID")!!) }.getOrDefault(Engine.HYBRID),
        profile = runCatching { Profile.valueOf(prefs.getString("profile", "BALANCED")!!) }.getOrDefault(Profile.BALANCED),
        protected = prefs.getStringSet("protected", emptySet())?.toSet().orEmpty()
    )
    override fun save(settings: UserSettings) {
        check(prefs.edit().putString("engine", settings.engine.name)
            .putString("profile", settings.profile.name)
            .putStringSet("protected", settings.protected.toSet()).commit()) { "Не удалось сохранить настройки" }
    }
}
