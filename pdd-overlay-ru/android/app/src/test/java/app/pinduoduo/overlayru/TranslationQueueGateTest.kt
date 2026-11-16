package app.pinduoduo.overlayru

import org.junit.Assert.*
import org.junit.Test
import java.util.function.Consumer

class TranslationQueueGateTest {
    @Test fun boundedRequestsAndSlotRecovery() {
        val gate = TranslationQueueGate(capacity = 2)
        val a = gate.reserve("a", 100L, null).ticket!!
        val b = gate.reserve("b", 100L, null).ticket!!
        assertEquals(2, gate.activeCount())
        assertNull(gate.reserve("c", 100L, null).ticket)
        assertNotNull(gate.finish("a", a, 101L))
        assertNotNull(gate.reserve("c", 102L, null).ticket)
        assertNotNull(gate.finish("b", b, 102L))
    }

    @Test fun duplicateRequestIsSingleFlightWithBoundedCallbacks() {
        val gate = TranslationQueueGate(callbackLimit = 2)
        val called = mutableListOf<String>()
        val ticket = gate.reserve("same", 0L, Consumer { called.add("first:" + it) }).ticket!!
        assertTrue(gate.reserve("same", 1L, Consumer { called.add("second:" + it) }).joined)
        assertTrue(gate.reserve("same", 2L, Consumer { called.add("third:" + it) }).joined)
        assertEquals(1, gate.activeCount())
        gate.finish("same", ticket, 3L)!!.forEach { it.accept("RU") }
        assertEquals(listOf("first:RU", "second:RU"), called)
    }

    @Test fun timedOutTaskCannotOverrideNewerTranslation() {
        val gate = TranslationQueueGate(capacity = 1, timeoutMs = 100L, retryDelayMs = 50L)
        val old = gate.reserve("same", 0L, null).ticket!!
        assertNull(gate.reserve("same", 100L, null).ticket)
        assertEquals(0, gate.activeCount())
        assertNull(gate.finish("same", old, 101L))
        assertNull(gate.reserve("same", 149L, null).ticket)
        val newer = gate.reserve("same", 150L, null).ticket!!
        assertNotEquals(old, newer)
        assertNull(gate.finish("same", old, 151L))
        assertNotNull(gate.finish("same", newer, 152L))
    }

    @Test fun expiryReleasesCapacityWithoutLeakingOriginalCallbacks() {
        val gate = TranslationQueueGate(capacity = 1, timeoutMs = 10L, retryDelayMs = 5L)
        val old = gate.reserve("old", 100L, Consumer { fail("Expired listener must not be called") }).ticket!!
        val newer = gate.reserve("new", 110L, null).ticket!!
        assertNull(gate.finish("old", old, 111L))
        assertNotNull(gate.finish("new", newer, 111L))
        assertEquals(0, gate.activeCount())
    }

    @Test fun failuresCanRetryImmediatelyAfterFinishedTask() {
        val gate = TranslationQueueGate(capacity = 1)
        val first = gate.reserve("retry", 0L, null).ticket!!
        assertNotNull(gate.finish("retry", first, 5L))
        assertNotNull(gate.reserve("retry", 6L, null).ticket)
    }

    @Test fun invalidLimitsAreRejected() {
        try {
            TranslationQueueGate(capacity = 0)
            fail("Zero capacity must be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
