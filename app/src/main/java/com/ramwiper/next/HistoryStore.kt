package com.ramwiper.next
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
class HistoryStore(private val prefs: SharedPreferences) {
    fun save(history: List<Result>) {
        val array = JSONArray(); history.forEach { r -> array.put(JSONObject().apply {
            put("time", r.time); put("engine", r.engine); put("profile", r.profile); put("before", r.before); put("after", r.after)
            put("allocated", r.allocated); put("accepted", r.accepted); put("attempted", r.attempted); put("cancelled", r.cancelled); put("note", r.note)
        }) }; prefs.edit().putString("history", array.toString()).apply()
    }
    fun load(): List<Result> = runCatching {
        val a = JSONArray(prefs.getString("history", "[]")); (0 until a.length()).map { i -> a.getJSONObject(i).let { r ->
            Result(r.getLong("time"), r.getString("engine"), r.getString("profile"), r.getLong("before"), r.getLong("after"), r.getLong("allocated"), r.getInt("accepted"), r.getInt("attempted"), r.getBoolean("cancelled"), r.getString("note"))
        } }
    }.getOrDefault(emptyList())
}
