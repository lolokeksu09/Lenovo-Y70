package app.pinduoduo.overlayru

/** Chinese/English → Russian offline translation requires both language models. */
object ModelAvailability {
    @JvmStatic
    fun usable(sourceDownloaded: Boolean, russianDownloaded: Boolean): Boolean =
        sourceDownloaded && russianDownloaded
}
