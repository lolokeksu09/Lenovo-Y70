package com.ramwiper.next.data
import com.ramwiper.next.domain.*
import org.json.JSONArray
import org.json.JSONObject

internal object PresetCodec {
    fun encode(presets: List<SelectionPreset>): String = JSONArray().apply {
        presets.take(SelectionPresets.LIMIT).forEach { p ->
            put(JSONObject().put("name",p.name).put("packages",JSONArray(p.packages.take(SelectionPolicy.LIMIT))))
        }
    }.toString()
    fun decode(raw: String): List<SelectionPreset> {
        val array = JSONArray(raw)
        val result = mutableListOf<SelectionPreset>()
        for(i in 0 until minOf(array.length(),SelectionPresets.LIMIT)) {
            val p = array.optJSONObject(i) ?: continue
            val name = p.optString("name").trim()
            if(name.isEmpty() || name.length > SelectionPresets.NAME_LIMIT || name.any { it.isISOControl() } ||
                result.any { it.name.equals(name,true) }) continue
            val packages = p.optJSONArray("packages") ?: continue
            val names = (0 until minOf(packages.length(),SelectionPolicy.LIMIT)).mapNotNull { j ->
                (packages.opt(j) as? String)?.takeIf { it.length in 1..255 && it.isNotBlank() }
            }.toSet()
            if(names.isNotEmpty()) result += SelectionPreset(name,names)
        }
        return result
    }
}
