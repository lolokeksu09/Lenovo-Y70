package app.pinduoduo.overlayru

import org.junit.Assert.*
import org.junit.Test

class OcrRegionTest {
    @Test fun normalizesAndClampsToImage() {
        assertEquals(
            OcrRegion(0f, 0.25f, 1f, 0.75f),
            OcrRegion.fromPixels(-5, 50, 900, 150, 400, 200)
        )
        assertNull(OcrRegion.fromPixels(100, 100, 99, 200, 400, 400))
        assertNull(OcrRegion.fromPixels(0, 0, 10, 10, 0, 400))
    }

    @Test fun squareImageCenteredInLandscapeCanvas() {
        val region=OcrRegion.fromPixels(0, 0, 100, 100, 100, 100)!!
        assertEquals(OcrRegion.Px(100f, 0f, 200f, 100f),
            region.intoCanvas(300f, 100f, 100f, 100f))
    }

    @Test fun tallScreenshotLetterboxedInsidePhoneViewport() {
        val region=OcrRegion.fromPixels(0, 0, 100, 1000, 100, 1000)!!
        assertEquals(OcrRegion.Px(130f, 0f, 170f, 400f),
            region.intoCanvas(300f, 400f, 100f, 1000f))
    }

    @Test fun narrowSourceLineMapsPreciselyIntoFittedPreview() {
        val region=OcrRegion.fromPixels(100, 100, 500, 200, 1000, 2000)!!
        assertEquals(OcrRegion.Px(70f, 20f, 150f, 40f),
            region.intoCanvas(300f, 400f, 1000f, 2000f))
    }

    @Test fun hitTestRejectsLetterboxing() {
        val region=OcrRegion.fromPixels(0, 0, 100, 1000, 100, 1000)!!
        val p=region.intoCanvas(300f, 400f, 100f, 1000f)!!
        assertTrue(p.contains(145f, 170f))
        assertFalse(p.contains(10f, 170f))
        assertFalse(p.contains(145f, 430f))
    }

    @Test fun zeroCanvasDimensionFailsClosed() {
        val region=OcrRegion(0f, 0f, 1f, 1f)
        assertNull(region.intoCanvas(0f, 100f, 200f, 100f))
        assertNull(region.intoCanvas(100f, 100f, 0f, 100f))
    }
}
