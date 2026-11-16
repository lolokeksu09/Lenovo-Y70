package app.pinduoduo.overlayru

/** Exact ComponentName match, including Android's abbreviated service-class format. */
object AccessibilityServiceIds {
    @JvmStatic
    fun contains(enabled: String?, packageName: String, serviceClassName: String): Boolean {
        if (enabled.isNullOrEmpty()) return false
        for (entry in enabled.split(':')) {
            val parts = entry.trim().split('/')
            if (parts.size != 2 || parts[0] != packageName) continue
            val actualClass =
                if (parts[1].startsWith('.')) packageName + parts[1] else parts[1]
            if (actualClass == serviceClassName) return true
        }
        return false
    }
}
