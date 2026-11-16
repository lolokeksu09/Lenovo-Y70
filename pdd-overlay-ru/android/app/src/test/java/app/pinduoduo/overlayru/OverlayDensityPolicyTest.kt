package app.pinduoduo.overlayru

import org.junit.Assert.*
import org.junit.Test

class OverlayDensityPolicyTest {
    private fun item(x: Int, y: Int, height: Int = 26, review: Boolean = false) =
        OverlayDensityPolicy.Candidate(x, y, x + 200, y + height, review)

    @Test fun capsDensePagesAtTwelveLabels() {
        val candidates = (0 until 35).map { item(20, 60 + it * 68) }
        val selected = OverlayDensityPolicy.select(candidates, 830, 3300, 40, 110, 60)
        assertEquals(12, selected.size)
        assertTrue(selected.all { it in candidates.indices })
    }

    @Test fun givesLongReviewPriorityOverChipsAtSameLocation() {
        val labels = listOf(
            item(18, 180), item(250, 190), item(20, 260),
            item(20, 170, 310, review = true), item(20, 800)
        )
        val selected = OverlayDensityPolicy.select(labels, 830, 1800, 45, 110, 90)
        assertEquals(2, selected.size)
        assertEquals(3, selected.first())
        assertTrue(4 in selected)
        assertFalse(0 in selected)
        assertFalse(1 in selected)
    }

    @Test fun excludesBottomShopBarAndTopStatusArea() {
        val labels = listOf(item(20, 10), item(20, 120), item(20, 1720),
            item(20, 1600))
        val selected = OverlayDensityPolicy.select(labels, 830, 1800, 50, 115, 90)
        assertEquals(listOf(1, 3), selected)
    }

    @Test fun preventsMoreThanTwoChipsPerDenseHorizontalBand() {
        val labels = (0 until 7).map { item(it * 80, 180) }
        val selected = OverlayDensityPolicy.select(labels, 830, 1800, 50, 115, 100)
        assertEquals(listOf(0, 1), selected)
    }

    @Test fun excludesUnsafeAndPartlyClippedGeometry() {
        val labels = listOf(
            OverlayDensityPolicy.Candidate(-1, 100, 90, 125, false),
            OverlayDensityPolicy.Candidate(20, 100, 999, 125, false),
            OverlayDensityPolicy.Candidate(20, 100, 10, 125, false),
            item(20, 100)
        )
        assertEquals(listOf(3), OverlayDensityPolicy.select(labels, 830, 1800, 50, 115, 100))
    }

    @Test fun invalidViewportOrBandSizeFailsClosed() {
        val labels = listOf(item(20, 100))
        assertTrue(OverlayDensityPolicy.select(labels, 0, 1800, 50, 115, 90).isEmpty())
        assertTrue(OverlayDensityPolicy.select(labels, 830, 1800, 50, 1790, 90).isEmpty())
        assertTrue(OverlayDensityPolicy.select(labels, 830, 1800, 50, 115, 0).isEmpty())
    }
}
