package app.pinduoduo.overlayru

import org.junit.Assert.*
import org.junit.Test

class ReviewOverlayPlacementTest {
    @Test fun paragraphNeverExpandsAcrossAdjacentControls() {
        assertArrayEquals(intArrayOf(20, 100, 640, 290),
            OverlayPlacement.fitParagraph(20,100,640,290,1080,2400))
    }
    @Test fun clipsReviewToViewportWithoutLeavingScreen() {
        assertArrayEquals(intArrayOf(100,200,500,400),
            OverlayPlacement.fitParagraph(100,200,900,900,600,600))
    }
    @Test fun rejectsTinyOrInvalidReviewBoxes() {
        assertNull(OverlayPlacement.fitParagraph(10,10,80,40,600,600))
        assertNull(OverlayPlacement.fitParagraph(700,100,200,100,600,600))
    }
}
