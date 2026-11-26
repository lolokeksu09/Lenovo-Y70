package com.ramwiper.next.data

import android.content.SharedPreferences
import com.ramwiper.next.domain.CleanupResult
import com.ramwiper.next.domain.HistoryRepository
import org.json.JSONException

class HistoryStore(private val prefs: SharedPreferences) : HistoryRepository {
    override fun save(history: List<CleanupResult>) {
        check(prefs.edit().putString("history", HistoryCodec.encode(history)).commit()) { "Не удалось сохранить журнал" }
    }
    override fun load(): List<CleanupResult> = try {
        HistoryCodec.decode(prefs.getString("history", "[]") ?: "[]")
    } catch (e: JSONException) {
        throw IllegalStateException("Не удалось прочитать журнал. Исходные данные не изменены.", e)
    }
}
