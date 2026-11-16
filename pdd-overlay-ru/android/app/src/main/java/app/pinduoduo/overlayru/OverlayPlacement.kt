package app.pinduoduo.overlayru

import kotlin.math.ceil

/** Source-anchored caption geometry. Never moves a caption into an adjacent row. */
object OverlayPlacement {
    /** Returns left, top, width, height, or null if the source is not visible. */
    @JvmStatic
    fun fit(
        x: Int, y: Int, sourceWidth: Int, sourceHeight: Int,
        desiredWidth: Int, viewportWidth: Int, viewportHeight: Int, edgeMargin: Int
    ): IntArray? {
        if (sourceWidth <= 0 || sourceHeight <= 0 || viewportWidth <= 0 ||
            viewportHeight <= 0 || x >= viewportWidth || y >= viewportHeight ||
            x + sourceWidth <= 0 || y + sourceHeight <= 0) return null

        val left = maxOf(0, x)
        val top = maxOf(0, y)
        val onLeft = x < viewportWidth * 0.70f
        val right = if (onLeft) minOf(viewportWidth - edgeMargin, (viewportWidth * 0.83f).toInt())
                    else viewportWidth - edgeMargin
        val allowedWidth = maxOf(0, right - left)
        val expandedWidth = ceil((sourceWidth * (if (onLeft) 1.55f else 1.10f)).toDouble()).toInt()
        val width = minOf(allowedWidth,
            maxOf(sourceWidth, minOf(maxOf(sourceWidth, desiredWidth), expandedWidth)))
        val height = minOf(sourceHeight, viewportHeight - top)
        if (width <= 0 || height <= 0) return null
        return intArrayOf(left, top, width, height)
    }

    /** Multiline review captions stay within their original text rectangle. */
    @JvmStatic
    fun fitParagraph(
        x: Int, y: Int, sourceWidth: Int, sourceHeight: Int,
        viewportWidth: Int, viewportHeight: Int
    ): IntArray? {
        if (sourceWidth <= 0 || sourceHeight <= 0 ||
            viewportWidth <= 0 || viewportHeight <= 0) return null
        val left = x.coerceIn(0, viewportWidth)
        val top = y.coerceIn(0, viewportHeight)
        val right = (x.toLong() + sourceWidth).coerceIn(0L, viewportWidth.toLong()).toInt()
        val bottom = (y.toLong() + sourceHeight).coerceIn(0L, viewportHeight.toLong()).toInt()
        if (right - left < 120 || bottom - top < 50) return null
        return intArrayOf(left, top, right - left, bottom - top)
    }
}
