package app.pinduoduo.overlayru

/** Screen coordinates of Accessibility nodes to overlay-local pixels. */
object OverlayCoordinates {
    @JvmStatic
    fun toLocalBounds(
        left: Int, top: Int, right: Int, bottom: Int,
        overlayScreenX: Int, overlayScreenY: Int
    ): IntArray = intArrayOf(
        left - overlayScreenX, top - overlayScreenY,
        maxOf(0, right - left), maxOf(0, bottom - top)
    )
}
