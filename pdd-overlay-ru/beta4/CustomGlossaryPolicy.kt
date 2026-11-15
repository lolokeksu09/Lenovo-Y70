package app.pinduoduo.overlayru

/** Validation shared by the editor and the overlay; no payment/OTP override. */
internal object CustomGlossaryPolicy {
    const val MAX_ENTRIES = 200
    const val MAX_SOURCE_LENGTH = 180
    const val MAX_TRANSLATION_LENGTH = 200

    fun normalize(value: String): String =
        value.trim().replace(Regex("\\s+"), " ")

    fun canOverride(source: String): Boolean {
        val key = normalize(source)
        return key.isNotEmpty() && key.length <= MAX_SOURCE_LENGTH &&
            TranslationPolicy.shouldTranslate(key, false, false) &&
            !PaymentActionPolicy.isProtected(key)
    }

    fun valid(source: String, translation: String): Boolean {
        val value = normalize(translation)
        return canOverride(source) && value.isNotEmpty() &&
            value.length <= MAX_TRANSLATION_LENGTH &&
            value.any { it.isLetter() } &&
            !value.any { it.isISOControl() }
    }
}
