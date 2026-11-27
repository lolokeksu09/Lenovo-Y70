package com.ramwiper.next.domain
import org.junit.Assert.*
import org.junit.Test
class KernelMemoryParserTest {
    @Test fun parsesOnlyValidByteCounters() {
        val result = KernelMemoryParser.meminfo(sequenceOf("MemFree: 2048 kB", "Cached: 10 kB", "Dirty: 0 kB",
            "SwapTotal: -1 kB", "SwapFree: 9223372036854775807 kB", "AnonPages: 12 MB", "MemTotal: 10 kB"))
        assertEquals(mapOf("MemFree" to 2097152L, "Cached" to 10240L, "Dirty" to 0L), result)
    }
    @Test fun separatesSomeAndFullAndRejectsBadValues() {
        val lines=listOf("some avg10=2.50 avg60=1.00 total=123", "full avg10=0.25 avg60=0.00 total=12")
        assertEquals(2.5, requireNotNull(KernelMemoryParser.pressureAvg10(lines,"some")),0.0)
        assertEquals(0.25, requireNotNull(KernelMemoryParser.pressureAvg10(lines,"full")),0.0)
        assertNull(KernelMemoryParser.pressureAvg10(listOf("some avg10=NaN"),"some"))
        assertNull(KernelMemoryParser.pressureAvg10(listOf("some avg10=101"),"some"))
        assertNull(KernelMemoryParser.pressureAvg10(emptyList(),"full"))
    }
}
