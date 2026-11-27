package com.ramwiper.next.domain
import org.junit.Assert.*
import org.junit.Test
class MeminfoParserTest {
    @Test fun readsModernAndLegacyWithoutDoubleCounting() {
        val report = MeminfoParser.parse("""
            ** MEMINFO in pid 123 [test.app] **
            TOTAL 10000 2000 3000 4000
            TOTAL PSS: 12000 TOTAL RSS: 22000 TOTAL SWAP PSS: 512
            ** MEMINFO in pid 124 [test.app:remote] **
            TOTAL 4000 1000 2000 3000
        """.trimIndent())
        assertEquals(2, report.processes.size)
        assertEquals(16000L, requireNotNull(report.totalPssKiB))
        assertEquals(22000L, requireNotNull(report.processes.first().rssKiB))
        assertNull(report.totalRssKiB)
        assertNull(report.totalSwapPssKiB)
    }
    @Test fun repeatedPidIsNotCountedTwice() {
        val report = MeminfoParser.parse("** MEMINFO in pid 123 [test.app] **\nTOTAL PSS: 4\n** MEMINFO in pid 123 [test.app] **\nTOTAL PSS: 4")
        assertEquals(4L, requireNotNull(report.totalPssKiB))
    }
    @Test fun errorsTruncationAndUnsupportedFormatStayUnknown() {
        for (raw in listOf("No process found", "Ошибка диагностики", "TOTAL PSS: 0", "Вывод усечён\n** MEMINFO in pid 123 [test.app] **\nTOTAL PSS: 10"))
            assertNull(MeminfoParser.parse(raw).totalPssKiB)
    }
}
