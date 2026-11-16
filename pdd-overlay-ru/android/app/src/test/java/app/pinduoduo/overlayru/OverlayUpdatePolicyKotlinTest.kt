package app.pinduoduo.overlayru

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayUpdatePolicyKotlinTest {
    @Test fun acceptsOnlyCurrentVisibleValidOverlay() {
        assertTrue(OverlayUpdatePolicy.shouldRedraw(true, true, true, true))
        assertFalse(OverlayUpdatePolicy.shouldRedraw(true, true, false, true))
        assertFalse(OverlayUpdatePolicy.shouldRedraw(false, true, true, true))
        assertFalse(OverlayUpdatePolicy.shouldRedraw(true, false, true, true))
        assertFalse(OverlayUpdatePolicy.shouldRedraw(true, true, true, false))
    }
}
