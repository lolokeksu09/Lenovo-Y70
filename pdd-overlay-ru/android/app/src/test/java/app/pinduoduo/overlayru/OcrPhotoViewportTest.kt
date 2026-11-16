package app.pinduoduo.overlayru

import org.junit.Assert.*
import org.junit.Test

class OcrPhotoViewportTest {
    private val width = 300f
    private val height = 400f

    @Test fun initialTransformMapsTouchesWithoutDistortion() {
        val view = OcrPhotoViewport()
        assertEquals(125f to 210f, view.toImageCanvas(125f, 210f, width, height))
    }

    @Test fun pinchZoomClampedBetweenOneAndFive() {
        val view = OcrPhotoViewport()
        val high = view.applyGesture(
            zoomChange = 60f, panX = 0f, panY = 0f,
            centroidX = 150f, centroidY = 200f,
            canvasWidth = width, canvasHeight = height,
            bitmapWidth = 300f, bitmapHeight = 400f
        )
        assertEquals(5f, high.scale, 0.001f)
        val low = high.applyGesture(
            zoomChange = 0.001f, panX = 0f, panY = 0f,
            centroidX = 150f, centroidY = 200f,
            canvasWidth = width, canvasHeight = height,
            bitmapWidth = 300f, bitmapHeight = 400f
        )
        assertEquals(OcrPhotoViewport(), low)
    }

    @Test fun pinchAroundFingerKeepsImagePointUnderFinger() {
        val x = 230f
        val y = 310f
        val after = OcrPhotoViewport().applyGesture(
            zoomChange = 2f, panX = 0f, panY = 0f,
            centroidX = x, centroidY = y,
            canvasWidth = width, canvasHeight = height,
            bitmapWidth = 300f, bitmapHeight = 400f
        )
        val mapped = after.toImageCanvas(x, y, width, height)
        assertEquals(x, mapped.first, 0.0001f)
        assertEquals(y, mapped.second, 0.0001f)
    }

    @Test fun panningIsClampedAndNeverShowsBlankScreen() {
        val initial = OcrPhotoViewport(scale = 3f)
        val next = initial.applyGesture(
            zoomChange = 1f, panX = 10000f, panY = -10000f,
            centroidX = 150f, centroidY = 200f,
            canvasWidth = width, canvasHeight = height,
            bitmapWidth = 300f, bitmapHeight = 400f
        )
        assertEquals(300f, next.offsetX, 0.001f)
        assertEquals(-400f, next.offsetY, 0.001f)
    }

    @Test fun tallScreenshotCannotPanSidewaysUntilWideEnough() {
        val view = OcrPhotoViewport(scale = 4f).applyGesture(
            zoomChange = 1f, panX = 200f, panY = 300f,
            centroidX = 150f, centroidY = 200f,
            canvasWidth = width, canvasHeight = height,
            bitmapWidth = 100f, bitmapHeight = 1000f
        )
        assertEquals(0f, view.offsetX, 0.001f)
        assertEquals(300f, view.offsetY, 0.001f)
    }

    @Test fun transformedTapMatchesOriginalRegionCoordinates() {
        val box = OcrRegion.fromPixels(60, 120, 160, 200, 300, 400)!!
        val view = OcrPhotoViewport().applyGesture(
            zoomChange = 2f, panX = 25f, panY = -30f,
            centroidX = 150f, centroidY = 200f,
            canvasWidth = width, canvasHeight = height,
            bitmapWidth = 300f, bitmapHeight = 400f
        )
        val centerX = 110f
        val centerY = 160f
        val onScreenX = width / 2 + (centerX - width / 2) * view.scale + view.offsetX
        val onScreenY = height / 2 + (centerY - height / 2) * view.scale + view.offsetY
        val point = view.toImageCanvas(onScreenX, onScreenY, width, height)
        assertTrue(box.intoCanvas(width, height, 300f, 400f)!!.contains(point.first, point.second))
    }

    @Test fun noPanOnBaseZoomOrMissingDimensions() {
        val view = OcrPhotoViewport().applyGesture(
            zoomChange = 1f, panX = 200f, panY = -100f,
            centroidX = 0f, centroidY = 0f,
            canvasWidth = width, canvasHeight = height,
            bitmapWidth = 300f, bitmapHeight = 400f
        )
        assertEquals(OcrPhotoViewport(), view)
        val broken = OcrPhotoViewport().applyGesture(
            zoomChange = 3f, panX = 200f, panY = 200f,
            centroidX = 10f, centroidY = 10f,
            canvasWidth = 0f, canvasHeight = height,
            bitmapWidth = 300f, bitmapHeight = 400f
        )
        assertEquals(OcrPhotoViewport(), broken)
    }

    @Test fun invalidGestureNumbersCannotCorruptViewport() {
        val view = OcrPhotoViewport()
        val after = view.applyGesture(
            zoomChange = Float.NaN, panX = Float.POSITIVE_INFINITY, panY = 10f,
            centroidX = 150f, centroidY = 200f,
            canvasWidth = width, canvasHeight = height,
            bitmapWidth = 300f, bitmapHeight = 400f
        )
        assertEquals(view, after)
    }
}
