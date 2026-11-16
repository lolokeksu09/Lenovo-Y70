package app.pinduoduo.overlayru

/** Keep readiness separate for Chinese and English; detection alone is not translation. */
internal object OcrModelReadiness {
    fun allRequiredModelsReady(
        sources: List<String>, chineseReady: Boolean, englishReady: Boolean
    ): Boolean {
        val languages = sources.filter { OcrTextPolicy.canTranslate(it) }
            .map { TranslationPolicy.sourceLanguage(it) }.distinct()
        return languages.isNotEmpty() && languages.all {
            when (it) {
                TranslationPolicy.CHINESE -> chineseReady
                TranslationPolicy.ENGLISH -> englishReady
                else -> false
            }
        }
    }
}
