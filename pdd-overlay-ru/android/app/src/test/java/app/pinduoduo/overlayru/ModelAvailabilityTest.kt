package app.pinduoduo.overlayru

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A successful local scan is authoritative; a timeout is not. */
class ModelAvailabilityTest {
    @Test fun successfulScanKeepsPairedChineseAndRussianModelReady() {
        assertTrue(ModelAvailability.afterInspection(false, true, true, true))
    }

    @Test fun sourceModelRemovalClearsStaleReadyState() {
        assertFalse(ModelAvailability.afterInspection(true, false, true, true))
    }

    @Test fun russianModelRemovalClearsBothSourcePairReadiness() {
        assertFalse(ModelAvailability.afterInspection(true, true, false, true))
    }

    @Test fun failedScanPreservesPreviouslyConfirmedReadyState() {
        assertTrue(ModelAvailability.afterInspection(true, false, false, false))
    }

    @Test fun failedScanDoesNotInventAvailability() {
        assertFalse(ModelAvailability.afterInspection(false, true, true, false))
    }
}
