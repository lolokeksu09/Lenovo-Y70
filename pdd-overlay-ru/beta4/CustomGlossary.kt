package app.pinduoduo.overlayru

import android.content.Context
import org.json.JSONObject

/**
 * Local, user-owned corrections, never transmitted to a server.
 * This store is separate from app preferences and PRO/license entitlements.
 * A correction takes precedence over built-in UI glossary and ML Kit cache.
 */
internal object CustomGlossary {
    private const val STORE = "local_user_glossary"
    private const val KEY = "corrections_v1"
    private var loaded: JSONObject? = null

    @Synchronized
    private fun read(context: Context): JSONObject {
        val prior = loaded
        if (prior != null) return prior
        val raw = context.applicationContext.getSharedPreferences(
            STORE, Context.MODE_PRIVATE
        ).getString(KEY, "{}") ?: "{}"
        val parsed = try { JSONObject(raw) } catch (_: Exception) { JSONObject() }
        loaded = parsed
        return parsed
    }

    @Synchronized
    fun lookup(context: Context, source: String): String? {
        if (!CustomGlossaryPolicy.canOverride(source)) return null
        return read(context).optString(CustomGlossaryPolicy.normalize(source), "")
            .takeIf { it.isNotBlank() }
    }

    @Synchronized
    fun entries(context: Context): List<Pair<String, String>> {
        val data = read(context)
        return data.keys().asSequence()
            .map { key -> key to data.optString(key, "") }
            .filter { it.second.isNotBlank() }
            .sortedBy { it.first }
            .toList()
    }

    @Synchronized
    fun put(context: Context, source: String, translation: String): Boolean {
        if (!CustomGlossaryPolicy.valid(source, translation)) return false
        val key = CustomGlossaryPolicy.normalize(source)
        val current = read(context)
        if (!current.has(key) && current.length() >= CustomGlossaryPolicy.MAX_ENTRIES) return false
        val updated = JSONObject(current.toString()).put(
            key, CustomGlossaryPolicy.normalize(translation)
        )
        val ok = context.applicationContext
            .getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .edit().putString(KEY, updated.toString()).commit()
        if (ok) {
            loaded = updated
            PddAccessibilityService.requestRedraw()
        }
        return ok
    }

    @Synchronized
    fun delete(context: Context, source: String): Boolean {
        val current = read(context)
        val key = CustomGlossaryPolicy.normalize(source)
        if (!current.has(key)) return false
        val updated = JSONObject(current.toString())
        updated.remove(key)
        val ok = context.applicationContext
            .getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .edit().putString(KEY, updated.toString()).commit()
        if (ok) {
            loaded = updated
            PddAccessibilityService.requestRedraw()
        }
        return ok
    }
}
