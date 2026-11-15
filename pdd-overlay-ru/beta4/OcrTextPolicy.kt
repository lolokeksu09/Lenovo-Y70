package app.pinduoduo.overlayru

/** Pure, bounded policy for interpreting text extracted from a user-chosen image. */
internal object OcrTextPolicy {
    const val MAX_LINES = 60

    fun cleaned(lines: Iterable<String>): List<String> =
        lines.asSequence().map { it.trim() }.filter { it.isNotBlank() }
            .distinct().take(MAX_LINES).toList()

    fun canTranslate(line: String): Boolean =
        TranslationPolicy.shouldTranslate(line, false, false) &&
            !PaymentActionPolicy.isProtected(line)
}
