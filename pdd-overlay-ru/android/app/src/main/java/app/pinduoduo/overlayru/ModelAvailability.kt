package app.pinduoduo.overlayru

/** Chinese/English → Russian offline translation requires both language models. */
object ModelAvailability {
    @JvmStatic
    fun usable(sourceDownloaded: Boolean, russianDownloaded: Boolean): Boolean =
        sourceDownloaded && russianDownloaded

    /**
     * A successful fresh scan must clear a stale 'ready' state if a model was
     * removed. A failed/timed-out scan cannot reliably invalidate prior state.
     */
    fun afterInspection(
        previouslyReady: Boolean,
        sourceDownloaded: Boolean,
        russianDownloaded: Boolean,
        scanSucceeded: Boolean
    ): Boolean =
        if (scanSucceeded) usable(sourceDownloaded, russianDownloaded) else previouslyReady
}
