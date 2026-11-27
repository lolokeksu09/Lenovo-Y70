package com.ramwiper.next.domain
import org.junit.Assert.*
import org.junit.Test
class AdaptivePolicyTest {
    private val sample = Snapshot(8L*1024*MIB, 2L*1024*MIB, 128*MIB, false, thermal=0, psiSome=0.1)
    private val adaptive = NativeLimits(adaptive=true)
    @Test fun sufficientMemorySkipsOnlyWhenOptedIn() {
        val high = sample.copy(available=4L*1024*MIB)
        assertEquals(0L, AdaptivePolicy.plan(high,Profile.STRONG,adaptive).budget)
        assertTrue(AdaptivePolicy.plan(high,Profile.STRONG,NativeLimits()).budget > 0)
    }
    @Test fun uncertaintyAndPressureLowerProfileButNeverRaiseIt() {
        assertEquals(Profile.BALANCED,AdaptivePolicy.plan(sample,Profile.STRONG,adaptive).profile)
        assertEquals(Profile.SOFT,AdaptivePolicy.plan(sample.copy(psiSome=null),Profile.STRONG,adaptive).profile)
        assertEquals(Profile.SOFT,AdaptivePolicy.plan(sample.copy(thermal=2),Profile.STRONG,adaptive).profile)
        assertEquals(Profile.SOFT,AdaptivePolicy.plan(sample,Profile.SOFT,adaptive).profile)
        assertEquals(0L,AdaptivePolicy.plan(sample.copy(low=true),Profile.STRONG,adaptive).budget)
        assertEquals(64*MIB,AdaptivePolicy.plan(sample,Profile.STRONG,adaptive.copy(capMiB=64)).budget)
    }
}
