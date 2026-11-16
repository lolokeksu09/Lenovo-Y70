package app.pinduoduo.overlayru

import org.junit.Assert.*
import org.junit.Test

class OcrImagePolicyTest {
    @Test fun validPhotosAreNotUpscaled() {
        assertEquals(1080 to 1920, OcrImagePolicy.targetSize(1080, 1920))
        assertEquals(1 to 1, OcrImagePolicy.targetSize(1, 1))
        assertEquals(0, OcrImagePolicy.sampleSize(100, 100) - 1)
    }

    @Test fun veryTallScreenshotsFitImageDecoderBounds() {
        assertEquals(384 to 2560, OcrImagePolicy.targetSize(1200, 8000))
        assertEquals(4, OcrImagePolicy.sampleSize(1200, 8000))
    }

    @Test fun wideAndHighResolutionImagesPreserveAspectRatio() {
        assertEquals(2560 to 1280, OcrImagePolicy.targetSize(8000, 4000))
        assertEquals(2560 to 2560, OcrImagePolicy.targetSize(5000, 5000))
        assertEquals(2, OcrImagePolicy.sampleSize(5000, 5000))
    }

    @Test fun missingOrCorruptImageDimensionsFailClosed() {
        assertNull(OcrImagePolicy.targetSize(0, 1024))
        assertNull(OcrImagePolicy.targetSize(900, -1))
        assertNull(OcrImagePolicy.targetSize(Int.MIN_VALUE, 900))
        assertNull(OcrImagePolicy.targetSize(900, 800, edge = 0))
    }

    @Test fun encodedInputIsBoundedIncludingExactBoundary() {
        assertTrue(OcrImagePolicy.withinEncodedLimit(0))
        assertTrue(OcrImagePolicy.withinEncodedLimit(OcrImagePolicy.MAX_ENCODED_BYTES.toLong()))
        assertFalse(OcrImagePolicy.withinEncodedLimit(-1))
        assertFalse(OcrImagePolicy.withinEncodedLimit(OcrImagePolicy.MAX_ENCODED_BYTES + 1L))
    }
}
