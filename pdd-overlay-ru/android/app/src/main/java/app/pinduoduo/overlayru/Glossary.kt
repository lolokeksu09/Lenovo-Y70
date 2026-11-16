package app.pinduoduo.overlayru

import android.content.Context
import org.json.JSONObject

/** Cached translation dictionary loaded only from the APK assets. */
object Glossary {
    private var entries: JSONObject? = null

    @JvmStatic
    @Synchronized
    fun lookup(context: Context, text: String): String? {
        var dictionary = entries
        if (dictionary == null) {
            dictionary = try {
                context.assets.open("glossary_zh_ru.json").bufferedReader(Charsets.UTF_8).use {
                    JSONObject(it.readText())
                }
            } catch (_: Exception) {
                JSONObject()
            }
            entries = dictionary
        }
        return dictionary.optString(text.trim(), null)
    }
}
