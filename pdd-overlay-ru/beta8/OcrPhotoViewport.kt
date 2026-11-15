package app.pinduoduo.overlayru

import kotlin.math.max
import kotlin.math.min

/**
 * Pure transform for the fullscreen OCR photo. The same scale/offset is
 * applied to the image and annotation canvas. Pointer coordinates are mapped
 * through its inverse before hit-testing any recognized text boxes.
 *
 * Coordinates and offsets are in raw Compose canvas pixels (not dp).
 */
internal data class OcrPhotoViewport(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
) {
    companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 5f
    }

    fun toImageCanvas(
        screenX: Float,
        screenY: Float,
        canvasWidth: Float,
        canvasHeight: Float
    ): Pair<Float, Float> =
        (screenX - canvasWidth / 2f - offsetX) / scale + canvasWidth / 2f to
            (screenY - canvasHeight / 2f - offsetY) / scale + canvasHeight / 2f

    fun applyGesture(
        zoomChange: Float,
        panX: Float,
        panY: Float,
        centroidX: Float,
        centroidY: Float,
        canvasWidth: Float,
        canvasHeight: Float,
        bitmapWidth: Float,
        bitmapHeight: Float
    ): OcrPhotoViewport {
        val values = floatArrayOf(
            zoomChange, panX, panY, centroidX, centroidY,
            canvasWidth, canvasHeight, bitmapWidth, bitmapHeight,
            scale, offsetX, offsetY
        )
        if (values.any { !it.isFinite() } ||
            canvasWidth <= 0f || canvasHeight <= 0f ||
            bitmapWidth <= 0f || bitmapHeight <= 0f ||
            scale <= 0f || zoomChange <= 0f
        ) return this

        val zoom = (scale * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (zoom == MIN_ZOOM) return OcrPhotoViewport()

        // Keep the bitmap coordinate under the user's fingers stationary.
        val pivotX = centroidX - canvasWidth / 2f
        val pivotY = centroidY - canvasHeight / 2f
        val proposedX = pivotX - (pivotX - offsetX) / scale * zoom + panX
        val proposedY = pivotY - (pivotY - offsetY) / scale * zoom + panY

        // Limit movement to actual image content: tall screenshots should not
        // drift sideways into black letterboxing when they are still narrow.
        val fit = min(canvasWidth / bitmapWidth, canvasHeight / bitmapHeight)
        val contentWidth = bitmapWidth * fit * zoom
        val contentHeight = bitmapHeight * fit * zoom
        val xBound = max(0f, (contentWidth - canvasWidth) / 2f)
        val yBound = max(0f, (contentHeight - canvasHeight) / 2f)
        return OcrPhotoViewport(
            scale = zoom,
            offsetX = proposedX.coerceIn(-xBound, xBound),
            offsetY = proposedY.coerceIn(-yBound, yBound)
        )
    }
}
