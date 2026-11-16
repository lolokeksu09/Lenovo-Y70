package app.pinduoduo.overlayru

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayPlacementKotlinTest {
    @Test fun doesNotOverlapAdjacentRowsOrTrailingControls() {
        val first = OverlayPlacement.fit(64, 210, 170, 40, 360, 830, 1850, 8)
        assertNotNull(first)
        first!!
        assertEquals(64, first[0])
        assertEquals(210, first[1])
        assertTrue(first[2] > 170 && first[0] + first[2] <= 690)
        assertTrue(first[3] <= 40)

        val right = OverlayPlacement.fit(710, 350, 110, 36, 360, 830, 1850, 8)
        assertNotNull(right)
        assertTrue(right!![0] + right[2] <= 822)

        val bottom = OverlayPlacement.fit(64, 1830, 160, 36, 260, 830, 1850, 8)
        assertNotNull(bottom)
        assertTrue(bottom!![1] + bottom[3] <= 1850)
    }

    @Test fun ignoresInvalidAndOffscreenNodes() {
        assertNull(OverlayPlacement.fit(100, 100, 0, 30, 200, 830, 1850, 8))
        assertNull(OverlayPlacement.fit(900, 100, 60, 30, 200, 830, 1850, 8))
        assertNull(OverlayPlacement.fit(100, 1850, 60, 30, 200, 830, 1850, 8))
    }
}
