package app.pinduoduo.overlayru

/**
 * Pure edit guard, exercised by JVM tests. New source keys must not replace
 * an unrelated correction; the storage layer commits remove+put atomically.
 */
internal object CustomGlossaryEditPolicy {
    fun canReplace(
        oldSource: String,
        source: String,
        translation: String,
        existing: Collection<String>
    ): Boolean {
        val oldKey = CustomGlossaryPolicy.normalize(oldSource)
        val newKey = CustomGlossaryPolicy.normalize(source)
        return oldKey.isNotBlank() &&
            CustomGlossaryPolicy.valid(source, translation) &&
            existing.contains(oldKey) &&
            (oldKey == newKey || !existing.contains(newKey))
    }
}
