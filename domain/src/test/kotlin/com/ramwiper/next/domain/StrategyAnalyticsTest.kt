package com.ramwiper.next.domain
import org.junit.Assert.*
import org.junit.Test
class StrategyAnalyticsTest {
    @Test fun groupsActualRoutesAndExcludesAutoAndUnmeasured() {
        val r=CleanupResult(1,"HYBRID","STRONG",100,200,16,0,0,false,"",Outcome.COMPLETED,200,1000,10,
            effectiveNativeProfile="SOFT")
        val rows=StrategyAnalytics.compare(listOf(r,r.copy(settled=50),r.copy(trigger=CleanupTrigger.AUTOMATIC),
            r.copy(settled=null),r.copy(allocated=0,accepted=1,effectiveNativeProfile=null),
            r.copy(effectiveNativeProfile=null)))
        val native=rows.single { it.engine=="NATIVE" && it.profile=="SOFT" }
        assertEquals(2,native.count); assertEquals(1,native.positive); assertEquals(25L,requireNotNull(native.medianDelta))
        assertTrue(rows.any { it.engine=="SHIZUKU" })
        assertTrue(rows.any { it.profile=="UNKNOWN" })
    }
}
