package com.ramwiper.next.domain
import org.junit.Assert.*
import org.junit.Test
class NativeLimitsTest {
    private val healthy = Snapshot(16L * 1024 * MIB, 6L * 1024 * MIB, 256 * MIB, false)
    @Test fun lowerLimitsAreEffectiveAndCannotExceedProfile() {
        assertEquals(64 * MIB, Policy.budget(healthy, Profile.STRONG, NativeLimits(64, 50)))
        assertEquals(128 * MIB, Policy.budget(healthy, Profile.SOFT, NativeLimits(768, 350)))
        assertEquals(100L, NativeLimits(768, 350).hold(Profile.SOFT))
        assertEquals(0L, NativeLimits(64, 0).hold(Profile.STRONG))
        assertEquals(0L, Policy.budget(healthy.copy(low = true), Profile.STRONG, NativeLimits(64, 0)))
    }
    @Test fun invalidValuesReturnToPreset() {
        assertEquals(NativeLimits(), NativeLimits(-999, 10000).normalized())
    }
}
