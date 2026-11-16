package app.pinduoduo.overlayru

/** Late ML Kit results may redraw only while their source is still visible. */
object OverlayUpdatePolicy {
    @JvmStatic
    fun shouldRedraw(
        targetForeground: Boolean, enabled: Boolean,
        sourceStillVisible: Boolean, validTranslation: Boolean
    ): Boolean = targetForeground && enabled && sourceStillVisible && validTranslation
}
