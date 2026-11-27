package com.ramwiper.next.domain
import org.junit.Assert.*
import org.junit.Test
class HistoryAnalyticsTest {
    private fun record(delta: Long?, outcome: Outcome, trigger: CleanupTrigger = CleanupTrigger.MANUAL) =
        CleanupResult(1,"NATIVE","SOFT",1000,1000,0,0,0,false,"",outcome,delta?.plus(1000),trigger=trigger)
    @Test fun medianAndSuccessUseOnlySettledCompletedOrPartial() {
        val s = HistoryAnalytics.summarize(listOf(record(100,Outcome.COMPLETED), record(-20,Outcome.PARTIAL,CleanupTrigger.AUTOMATIC),
            record(900,Outcome.CANCELLED), record(null,Outcome.SKIPPED)))
        assertEquals(4,s.count); assertEquals(2,s.measured); assertEquals(1,s.positive)
        assertEquals(40L,requireNotNull(s.medianDelta)); assertEquals(1,s.automatic)
    }
    @Test fun emptyHistoryHasNoMedian() { assertNull(HistoryAnalytics.summarize(emptyList()).medianDelta) }
}
