package app.pinduoduo.overlayru

/** OCR detection is not the same as successful Russian translation. */
internal object OcrViewerPresentation {
    fun translatedCount(rows: List<Pair<String, String?>>): Int =
        rows.count { (source, translation) ->
            !translation.isNullOrBlank() && translation.trim() != source.trim()
        }

    fun openViewerLabel(rows: List<Pair<String, String?>>): String =
        if (translatedCount(rows) > 0) "Показать перевод поверх фото"
        else "Посмотреть распознанные области"

    fun viewerSummary(rows: List<Pair<String, String?>>): String {
        val translated = translatedCount(rows)
        return if (translated == 0)
            "Распознано ${rows.size} строк. Русский перевод пока недоступен."
        else "Переведено $translated из ${rows.size} строк."
    }

    fun showRegionOutline(
        active: Boolean, translatedEligible: Boolean, showRussian: Boolean
    ): Boolean = active || (showRussian && translatedEligible)
}
