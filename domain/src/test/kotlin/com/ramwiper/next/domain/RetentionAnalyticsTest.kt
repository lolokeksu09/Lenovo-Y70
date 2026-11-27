package com.ramwiper.next.domain
import org.junit.Assert.*
import org.junit.Test
class RetentionAnalyticsTest {
    @Test fun onlyPairedValidMeasurementsContribute() {
        val r=CleanupResult(1,"NATIVE","SOFT",100,200,16,0,0,false,"",Outcome.COMPLETED,200,extendedAvailable=150)
        val rows=RetentionAnalytics.summarize(listOf(r,r.copy(settled=140,extendedAvailable=90),r.copy(extendedAvailable=null),r.copy(cancelled=true)))
        assertEquals(2,rows.count); assertEquals(2,rows.positiveAtThree); assertEquals(1,rows.positiveAtTen)
        assertEquals(-50L,requireNotNull(rows.medianChange))
        assertNull(RetentionAnalytics.summarize(emptyList()).medianChange)
    }
}
