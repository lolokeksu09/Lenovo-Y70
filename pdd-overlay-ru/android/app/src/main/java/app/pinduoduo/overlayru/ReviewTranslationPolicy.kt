package app.pinduoduo.overlayru

/**
 * Bound untrusted long review text before local English→Russian ML Kit translation.
 * Never transmit text; each chunk must satisfy the ordinary payment/OTP/privacy guard.
 */
internal object ReviewTranslationPolicy {
    private const val MIN_REVIEW = 181
    private const val MAX_REVIEW = 1200
    private const val CHUNK_TARGET = 155
    private const val MAX_CHUNKS = 10
    private val whitespace = Regex("\\s+")

    fun chunks(text: String?): List<String>? {
        if (text == null) return null
        val normalized = text.trim().replace(whitespace, " ")
        if (normalized.length !in MIN_REVIEW..MAX_REVIEW ||
            TranslationPolicy.sourceLanguage(normalized) != TranslationPolicy.ENGLISH ||
            PaymentActionPolicy.isProtected(normalized)) return null
        if (normalized.any { it in "¥￥₽$" } ||
            normalized.contains("://") || normalized.contains("www.") ||
            normalized.contains('@') ||
            Regex("\\b[0-9]{4,8}\\b").containsMatchIn(normalized)) return null

        val output = ArrayList<String>(MAX_CHUNKS)
        var current = ""
        for (word in normalized.split(' ')) {
            if (word.length > CHUNK_TARGET) return null
            if (current.isEmpty()) {
                current = word
            } else if (current.length + word.length + 1 <= CHUNK_TARGET) {
                current += " " + word
            } else {
                output.add(current)
                current = word
                if (output.size >= MAX_CHUNKS) return null
            }
        }
        if (current.isNotEmpty()) output.add(current)
        // Keep one-word trailing fragments attached when possible.
        if (output.size > 1 && output.last().count { it == ' ' } == 0) {
            val last = output.removeAt(output.lastIndex)
            val previous = output.removeAt(output.lastIndex)
            if (previous.length + last.length + 1 > 180) return null
            output.add(previous + " " + last)
        }
        if (output.size < 2 || output.size > MAX_CHUNKS ||
            output.any { !TranslationPolicy.shouldTranslate(it, false, false) }) return null
        return output
    }
}
