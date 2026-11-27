package com.ramwiper.next.data

import com.ramwiper.next.domain.*
import org.json.JSONException
import org.junit.Assert.*
import org.junit.Test

class HistoryCodecTest {
    private val legacy = """{"time":123,"engine":"NATIVE","profile":"SOFT","before":100,"after":200,"allocated":16,"accepted":0,"attempted":0,"cancelled":false,"note":""}"""
    @Test fun oldRecordStillDecodes() {
        val record = HistoryCodec.decode("[$legacy]").single()
        assertEquals(123L, record.time)
        assertEquals(100L, record.delta)
        assertEquals(Outcome.LEGACY, record.outcome)
        assertEquals(CleanupTrigger.MANUAL, record.trigger)
        assertNull(record.settled)
        assertNull(record.durationMs)
    }
    @Test fun newRecordRoundTrips() {
        val record = CleanupResult(123L, "HYBRID", "SOFT", 100, 200, 16, 1, 2, false, "test",
            Outcome.PARTIAL, 180, 4000, -20, CleanupTrigger.AUTOMATIC)
        assertEquals(listOf(record), HistoryCodec.decode(HistoryCodec.encode(listOf(record))))
    }
    @Test fun comparisonRoundTripsWithMissingMeasurement() {
        val sample = AppMemorySample("test.app", 2048, null, 0, 1, 123, null)
        val r = CleanupResult(1,"NATIVE","SOFT",100,200,16,0,0,false,"",
            Outcome.COMPLETED, comparisons = listOf(AppMemoryComparison(sample.pkg, sample, null)))
        assertEquals(listOf(r), HistoryCodec.decode(HistoryCodec.encode(listOf(r))))
        assertTrue(HistoryCodec.decode("[$legacy]").single().comparisons.isEmpty())
    }
    @Test fun nativePlanFieldsRoundTrip() {
        val r = CleanupResult(1,"NATIVE","STRONG",100,200,16,0,0,false,"",
            Outcome.COMPLETED, effectiveNativeProfile="SOFT", plannedNativeBytes=128L*MIB,
            nativeDecision="Неполная телеметрия", availablePercentBefore=25.5)
        assertEquals(listOf(r), HistoryCodec.decode(HistoryCodec.encode(listOf(r))))
        assertNull(HistoryCodec.decode("[$legacy]").single().effectiveNativeProfile)
    }
    @Test fun malformedItemDoesNotHideValidRecords() {
        assertEquals(1, HistoryCodec.decode("[{},$legacy]").size)
    }
    @Test fun malformedRootFailsInsteadOfPretendingHistoryIsEmpty() {
        assertThrows(JSONException::class.java) { HistoryCodec.decode("{broken") }
    }
}
