package app.pinduoduo.overlayru

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayCoordinatesKotlinTest {
    @Test fun subtractsScreenInsetsExactlyOnce() {
        val rect = OverlayCoordinates.toLocalBounds(64, 118, 270, 146, 0, 80)
        assertArrayEquals(intArrayOf(64, 38, 206, 28), rect)
        assertEquals(118, rect[1] + 80)
        assertArrayEquals(intArrayOf(88, 350, 190, 36),
            OverlayCoordinates.toLocalBounds(120, 350, 310, 386, 32, 0))
        assertArrayEquals(intArrayOf(20, 40, 10, 5),
            OverlayCoordinates.toLocalBounds(20, 40, 30, 45, 0, 0))
    }
    @Test fun malformedBoundsCannotProduceNegativeSize() {
        assertArrayEquals(intArrayOf(10, 10, 0, 0),
            OverlayCoordinates.toLocalBounds(10, 10, 0, 0, 0, 0))
    }
}
