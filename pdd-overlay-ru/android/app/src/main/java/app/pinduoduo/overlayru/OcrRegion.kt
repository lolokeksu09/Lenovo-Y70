package app.pinduoduo.overlayru

import kotlin.math.min

/** Positions of Chinese OCR lines in [0, 1] bitmap coordinates. */
internal data class OcrRegion(
    val left: Float, val top: Float, val right: Float, val bottom: Float
) {
    data class Px(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        fun contains(x: Float, y: Float): Boolean =
            x >= left && x <= right && y >= top && y <= bottom
    }

    companion object {
        fun fromPixels(
            left: Int, top: Int, right: Int, bottom: Int,
            imageWidth: Int, imageHeight: Int
        ): OcrRegion? {
            if (imageWidth <= 0 || imageHeight <= 0) return null
            val l = left.coerceIn(0, imageWidth)
            val t = top.coerceIn(0, imageHeight)
            val r = right.coerceIn(0, imageWidth)
            val b = bottom.coerceIn(0, imageHeight)
            if (r <= l || b <= t) return null
            return OcrRegion(
                l.toFloat() / imageWidth, t.toFloat() / imageHeight,
                r.toFloat() / imageWidth, b.toFloat() / imageHeight
            )
        }
    }

    /**
     * Fit-center is how Image(... ContentScale.Fit) draws the bitmap.
     * Rectangles use the same letterboxing offsets, even for tall screenshots.
     */
    fun intoCanvas(
        canvasWidth: Float, canvasHeight: Float,
        imageWidth: Float, imageHeight: Float
    ): Px? {
        if (canvasWidth <= 0f || canvasHeight <= 0f ||
            imageWidth <= 0f || imageHeight <= 0f) return null
        val scale = min(canvasWidth / imageWidth, canvasHeight / imageHeight)
        val fittedWidth = imageWidth * scale
        val fittedHeight = imageHeight * scale
        val ox = (canvasWidth - fittedWidth) / 2f
        val oy = (canvasHeight - fittedHeight) / 2f
        return Px(
            ox + left * fittedWidth,
            oy + top * fittedHeight,
            ox + right * fittedWidth,
            oy + bottom * fittedHeight
        )
    }
}
